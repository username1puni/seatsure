import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { api } from '../../api/client'
import type { Screen, Theatre } from '../../api/types'
import { useCity } from '../../context/cityContext'

const ROW_LETTERS = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'

export default function TheatresTab() {
  const { cities } = useCity()
  const [theatres, setTheatres] = useState<Theatre[]>([])
  const [selected, setSelected] = useState<Theatre | null>(null)
  const [screens, setScreens] = useState<Screen[]>([])
  const [theatreForm, setTheatreForm] = useState({ cityId: '', name: '', address: '' })
  const [screenForm, setScreenForm] = useState({ name: 'Screen 3', rows: 8, seatsPerRow: 12, premiumRows: ['G', 'H'] })
  const [message, setMessage] = useState<{ type: 'error' | 'success'; text: string } | null>(null)

  const loadTheatres = useCallback(() => api.theatres().then(setTheatres).catch(() => undefined), [])

  useEffect(() => {
    void loadTheatres()
  }, [loadTheatres])

  useEffect(() => {
    if (!selected) return
    api.screens(selected.id).then(setScreens).catch(() => setScreens([]))
  }, [selected])

  const addTheatre = async (e: FormEvent) => {
    e.preventDefault()
    try {
      const t = await api.createTheatre({ ...theatreForm, cityId: Number(theatreForm.cityId) })
      setMessage({ type: 'success', text: `${t.name} added` })
      setTheatreForm({ cityId: theatreForm.cityId, name: '', address: '' })
      await loadTheatres()
      setSelected(t)
    } catch (err) {
      setMessage({ type: 'error', text: (err as Error).message })
    }
  }

  const addScreen = async (e: FormEvent) => {
    e.preventDefault()
    if (!selected) return
    try {
      await api.createScreen({ theatreId: selected.id, ...screenForm })
      setMessage({ type: 'success', text: `${screenForm.name} created with ${screenForm.rows * screenForm.seatsPerRow} seats` })
      setScreens(await api.screens(selected.id))
      await loadTheatres()
    } catch (err) {
      setMessage({ type: 'error', text: (err as Error).message })
    }
  }

  const rows = ROW_LETTERS.slice(0, Math.min(26, Math.max(1, screenForm.rows))).split('')
  const togglePremium = (row: string) => setScreenForm((f) => ({
    ...f,
    premiumRows: f.premiumRows.includes(row) ? f.premiumRows.filter((r) => r !== row) : [...f.premiumRows, row],
  }))

  return (
    <div>
      {message && <div className={`alert alert-${message.type}`}>{message.text}</div>}
      <div className="dash-grid">
        <div className="card">
          <h3>Theatres</h3>
          <ul className="pick-list">
            {theatres.map((t) => (
              <li key={t.id}>
                <button className={selected?.id === t.id ? 'active' : ''} onClick={() => setSelected(t)}>
                  <b>{t.name}</b>
                  <span className="muted small">{t.cityName} &middot; {t.screenCount} screen{t.screenCount === 1 ? '' : 's'}</span>
                </button>
              </li>
            ))}
          </ul>
          <form onSubmit={addTheatre} className="stack">
            <h4>Add a theatre</h4>
            <select value={theatreForm.cityId} onChange={(e) => setTheatreForm({ ...theatreForm, cityId: e.target.value })} required aria-label="City">
              <option value="">City...</option>
              {cities.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
            </select>
            <input placeholder="Name" value={theatreForm.name} onChange={(e) => setTheatreForm({ ...theatreForm, name: e.target.value })} required />
            <input placeholder="Address" value={theatreForm.address} onChange={(e) => setTheatreForm({ ...theatreForm, address: e.target.value })} required />
            <button className="btn btn-small">Add theatre</button>
          </form>
        </div>

        <div className="card">
          {!selected && <p className="muted">Pick a theatre to see its screens.</p>}
          {selected && (
            <>
              <h3>{selected.name} screens</h3>
              <ul className="plain-list">
                {screens.map((s) => (
                  <li key={s.id}>
                    <b>{s.name}</b> &middot; {s.rows} rows x {s.seatsPerRow} = {s.seatCount} seats
                    {s.premiumRows.length > 0 && <span className="muted"> &middot; premium rows {s.premiumRows.join(', ')}</span>}
                  </li>
                ))}
              </ul>
              <form onSubmit={addScreen} className="stack">
                <h4>New screen and seat layout</h4>
                <div className="form-row">
                  <label>Name<input value={screenForm.name} onChange={(e) => setScreenForm({ ...screenForm, name: e.target.value })} required /></label>
                  <label>Rows<input type="number" min={1} max={26} value={screenForm.rows} onChange={(e) => setScreenForm({ ...screenForm, rows: Number(e.target.value), premiumRows: [] })} /></label>
                  <label>Seats per row<input type="number" min={1} max={40} value={screenForm.seatsPerRow} onChange={(e) => setScreenForm({ ...screenForm, seatsPerRow: Number(e.target.value) })} /></label>
                </div>
                <p className="muted small">Click a row to make it premium. Row A is closest to the screen.</p>
                <div className="layout-preview">
                  {[...rows].reverse().map((r) => (
                    <button type="button" key={r} className={`layout-row ${screenForm.premiumRows.includes(r) ? 'premium' : ''}`} onClick={() => togglePremium(r)}>
                      <span>{r}</span>
                      {Array.from({ length: Math.min(40, screenForm.seatsPerRow) }, (_, i) => <i key={i} />)}
                    </button>
                  ))}
                  <div className="screen small"><div className="screen-curve" /></div>
                </div>
                <button className="btn btn-small">Create screen</button>
              </form>
            </>
          )}
        </div>
      </div>
    </div>
  )
}
