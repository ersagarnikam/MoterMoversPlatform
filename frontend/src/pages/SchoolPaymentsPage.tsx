import { useEffect, useState } from 'react'
import { api, type Payment, type Totals } from '../api'
import { useAuth } from '../auth'
import PaymentTable from '../components/PaymentTable'
import { gbp } from '../money'

/** A head's view of what their school has been invoiced and has paid Motor Movers. */
export default function SchoolPaymentsPage() {
  const { user } = useAuth()
  const [data, setData] = useState<(Totals & { schoolName: string; payments: Payment[] }) | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (user?.schoolId) api.schoolPayments(user.schoolId).then(setData).catch((e) => setError(e.message))
  }, [user?.schoolId])

  if (!user?.schoolId) return <p className="muted">This account is not linked to a school.</p>
  if (error) return <p className="error">{error}</p>
  if (!data) return <p className="muted">Loading…</p>

  return (
    <>
      <div className="page-head">
        <h1>{data.schoolName} · Payments</h1>
        <span className="muted">Licences and other invoices to the school</span>
      </div>
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
