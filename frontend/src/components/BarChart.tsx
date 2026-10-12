interface Props {
  data: { label: string; value: number; hint?: string }[]
  format?: (value: number) => string
}

/** Small dependency free bar chart for the admin dashboard. */
export default function BarChart({ data, format = String }: Props) {
  const max = Math.max(1, ...data.map((d) => d.value))
  return (
    <div className="bar-chart">
      {data.map((d) => (
        <div key={d.label} className="bar-col" title={d.hint ?? `${d.label}: ${format(d.value)}`}>
          <span className="bar-value">{d.value > 0 ? format(d.value) : ''}</span>
          <div className="bar" style={{ height: `${(d.value / max) * 100}%` }} />
          <span className="bar-label">{d.label}</span>
        </div>
      ))}
    </div>
  )
}
