import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { api } from '../api/client'
import type { City } from '../api/types'
import { CityContext } from './cityContext'

const CITY_KEY = 'seatsure.city'

export function CityProvider({ children }: { children: ReactNode }) {
  const [cities, setCities] = useState<City[]>([])
  const [city, setCityState] = useState<City | null>(null)

  useEffect(() => {
    api.cities().then((list) => {
      setCities(list)
      const saved = Number(localStorage.getItem(CITY_KEY))
      setCityState(list.find((c) => c.id === saved) ?? list.find((c) => c.name === 'Bengaluru') ?? list[0] ?? null)
    }).catch(() => setCities([]))
  }, [])

  const setCity = useCallback((next: City) => {
    localStorage.setItem(CITY_KEY, String(next.id))
    setCityState(next)
  }, [])

  const value = useMemo(() => ({ cities, city, setCity }), [cities, city, setCity])
  return <CityContext.Provider value={value}>{children}</CityContext.Provider>
}
