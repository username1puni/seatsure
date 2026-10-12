import type { SeatMap } from '../api/types'
import { applySeatUpdate, selectionTotal, toggleSeat } from './seats'

const map: SeatMap = {
  showId: 1,
  holdMinutes: 5,
  maxSeats: 3,
  rows: [
    {
      label: 'A',
      category: 'NORMAL',
      seats: [
        { id: 1, number: 1, label: 'A1', category: 'NORMAL', status: 'AVAILABLE', price: 200 },
        { id: 2, number: 2, label: 'A2', category: 'NORMAL', status: 'AVAILABLE', price: 200 },
      ],
    },
    {
      label: 'B',
      category: 'PREMIUM',
      seats: [{ id: 3, number: 1, label: 'B1', category: 'PREMIUM', status: 'AVAILABLE', price: 350 }],
    },
  ],
}

describe('seat helpers', () => {
  it('applies live updates without touching other seats', () => {
    const next = applySeatUpdate(map, [{ seatId: 2, status: 'HELD' }])
    expect(next.rows[0].seats[1].status).toBe('HELD')
    expect(next.rows[0].seats[0].status).toBe('AVAILABLE')
    expect(map.rows[0].seats[1].status).toBe('AVAILABLE') // original not mutated
  })

  it('toggles seats up to the limit', () => {
    let selected = toggleSeat([], 1, 2)
    selected = toggleSeat(selected, 2, 2)
    expect(toggleSeat(selected, 3, 2)).toEqual([1, 2])
    expect(toggleSeat(selected, 1, 2)).toEqual([2])
  })

  it('adds up the price of the selection', () => {
    expect(selectionTotal(map, [1, 3])).toEqual({ total: 550, labels: ['A1', 'B1'] })
  })
})
