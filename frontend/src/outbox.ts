// Assessment records are queued on the device and sent when the connection allows.
// The server ignores a clientId it has already stored, so resending is safe.
import { api, ApiError, type AchievementIn, type ExerciseScoreIn } from './api'

export interface Outbox<T extends { clientId: string }> {
  pending: () => T[]
  enqueue: (item: T) => void
  /**
   * Sends everything queued. Returns how many remain unsent. A record the server refuses (400) is dropped, not
   * retried for ever, and each reason is passed to `onRefused` so the page can tell the teacher.
   */
  flush: (onRefused?: (reason: string) => void) => Promise<number>
}

/** The server read the record and will never accept it (a network fault or a sign-out is worth trying again). */
function refused(e: unknown): e is ApiError {
  return e instanceof ApiError && e.status === 400
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
    flush: async (onRefused) => {
      const queued = pending()
      if (queued.length === 0 || !navigator.onLine) return queued.length
      const settled = new Set<string>()
      const reasons = new Set<string>()
      try {
        await send(queued)
        queued.forEach((s) => settled.add(s.clientId))
      } catch (e) {
        if (!refused(e)) return pending().length
        if (queued.length === 1) {
          settled.add(queued[0].clientId)
          reasons.add(e.message)
        } else {
          // Something in the batch will never be accepted. Send one at a time so the rest still go, and drop
          // only what is refused.
          for (const item of queued) {
            try {
              await send([item])
              settled.add(item.clientId)
            } catch (one) {
              if (!refused(one)) break
              settled.add(item.clientId)
              reasons.add(one.message)
            }
          }
        }
      }
      const remaining = pending().filter((s) => !settled.has(s.clientId))
      save(remaining)
      reasons.forEach((r) => onRefused?.(r))
      return remaining.length
    },
  }
}

/** Pat's exercise scores (1 to 4). */
export const exerciseOutbox = makeOutbox<ExerciseScoreIn>('mm.exercise.outbox', (q) => api.submitExerciseScores(q))

/** Movements recorded as secure on the developmental charts. */
export const milestoneOutbox = makeOutbox<AchievementIn>('mm.milestone.outbox', (q) => api.submitAchievements(q))
