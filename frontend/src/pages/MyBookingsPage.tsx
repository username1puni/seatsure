import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api/client'
import type { Booking } from '../api/types'
import Poster from '../components/Poster'
import { rupees, showDateTime, statusLabel } from '../utils/format'

export default function MyBookingsPage() {
  const [bookings, setBookings] = useState<Booking[] | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    api.myBookings().then(setBookings).catch((e: Error) => setError(e.message))
  }, [])

  const [now] = useState(() => Date.now())
  const upcoming = bookings?.filter((b) => b.status === 'CONFIRMED' && new Date(b.startTime).getTime() > now) ?? []
  const others = bookings?.filter((b) => !upcoming.includes(b)) ?? []

  return (
    <div className="container page narrow">
      <h1>My bookings</h1>
      {error && <div className="alert alert-error">{error}</div>}
      {bookings === null && !error && <p className="muted">Loading...</p>}
      {bookings?.length === 0 && (
        <div className="empty">
          <p>No bookings yet.</p>
          <Link to="/" className="btn">Find a show</Link>
        </div>
      )}
      {upcoming.length > 0 && <h2>Upcoming</h2>}
      {upcoming.map((b) => <BookingRow key={b.id} booking={b} />)}
      {others.length > 0 && <h2>Past and cancelled</h2>}
      {others.map((b) => <BookingRow key={b.id} booking={b} />)}
    </div>
  )
}

function BookingRow({ booking }: { booking: Booking }) {
  const target = booking.status === 'PENDING_PAYMENT' ? `/checkout/${booking.id}` : `/bookings/${booking.id}`
  return (
    <Link to={target} className="card booking-row">
      <Poster movie={{ id: booking.movieId, title: booking.movieTitle, language: booking.language, kind: booking.kind, posterUrl: booking.posterUrl }} small />
      <div className="booking-row-body">
        <div className="booking-row-head">
          <b>{booking.movieTitle}</b>
          <span className={`status status-${booking.status.toLowerCase()}`}>{statusLabel[booking.status]}</span>
        </div>
        <p className="small">{showDateTime(booking.startTime)} &middot; {booking.theatreName}</p>
        <p className="muted small">
          {booking.seats.join(', ')} &middot; {rupees(booking.totalAmount)} &middot; <span className="mono">{booking.bookingCode}</span>
        </p>
      </div>
    </Link>
  )
}
