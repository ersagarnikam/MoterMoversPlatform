import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, type AssessmentOverview } from '../api'
import Skeleton from './Skeleton'
import StageBadge from './StageBadge'

/**
 * The head's view of Pat's two assessments: per class, how many children have a developmental assessment at each
 * point this school year, the average movements secured per child assessed, and how many children are at each exercise
 * level (each child once, at the middle of their latest scores).
 * Then the children who may benefit from extra support.
 */
export default function AssessmentOverviewCard({ schoolId }: { schoolId: number }) {
  const [data, setData] = useState<AssessmentOverview | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api.assessmentOverview(schoolId).then(setData).catch((e) => setError(e.message))
  }, [schoolId])

  if (error) return <p className="error">{error}</p>
  if (!data) return <Skeleton />

  return (
    <section className="card">
      <h3>
        Assessments · school year {data.schoolYear} to {data.schoolYear + 1}
      </h3>
      <div className="table-wrap" tabIndex={0} role="region" aria-label="Assessments by class">
        <table className="cards">
          <thead>
            <tr>
              <th>Class</th>
              <th>Colour</th>
              <th className="num">Developmental: start / middle / end</th>
              <th className="num">Movements secured per child</th>
              <th>Children at each exercise level</th>
              <th className="num">May benefit from extra support</th>
            </tr>
          </thead>
          <tbody>
            {data.classes.map((c) => {
              const total = Object.values(c.childrenAtLevel).reduce((a, b) => a + b, 0)
              return (
                <tr key={c.classId} className={`stage-row stage-tint-${c.stage.toLowerCase()}`}>
                  <td>{c.name}</td>
                  <td data-label="Colour">
                    <StageBadge stage={c.stage} label={c.stageName} />
                  </td>
                  <td className="num" data-label="Developmental: start / middle / end">
                    {c.developmental.START} / {c.developmental.MIDDLE} / {c.developmental.END}
                    <span className="muted"> of {c.children}</span>
                  </td>
                  <td className="num" data-label="Movements secured per child">
                    {c.movementsPerChild === 0 ? <span className="muted">-</span> : c.movementsPerChild}
                  </td>
                  <td data-label="Children at each exercise level">
                    {total === 0 ? (
                      <span className="muted">None yet</span>
                    ) : (
                      <LevelCounts counts={c.childrenAtLevel} />
                    )}
                  </td>
                  <td className="num" data-label="May benefit from extra support">
                    {c.extraSupport}
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>
      <h4>May benefit from extra support</h4>
      {data.extraSupport.length === 0 ? (
        <p className="muted">No child has a pattern of low scores.</p>
      ) : (
        <ul className="support-list">
          {data.extraSupport.map((f) => (
            <li key={f.childId}>
              <Link to={`/child/${f.childId}`}>{f.name}</Link> <span className="muted">· {f.className}</span>
              <span className="muted">
                {' '}
                · {f.reasons[0]}
                {f.reasons.length > 1 &&
                  ` (${f.reasons.length - 1} more ${f.reasons.length === 2 ? 'reason' : 'reasons'} on the profile)`}
              </span>
            </li>
          ))}
        </ul>
      )}
      <p className="fine">A flag shows a pattern of low exercise scores. It is not a diagnosis and names no cause.</p>
    </section>
  )
}

/** Short names for the table; the full name is the hover text. */
const LEVELS: [number, string, string][] = [
  [1, 'Not yet', 'Not yet'],
  [2, 'Developing', 'Developing'],
  [3, 'Nearly there', 'Nearly there'],
  [4, 'Confident', 'Confident and accurate'],
]

/** How many children are at each level, labelled so the numbers read without a key. */
function LevelCounts({ counts }: { counts: Record<string, number> }) {
  return (
    <ul className="level-counts">
      {LEVELS.map(([n, name, full]) => (
        <li key={n} title={full}>
          <span className="level-num">{n}</span> {name}: <strong>{counts[n] ?? 0}</strong>
        </li>
      ))}
    </ul>
  )
}
