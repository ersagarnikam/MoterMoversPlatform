import { useEffect, useState } from 'react'
import { api, type SchoolRow } from '../api'
import StageBadge from '../components/StageBadge'
import { STAGES } from '../stages'

export default function SchoolsPage() {
  const [schools, setSchools] = useState<SchoolRow[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api.schools().then(setSchools).catch((e) => setError(e.message))
  }, [])

  if (error) return <p className="error">{error}</p>
  if (!schools) return <p className="muted">Loading…</p>

  return (
    <>
      <h1>Schools & licences</h1>
      <div className="card table-wrap">
        <table>
          <thead>
            <tr>
              <th>School</th>
              <th>Trust</th>
              <th>Plan</th>
              <th>Status</th>
              <th>Ends</th>
              <th>Stages</th>
            </tr>
          </thead>
          <tbody>
            {schools.map((s) => (
              <tr key={s.id}>
                <td>{s.name}</td>
                <td>{s.trustName ?? '-'}</td>
                <td>{s.plan ?? '-'}</td>
                <td>{s.licenceStatus.replace('_', ' ').toLowerCase()}</td>
                <td>{s.endsOn ?? '-'}</td>
                <td className="stage-list">
                  {STAGES.filter((st) => s.stages.includes(st.key)).map((st) => (
                    <StageBadge key={st.key} stage={st.key} />
                  ))}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  )
}
