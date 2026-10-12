import type { SeatMap, SeatStatus } from '../api/types'

/** Applies a live update from the websocket to the seat map. */
export function applySeatUpdate(map: SeatMap, updates: { seatId: number; status: SeatStatus }[]): SeatMap {
  const changes = new Map(updates.map((u) => [u.seatId, u.status]))
  return {
    ...map,
    rows: map.rows.map((row) => ({
      ...row,
      seats: row.seats.map((seat) => (changes.has(seat.id) ? { ...seat, status: changes.get(seat.id)! } : seat)),
    })),
  }
}

/** Toggle a seat in the selection, respecting the max seats per booking. */
export function toggleSeat(selected: number[], seatId: number, max: number): number[] {
  if (selected.includes(seatId)) return selected.filter((id) => id !== seatId)
  if (selected.length >= max) return selected
  return [...selected, seatId]
}

export function selectionTotal(map: SeatMap, selected: number[]) {
  let total = 0
  const labels: string[] = []
  for (const row of map.rows) {
    for (const seat of row.seats) {
      if (selected.includes(seat.id)) {
        total += seat.price
        labels.push(seat.label)
      }
    }
  }
  return { total, labels }
}
