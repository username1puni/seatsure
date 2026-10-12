import { useEffect, useState } from 'react'
import { clock, secondsLeft } from '../utils/format'

interface Props {
  expiresAt: string
  onExpire: () => void
}

export default function Countdown({ expiresAt, onExpire }: Props) {
  const [left, setLeft] = useState(() => secondsLeft(expiresAt))

  useEffect(() => {
    const timer = setInterval(() => {
      const next = secondsLeft(expiresAt)
      setLeft(next)
      if (next === 0) {
        clearInterval(timer)
        onExpire()
      }
    }, 1000)
    return () => clearInterval(timer)
  }, [expiresAt, onExpire])

  return (
    <div className={`countdown ${left < 60 ? 'countdown-urgent' : ''}`} role="timer" aria-live="polite">
      Seats held for you: <b>{clock(left)}</b>
    </div>
  )
}
