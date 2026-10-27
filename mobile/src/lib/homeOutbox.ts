// Motor Movers at Home: sessions and ratings are saved on the phone first and sent when there is a connection. The
// server ignores a clientId it has already stored, so resending is safe.
import { api, type ExerciseRating, type HomeSessionEntry } from './api'
import { getLocal, setLocal } from './storage'

function queue<T extends { clientId: string }>(key: string, send: (items: T[]) => Promise<unknown>) {
  const pending = () => getLocal<T[]>(key, [])
  let sending: Promise<number> | null = null
  return {
    pending,
    enqueue: async (items: T[]) => setLocal(key, [...(await pending()), ...items]),
    /** Sends everything queued. Returns how many remain unsent. */
    flush: (): Promise<number> => {
      sending ??= (async () => {
        const queued = await pending()
        if (queued.length === 0) return 0
        try {
          await send(queued)
          const sent = new Set(queued.map((e) => e.clientId))
          const remaining = (await pending()).filter((e) => !sent.has(e.clientId))
          await setLocal(key, remaining)
          return remaining.length
        } catch {
          return (await pending()).length
        }
      })().finally(() => {
        sending = null
      })
      return sending
    },
  }
}

export const homeSessions = queue<HomeSessionEntry>('mm.home.sessions', (q) => api.recordHomeSessions(q))

export const homeRatings = queue<ExerciseRating>('mm.home.ratings', (q) => api.rateExercises(q))

/** Sends both queues; sessions first, so the module count is up to date. Returns how many remain. */
export async function flushHome(): Promise<number> {
  return (await homeSessions.flush()) + (await homeRatings.flush())
}
