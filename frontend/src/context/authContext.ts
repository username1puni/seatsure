import { createContext } from 'react'
import type { User } from '../api/types'

export interface AuthState {
  user: User | null
  loading: boolean
  login: (email: string, password: string) => Promise<void>
  register: (fullName: string, email: string, password: string, phone?: string) => Promise<void>
  logout: () => void
}

export const AuthContext = createContext<AuthState | null>(null)
