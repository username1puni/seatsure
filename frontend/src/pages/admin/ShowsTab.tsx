import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { api } from '../../api/client'
import type { AdminShow, Movie, Screen, Theatre } from '../../api/types'
import { isoDate, rupees, showTime } from '../../utils/format'

export default function ShowsTab() {
  const [date, setDate] = useState(() => isoDate(new Date()))
  const [shows, setShows] = useState<AdminShow[]>([])
  const [movies, setMovies] = useState<Movie[]>([])
  const [theatres, setTheatres] = useState<Theatre[]>([])
  const [screens, setScreens] = useState<Screen[]>([])
  const [form, setForm] = useState({ movieId: '', theatreId: '', screenId: '', time: '19:00', priceNormal: '200', pricePremium: '350' })
  const [editing, setEditing] = useState<AdminShow | null>(null)
  const [message, setMessage] = useState<{ type: 'error' | 'success'; text: string } | null>(null)

  const load = useCallback(
    () => api.adminShows(date).then(setShows).catch((e: Error) => setMessage({ type: 'error', text: e.message })),
    [date],
  )

  useEffect(() => {
    void load()
  }, [load])

  useEffect(() => {
    api.adminMovies().then((list) => setMovies(list.filter((m) => m.active))).catch(() => undefined)
    api.theatres().then(setTheatres).catch(() => undefined)
  }, [])

  useEffect(() => {
    if (!form.theatreId) return
    api.screens(Number(form.theatreId)).then(setScreens).catch(() => setScreens([]))
  }, [form.theatreId])

  const create = async (e: FormEvent) => {
    e.preventDefault()
    setMessage(null)
    try {
      await api.createShow({
        movieId: Number(form.movieId),
        screenId: Number(form.screenId),
        startTime: `${date}T${form.time}:00`,
        priceNormal: Number(form.priceNormal),
        pricePremium: Number(form.pricePremium),
      })
      setMessage({ type: 'success', text: 'Show scheduled' })
      await load()
    } catch (err) {
      setMessage({ type: 'error', text: (err as Error).message })
    }
  }

  const savePricing = async () => {
    if (!editing) return
    try {
      await api.updatePricing(editing.id, editing.priceNormal, editing.pricePremium, editing.version)
      setEditing(null)
      setMessage({ type: 'success', text: 'Prices updated. Seats already sold keep their old price.' })
      await load()
    } catch (err) {
      setMessage({ type: 'error', text: (err as Error).message })
    }
  }

  const cancel = async (show: AdminShow) => {
    if (!window.confirm(`Cancel ${show.movieTitle} at ${showTime(show.startTime)}? Everyone who booked gets a full refund.`)) return
    try {
      const res = await api.cancelShow(show.id)
      setMessage({ type: 'success', text: `Show cancelled, ${res.bookingsRefunded} booking(s) refunded` })
      await load()
    } catch (err) {
      setMessage({ type: 'error', text: (err as Error).message })
    }
  }

  return (
    <div>
      {message && <div className={`alert alert-${message.type}`}>{message.text}</div>}
      <form className="card form-grid" onSubmit={create}>
        <h3>Schedule a show</h3>
        <label>
          Date
          <input type="date" value={date} onChange={(e) => setDate(e.target.value)} required />
        </label>
        <label>
          Start time
          <input type="time" value={form.time} onChange={(e) => setForm({ ...form, time: e.target.value })} required />
        </label>
        <label>
          Movie / event
          <select value={form.movieId} onChange={(e) => setForm({ ...form, movieId: e.target.value })} required>
            <option value="">Choose...</option>
            {movies.map((m) => <option key={m.id} value={m.id}>{m.title}</option>)}
          </select>
        </label>
        <label>
          Theatre
          <select value={form.theatreId} onChange={(e) => setForm({ ...form, theatreId: e.target.value, screenId: '' })} required>
            <option value="">Choose...</option>
            {theatres.map((t) => <option key={t.id} value={t.id}>{t.name} ({t.cityName})</option>)}
          </select>
        </label>
        <label>
          Screen
          <select value={form.screenId} onChange={(e) => setForm({ ...form, screenId: e.target.value })} required>
            <option value="">Choose...</option>
            {screens.map((s) => <option key={s.id} value={s.id}>{s.name} ({s.seatCount} seats)</option>)}
          </select>
        </label>
        <label>
          Normal price
          <input type="number" min={50} value={form.priceNormal} onChange={(e) => setForm({ ...form, priceNormal: e.target.value })} required />
        </label>
        <label>
          Premium price
          <input type="number" min={50} value={form.pricePremium} onChange={(e) => setForm({ ...form, pricePremium: e.target.value })} required />
        </label>
        <button className="btn">Add show</button>
      </form>

      <div className="card">
        <h3>Shows on {date}</h3>
        <div className="table-scroll">
          <table className="table">
            <thead>
              <tr><th>Time</th><th>Movie</th><th>Theatre</th><th>Screen</th><th className="num">Normal</th><th className="num">Premium</th><th className="num">Taken</th><th /></tr>
            </thead>
            <tbody>
              {shows.length === 0 && <tr><td colSpan={8} className="muted">No shows on this day</td></tr>}
              {shows.map((s) => (
                <tr key={s.id} className={s.status === 'CANCELLED' ? 'row-muted' : ''}>
                  <td>{showTime(s.startTime)}</td>
                  <td>{s.movieTitle}</td>
                  <td>{s.theatreName}</td>
                  <td>{s.screenName}</td>
                  {editing?.id === s.id ? (
                    <>
                      <td><input className="input-small" type="number" value={editing.priceNormal} onChange={(e) => setEditing({ ...editing, priceNormal: Number(e.target.value) })} /></td>
                      <td><input className="input-small" type="number" value={editing.pricePremium} onChange={(e) => setEditing({ ...editing, pricePremium: Number(e.target.value) })} /></td>
                    </>
                  ) : (
                    <>
                      <td className="num">{rupees(s.priceNormal)}</td>
                      <td className="num">{rupees(s.pricePremium)}</td>
                    </>
                  )}
                  <td className="num">{s.seatsBooked}/{s.capacity}</td>
                  <td className="row-actions">
                    {s.status === 'CANCELLED' ? <span className="status status-cancelled">Cancelled</span> : editing?.id === s.id ? (
                      <>
                        <button className="link-button" onClick={savePricing}>Save</button>
                        <button className="link-button" onClick={() => setEditing(null)}>Cancel</button>
                      </>
                    ) : (
                      <>
                        <button className="link-button" onClick={() => setEditing(s)}>Edit prices</button>
                        <button className="link-button danger" onClick={() => cancel(s)}>Cancel show</button>
                      </>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  )
}
