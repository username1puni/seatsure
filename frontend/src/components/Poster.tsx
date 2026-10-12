import type { Movie } from '../api/types'

// Hand picked gradients so posters without an image still look different from each other
const PALETTES = [
  ['#7f1d1d', '#e11d48'],
  ['#1e3a8a', '#0ea5e9'],
  ['#14532d', '#22c55e'],
  ['#581c87', '#d946ef'],
  ['#78350f', '#f59e0b'],
  ['#0f172a', '#64748b'],
  ['#134e4a', '#2dd4bf'],
  ['#831843', '#fb7185'],
]

type PosterMovie = Pick<Movie, 'id' | 'title' | 'language' | 'kind'> & { posterUrl?: string | null }

export default function Poster({ movie, small = false }: { movie: PosterMovie; small?: boolean }) {
  if (movie.posterUrl) {
    return <img className={`poster ${small ? 'poster-small' : ''}`} src={movie.posterUrl} alt={movie.title} />
  }
  const [from, to] = PALETTES[movie.id % PALETTES.length]
  return (
    <div
      className={`poster poster-generated ${small ? 'poster-small' : ''}`}
      style={{ background: `linear-gradient(160deg, ${from}, ${to})` }}
      role="img"
      aria-label={movie.title}
    >
      {movie.kind === 'EVENT' && <span className="poster-tag">LIVE</span>}
      <span className="poster-title">{movie.title}</span>
      {!small && <span className="poster-lang">{movie.language}</span>}
    </div>
  )
}
