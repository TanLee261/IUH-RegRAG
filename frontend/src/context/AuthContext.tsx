import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import type { User, UserRole } from '../types'
import { clearStoredTokens, getStoredToken, logoutApi } from '../services/api'

interface AuthContextType {
  user: User | null
  token: string | null
  isAuthenticated: boolean
  isAdmin: boolean
  loginWithToken: (token: string) => void
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthContextType | undefined>(undefined)

function decodeJwtPayload(token: string): { sub?: string; role?: string } | null {
  try {
    const parts = token.split('.')
    if (parts.length < 2) return null
    let payload = parts[1].replace(/-/g, '+').replace(/_/g, '/')
    while (payload.length % 4 !== 0) {
      payload += '='
    }
    const decoded = atob(payload)
    return JSON.parse(decoded)
  } catch {
    return null
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState<string | null>(getStoredToken)
  const [user, setUser] = useState<User | null>(null)

  useEffect(() => {
    const handleExpired = () => {
      setToken(null)
      setUser(null)
    }
    window.addEventListener('auth:expired', handleExpired)
    return () => window.removeEventListener('auth:expired', handleExpired)
  }, [])

  useEffect(() => {
    if (!token) {
      setUser(null)
      return
    }

    const payload = decodeJwtPayload(token) as { sub?: string; role?: string; email?: string; exp?: number } | null
    if (payload && payload.sub) {
      const storedEmail = localStorage.getItem('regrag_user_email') || ''
      const email = payload.email || storedEmail || (payload.sub.includes('@') ? payload.sub : `user_${payload.sub.slice(0, 6)}@iuh.edu.vn`)
      const rawRole = (payload.role || '').toUpperCase()
      const isAdminUser = rawRole === 'ADMIN' && email.toLowerCase() === 'admin@iuh.edu.vn'
      const role: UserRole = isAdminUser ? 'ADMIN' : 'STUDENT'
      setUser({
        id: payload.sub,
        email,
        role,
      })
    } else {
      setUser(null)
    }
  }, [token])

  const loginWithToken = (newToken: string) => {
    setToken(newToken)
  }

  const logout = async () => {
    await logoutApi()
    setToken(null)
    setUser(null)
    clearStoredTokens()
  }

  return (
    <AuthContext.Provider
      value={{
        user,
        token,
        isAuthenticated: !!token,
        isAdmin: user?.role === 'ADMIN',
        loginWithToken,
        logout,
      }}
    >
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider')
  }
  return context
}
