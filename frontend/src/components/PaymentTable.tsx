import type { ReactNode } from 'react'
import { api, type Payment } from '../api'
import { money, taxLine } from '../money'
import { day } from '../dates'

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
              <td className="nowrap">{day(p.invoicedOn)}</td>
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
              <td className="num nowrap">
                {money(p.amountPence)}
                {taxLine(p.taxPence) && <div className="muted">{taxLine(p.taxPence)}</div>}
              </td>
              <td className="nowrap">
                <span className={`status status-payment-${p.status.toLowerCase()}`}>{STATUS[p.status]}</span>
                {p.paidOn && <span className="muted"> {day(p.paidOn)}</span>}
                {p.paidOnline && <span className="muted"> · by card</span>}
                {p.refundLabel && <div><strong>{p.refundLabel}</strong></div>}
                {p.refunds.map((r) => (
                  <div key={r.id} className="muted">
                    {r.failed ? 'Refund failed' : 'Refunded'} {money(r.amountPence)} {day(r.refundedOn)}
                    {r.byHand && ' · by hand'}
                    {taxLine(r.taxPence) && ` · ${taxLine(r.taxPence)}`}
                    {!r.failed && (
                      <>
                        {' · '}
                        <button className="btn-link" onClick={() => void api.creditNotePdf(r)}>
                          {r.creditNote}
                        </button>
                      </>
                    )}
                  </div>
                ))}
              </td>
              {actions && <td className="row-actions">{actions(p)}</td>}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
