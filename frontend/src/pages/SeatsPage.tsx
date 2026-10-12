import { useCallback, useEffect, useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { ApiError, api } from '../api/client'
import type { SeatMap as SeatMapData, SeatUpdate, ShowDetail } from '../api/types'
import SeatMap from '../components/SeatMap'
import { useAuth } from '../context/useAuth'
import { useSeatUpdates } from '../hooks/useSeatUpdates'
import { rupees, showDateTime } from '../utils/format'
import { applySeatUpdate, selectionTotal, toggleSeat } from '../utils/seats'

export default function SeatsPage() {
  const { id } = useParams()
  const showId = Number(id)
  const { user } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [show, setShow] = useState<ShowDetail | null>(null)
  const [map, setMap] = useState<SeatMapData | null>(null)
  const [selected, setSelected] = useState<number[]>(() => (location.state as { seats?: number[] } | null)?.seats ?? [])
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [busy, setBusy] = useState(false)

  const loadSeats = useCallback(() => api.seatMap(showId).then(setMap), [showId])

  useEffect(() => {
    api.show(showId).then(setShow).catch((e: Error) => setError(e.message))
    loadSeats().catch((e: Error) => setError(e.message))
  }, [showId, loadSeats])

  const onUpdate = useCallback((update: SeatUpdate) => {
    setMap((current) => (current ? applySeatUpdate(current, update.seats) : current))
    // someone else just took a seat this user had picked
    const lost = update.seats.filter((s) => s.status !== 'AVAILABLE').map((s) => s.seatId)
    setSelected((current) => {
      const next = current.filter((seatId) => !lost.includes(seatId))
      if (next.length !== current.length) setNotice('One of your selected seats was just taken by someone else.')
      return next
    })
  }, [])
  useSeatUpdates(showId, onUpdate)

  const book = async () => {
    if (!user) {
      navigate('/login', { state: { from: location.pathname, seats: selected } })
      return
    }
    setBusy(true)
    setError('')
    try {
      const booking = await api.hold(showId, selected)
      navigate(`/checkout/${booking.id}`)
    } catch (e) {
      setError((e as Error).message)
      if (e instanceof ApiError && e.status === 409) {
        setSelected([])
        await loadSeats()
      }
    } finally {
      setBusy(false)
    }
  }

  if (error && !map) return <div className="container page"><div className="alert alert-error">{error}</div></div>
  if (!map || !show) return <div className="container page"><p className="muted">Loading seats...</p></div>

  const { total, labels } = selectionTotal(map, selected)

  return (
    <div className="page seats-page">
      <div className="show-bar">
        <div className="container">
          <Link to={`/movies/${show.movie.id}`} className="back-link">&larr; {show.movie.title}</Link>
          <p className="muted small">
            {show.theatreName}, {show.city} &middot; {show.screenName} &middot; {showDateTime(show.startTime)}
          </p>
        </div>
      </div>

      <div className="container">
        {notice && <div className="alert alert-warning" onClick={() => setNotice('')}>{notice}</div>}
        {error && <div className="alert alert-error">{error}</div>}
        <p className="muted small center">Pick up to {map.maxSeats} seats. The map updates live while others book.</p>
        <SeatMap map={map} selected={selected} onToggle={(seatId) => setSelected((s) => toggleSeat(s, seatId, map.maxSeats))} />
      </div>

      {selected.length > 0 && (
        <div className="checkout-bar">
          <div className="container checkout-bar-inner">
            <div>
              <b>{labels.join(', ')}</b>
              <span className="muted small"> &middot; {selected.length} {selected.length === 1 ? 'seat' : 'seats'}</span>
            </div>
            <button className="btn" onClick={book} disabled={busy}>
              {busy ? 'Holding seats...' : `Pay ${rupees(total)}`}
            </button>
          </div>
        </div>
      )}
    </div>
  )
}
