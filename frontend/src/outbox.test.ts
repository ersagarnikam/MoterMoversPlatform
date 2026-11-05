import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { ExerciseScoreIn } from './api'

// A plain stand-in for the API (not a vi.fn spy, which keeps its own copy of a failed promise and reports it as
// unhandled): it records each send and answers with whatever the test sets.
const server = vi.hoisted(() => ({
  sent: [] as { clientId: string }[][],
  answer: (_scores: { clientId: string }[]): Promise<unknown> => Promise.resolve({ saved: 0, duplicates: 0 }),
}))
// The API client's error, as the outbox tells a refusal (400) from a fault worth retrying by its status.
const { ApiError } = vi.hoisted(() => ({
  ApiError: class ApiError extends Error {
    constructor(
      public status: number,
      message: string,
    ) {
      super(message)
    }
  },
}))
vi.mock('./api', () => ({
  ApiError,
  api: {
    submitExerciseScores: (scores: { clientId: string }[]) => {
      server.sent.push(scores)
      return server.answer(scores)
    },
  },
}))

const { exerciseOutbox } = await import('./outbox')
const { enqueue, flush, pending } = exerciseOutbox

/** A minimal localStorage, and whether the device is online. */
function stubBrowser(online: boolean, store = new Map<string, string>()) {
  vi.stubGlobal('localStorage', {
    getItem: (k: string) => store.get(k) ?? null,
    setItem: (k: string, v: string) => store.set(k, v),
  })
  vi.stubGlobal('navigator', { onLine: online })
  return store
}

const score = (clientId: string): ExerciseScoreIn => ({
  clientId,
  childId: 7,
  exerciseId: 3,
  point: 'START',
  score: 2,
  assessedOn: '2026-10-24',
})

describe('the offline assessment queue', () => {
  beforeEach(() => {
    server.sent = []
    server.answer = () => Promise.resolve({ saved: 0, duplicates: 0 })
  })
  afterEach(() => vi.unstubAllGlobals())

  it('keeps scores on the device until they are sent', () => {
    stubBrowser(true)
    enqueue(score('a'))
    enqueue(score('b'))
    expect(pending().map((s) => s.clientId)).toEqual(['a', 'b'])
  })

  it('sends nothing while offline, and keeps everything', async () => {
    stubBrowser(false)
    enqueue(score('a'))
    expect(await flush()).toBe(1)
    expect(server.sent).toEqual([])
    expect(pending()).toHaveLength(1)
  })

  it('sends everything once online and empties the queue', async () => {
    stubBrowser(true)
    enqueue(score('a'))
    enqueue(score('b'))
    expect(await flush()).toBe(0)
    expect(server.sent).toEqual([[score('a'), score('b')]])
    expect(pending()).toEqual([])
  })

  it('keeps scores added while a send was in flight', async () => {
    stubBrowser(true)
    enqueue(score('a'))
    server.answer = async () => {
      enqueue(score('late'))
      return { saved: 1, duplicates: 0 }
    }
    expect(await flush()).toBe(1)
    expect(pending().map((s) => s.clientId)).toEqual(['late'])
  })

  it('keeps everything when sending fails, to try again later', async () => {
    stubBrowser(true)
    enqueue(score('a'))
    server.answer = () => Promise.reject(new Error('network'))
    expect(await flush()).toBe(1)
    expect(pending().map((s) => s.clientId)).toEqual(['a'])
  })

  it('keeps everything when the server is down, to try again later', async () => {
    stubBrowser(true)
    enqueue(score('a'))
    server.answer = () => Promise.reject(new ApiError(503, 'Service unavailable'))
    const reasons: string[] = []
    expect(await flush((r) => reasons.push(r))).toBe(1)
    expect(reasons).toEqual([])
  })

  it('drops a score the server refuses and says why, rather than retrying it for ever', async () => {
    stubBrowser(true)
    enqueue(score('bad'))
    server.answer = () => Promise.reject(new ApiError(400, 'The date cannot be in the future'))
    const reasons: string[] = []
    expect(await flush((r) => reasons.push(r))).toBe(0)
    expect(pending()).toEqual([])
    expect(reasons).toEqual(['The date cannot be in the future'])
    expect(await flush()).toBe(0)
    expect(server.sent).toHaveLength(1)
  })

  it('still sends the rest of a batch when one score in it is refused', async () => {
    stubBrowser(true)
    enqueue(score('a'))
    enqueue(score('bad'))
    enqueue(score('b'))
    server.answer = (scores) =>
      scores.some((s) => s.clientId === 'bad')
        ? Promise.reject(new ApiError(400, 'Not covered by the licence'))
        : Promise.resolve({ saved: scores.length, duplicates: 0 })
    const reasons: string[] = []
    expect(await flush((r) => reasons.push(r))).toBe(0)
    expect(server.sent.slice(1).map((batch) => batch.map((s) => s.clientId))).toEqual([['a'], ['bad'], ['b']])
    expect(reasons).toEqual(['Not covered by the licence'])
  })

  it('treats unreadable storage as an empty queue rather than failing', () => {
    stubBrowser(true, new Map([['mm.exercise.outbox', 'not json']]))
    expect(pending()).toEqual([])
  })
})
