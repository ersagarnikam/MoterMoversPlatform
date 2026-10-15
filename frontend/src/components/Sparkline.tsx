/** Small line chart of change since the first screening. Values are fractions 0..1. */
export default function Sparkline({ values, labels }: { values: number[]; labels: string[] }) {
  const w = 320
  const h = 70
  const pad = 8
  if (values.length === 0) return null
  const x = (i: number) => (values.length === 1 ? w / 2 : pad + (i * (w - 2 * pad)) / (values.length - 1))
  const y = (v: number) => h - pad - v * (h - 2 * pad)
  const points = values.map((v, i) => `${x(i)},${y(v)}`).join(' ')
  return (
    <svg className="sparkline" viewBox={`0 0 ${w} ${h}`} role="img" aria-label="Change since the first screening">
      <line x1={pad} x2={w - pad} y1={h - pad} y2={h - pad} className="axis" />
      <polyline points={points} className="line" />
      {values.map((v, i) => (
        <circle key={i} cx={x(i)} cy={y(v)} r={4} className="point">
          <title>
            {labels[i]}: {Math.round(v * 100)}%
          </title>
        </circle>
      ))}
    </svg>
  )
}
