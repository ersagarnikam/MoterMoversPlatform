import { useEffect, useMemo, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api, type ClassSheet } from '../api'
import { exerciseOutbox } from '../outbox'
import Skeleton from '../components/Skeleton'
import StageBadge from '../components/StageBadge'
import { suggestedPoint } from '../assessment'

const LEVEL_NAMES: Record<number, string> = { 1: 'Not yet', 2: 'Developing', 3: 'Nearly there', 4: 'Confident and accurate' }

/**
 * Pat's two assessments. Exercises: the class does an exercise together, so the teacher scores the whole class on
 * one exercise at one point (1 to 4). Developmental: one child at a time, on the fine and gross motor charts.
 */
export default function AssessPage() {
  const { classId } = useParams()
  const [tab, setTab] = useState<'exercises' | 'developmental'>('exercises')
  const [sheet, setSheet] = useState<ClassSheet | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api.exerciseSheet(Number(classId)).then(setSheet).catch((e) => setError(e.message))
  }, [classId])

  if (error) return <p className="error">{error}</p>
  if (!sheet) return <Skeleton />

  return (
    <>
      <div className="page-head">
        <div>
          <p className="eyebrow-badge">
            <StageBadge stage={sheet.stage} label={sheet.stageName} />
          </p>
          <h1>Assess · {sheet.className}</h1>
        </div>
        <Link className="btn" to={`/class/${classId}/today`}>
          Back to today
        </Link>
      </div>
      <div className="chips" role="group" aria-label="Assessment">
        <button
          aria-pressed={tab === 'exercises'}
          className={`chip ${tab === 'exercises' ? 'chip-on' : ''}`}
          onClick={() => setTab('exercises')}
        >
          Exercises (1 to 4)
        </button>
        <button
          aria-pressed={tab === 'developmental'}
          className={`chip ${tab === 'developmental' ? 'chip-on' : ''}`}
          onClick={() => setTab('developmental')}
        >
          Developmental charts
        </button>
        <Link className="chip" to={`/class/${classId}/screening`}>
          Readiness check (0 to 3)
        </Link>
      </div>
      {tab === 'exercises' ? <Exercises sheet={sheet} onChange={setSheet} /> : <Developmental sheet={sheet} />}
    </>
  )
}

function Exercises({ sheet, onChange }: { sheet: ClassSheet; onChange: (s: ClassSheet) => void }) {
  const [exerciseId, setExerciseId] = useState<number | null>(sheet.exercises[0]?.id ?? null)
  const exercise = sheet.exercises.find((e) => e.id === exerciseId) ?? null
  const suggested = useMemo(() => (exercise ? suggestedPoint(exercise, sheet.children) : null), [exercise, sheet])
  const [point, setPoint] = useState<string | null>(null)
  const [unsent, setUnsent] = useState(exerciseOutbox.pending().length)
  const [online, setOnline] = useState(navigator.onLine)

  useEffect(() => {
    const up = () => {
      setOnline(true)
      exerciseOutbox.flush().then(setUnsent)
    }
    const down = () => setOnline(false)
    window.addEventListener('online', up)
    window.addEventListener('offline', down)
    exerciseOutbox.flush().then(setUnsent)
    return () => {
      window.removeEventListener('online', up)
      window.removeEventListener('offline', down)
    }
  }, [])

  if (sheet.exercises.length === 0) {
    return (
      <p className="banner-info">
        The exercise assessment for {sheet.stageName} is not loaded yet. Green and Yellow are ready; the other colours
        follow when Pat sends their grids.
      </p>
    )
  }
  if (!exercise) return null
  const at = point && exercise.points.some((p) => p.point === point) ? point : (suggested ?? exercise.points[0].point)
  const scored = sheet.children.filter((c) => c.scores.some((s) => s.exerciseId === exercise.id && s.point === at))

  async function score(childId: number, value: number) {
    const today = new Date().toISOString().slice(0, 10)
    exerciseOutbox.enqueue({
      clientId: crypto.randomUUID(),
      childId,
      exerciseId: exercise!.id,
      point: at,
      score: value,
      assessedOn: today,
    })
    onChange({
      ...sheet,
      children: sheet.children.map((c) =>
        c.id !== childId
          ? c
          : {
              ...c,
              scores: [
                ...c.scores.filter((s) => !(s.exerciseId === exercise!.id && s.point === at)),
                { exerciseId: exercise!.id, point: at, score: value, assessedOn: today },
              ],
            },
      ),
    })
    setUnsent(exerciseOutbox.pending().length)
    setUnsent(await exerciseOutbox.flush())
  }

  return (
    <div className="assess-grid">
      <section className="card">
        <div className="assess-pickers">
          <label>
            Exercise
            <select value={exercise.id} onChange={(e) => setExerciseId(Number(e.target.value))}>
              {sheet.exercises.map((e) => (
                <option key={e.id} value={e.id}>
                  {e.number}. {e.title}
                </option>
              ))}
            </select>
          </label>
          <label>
            When
            <select value={at} onChange={(e) => setPoint(e.target.value)}>
              {exercise.points.map((p) => (
                <option key={p.point} value={p.point}>
                  {p.label}
                  {p.point === suggested ? ' (suggested)' : ''}
                </option>
              ))}
            </select>
          </label>
        </div>
        <details className="movement">
          <summary>The movement</summary>
          <p className="pre-line">{exercise.instructions}</p>
        </details>
        <dl className="levels">
          {[1, 2, 3, 4].map((n) => (
            <div key={n}>
              <dt>
                {n} · {LEVEL_NAMES[n]}
              </dt>
              <dd>{exercise.levels[n] ?? <span className="muted">No description yet</span>}</dd>
            </div>
          ))}
        </dl>
      </section>
      <section className="card">
        <div className="section-head">
          <strong>
            {scored.length} of {sheet.children.length} scored
          </strong>
          <span className={online ? 'muted' : 'offline-pill'}>
            {online ? 'Online' : 'Offline'} · {unsent} saved on this device
          </span>
        </div>
        <ul className="score-list">
          {sheet.children.map((c) => {
            const current = c.scores.find((s) => s.exerciseId === exercise.id && s.point === at)?.score
            return (
              <li key={c.id}>
                <Link to={`/child/${c.id}`} className="score-name">
                  {c.name}
                  {c.extraSupport && <span className="support-pill">Extra support</span>}
                </Link>
                <span className="score-buttons" role="radiogroup" aria-label={`${c.name}, score`}>
                  {[1, 2, 3, 4].map((n) => (
                    <button
                      key={n}
                      role="radio"
                      aria-checked={current === n}
                      title={LEVEL_NAMES[n]}
                      className={`score-btn ${current === n ? 'score-on' : ''}`}
                      onClick={() => score(c.id, n)}
                    >
                      {n}
                    </button>
                  ))}
                </span>
              </li>
            )
          })}
        </ul>
      </section>
    </div>
  )
}

function Developmental({ sheet }: { sheet: ClassSheet }) {
  return (
    <section className="card">
      <p className="muted">
        Choose a child. Tap each movement they can do confidently and securely; the platform records their age in
        months. Do it at the start, middle and end of the school year.
      </p>
      <ul className="score-list">
        {sheet.children.map((c) => (
          <li key={c.id}>
            <Link to={`/child/${c.id}/development`} className="score-name">
              {c.name}
            </Link>
            <Link className="btn" to={`/child/${c.id}/development`} aria-label={`${c.name}, developmental charts`}>
              Open charts
            </Link>
          </li>
        ))}
      </ul>
    </section>
  )
}
