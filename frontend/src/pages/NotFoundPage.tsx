import { Link } from 'react-router-dom'

export default function NotFoundPage() {
  return (
    <div className="container page empty">
      <h1>Page not found</h1>
      <p className="muted">This page doesn't exist. Maybe the show already ended?</p>
      <Link to="/" className="btn">Back to home</Link>
    </div>
  )
}
