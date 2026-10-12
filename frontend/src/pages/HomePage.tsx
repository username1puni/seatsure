import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api/client'
import type { Movie, MovieKind } from '../api/types'
import DateStrip from '../components/DateStrip'
import Poster from '../components/Poster'
import { useCity } from '../context/cityContext'
import { duration, nextDays } from '../utils/format'

const DAYS = 5

export default function HomePage() {
  const { city } = useCity()
  const [dates] = useState(() => nextDays(DAYS))
  const [date, setDate] = useState(dates[0])
  const [kind, setKind] = useState<MovieKind>('MOVIE')
  const [movies, setMovies] = useState<Movie[] | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!city) return
    let cancelled = false
    api.movies(city.id, date, kind)
      .then((list) => { if (!cancelled) { setMovies(list); setError('') } })
      .catch((e: Error) => { if (!cancelled) setError(e.message) })
    return () => { cancelled = true }
  }, [city, date, kind])

  return (
    <div className="page">
      <section className="hero">
        <div className="container">
          <h1>Book your seats in {city?.name ?? '...'}</h1>
          <p>Pick a show, choose your seats on the live seat map and pay. Your seats are held for 5 minutes while you pay, so nobody else can grab them.</p>
        </div>
      </section>

      <div className="container">
        <div className="toolbar">
          <div className="tabs" role="tablist">
            <button role="tab" aria-selected={kind === 'MOVIE'} className={kind === 'MOVIE' ? 'active' : ''} onClick={() => setKind('MOVIE')}>Movies</button>
            <button role="tab" aria-selected={kind === 'EVENT'} className={kind === 'EVENT' ? 'active' : ''} onClick={() => setKind('EVENT')}>Events</button>
          </div>
          <DateStrip dates={dates} selected={date} onSelect={setDate} />
        </div>

        {error && <div className="alert alert-error">{error}</div>}
        {movies === null && !error && <p className="muted">Loading shows...</p>}
        {movies?.length === 0 && (
          <div className="empty">
            <p>No {kind === 'MOVIE' ? 'movies' : 'events'} left on this day in {city?.name}. Try another date.</p>
          </div>
        )}

        <div className="movie-grid">
          {movies?.map((m) => (
            <Link key={m.id} to={`/movies/${m.id}?date=${date}`} className="movie-card">
              <Poster movie={m} />
              <div className="movie-card-body">
                <h3>{m.title}</h3>
                <p className="muted small">{m.certificate} &middot; {m.language}</p>
                <p className="muted small">{m.genre} &middot; {duration(m.durationMinutes)}</p>
              </div>
            </Link>
          ))}
        </div>
      </div>
    </div>
  )
}
