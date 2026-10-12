import { createContext, useContext } from 'react'
import type { City } from '../api/types'

export interface CityState {
  cities: City[]
  city: City | null
  setCity: (city: City) => void
}

export const CityContext = createContext<CityState | null>(null)

export function useCity() {
  const ctx = useContext(CityContext)
  if (!ctx) throw new Error('useCity must be used inside CityProvider')
  return ctx
}
