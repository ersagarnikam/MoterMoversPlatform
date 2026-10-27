import { useEffect, useState } from 'react'
import { api, type TrustDashboard } from '../api'
import { useAuth } from '../auth'
import { gbp } from '../money'
import { pct } from '../stages'
import { day } from '../dates'
import Skeleton from '../components/Skeleton'

const STATUS_LABEL = { ACTIVE: 'Active', RENEWAL_DUE: 'Renewal due', FREE_TRIAL: 'Free trial', EXPIRED: 'Expired' }

/** Trust or group leads see each school's licence and summary results only. */
export default function TrustDashboardPage() {
  const { user } = useAuth()
  const [data, setData] = useState<TrustDashboard | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (user?.trustId) api.trustDashboard(user.trustId).then(setData).catch((e) => setError(e.message))
  }, [user?.trustId])

  if (!user?.trustId) return <p className="muted">This account is not linked to a trust.</p>
  if (error) return <p className="error">{error}</p>
  if (!data) return <Skeleton />

  return (
    <>
      <div className="page-head">
        <h1>
          {data.trustName} · {data.schools.length} schools
        </h1>
        <span className="muted">Trust or group lead · summaries only</span>
      </div>
      <div className="card table-wrap">
        <table>
          <thead>
            <tr>
              <th>School</th>
              <th>Licence</th>
              <th>Renewal</th>
              <th>Stages</th>
              <th className="num">Assessed (0 to 3 check)</th>
              <th className="num">Developmental this year</th>
              <th className="num">May benefit from extra support</th>
              <th className="num">Participation</th>
            </tr>
          </thead>
          <tbody>
            {data.schools.map((s) => (
              <tr key={s.schoolId}>
                <td>{s.schoolName}</td>
                <td className={s.licenceStatus === 'RENEWAL_DUE' ? 'strong' : ''}>{STATUS_LABEL[s.licenceStatus]}</td>
                <td>{s.renewalDate ? day(s.renewalDate) : '-'}</td>
                <td>{s.stages}</td>
                <td className="num">{pct(s.screenedRate)}</td>
                <td className="num">{pct(s.developmentalRate)}</td>
                <td className="num">{s.extraSupport}</td>
                <td className="num">{pct(s.participation)}</td>
              </tr>
            ))}
          </tbody>
        </table>
        <p className="fine">Individual children are not visible at trust or group level unless a school grants access.</p>
      </div>
      <TrustSpending trustId={user.trustId} />
    </>
  )
}

/** What the trust or group's schools have paid and owe Motor Movers, school by school. */
function TrustSpending({ trustId }: { trustId: number }) {
  const [data, setData] = useState<Awaited<ReturnType<typeof api.trustPayments>> | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api.trustPayments(trustId).then(setData).catch((e) => setError(e.message))
  }, [trustId])

  if (error) return <p className="error">{error}</p>
  if (!data) return null
  return (
    <section className="card table-wrap">
      <h3>Spending</h3>
      <table>
        <thead>
          <tr>
            <th>School</th>
            <th className="num">Paid</th>
            <th className="num">Outstanding</th>
          </tr>
        </thead>
        <tbody>
          {data.schools.map((s) => (
            <tr key={s.schoolId}>
              <td>{s.schoolName}</td>
              <td className="num">{gbp(s.paidPence)}</td>
              <td className="num">{s.outstandingPence ? gbp(s.outstandingPence) : '-'}</td>
            </tr>
          ))}
        </tbody>
        <tfoot>
          <tr>
            <th>All schools</th>
            <th className="num">{gbp(data.paidPence)}</th>
            <th className="num">{data.outstandingPence ? gbp(data.outstandingPence) : '-'}</th>
          </tr>
        </tfoot>
      </table>
      <p className="fine">Licences and other invoices paid by each school.</p>
    </section>
  )
}
