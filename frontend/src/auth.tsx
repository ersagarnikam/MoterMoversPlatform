import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react'
import { api, getToken, setToken, type CurrentUser } from './api'

interface AuthState {
  user: CurrentUser | null
  loading: boolean
  signIn: (email: string, password: string) => Promise<CurrentUser>
  /** Creates a parent's account and signs them in. */
  register: (email: string, password: string, displayName: string) => Promise<CurrentUser>
  signOut: () => void
}

const AuthContext = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<CurrentUser | null>(null)
  const [loading, setLoading] = useState(() => getToken() !== null)

  useEffect(() => {
    if (!getToken()) return
    api
      .me()
      .then(setUser)
      .catch(() => setToken(null))
      .finally(() => setLoading(false))
  }, [])

  const signIn = useCallback(async (email: string, password: string) => {
    const { token, user } = await api.login(email, password)
    setToken(token)
    setUser(user)
    return user
  }, [])

  const register = useCallback(async (email: string, password: string, displayName: string) => {
    const { token, user } = await api.register(email, password, displayName)
    setToken(token)
    setUser(user)
    return user
  }, [])

  const signOut = useCallback(() => {
    setToken(null)
    setUser(null)
  }, [])

  return <AuthContext.Provider value={{ user, loading, signIn, register, signOut }}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth outside AuthProvider')
  return ctx
}
