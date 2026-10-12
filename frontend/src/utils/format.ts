const rupeeFormat = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 2, minimumFractionDigits: 0 })

export const rupees = (amount: number) => rupeeFormat.format(amount)

/** Show times come from the server as local theatre time without a zone, e.g. 2026-10-12T20:30:00 */
const parseLocal = (value: string) => {
  const [date, time = '00:00'] = value.split('T')
  const [y, m, d] = date.split('-').map(Number)
  const [hh, mm] = time.split(':').map(Number)
  return new Date(y, m - 1, d, hh, mm)
}

export const showTime = (value: string) =>
  parseLocal(value).toLocaleTimeString('en-IN', { hour: 'numeric', minute: '2-digit', hour12: true }).toUpperCase()

export const showDate = (value: string) =>
  parseLocal(value).toLocaleDateString('en-IN', { weekday: 'short', day: 'numeric', month: 'short' })

export const showDateTime = (value: string) => `${showDate(value)}, ${showTime(value)}`

export const duration = (minutes: number) => {
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  return h > 0 ? `${h}h ${m.toString().padStart(2, '0')}m` : `${m}m`
}

/** yyyy-mm-dd in the browser's local time */
export const isoDate = (date: Date) => {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

/** Today and the following days as yyyy-mm-dd */
export const nextDays = (count: number, from = new Date()) =>
  Array.from({ length: count }, (_, i) => isoDate(new Date(from.getFullYear(), from.getMonth(), from.getDate() + i)))

export const dayChip = (iso: string) => {
  const date = parseLocal(iso)
  return {
    weekday: date.toLocaleDateString('en-IN', { weekday: 'short' }).toUpperCase(),
    day: date.getDate(),
    month: date.toLocaleDateString('en-IN', { month: 'short' }).toUpperCase(),
  }
}

export const secondsLeft = (expiresAt: string, now = Date.now()) =>
  Math.max(0, Math.floor((new Date(expiresAt).getTime() - now) / 1000))

export const clock = (seconds: number) =>
  `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`

export const statusLabel: Record<string, string> = {
  PENDING_PAYMENT: 'Awaiting payment',
  CONFIRMED: 'Confirmed',
  CANCELLED: 'Cancelled',
  EXPIRED: 'Expired',
  PAYMENT_FAILED: 'Payment failed',
}
