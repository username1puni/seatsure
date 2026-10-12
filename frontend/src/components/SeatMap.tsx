import type { SeatMap as SeatMapData } from '../api/types'
import { rupees } from '../utils/format'

interface Props {
  map: SeatMapData
  selected: number[]
  onToggle: (seatId: number) => void
}

export default function SeatMap({ map, selected, onToggle }: Props) {
  const sections: { category: string; price: number; rows: SeatMapData['rows'] }[] = []
  for (const row of map.rows) {
    const last = sections[sections.length - 1]
    if (last && last.category === row.category) {
      last.rows.push(row)
    } else {
      sections.push({ category: row.category, price: row.seats[0]?.price ?? 0, rows: [row] })
    }
  }

  return (
    <div className="seat-map">
      <div className="seat-scroll">
        <div className="seat-inner">
          {/* back rows (premium) are drawn first so the screen ends up at the bottom like in a hall */}
          {[...sections].reverse().map((section) => (
            <div key={section.category + section.rows[0].label} className="seat-section">
              <div className="seat-section-title">
                {section.category === 'PREMIUM' ? 'Premium' : 'Normal'} &middot; {rupees(section.price)}
              </div>
              {[...section.rows].reverse().map((row) => (
                <div key={row.label} className="seat-row">
                  <span className="row-label">{row.label}</span>
                  <div className="row-seats">
                    {row.seats.map((seat, i) => {
                      const isSelected = selected.includes(seat.id)
                      const taken = seat.status !== 'AVAILABLE'
                      // aisle in the middle of the row
                      const aisle = i === Math.floor(row.seats.length / 2) ? ' aisle' : ''
                      return (
                        <button
                          key={seat.id}
                          type="button"
                          className={`seat seat-${seat.status.toLowerCase()}${isSelected ? ' seat-selected' : ''}${aisle}`}
                          disabled={taken && !isSelected}
                          aria-pressed={isSelected}
                          aria-label={`Seat ${seat.label}, ${taken ? 'not available' : rupees(seat.price)}`}
                          title={`${seat.label} - ${taken ? (seat.status === 'HELD' ? 'being booked' : 'sold') : rupees(seat.price)}`}
                          onClick={() => onToggle(seat.id)}
                        >
                          {seat.number}
                        </button>
                      )
                    })}
                  </div>
                  <span className="row-label">{row.label}</span>
                </div>
              ))}
            </div>
          ))}
          <div className="screen">
            <div className="screen-curve" />
            <span>All eyes this way please</span>
          </div>
        </div>
      </div>
      <div className="seat-legend">
        <span><i className="seat-dot available" /> Available</span>
        <span><i className="seat-dot selected" /> Selected</span>
        <span><i className="seat-dot held" /> Being booked</span>
        <span><i className="seat-dot booked" /> Sold</span>
      </div>
    </div>
  )
}
