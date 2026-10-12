import { useEffect, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { api } from '../api/client'
import type { Booking, RefundPreview } from '../api/types'
import Poster from '../components/Poster'
import { rupees, showDateTime, statusLabel } from '../utils/format'

export default function BookingPage() {
  const { id } = useParams()
  const bookingId = Number(id)
  const [params] = useSearchParams()
  const [booking, setBooking] = useState<Booking | null>(null)
  const [qr, setQr] = useState<string | null>(null)
  const [preview, setPreview] = useState<RefundPreview | null>(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    api.booking(bookingId).then(setBooking).catch((e: Error) => setError(e.message))
  }, [bookingId])

  useEffect(() => {
    if (booking?.status !== 'CONFIRMED') return
    let url: string | null = null
    api.qrImage(booking.id).then((u) => { url = u; setQr(u) }).catch(() => setQr(null))
    return () => { if (url) URL.revokeObjectURL(url) }
  }, [booking?.id, booking?.status])

  const askCancel = async () => {
    setError('')
    try {
      setPreview(await api.refundPreview(bookingId))
    } catch (e) {
      setError((e as Error).message)
    }
  }

  const cancel = async () => {
    setBusy(true)
    try {
      setBooking(await api.cancelBooking(bookingId))
      setPreview(null)
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setBusy(false)
    }
  }

  if (error && !booking) return <div className="container page"><div className="alert alert-error">{error}</div></div>
  if (!booking) return <div className="container page"><p className="muted">Loading...</p></div>

  const confirmed = booking.status === 'CONFIRMED'

  return (
    <div className="container page narrow">
      {params.get('paid') && confirmed && (
        <div className="alert alert-success">Payment successful. Your seats are booked, enjoy the show!</div>
      )}
      {error && <div className="alert alert-error">{error}</div>}

      <div className={`ticket ${confirmed ? '' : 'ticket-inactive'}`}>
        <div className="ticket-top">
          <Poster movie={{ id: booking.movieId, title: booking.movieTitle, language: booking.language, kind: booking.kind, posterUrl: booking.posterUrl }} small />
          <div>
            <span className={`status status-${booking.status.toLowerCase()}`}>{statusLabel[booking.status]}</span>
            <h2>{booking.movieTitle}</h2>
            <p className="muted small">{booking.language} &middot; {booking.certificate}</p>
            <p>{showDateTime(booking.startTime)}</p>
            <p className="muted small">{booking.theatreName}, {booking.city} &middot; {booking.screenName}</p>
          </div>
        </div>
        <div className="ticket-divider" />
        <div className="ticket-bottom">
          <div className="ticket-details">
            <div><span className="label">Seats</span><b>{booking.seats.join(', ')}</b></div>
            <div><span className="label">Booking ID</span><b className="mono">{booking.bookingCode}</b></div>
            <div><span className="label">Amount paid</span><b>{rupees(booking.totalAmount)}</b></div>
            {booking.refundAmount != null && (
              <div><span className="label">Refunded</span><b>{rupees(booking.refundAmount)}</b></div>
            )}
            <p className="muted small">{booking.theatreAddress}</p>
          </div>
          {confirmed && qr && (
            <div className="ticket-qr">
              <img src={qr} alt="Entry QR code" width={160} height={160} />
              <span className="small muted">Show this at the entrance</span>
            </div>
          )}
        </div>
      </div>

      <div className="actions">
        <Link to="/bookings" className="btn btn-outline">All bookings</Link>
        {confirmed && <button className="btn" onClick={() => api.downloadTicket(booking)}>Download ticket (PDF)</button>}
        {booking.cancellable && <button className="btn btn-danger-outline" onClick={askCancel}>Cancel booking</button>}
      </div>

      {preview && (
        <div className="modal-backdrop" role="dialog" aria-modal="true">
          <div className="modal">
            <h3>Cancel this booking?</h3>
            {preview.cancellable ? (
              <p>You will get <b>{rupees(preview.refundAmount)}</b> back ({preview.message.toLowerCase()}). The seats will be released for others.</p>
            ) : (
              <p>{preview.message}</p>
            )}
            <div className="actions">
              <button className="btn btn-outline" onClick={() => setPreview(null)}>Keep it</button>
              {preview.cancellable && (
                <button className="btn btn-danger" onClick={cancel} disabled={busy}>{busy ? 'Cancelling...' : 'Yes, cancel'}</button>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
