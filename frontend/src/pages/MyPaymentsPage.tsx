import { useEffect, useState } from 'react'
import { api, type Payment, type Totals } from '../api'
import { CardPaymentReturn, PayByCard, useCardPayments } from '../components/PayByCard'
import PaymentTable from '../components/PaymentTable'
import { gbp } from '../money'
import { CONTACT_EMAIL, CONTACT_PHONE } from '../site'
import { day } from '../dates'
import Skeleton from '../components/Skeleton'

/** A parent's or practitioner's own payments: what is due now, and everything paid so far. */
export default function MyPaymentsPage() {
  const [data, setData] = useState<(Totals & { payments: Payment[] }) | null>(null)
  const [error, setError] = useState<string | null>(null)
  const cards = useCardPayments()

  useEffect(() => {
    api
      .myPayments()
      .then(setData)
      .catch((e) => setError(e.message))
  }, [])

  if (!data) return error ? <p className="error">{error}</p> : <Skeleton />
  const due = data.payments.filter((p) => p.status === 'DUE')

  return (
    <>
      <h1>Payments</h1>
      <CardPaymentReturn />
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
      <div className="kpis">
        <div className="card kpi">
          <span className="muted">Paid in total</span>
          <strong>{gbp(data.paidPence)}</strong>
        </div>
        <div className="card kpi">
          <span className="muted">Due now</span>
          <strong>{gbp(data.outstandingPence)}</strong>
        </div>
      </div>

      {due.length > 0 && (
        <section className="card">
          <h3>To pay</h3>
          <ul className="due-list">
            {due.map((p) => (
              <li key={p.id}>
                <div>
                  <strong>{p.description}</strong>
                  <p className="muted">
                    {p.purposeLabel} · invoiced {day(p.invoicedOn)}
                    {p.reference && <> · reference {p.reference}</>}
                  </p>
                </div>
                <span className="actions">
                  <strong className="nowrap">{gbp(p.amountPence)}</strong>
                  {cards && <PayByCard payment={p} onError={setError} />}
                </span>
              </li>
            ))}
          </ul>
          <p className="fine">
            {cards && 'Pay by card on our payment provider’s secure page, or '}
            {cards ? 'pay' : 'Pay'} as set out on your invoice, quoting the reference so the Motor Movers team can match
            your payment. It shows here as paid once they have received it. Questions about a payment: email{' '}
            <a href={`mailto:${CONTACT_EMAIL}`}>{CONTACT_EMAIL}</a> or call {CONTACT_PHONE}.
          </p>
        </section>
      )}

      <section className="card">
        <h3>Payment history</h3>
        {data.payments.length === 0 ? (
          <p className="muted">No payments yet. When you book a course, its invoice and payment show here.</p>
        ) : (
          <PaymentTable payments={data.payments} />
        )}
      </section>
    </>
  )
}
