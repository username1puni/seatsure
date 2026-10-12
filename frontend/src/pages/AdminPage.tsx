import { useState } from 'react'
import CheckInTab from './admin/CheckInTab'
import DashboardTab from './admin/DashboardTab'
import MoviesTab from './admin/MoviesTab'
import ShowsTab from './admin/ShowsTab'
import TheatresTab from './admin/TheatresTab'

const TABS = [
  { key: 'dashboard', label: 'Dashboard' },
  { key: 'shows', label: 'Shows and pricing' },
  { key: 'theatres', label: 'Theatres and screens' },
  { key: 'movies', label: 'Movies and events' },
  { key: 'checkin', label: 'Ticket check-in' },
] as const

type TabKey = (typeof TABS)[number]['key']

export default function AdminPage() {
  const [tab, setTab] = useState<TabKey>('dashboard')
  return (
    <div className="container page">
      <h1>Admin</h1>
      <div className="tabs admin-tabs" role="tablist">
        {TABS.map((t) => (
          <button key={t.key} role="tab" aria-selected={tab === t.key} className={tab === t.key ? 'active' : ''} onClick={() => setTab(t.key)}>
            {t.label}
          </button>
        ))}
      </div>
      {tab === 'dashboard' && <DashboardTab />}
      {tab === 'shows' && <ShowsTab />}
      {tab === 'theatres' && <TheatresTab />}
      {tab === 'movies' && <MoviesTab />}
      {tab === 'checkin' && <CheckInTab />}
    </div>
  )
}
