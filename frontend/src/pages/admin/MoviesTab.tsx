import { useEffect, useState, type FormEvent } from 'react'
import { api } from '../../api/client'
import type { Movie, MovieKind } from '../../api/types'
import { duration } from '../../utils/format'

const EMPTY = { title: '', description: '', kind: 'MOVIE' as MovieKind, language: '', genre: '', durationMinutes: 120, certificate: 'UA', posterUrl: '' }

export default function MoviesTab() {
  const [movies, setMovies] = useState<Movie[]>([])
  const [form, setForm] = useState(EMPTY)
  const [editId, setEditId] = useState<number | undefined>()
  const [message, setMessage] = useState<{ type: 'error' | 'success'; text: string } | null>(null)

  useEffect(() => {
    api.adminMovies().then(setMovies).catch(() => undefined)
  }, [])

  const save = async (e: FormEvent) => {
    e.preventDefault()
    try {
      await api.saveMovie({ ...form, active: true }, editId)
      setMessage({ type: 'success', text: editId ? 'Saved' : `${form.title} added` })
      setForm(EMPTY)
      setEditId(undefined)
      setMovies(await api.adminMovies())
    } catch (err) {
      setMessage({ type: 'error', text: (err as Error).message })
    }
  }

  const toggleActive = async (m: Movie) => {
    await api.saveMovie({ ...m, active: !m.active }, m.id)
    setMovies(await api.adminMovies())
  }

  return (
    <div className="dash-grid">
      <div className="card">
        <h3>Movies and events</h3>
        <table className="table">
          <tbody>
            {movies.map((m) => (
              <tr key={m.id} className={m.active ? '' : 'row-muted'}>
                <td><b>{m.title}</b><div className="muted small">{m.kind === 'EVENT' ? 'Event' : 'Movie'} &middot; {m.language} &middot; {duration(m.durationMinutes)} &middot; {m.certificate}</div></td>
                <td className="row-actions">
                  <button className="link-button" onClick={() => {
                    setEditId(m.id)
                    setForm({ title: m.title, description: m.description ?? '', kind: m.kind, language: m.language, genre: m.genre, durationMinutes: m.durationMinutes, certificate: m.certificate, posterUrl: m.posterUrl ?? '' })
                  }}>Edit</button>
                  <button className="link-button" onClick={() => toggleActive(m)}>{m.active ? 'Hide' : 'Show'}</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <form className="card stack" onSubmit={save}>
        <h3>{editId ? 'Edit' : 'Add'} movie or event</h3>
        {message && <div className={`alert alert-${message.type}`}>{message.text}</div>}
        <input placeholder="Title" value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} required />
        <textarea placeholder="Short description" value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} rows={3} />
        <div className="form-row">
          <select value={form.kind} onChange={(e) => setForm({ ...form, kind: e.target.value as MovieKind })} aria-label="Type">
            <option value="MOVIE">Movie</option>
            <option value="EVENT">Event</option>
          </select>
          <select value={form.certificate} onChange={(e) => setForm({ ...form, certificate: e.target.value })} aria-label="Certificate">
            {['U', 'UA', 'A', 'S'].map((c) => <option key={c}>{c}</option>)}
          </select>
          <input type="number" min={15} max={400} value={form.durationMinutes} onChange={(e) => setForm({ ...form, durationMinutes: Number(e.target.value) })} aria-label="Duration in minutes" />
        </div>
        <div className="form-row">
          <input placeholder="Language" value={form.language} onChange={(e) => setForm({ ...form, language: e.target.value })} required />
          <input placeholder="Genre" value={form.genre} onChange={(e) => setForm({ ...form, genre: e.target.value })} required />
        </div>
        <input placeholder="Poster image URL (optional)" value={form.posterUrl} onChange={(e) => setForm({ ...form, posterUrl: e.target.value })} />
        <div className="actions">
          {editId && <button type="button" className="btn btn-outline" onClick={() => { setEditId(undefined); setForm(EMPTY) }}>Cancel</button>}
          <button className="btn">{editId ? 'Save' : 'Add'}</button>
        </div>
      </form>
    </div>
  )
}
