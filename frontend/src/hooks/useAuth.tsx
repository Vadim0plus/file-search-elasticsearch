import { createContext, type ReactNode, useContext, useEffect, useState } from 'react'
import { login as apiLogin, logout as apiLogout, me as apiMe } from '../api/client'

interface AuthContextValue {
  username: string | null
  loading: boolean
  login: (username: string, password: string) => Promise<void>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [username, setUsername] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    apiMe()
      .then((result) => setUsername(result?.username ?? null))
      .finally(() => setLoading(false))
  }, [])

  const login = async (nextUsername: string, password: string) => {
    await apiLogin(nextUsername, password)
    setUsername(nextUsername)
  }

  const logout = async () => {
    await apiLogout()
    setUsername(null)
  }

  return <AuthContext.Provider value={{ username, loading, login, logout }}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider')
  }
  return context
}
