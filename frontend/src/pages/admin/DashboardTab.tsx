import { useEffect, useState } from 'react'
import { api } from '../../api/client'
import type { Dashboard } from '../../api/types'
import BarChart from '../../components/BarChart'
import { rupees, showDateTime, statusLabel } from '../../utils/format'

export default function DashboardTab() {
  const [data, setData] = useState<Dashboard | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    api.dashboard().then(setData).catch((e: Error) => setError(e.message))
  }, [])

  if (error) return <div className="alert alert-error">{error}</div>
  if (!data) return <p className="muted">Loading...</p>

  const stats = [
    { label: 'Total revenue', value: rupees(data.totalRevenue) },
    { label: 'Confirmed bookings', value: data.confirmedBookings },
    { label: 'Cancelled', value: data.cancelledBookings },
    { label: 'Seats on hold now', value: data.activeHolds },
    { label: 'Upcoming shows', value: data.upcomingShows },
    { label: 'Users', value: data.users },
  ]

  return (
    <div className="dashboard">
      <div className="stat-grid">
        {stats.map((s) => (
          <div key={s.label} className="card stat">
            <span className="muted small">{s.label}</span>
            <b>{s.value}</b>
          </div>
        ))}
      </div>

      <div className="dash-grid">
        <div className="card">
          <h3>Revenue, last 7 days</h3>
          <BarChart
            data={data.revenueByDay.map((d) => ({
              label: new Date(d.day).toLocaleDateString('en-IN', { day: 'numeric', month: 'short' }),
              value: Number(d.revenue),
              hint: `${d.bookings} bookings, ${rupees(d.revenue)}`,
            }))}
            format={(v) => (v >= 1000 ? `${(v / 1000).toFixed(1)}k` : String(v))}
          />
        </div>
        <div className="card">
          <h3>Top movies by tickets sold</h3>
          <table className="table">
            <tbody>
              {data.topMovies.map((m) => (
                <tr key={m.title}>
                  <td>{m.title}</td>
                  <td className="num">{m.seatsSold}</td>
                  <td className="num">{rupees(m.revenue)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      <div className="card">
        <h3>Most booked shows</h3>
        <table className="table">
          <thead>
            <tr><th>Show</th><th>Theatre</th><th>When</th><th className="num">Sold</th><th className="num">Revenue</th></tr>
          </thead>
          <tbody>
            {data.mostBookedShows.map((s) => (
              <tr key={s.showId}>
                <td>{s.movieTitle}</td>
                <td>{s.theatreName}</td>
                <td>{showDateTime(s.startTime)}</td>
                <td className="num">
                  {s.seatsSold}/{s.capacity}
                  <div className="occupancy"><div style={{ width: `${(s.seatsSold / s.capacity) * 100}%` }} /></div>
                </td>
                <td className="num">{rupees(s.revenue)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="card">
        <h3>Recent bookings</h3>
        <table className="table">
          <thead>
            <tr><th>Booking</th><th>Customer</th><th>Movie</th><th className="num">Seats</th><th className="num">Amount</th><th>Status</th></tr>
          </thead>
          <tbody>
            {data.recentBookings.map((b) => (
              <tr key={b.bookingCode}>
                <td className="mono">{b.bookingCode}</td>
                <td>{b.userName}</td>
                <td>{b.movieTitle}</td>
                <td className="num">{b.seatCount}</td>
                <td className="num">{rupees(b.amount)}</td>
                <td><span className={`status status-${b.status.toLowerCase()}`}>{statusLabel[b.status]}</span></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}
