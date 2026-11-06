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

/** "1 session and 2 ratings are": what the banner counts, sessions and ratings each by name. */
export function unsentWords(sessions: number, ratings: number): string {
  const count = (n: number, word: string) => `${n} ${word}${n === 1 ? '' : 's'}`
  const parts = [sessions > 0 && count(sessions, 'session'), ratings > 0 && count(ratings, 'rating')].filter(Boolean)
  return `${parts.join(' and ')} ${sessions + ratings === 1 ? 'is' : 'are'}`
}

/** Sends both queues; sessions first, so the module count is up to date. Returns how many remain. */
export async function flushHome(): Promise<number> {
  return (await homeSessions.flush()) + (await homeRatings.flush())
}
