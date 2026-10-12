import { useState, type FormEvent } from 'react'
import { api } from '../../api/client'
import type { TicketCheck } from '../../api/types'
import { showDateTime } from '../../utils/format'

export default function CheckInTab() {
  const [code, setCode] = useState('')
  const [result, setResult] = useState<TicketCheck | null>(null)
  const [error, setError] = useState('')

  const verify = async (e: FormEvent) => {
    e.preventDefault()
    setError('')
    try {
      setResult(await api.verifyTicket(code))
    } catch (err) {
      setError((err as Error).message)
    }
  }

  return (
    <div className="narrow">
      <form className="card stack" onSubmit={verify}>
        <h3>Check a ticket at the entrance</h3>
        <p className="muted small">Scan the QR code on the ticket with any scanner app and paste the text here (it looks like SEATSURE:SS...:...).</p>
        <input value={code} onChange={(e) => setCode(e.target.value)} placeholder="SEATSURE:SSABCD2345:..." required />
        <button className="btn">Verify</button>
      </form>
      {error && <div className="alert alert-error">{error}</div>}
      {result && (
        <div className={`card checkin checkin-${result.result.toLowerCase()}`}>
          <h3>{result.result === 'VALID' ? 'Valid ticket' : result.result === 'ALREADY_USED' ? 'Already used' : result.result === 'CANCELLED' ? 'Cancelled booking' : 'Invalid ticket'}</h3>
          <p>{result.message}</p>
          {result.bookingCode && (
            <p className="small">
              {result.movieTitle} &middot; {result.theatreName} &middot; {result.startTime && showDateTime(result.startTime)}<br />
              Seats <b>{result.seats.join(', ')}</b> &middot; {result.holder} &middot; <span className="mono">{result.bookingCode}</span>
            </p>
          )}
        </div>
      )}
    </div>
  )
}
