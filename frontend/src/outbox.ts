// Assessment records are queued on the device and sent when the connection allows.
// The server ignores a clientId it has already stored, so resending is safe.
import { api, type AchievementIn, type ExerciseScoreIn, type Score } from './api'

export interface Outbox<T extends { clientId: string }> {
  pending: () => T[]
  enqueue: (item: T) => void
  /** Sends everything queued. Returns how many remain unsent. */
  flush: () => Promise<number>
}

export function makeOutbox<T extends { clientId: string }>(
  key: string,
  send: (items: T[]) => Promise<unknown>,
): Outbox<T> {
  function pending(): T[] {
    try {
      return JSON.parse(localStorage.getItem(key) ?? '[]') as T[]
    } catch {
      return []
    }
  }

  function save(items: T[]) {
    try {
      localStorage.setItem(key, JSON.stringify(items))
    } catch {
      // storage full or blocked: the record stays in memory only until the page closes
    }
  }

  return {
    pending,
    enqueue: (item) => save([...pending(), item]),
    flush: async () => {
      const queued = pending()
      if (queued.length === 0 || !navigator.onLine) return queued.length
      try {
        await send(queued)
        const sent = new Set(queued.map((s) => s.clientId))
        const remaining = pending().filter((s) => !sent.has(s.clientId))
        save(remaining)
        return remaining.length
      } catch {
        return pending().length
      }
    },
  }
}

/** The current 0 to 3 readiness check. */
const screening = makeOutbox<Score>('mm.screening.outbox', (q) => api.submitScores(q))
export const { pending, enqueue, flush } = screening

/** Pat's exercise scores (1 to 4). */
export const exerciseOutbox = makeOutbox<ExerciseScoreIn>('mm.exercise.outbox', (q) => api.submitExerciseScores(q))

/** Movements recorded as secure on the developmental charts. */
export const milestoneOutbox = makeOutbox<AchievementIn>('mm.milestone.outbox', (q) => api.submitAchievements(q))
