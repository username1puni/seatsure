import { useEffect, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { api } from '../api/client'
import type { Movie, TheatreShows } from '../api/types'
import DateStrip from '../components/DateStrip'
import Poster from '../components/Poster'
import { useCity } from '../context/cityContext'
import { duration, rupees, showTime } from '../utils/format'

export default function MoviePage() {
  const { id } = useParams()
  const movieId = Number(id)
  const { city } = useCity()
  const [params, setParams] = useSearchParams()
  const [movie, setMovie] = useState<Movie | null>(null)
  const [dates, setDates] = useState<string[]>([])
  const [theatres, setTheatres] = useState<TheatreShows[] | null>(null)
  const [error, setError] = useState('')
  const date = params.get('date') ?? dates[0] ?? ''

  useEffect(() => {
    api.movie(movieId).then(setMovie).catch((e: Error) => setError(e.message))
  }, [movieId])

  useEffect(() => {
    if (!city) return
    api.movieDates(movieId, city.id).then(setDates).catch(() => setDates([]))
  }, [movieId, city])

  useEffect(() => {
    if (!city || !date) return
    let cancelled = false
    api.movieShows(movieId, city.id, date)
      .then((list) => { if (!cancelled) setTheatres(list) })
      .catch((e: Error) => { if (!cancelled) setError(e.message) })
    return () => { cancelled = true }
  }, [movieId, city, date])

  if (error && !movie) return <div className="container page"><div className="alert alert-error">{error}</div></div>
  if (!movie) return <div className="container page"><p className="muted">Loading...</p></div>

  return (
    <div className="page">
      <section className="movie-hero">
        <div className="container movie-hero-inner">
          <Poster movie={movie} />
          <div>
            <span className="badge">{movie.kind === 'EVENT' ? 'Live event' : 'Movie'}</span>
            <h1>{movie.title}</h1>
            <p className="movie-meta">
              {movie.certificate} &middot; {movie.language} &middot; {movie.genre} &middot; {duration(movie.durationMinutes)}
            </p>
            {movie.description && <p className="movie-desc">{movie.description}</p>}
          </div>
        </div>
      </section>

      <div className="container">
        {dates.length > 0 && (
          <DateStrip dates={dates} selected={date} onSelect={(d) => setParams({ date: d }, { replace: true })} />
        )}
        {dates.length === 0 && <div className="empty"><p>No upcoming shows in {city?.name}.</p></div>}

        <div className="theatre-list">
          {theatres?.length === 0 && dates.length > 0 && <p className="muted">No shows on this date.</p>}
          {theatres?.map((t) => (
            <div key={t.theatreId} className="card theatre-card">
              <div className="theatre-info">
                <h3>{t.theatreName}</h3>
                <p className="muted small">{t.address}</p>
              </div>
              <div className="show-times">
                {t.shows.map((s) => {
                  const ratio = s.availableSeats / s.totalSeats
                  const state = s.availableSeats === 0 ? 'sold-out' : ratio < 0.3 ? 'filling' : 'open'
                  return (
                    <Link
                      key={s.id}
                      to={s.availableSeats === 0 ? '#' : `/shows/${s.id}`}
                      className={`time-chip ${state}`}
                      aria-disabled={s.availableSeats === 0}
                      title={`${s.screenName} - ${rupees(s.priceNormal)} / ${rupees(s.pricePremium)} - ${s.availableSeats} seats left`}
                    >
                      <span>{showTime(s.startTime)}</span>
                      <small>{s.screenName}</small>
                    </Link>
                  )
                })}
              </div>
            </div>
          ))}
        </div>
        <p className="legend-note muted small">
          <span className="dot open" /> Available <span className="dot filling" /> Filling fast <span className="dot sold-out" /> Sold out
        </p>
      </div>
    </div>
  )
}
