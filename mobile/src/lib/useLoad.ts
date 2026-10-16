import { useFocusEffect } from 'expo-router'
import { useCallback, useRef, useState } from 'react'

/** Loads data when the screen comes into view, and again on pull-to-refresh. */
export function useLoad<T>(load: () => Promise<T>, deps: unknown[] = []) {
  const [data, setData] = useState<T | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [refreshing, setRefreshing] = useState(false)
  const latest = useRef(0)

  // eslint-disable-next-line react-hooks/exhaustive-deps
  const run = useCallback(load, deps)

  const reload = useCallback(
    async (pull = false) => {
      const id = ++latest.current
      if (pull) setRefreshing(true)
      try {
        const result = await run()
        if (id === latest.current) {
          setData(result)
          setError(null)
        }
      } catch (e) {
        if (id === latest.current) setError(e instanceof Error ? e.message : 'Something went wrong')
      } finally {
        if (id === latest.current) setRefreshing(false)
      }
    },
    [run],
  )

  useFocusEffect(
    useCallback(() => {
      void reload()
    }, [reload]),
  )

  return { data, error, refreshing, reload, setData }
}
