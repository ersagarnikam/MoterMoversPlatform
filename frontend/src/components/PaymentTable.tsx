import type { ReactNode } from 'react'
import type { Payment } from '../api'
import { gbp } from '../money'

const STATUS = { DUE: 'Due', PAID: 'Paid', CANCELLED: 'Cancelled' }

/** A list of payments. showPayer adds the payer column (the team's view); actions adds a last column. */
export default function PaymentTable({
  payments,
  showPayer = false,
  actions,
}: {
  payments: Payment[]
  showPayer?: boolean
  actions?: (p: Payment) => ReactNode
}) {
  if (payments.length === 0) return <p className="muted">No payments recorded yet.</p>
  return (
    <div className="table-wrap">
      <table>
        <thead>
          <tr>
            <th>Invoiced</th>
            {showPayer && <th>Payer</th>}
            <th>For</th>
            <th>Reference</th>
            <th className="num">Amount</th>
            <th>Status</th>
            {actions && <th />}
          </tr>
        </thead>
        <tbody>
          {payments.map((p) => (
            <tr key={p.id} className={p.status === 'CANCELLED' ? 'muted' : undefined}>
              <td className="nowrap">{p.invoicedOn}</td>
              {showPayer && (
                <td>
                  {p.payerName}
                  {p.payerEmail && <span className="muted"> · {p.payerEmail}</span>}
                </td>
              )}
              <td>
                {p.description} <span className="muted">· {p.purposeLabel}</span>
              </td>
              <td>{p.reference ?? '-'}</td>
              <td className="num nowrap">{gbp(p.amountPence)}</td>
              <td className="nowrap">
                <span className={`status status-payment-${p.status.toLowerCase()}`}>{STATUS[p.status]}</span>
                {p.paidOn && <span className="muted"> {p.paidOn}</span>}
                {p.paidOnline && <span className="muted"> · by card</span>}
              </td>
              {actions && <td className="row-actions">{actions(p)}</td>}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
