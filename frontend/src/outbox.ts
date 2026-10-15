// Screening scores are queued on the device and sent when the connection allows.
// The server ignores a clientId it has already stored, so resending is safe.
import { api, type Score } from './api'

const KEY = 'mm.screening.outbox'

export function pending(): Score[] {
  try {
    return JSON.parse(localStorage.getItem(KEY) ?? '[]') as Score[]
  } catch {
    return []
  }
}

function save(scores: Score[]) {
  try {
    localStorage.setItem(KEY, JSON.stringify(scores))
  } catch {
    // storage full or blocked: the score stays in memory only until the page closes
  }
}

export function enqueue(score: Score) {
  save([...pending(), score])
}

/** Sends everything queued. Returns how many remain unsent. */
export async function flush(): Promise<number> {
  const queued = pending()
  if (queued.length === 0 || !navigator.onLine) return queued.length
  try {
    await api.submitScores(queued)
    const sent = new Set(queued.map((s) => s.clientId))
    const remaining = pending().filter((s) => !sent.has(s.clientId))
    save(remaining)
    return remaining.length
  } catch {
    return pending().length
  }
}
