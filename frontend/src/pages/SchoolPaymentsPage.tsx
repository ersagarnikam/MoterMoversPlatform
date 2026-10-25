import { useCallback, useEffect, useState } from 'react'
import { api, type CourseRequest, type Payment, type Totals } from '../api'
import { useAuth } from '../auth'
import PaymentTable from '../components/PaymentTable'
import { gbp } from '../money'

/**
 * A head's view of what their school has been invoiced and has paid Motor Movers, with the course places staff
 * have asked for: approving one books the place and adds its invoice here.
 */
export default function SchoolPaymentsPage() {
  const { user } = useAuth()
  const [data, setData] = useState<(Totals & { schoolName: string; payments: Payment[] }) | null>(null)
  const [requests, setRequests] = useState<CourseRequest[]>([])
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  const load = useCallback(() => {
    if (!user?.schoolId) return
    Promise.all([api.schoolPayments(user.schoolId), api.schoolRequests(user.schoolId)])
      .then(([payments, asked]) => {
        setData(payments)
        setRequests(asked)
      })
      .catch((e) => setError(e.message))
  }, [user?.schoolId])

  useEffect(load, [load])

  async function decide(r: CourseRequest, approve: boolean) {
    setError(null)
    setNotice(null)
    try {
      await (approve ? api.approveRequest(r.id) : api.declineRequest(r.id))
      setNotice(
        approve
          ? `${r.learnerName}'s place on ${r.courseTitle} is booked, and its invoice added below.`
          : `${r.learnerName}'s request is declined.`,
      )
      load()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Something went wrong')
    }
  }

  if (!user?.schoolId) return <p className="muted">This account is not linked to a school.</p>
  if (!data) return error ? <p className="error">{error}</p> : <p className="muted">Loading…</p>
  const waiting = requests.filter((r) => r.status === 'REQUESTED')
  const decided = requests.filter((r) => r.status === 'APPROVED' || r.status === 'DECLINED').slice(0, 10)

  return (
    <>
      <div className="page-head">
        <h1>{data.schoolName} · Payments</h1>
        <span className="muted">Licences and other invoices to the school</span>
      </div>
      {error && <p className="error">{error}</p>}
      {notice && <p className="banner-teal">{notice}</p>}
      <div className="kpis">
        <div className="card kpi">
          <span className="muted">Paid</span>
          <strong>{gbp(data.paidPence)}</strong>
        </div>
        <div className="card kpi">
          <span className="muted">Outstanding</span>
          <strong>{gbp(data.outstandingPence)}</strong>
        </div>
      </div>
      <section className="card">
        <h3>Course places to approve</h3>
        {waiting.length === 0 ? (
          <p className="muted">No requests waiting. Staff ask for course places from their Training page.</p>
        ) : (
          <div className="cohorts">
            {waiting.map((r) => (
              <div key={r.id} className="cohort">
                <div>
                  <strong>
                    {r.learnerName} · {r.courseTitle}
                  </strong>
                  <p className="muted">
                    {r.cohortName} · starts {r.startsOn} · {r.timeSlot} · {gbp(r.pricePence)}
                  </p>
                </div>
                <div className="actions">
                  <button className="btn" onClick={() => decide(r, false)}>
                    Decline
                  </button>
                  <button className="btn btn-primary" onClick={() => decide(r, true)}>
                    Approve
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}
        {decided.length > 0 && (
          <details className="add-item">
            <summary>Recent decisions</summary>
            <ul>
              {decided.map((r) => (
                <li key={r.id}>
                  {r.learnerName} · {r.courseTitle} ({r.cohortName}): {r.status === 'APPROVED' ? 'approved' : 'declined'}{' '}
                  by {r.decidedBy}
                </li>
              ))}
            </ul>
          </details>
        )}
      </section>
      <section className="card">
        <h3>Invoices and payments</h3>
        <PaymentTable payments={data.payments} />
        <p className="fine">
          Staff who pay for their own courses see those payments on their Training page. Questions about an invoice go
          to the Motor Movers team.
        </p>
      </section>
    </>
  )
}
