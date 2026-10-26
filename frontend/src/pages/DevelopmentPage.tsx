import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api, type Chart, type Development, type YearPoint } from '../api'
import { milestoneOutbox } from '../outbox'
import { bandOpen } from '../assessment'
import { day } from '../dates'
import NotADiagnosis from '../components/NotADiagnosis'
import { useConfirm, useToast } from '../components/Feedback'
import Skeleton from '../components/Skeleton'

const YEAR_POINTS: { value: YearPoint; label: string }[] = [
  { value: 'START', label: 'Start of year' },
  { value: 'MIDDLE', label: 'Middle of year' },
  { value: 'END', label: 'End of year' },
]

/**
 * Pat's developmental assessment: the fine and gross motor charts from birth to 7 years. When a child can do a
 * movement confidently and securely, the teacher taps it and the child's age in months is recorded with it.
 */
export default function DevelopmentPage() {
  const { childId } = useParams()
  const id = Number(childId)
  const [data, setData] = useState<Development | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [age, setAge] = useState('')
  const [yearPoint, setYearPoint] = useState<YearPoint>('START')
  const confirm = useConfirm()
  const toast = useToast()

  useEffect(() => {
    api
      .development(id)
      .then((d) => {
        setData(d)
        setYearPoint(d.suggestedYearPoint)
        const ages = d.charts.flatMap((c) =>
          c.milestones.flatMap((m) => (m.achieved?.schoolYear === d.schoolYear ? [m.achieved.ageMonths] : [])),
        )
        if (ages.length > 0) setAge(String(Math.max(...ages)))
      })
      .catch((e) => setError(e.message))
    milestoneOutbox.flush()
  }, [id])

  if (error) return <p className="error">{error}</p>
  if (!data) return <Skeleton />

  const ageMonths = /^\d{1,3}$/.test(age) ? Number(age) : null
  const pointLabel = YEAR_POINTS.find((p) => p.value === yearPoint)!.label

  function update(chart: Chart, milestoneId: number, achieved: Development['charts'][0]['milestones'][0]['achieved']) {
    setData((d) =>
      d && {
        ...d,
        charts: d.charts.map((c) =>
          c.chart !== chart
            ? c
            : {
                ...c,
                secure: c.secure + (achieved ? 1 : -1),
                milestones: c.milestones.map((m) => (m.id === milestoneId ? { ...m, achieved } : m)),
              },
        ),
      },
    )
  }

  async function toggle(chart: Chart, m: Development['charts'][0]['milestones'][0]) {
    if (m.achieved) {
      const ok = await confirm({
        title: 'Take this movement off?',
        body: `"${m.description}" was recorded as secure at ${m.achieved.ageMonths} months. Only take it off if it was recorded by mistake.`,
        confirmLabel: 'Take it off',
      })
      if (!ok) return
      try {
        await api.clearAchievement(id, m.id)
        update(chart, m.id, null)
      } catch (e) {
        toast(e instanceof Error ? e.message : 'Could not take it off', 'error')
      }
      return
    }
    if (ageMonths === null) {
      toast('Enter the child’s age in months first', 'error')
      return
    }
    const today = new Date().toISOString().slice(0, 10)
    milestoneOutbox.enqueue({
      clientId: crypto.randomUUID(),
      childId: id,
      milestoneId: m.id,
      ageMonths,
      yearPoint,
      achievedOn: today,
    })
    update(chart, m.id, { ageMonths, yearPoint, yearPointLabel: pointLabel, schoolYear: data!.schoolYear, achievedOn: today })
    const left = await milestoneOutbox.flush()
    if (left > 0) toast(`${left} saved on this device, to send when back online`)
  }

  return (
    <div className="development">
      <div className="page-head">
        <div>
          <p className="eyebrow">{data.className}</p>
          <h1>Developmental charts · {data.name}</h1>
        </div>
        <Link className="btn" to={`/child/${id}`}>
          Assessment profile
        </Link>
      </div>
      <NotADiagnosis text="Progress with the programme's movements, not a clinical test." />
      <div className="card assess-pickers">
        <label>
          Age in months
          <input
            inputMode="numeric"
            value={age}
            onChange={(e) => setAge(e.target.value.replace(/\D/g, '').slice(0, 3))}
            placeholder="e.g. 62"
            aria-describedby="age-help"
          />
        </label>
        <label>
          When
          <select value={yearPoint} onChange={(e) => setYearPoint(e.target.value as YearPoint)}>
            {YEAR_POINTS.map((p) => (
              <option key={p.value} value={p.value}>
                {p.label}
              </option>
            ))}
          </select>
        </label>
        <p id="age-help" className="muted">
          Recorded with each movement you mark secure now. No date of birth is kept.
        </p>
      </div>
      {data.charts.map((c) => (
        <section key={c.chart} className="card chart">
          <div className="section-head">
            <h2>{c.label}</h2>
            <span className="muted">
              {c.secure} of {c.total} secure
            </span>
          </div>
          {bands(c.milestones).map((b) => (
            <details key={b.band} open={bandOpen(ageMonths, b.from, b.to)}>
              <summary>
                {b.band} <span className="muted">· {b.items.filter((m) => m.achieved).length} of {b.items.length}</span>
              </summary>
              <ul className="milestones">
                {b.items.map((m) => (
                  <li key={m.id}>
                    <button
                      role="switch"
                      aria-checked={!!m.achieved}
                      className={`secure-toggle ${m.achieved ? 'secure-on' : ''}`}
                      onClick={() => toggle(c.chart, m)}
                    >
                      <span className="tick" aria-hidden="true">
                        {m.achieved ? '✓' : ''}
                      </span>
                      <span>{m.description}</span>
                    </button>
                    {m.achieved && (
                      <span className="muted secure-note">
                        Secure at {m.achieved.ageMonths} months · {m.achieved.yearPointLabel} {m.achieved.schoolYear}
                      </span>
                    )}
                  </li>
                ))}
              </ul>
            </details>
          ))}
          <Comment
            key={`${c.chart}-${yearPoint}`}
            childId={id}
            chart={c.chart}
            yearPoint={yearPoint}
            label={pointLabel}
            existing={c.comments.find((n) => n.yearPoint === yearPoint && n.schoolYear === data.schoolYear) ?? null}
            earlier={c.comments.filter((n) => !(n.yearPoint === yearPoint && n.schoolYear === data.schoolYear))}
          />
        </section>
      ))}
    </div>
  )
}

function bands(milestones: Development['charts'][0]['milestones']) {
  const out: { band: string; from: number; to: number; items: typeof milestones }[] = []
  for (const m of milestones) {
    const last = out[out.length - 1]
    if (last && last.band === m.band) last.items.push(m)
    else out.push({ band: m.band, from: m.ageFromMonths, to: m.ageToMonths, items: [m] })
  }
  return out
}

function Comment(props: {
  childId: number
  chart: Chart
  yearPoint: YearPoint
  label: string
  existing: Development['charts'][0]['comments'][0] | null
  earlier: Development['charts'][0]['comments']
}) {
  const [note, setNote] = useState(props.existing?.note ?? '')
  const [busy, setBusy] = useState(false)
  const toast = useToast()

  async function save() {
    setBusy(true)
    try {
      await api.writeDevelopmentComment(props.childId, { chart: props.chart, yearPoint: props.yearPoint, note })
      toast('Comment saved')
    } catch (e) {
      toast(e instanceof Error ? e.message : 'Could not save', 'error')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="chart-comment">
      <label>
        Comments · {props.label}
        <textarea rows={3} maxLength={2000} value={note} onChange={(e) => setNote(e.target.value)} />
      </label>
      <div className="actions">
        <button className="btn" onClick={save} disabled={busy}>
          Save comment
        </button>
      </div>
      {props.earlier.map((n) => (
        <p key={`${n.schoolYear}-${n.yearPoint}`} className="muted">
          <strong>
            {n.yearPointLabel} {n.schoolYear}
          </strong>{' '}
          ({day(n.writtenOn)}): {n.note}
        </p>
      ))}
    </div>
  )
}
