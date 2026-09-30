import React, { createContext, useContext, useEffect, useState } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { api, clearToken, getToken, setToken, MeView } from './api'

interface AuthState {
  user: MeView | null
  loading: boolean
  login: (email: string, password: string) => Promise<MeView>
  logout: () => void
  refreshUser: () => Promise<MeView | null>
}

const Ctx = createContext<AuthState>(null as any)
export const useAuth = () => useContext(Ctx)

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<MeView | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    if (!getToken()) { setLoading(false); return }
    api.me().then(setUser).catch(() => clearToken()).finally(() => setLoading(false))
  }, [])

  const login = async (email: string, password: string) => {
    const res = await api.login(email, password)
    setToken(res.token)
    const me = await api.me()
    setUser(me)
    return me
  }

  const refreshUser = async () => {
    try {
      const me = await api.me()
      setUser(me)
      return me
    } catch {
      clearToken()
      setUser(null)
      return null
    }
  }

  const logout = () => { clearToken(); setUser(null); window.location.href = '/login' }

  return <Ctx.Provider value={{ user, loading, login, logout, refreshUser }}>{children}</Ctx.Provider>
}

export function hasAnyRole(user: MeView | null, roles: string[]): boolean {
  if (!user) return false
  return user.roles.some(r => roles.includes(r))
}

export function RequireAuth({ children, roles }: { children: React.ReactNode; roles?: string[] }) {
  const { user, loading } = useAuth()
  const location = useLocation()
  if (loading) return <div className="center muted">Loading…</div>
  if (!user) return <Navigate to="/login" replace state={{ from: location }} />
  if (roles && !hasAnyRole(user, roles)) return <div className="center muted">You don't have access to this page.</div>
  // Force password change before using the app
  if (user.mustChangePassword && location.pathname !== '/account') {
    return <Navigate to="/account" replace state={{ forcePassword: true }} />
  }
  return <>{children}</>
}
