import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { SeatMap as SeatMapData } from '../api/types'
import SeatMap from './SeatMap'

const map: SeatMapData = {
  showId: 1,
  holdMinutes: 5,
  maxSeats: 10,
  rows: [
    {
      label: 'A',
      category: 'NORMAL',
      seats: [
        { id: 1, number: 1, label: 'A1', category: 'NORMAL', status: 'AVAILABLE', price: 200 },
        { id: 2, number: 2, label: 'A2', category: 'NORMAL', status: 'BOOKED', price: 200 },
        { id: 3, number: 3, label: 'A3', category: 'NORMAL', status: 'HELD', price: 200 },
      ],
    },
  ],
}

describe('SeatMap', () => {
  it('lets the user pick a free seat', async () => {
    const onToggle = vi.fn()
    render(<SeatMap map={map} selected={[]} onToggle={onToggle} />)
    await userEvent.click(screen.getByRole('button', { name: /Seat A1/ }))
    expect(onToggle).toHaveBeenCalledWith(1)
  })

  it('disables sold and held seats', () => {
    render(<SeatMap map={map} selected={[]} onToggle={() => undefined} />)
    expect(screen.getByRole('button', { name: /Seat A2/ })).toBeDisabled()
    expect(screen.getByRole('button', { name: /Seat A3/ })).toBeDisabled()
  })

  it('marks selected seats', () => {
    render(<SeatMap map={map} selected={[1]} onToggle={() => undefined} />)
    expect(screen.getByRole('button', { name: /Seat A1/ })).toHaveAttribute('aria-pressed', 'true')
  })
})
