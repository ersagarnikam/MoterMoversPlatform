import { useEffect, useState } from 'react'
import { api, type TrustDashboard } from '../api'
import { useAuth } from '../auth'
import { pct } from '../stages'

const STATUS_LABEL = { ACTIVE: 'Active', RENEWAL_DUE: 'Renewal due', FREE_TRIAL: 'Free trial', EXPIRED: 'Expired' }

/** Trust leads see each school's licence and summary results only. */
export default function TrustDashboardPage() {
  const { user } = useAuth()
  const [data, setData] = useState<TrustDashboard | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (user?.trustId) api.trustDashboard(user.trustId).then(setData).catch((e) => setError(e.message))
  }, [user?.trustId])

  if (!user?.trustId) return <p className="muted">This account is not linked to a trust.</p>
  if (error) return <p className="error">{error}</p>
  if (!data) return <p className="muted">Loading…</p>

  return (
    <>
      <div className="page-head">
        <h1>
          {data.trustName} · {data.schools.length} schools
        </h1>
        <span className="muted">Trust lead · summaries only</span>
      </div>
      <div className="card table-wrap">
        <table>
          <thead>
            <tr>
              <th>School</th>
              <th>Licence</th>
              <th>Renewal</th>
              <th>Stages</th>
              <th className="num">Screened</th>
              <th className="num">Participation</th>
            </tr>
          </thead>
          <tbody>
            {data.schools.map((s) => (
              <tr key={s.schoolId}>
                <td>{s.schoolName}</td>
                <td className={s.licenceStatus === 'RENEWAL_DUE' ? 'strong' : ''}>{STATUS_LABEL[s.licenceStatus]}</td>
                <td>{s.renewalDate ?? '-'}</td>
                <td>{s.stages}</td>
                <td className="num">{pct(s.screenedRate)}</td>
                <td className="num">{pct(s.participation)}</td>
              </tr>
            ))}
          </tbody>
        </table>
        <p className="fine">Individual children are not visible at trust level unless a school grants access.</p>
      </div>
    </>
  )
}
