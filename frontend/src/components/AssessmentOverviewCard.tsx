import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, type AssessmentOverview } from '../api'
import Skeleton from './Skeleton'

/**
 * The head's view of Pat's two assessments: per class, how many children have a developmental assessment at each
 * point this school year, and the latest exercise scores as the number of scores at each level (not averages).
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
              <th className="num">Movements secured</th>
              <th>Latest exercise scores (1 · 2 · 3 · 4)</th>
              <th className="num">May benefit from extra support</th>
            </tr>
          </thead>
          <tbody>
            {data.classes.map((c) => {
              const total = Object.values(c.scoreLevels).reduce((a, b) => a + b, 0)
              return (
                <tr key={c.classId}>
                  <td>{c.name}</td>
                  <td data-label="Colour">{c.stageName}</td>
                  <td className="num" data-label="Developmental: start / middle / end">
                    {c.developmental.START} / {c.developmental.MIDDLE} / {c.developmental.END}
                    <span className="muted"> of {c.children}</span>
                  </td>
                  <td className="num" data-label="Movements secured">
                    {c.movementsSecured}
                  </td>
                  <td data-label="Latest exercise scores (1 · 2 · 3 · 4)">
                    {total === 0 ? (
                      <span className="muted">None yet</span>
                    ) : (
                      [1, 2, 3, 4].map((n) => c.scoreLevels[n] ?? 0).join(' · ')
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
                {f.reasons.length > 1 && ` and ${f.reasons.length - 1} more`}
              </span>
            </li>
          ))}
        </ul>
      )}
      <p className="fine">A flag shows a pattern of low exercise scores. It is not a diagnosis and names no cause.</p>
    </section>
  )
}
