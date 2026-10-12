import { useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/useAuth'

export default function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const state = location.state as { from?: string; seats?: number[] } | null
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setBusy(true)
    setError('')
    try {
      await login(email, password)
      // send them back to the seat page with their selection still there
      navigate(state?.from ?? '/', { replace: true, state: state?.seats ? { seats: state.seats } : undefined })
    } catch (err) {
      setError((err as Error).message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="container page auth-page">
      <form className="card auth-card" onSubmit={submit}>
        <h1>Sign in</h1>
        {error && <div className="alert alert-error">{error}</div>}
        <label>
          Email
          <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required autoComplete="email" />
        </label>
        <label>
          Password
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required autoComplete="current-password" />
        </label>
        <button className="btn btn-block" disabled={busy}>{busy ? 'Signing in...' : 'Sign in'}</button>
        <p className="muted small center">
          New here? <Link to="/register" state={state}>Create an account</Link>
        </p>
      </form>
    </div>
  )
}
