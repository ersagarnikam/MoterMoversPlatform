// Home sessions are saved on the phone first and sent when there is a connection. The server ignores
// a clientId it has already stored, so resending is safe.
import { api, type HomeEntry } from './api'
import { getLocal, setLocal } from './storage'

const KEY = 'mm.home.outbox'

export function pending(): Promise<HomeEntry[]> {
  return getLocal<HomeEntry[]>(KEY, [])
}

export async function enqueue(entry: HomeEntry): Promise<void> {
  await setLocal(KEY, [...(await pending()), entry])
}

let sending: Promise<number> | null = null

/** Sends everything queued. Returns how many remain unsent. */
export function flush(): Promise<number> {
  sending ??= (async () => {
    const queued = await pending()
    if (queued.length === 0) return 0
    try {
      await api.recordHome(queued)
      const sent = new Set(queued.map((e) => e.clientId))
      const remaining = (await pending()).filter((e) => !sent.has(e.clientId))
      await setLocal(KEY, remaining)
      return remaining.length
    } catch {
      return (await pending()).length
    }
  })().finally(() => {
    sending = null
  })
  return sending
}
