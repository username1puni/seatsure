import { Client } from '@stomp/stompjs'
import { useEffect, useRef } from 'react'
import type { SeatUpdate } from '../api/types'

/**
 * Subscribes to live seat changes of a show. The server publishes after every hold,
 * booking or release, so seats turn grey for everyone else without a refresh.
 */
export function useSeatUpdates(showId: number | null, onUpdate: (update: SeatUpdate) => void) {
  const callback = useRef(onUpdate)

  useEffect(() => {
    callback.current = onUpdate
  }, [onUpdate])

  useEffect(() => {
    if (!showId) return
    const scheme = window.location.protocol === 'https:' ? 'wss' : 'ws'
    const client = new Client({
      brokerURL: `${scheme}://${window.location.host}/ws`,
      reconnectDelay: 3000,
      onConnect: () => {
        client.subscribe(`/topic/shows/${showId}/seats`, (message) => {
          callback.current(JSON.parse(message.body) as SeatUpdate)
        })
      },
    })
    client.activate()
    return () => {
      void client.deactivate()
    }
  }, [showId])
}
