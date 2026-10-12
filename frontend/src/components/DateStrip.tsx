import { dayChip } from '../utils/format'

interface Props {
  dates: string[]
  selected: string
  onSelect: (date: string) => void
}

export default function DateStrip({ dates, selected, onSelect }: Props) {
  return (
    <div className="date-strip" role="tablist" aria-label="Pick a date">
      {dates.map((d) => {
        const chip = dayChip(d)
        return (
          <button
            key={d}
            role="tab"
            aria-selected={d === selected}
            className={`date-chip ${d === selected ? 'active' : ''}`}
            onClick={() => onSelect(d)}
          >
            <span className="date-weekday">{chip.weekday}</span>
            <span className="date-day">{chip.day}</span>
            <span className="date-month">{chip.month}</span>
          </button>
        )
      })}
    </div>
  )
}
