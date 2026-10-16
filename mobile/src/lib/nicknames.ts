// Schools usually hold children by initials only. A parent can give their child a name that is
// shown in the app; it is kept on this phone and never sent to Motor Movers.
import { useCallback, useEffect, useState } from 'react'
import { getLocal, setLocal } from './storage'

const KEY = 'mm.nicknames'

export function useNicknames() {
  const [names, setNames] = useState<Record<string, string>>({})

  useEffect(() => {
    getLocal<Record<string, string>>(KEY, {}).then(setNames)
  }, [])

  const setNickname = useCallback(async (childId: number, name: string) => {
    const all = await getLocal<Record<string, string>>(KEY, {})
    if (name.trim()) all[childId] = name.trim()
    else delete all[childId]
    await setLocal(KEY, all)
    setNames(all)
  }, [])

  const nameFor = useCallback(
    (child: { childId: number; name: string }) => names[child.childId] ?? child.name,
    [names],
  )

  return { names, nameFor, setNickname }
}
