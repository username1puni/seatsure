import { Link, NavLink, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/useAuth'
import { useCity } from '../context/cityContext'
import Logo from './Logo'

export default function Navbar() {
  const { user, logout } = useAuth()
  const { cities, city, setCity } = useCity()
  const navigate = useNavigate()

  return (
    <header className="navbar">
      <div className="container navbar-inner">
        <Link to="/" className="brand" aria-label="SeatSure home">
          <Logo />
        </Link>
        <select
          className="city-select"
          aria-label="City"
          value={city?.id ?? ''}
          onChange={(e) => {
            const next = cities.find((c) => c.id === Number(e.target.value))
            if (next) {
              setCity(next)
              navigate('/')
            }
          }}
        >
          {cities.map((c) => (
            <option key={c.id} value={c.id}>{c.name}</option>
          ))}
        </select>
        <nav className="nav-links">
          {user && <NavLink to="/bookings">My bookings</NavLink>}
          {user?.role === 'ADMIN' && <NavLink to="/admin">Admin</NavLink>}
          {user ? (
            <button className="link-button" onClick={() => { logout(); navigate('/') }}>
              Log out
            </button>
          ) : (
            <NavLink to="/login" className="btn btn-small">Sign in</NavLink>
          )}
        </nav>
      </div>
    </header>
  )
}
