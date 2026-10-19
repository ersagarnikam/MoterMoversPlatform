import { gbp } from '../money'

const MONTH = new Intl.DateTimeFormat('en-GB', { month: 'short', timeZone: 'UTC' })
const MONTH_YEAR = new Intl.DateTimeFormat('en-GB', { month: 'long', year: 'numeric', timeZone: 'UTC' })
const monthDate = (m: string) => new Date(`${m}-01T00:00:00Z`)

/**
 * Money received each month, one bar per month, oldest first. One series, so no legend: the heading
 * names it. Each bar has a tooltip, and the same figures are in the table underneath.
 */
export default function MonthBars({ months }: { months: { month: string; paidPence: number }[] }) {
  const w = 640
  const h = 180
  const top = 12
  const bottom = 24
  const slot = w / months.length
  const barWidth = Math.min(28, slot - 8)
  const max = Math.max(1, ...months.map((m) => m.paidPence))
  const y = (pence: number) => h - bottom - (pence / max) * (h - top - bottom)

  /** A bar with 4px rounded top corners, square at the baseline. */
  function bar(x: number, pence: number) {
    const base = h - bottom
    const t = y(pence)
    const r = Math.min(4, (base - t) / 2, barWidth / 2)
    return `M${x},${base} V${t + r} Q${x},${t} ${x + r},${t} H${x + barWidth - r} Q${x + barWidth},${t} ${x + barWidth},${t + r} V${base} Z`
  }

  return (
    <>
      <svg className="month-bars" viewBox={`0 0 ${w} ${h}`} role="img" aria-label="Money received each month">
        <line x1={0} x2={w} y1={h - bottom} y2={h - bottom} className="axis" />
        {months.map((m, i) => {
          const x = i * slot + (slot - barWidth) / 2
          return (
            <g key={m.month}>
              {/* A full-height hit area, so empty and small months still show their tooltip. */}
              <rect x={i * slot} y={0} width={slot} height={h - bottom} className="hit">
                <title>
                  {MONTH_YEAR.format(monthDate(m.month))}: {gbp(m.paidPence)}
                </title>
              </rect>
              {m.paidPence > 0 && <path d={bar(x, m.paidPence)} className="bar" />}
              <text x={i * slot + slot / 2} y={h - 6} className="tick">
                {MONTH.format(monthDate(m.month))}
              </text>
            </g>
          )
        })}
      </svg>
      <details className="fine">
        <summary className="fine-link">Show as a table</summary>
        <table>
          <thead>
            <tr>
              <th>Month</th>
              <th className="num">Received</th>
            </tr>
          </thead>
          <tbody>
            {months.map((m) => (
              <tr key={m.month}>
                <td>{MONTH_YEAR.format(monthDate(m.month))}</td>
                <td className="num">{gbp(m.paidPence)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </details>
    </>
  )
}
