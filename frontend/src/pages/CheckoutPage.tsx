import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api } from '../api/client'
import type { Booking, PaymentOrder } from '../api/types'
import Countdown from '../components/Countdown'
import MockPaymentModal from '../components/MockPaymentModal'
import Poster from '../components/Poster'
import { useAuth } from '../context/useAuth'
import { rupees, showDateTime } from '../utils/format'
import { openRazorpay } from '../utils/razorpay'

export default function CheckoutPage() {
  const { id } = useParams()
  const bookingId = Number(id)
  const navigate = useNavigate()
  const { user } = useAuth()
  const [booking, setBooking] = useState<Booking | null>(null)
  const [order, setOrder] = useState<PaymentOrder | null>(null)
  const [error, setError] = useState('')
  const [expired, setExpired] = useState(false)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    api.booking(bookingId).then((b) => {
      if (b.status !== 'PENDING_PAYMENT') {
        navigate(`/bookings/${b.id}`, { replace: true })
        return
      }
      setBooking(b)
    }).catch((e: Error) => setError(e.message))
  }, [bookingId, navigate])

  const onExpire = useCallback(() => {
    setExpired(true)
    setOrder(null)
  }, [])

  const confirm = async (orderId: string, paymentId: string, signature: string) => {
    try {
      const confirmed = await api.verifyPayment(orderId, paymentId, signature)
      navigate(`/bookings/${confirmed.id}?paid=1`, { replace: true })
    } catch (e) {
      setOrder(null)
      setError((e as Error).message)
    }
  }

  const failed = async (orderId: string, reason: string) => {
    setOrder(null)
    await api.paymentFailed(orderId, reason).catch(() => undefined)
    setError(`Payment failed: ${reason}. Your seats have been released, please pick them again.`)
    setBooking((b) => (b ? { ...b, status: 'PAYMENT_FAILED' } : b))
  }

  const pay = async () => {
    if (!booking || !user) return
    setBusy(true)
    setError('')
    try {
      const created = await api.createOrder(booking.id)
      if (created.gateway === 'RAZORPAY') {
        await openRazorpay(created, { name: user.fullName, email: user.email }, {
          onSuccess: (r) => confirm(r.razorpay_order_id, r.razorpay_payment_id, r.razorpay_signature),
          onFailure: (reason) => failed(created.orderId, reason),
          onDismiss: () => undefined,
        })
      } else {
        setOrder(created)
      }
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setBusy(false)
    }
  }

  const mockPay = async (success: boolean) => {
    if (!order) return
    const result = await api.mockCheckout(order.orderId, success)
    if (result.success && result.paymentId && result.signature) {
      await confirm(order.orderId, result.paymentId, result.signature)
    } else {
      await failed(order.orderId, result.error ?? 'Payment declined')
    }
  }

  const changeSeats = async () => {
    if (booking && booking.status === 'PENDING_PAYMENT' && !expired) {
      await api.releaseHold(booking.id).catch(() => undefined)
    }
    navigate(`/shows/${booking?.showId}`)
  }

  if (error && !booking) return <div className="container page"><div className="alert alert-error">{error}</div></div>
  if (!booking) return <div className="container page"><p className="muted">Loading...</p></div>

  const open = booking.status === 'PENDING_PAYMENT' && !expired

  return (
    <div className="container page narrow">
      <h1>Review and pay</h1>
      {open && <Countdown expiresAt={booking.holdExpiresAt} onExpire={onExpire} />}
      {expired && (
        <div className="alert alert-warning">
          Your 5 minute hold ran out and the seats were released. <Link to={`/shows/${booking.showId}`}>Pick seats again</Link>
        </div>
      )}
      {error && <div className="alert alert-error">{error}</div>}

      <div className="card summary-card">
        <Poster movie={{ id: booking.movieId, title: booking.movieTitle, language: booking.language, kind: booking.kind, posterUrl: booking.posterUrl }} small />
        <div className="summary-body">
          <h2>{booking.movieTitle}</h2>
          <p className="muted small">{booking.language} &middot; {booking.certificate}</p>
          <p>{booking.theatreName}, {booking.city}</p>
          <p className="muted small">{booking.screenName} &middot; {showDateTime(booking.startTime)}</p>
          <p className="seats-line">Seats: <b>{booking.seats.join(', ')}</b></p>
        </div>
      </div>

      <div className="card price-card">
        <div className="price-row"><span>Tickets ({booking.seatCount})</span><span>{rupees(booking.totalAmount)}</span></div>
        <div className="price-row total"><span>Amount to pay</span><span>{rupees(booking.totalAmount)}</span></div>
      </div>

      <div className="actions">
        <button className="btn btn-outline" onClick={changeSeats}>Change seats</button>
        <button className="btn" onClick={pay} disabled={!open || busy}>{busy ? 'Starting payment...' : `Pay ${rupees(booking.totalAmount)}`}</button>
      </div>
      <p className="muted small">Cancellation: full refund up to 24 hours before the show, 75% up to 2 hours before.</p>

      {order && order.gateway === 'MOCK' && (
        <MockPaymentModal order={order} onPay={mockPay} onClose={() => setOrder(null)} />
      )}
    </div>
  )
}
