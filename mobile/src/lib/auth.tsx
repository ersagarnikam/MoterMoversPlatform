import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { api, ApiError, setApiToken, setUnauthorizedHandler, type CurrentUser } from './api'
import { clearLocal, getLocal, loadToken, saveToken, setLocal } from './storage'

const USER_KEY = 'mm.user'

const PARENTS_ONLY =
  'This app is for parents and carers. School staff and practitioners can use the Motor Movers website.'

interface Auth {
  user: CurrentUser | null
  loading: boolean
  signIn: (email: string, password: string) => Promise<void>
  register: (displayName: string, email: string, password: string) => Promise<void>
  signOut: () => Promise<void>
}

const AuthContext = createContext<Auth | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<CurrentUser | null>(null)
  const [loading, setLoading] = useState(true)

  const signOut = useCallback(async () => {
    setApiToken(null)
    await saveToken(null)
    await clearLocal()
    setUser(null)
  }, [])

  const accept = useCallback(async (token: string, me: CurrentUser) => {
    if (me.role !== 'PARENT') throw new ApiError(403, PARENTS_ONLY)
    setApiToken(token)
    await saveToken(token)
    await setLocal(USER_KEY, me)
    setUser(me)
  }, [])

  useEffect(() => {
    setUnauthorizedHandler(() => void signOut())
    ;(async () => {
      const token = await loadToken()
      if (token) {
        setApiToken(token)
        try {
          const me = await api.me()
          if (me.role === 'PARENT') {
            await setLocal(USER_KEY, me)
            setUser(me)
          } else await signOut()
        } catch (e) {
          // Offline at launch: keep the saved sign-in rather than signing the parent out.
          const cached = await getLocal<CurrentUser | null>(USER_KEY, null)
          if (e instanceof ApiError && e.status === 0 && cached) setUser(cached)
          else await signOut()
        }
      }
      setLoading(false)
    })()
    return () => setUnauthorizedHandler(null)
  }, [signOut])

  const value = useMemo<Auth>(
    () => ({
      user,
      loading,
      signOut,
      signIn: async (email, password) => {
        const { token, user: me } = await api.login(email.trim(), password)
        await accept(token, me)
      },
      register: async (displayName, email, password) => {
        const { token, user: me } = await api.register(displayName.trim(), email.trim(), password)
        await accept(token, me)
      },
    }),
    [user, loading, signOut, accept],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): Auth {
  const auth = useContext(AuthContext)
  if (!auth) throw new Error('useAuth outside AuthProvider')
  return auth
}
