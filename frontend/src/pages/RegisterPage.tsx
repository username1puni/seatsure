import { useState, type ChangeEvent, type FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { ApiError } from '../api/client'
import { useAuth } from '../context/useAuth'

export default function RegisterPage() {
  const { register } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const state = location.state as { from?: string; seats?: number[] } | null
  const [form, setForm] = useState({ fullName: '', email: '', phone: '', password: '' })
  const [error, setError] = useState('')
  const [fields, setFields] = useState<Record<string, string>>({})
  const [busy, setBusy] = useState(false)

  const set = (key: keyof typeof form) => (e: ChangeEvent<HTMLInputElement>) =>
    setForm((f) => ({ ...f, [key]: e.target.value }))

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setBusy(true)
    setError('')
    setFields({})
    try {
      await register(form.fullName, form.email, form.password, form.phone || undefined)
      navigate(state?.from ?? '/', { replace: true, state: state?.seats ? { seats: state.seats } : undefined })
    } catch (err) {
      setError((err as Error).message)
      if (err instanceof ApiError && err.fields) setFields(err.fields)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="container page auth-page">
      <form className="card auth-card" onSubmit={submit}>
        <h1>Create account</h1>
        {error && <div className="alert alert-error">{error}</div>}
        <label>
          Full name
          <input value={form.fullName} onChange={set('fullName')} required autoComplete="name" />
          {fields.fullName && <span className="field-error">{fields.fullName}</span>}
        </label>
        <label>
          Email
          <input type="email" value={form.email} onChange={set('email')} required autoComplete="email" />
          {fields.email && <span className="field-error">{fields.email}</span>}
        </label>
        <label>
          Mobile number <span className="muted">(optional)</span>
          <input value={form.phone} onChange={set('phone')} inputMode="numeric" maxLength={10} autoComplete="tel-national" />
          {fields.phone && <span className="field-error">{fields.phone}</span>}
        </label>
        <label>
          Password
          <input type="password" value={form.password} onChange={set('password')} required minLength={8} autoComplete="new-password" />
          {fields.password && <span className="field-error">{fields.password}</span>}
        </label>
        <button className="btn btn-block" disabled={busy}>{busy ? 'Creating account...' : 'Create account'}</button>
        <p className="muted small center">
          Already have an account? <Link to="/login" state={state}>Sign in</Link>
        </p>
      </form>
    </div>
  )
}
