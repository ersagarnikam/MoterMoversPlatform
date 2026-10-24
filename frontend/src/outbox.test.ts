import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Score } from './api'

// A plain stand-in for the API (not a vi.fn spy, which keeps its own copy of a failed promise and reports it as
// unhandled): it records each send and answers with whatever the test sets.
const server = vi.hoisted(() => ({
  sent: [] as unknown[][],
  answer: (): Promise<unknown> => Promise.resolve({ saved: 0, duplicates: 0 }),
}))
vi.mock('./api', () => ({
  api: {
    submitScores: (scores: unknown[]) => {
      server.sent.push(scores)
      return server.answer()
    },
  },
}))

const { enqueue, flush, pending } = await import('./outbox')

/** A minimal localStorage, and whether the device is online. */
function stubBrowser(online: boolean, store = new Map<string, string>()) {
  vi.stubGlobal('localStorage', {
    getItem: (k: string) => store.get(k) ?? null,
    setItem: (k: string, v: string) => store.set(k, v),
  })
  vi.stubGlobal('navigator', { onLine: online })
  return store
}

const score = (clientId: string): Score => ({
  clientId,
  childId: 7,
  itemCode: 'GM_HOP',
  score: 2,
  screenedOn: '2026-10-24',
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

  it('treats unreadable storage as an empty queue rather than failing', () => {
    stubBrowser(true, new Map([['mm.screening.outbox', 'not json']]))
    expect(pending()).toEqual([])
  })
})
