import { useCallback, useEffect, useState, type FormEvent } from 'react'
import {
  api,
  type NewPayment,
  type Payment,
  type PaymentPurpose,
  type PaymentSummary,
  type SchoolRow,
  type Totals,
} from '../api'
import MonthBars from '../components/MonthBars'
import PaymentTable from '../components/PaymentTable'
import { gbp } from '../money'

const PURPOSES: [PaymentPurpose, string][] = [
  ['LICENCE', 'Licence'],
  ['COURSE', 'Course'],
  ['OTHER', 'Other'],
]
const PAYER_TYPE = { SCHOOL: 'School', PARENT: 'Parent or carer', INDIVIDUAL: 'Practitioner or staff' }
const today = () => new Date().toISOString().slice(0, 10)
const count = (n: number, thing: string) => `${n} ${thing}${n === 1 ? '' : 's'}`

/** The team records invoices and payments, and sees spend by school, trust, person and month. */
export default function PaymentsPage() {
  const [summary, setSummary] = useState<PaymentSummary | null>(null)
  const [payments, setPayments] = useState<Payment[]>([])
  const [schools, setSchools] = useState<SchoolRow[]>([])
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  const load = useCallback(() => {
    Promise.all([api.paymentSummary(), api.payments(), api.schools()])
      .then(([s, p, sc]) => {
        setSummary(s)
        setPayments(p)
        setSchools(sc)
      })
      .catch((e) => setError(e.message))
  }, [])

  useEffect(load, [load])

  async function run(action: () => Promise<unknown>, done: string) {
    setError(null)
    setNotice(null)
    try {
      await action()
      setNotice(done)
      load()
      return true
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Something went wrong')
      return false
    }
  }

  if (!summary) return error ? <p className="error">{error}</p> : <p className="muted">Loading…</p>

  return (
    <>
      <div className="page-head">
        <h1>Payments</h1>
        <span className="muted">Recorded by the team · online payment is not connected yet</span>
      </div>
      {error && <p className="error">{error}</p>}
      {notice && <p className="banner-teal">{notice}</p>}

      <div className="kpis">
        <div className="card kpi">
          <span className="muted">Received, all time</span>
          <strong>{gbp(summary.paidPence)}</strong>
        </div>
        <div className="card kpi">
          <span className="muted">Received, last 12 months</span>
          <strong>{gbp(summary.paidLast12MonthsPence)}</strong>
        </div>
        <div className="card kpi">
          <span className="muted">Outstanding (invoiced, not paid)</span>
          <strong>{gbp(summary.outstandingPence)}</strong>
        </div>
      </div>

      <section className="card">
        <h3>Received each month</h3>
        <MonthBars months={summary.byMonth} />
      </section>

      <div className="split">
        <TotalsTable
          title="By payer"
          rows={summary.byPayerType.map((g) => row(g.key, g.label, count(g.payments, 'payment'), g))}
        />
        <TotalsTable
          title="By what was paid for"
          rows={summary.byPurpose.map((g) => row(g.key, g.label, count(g.payments, 'payment'), g))}
        />
      </div>
      <TotalsTable
        title="Trusts"
        rows={summary.byTrust.map((t) => row(t.trustId, t.trustName, count(t.schools, 'school'), t))}
      />
      <TotalsTable
        title="Schools"
        rows={summary.bySchool.map((s) => row(s.schoolId, s.schoolName, s.trustName ?? 'No trust', s))}
      />
      <TotalsTable
        title="Parents, practitioners and staff"
        empty="No payments from individuals yet."
        rows={summary.byPerson.map((p) => row(p.userId, p.name, `${PAYER_TYPE[p.payerType]} · ${p.email}`, p))}
      />

      <section className="card">
        <h3>Record a payment</h3>
        <p className="muted">
          Record an invoice when it is sent, then mark it paid when the money arrives. If it is already paid, give the
          date.
        </p>
        <NewPaymentForm
          schools={schools}
          onSave={(p) => run(() => api.recordPayment(p), `${gbp(p.amountPence)} from ${p.payerEmail ?? 'the school'} recorded`)}
        />
      </section>

      <section className="card">
        <h3>All payments</h3>
        <PaymentTable
          payments={payments}
          showPayer
          actions={(p) =>
            p.status === 'DUE' && (
              <>
                <button className="btn" onClick={() => run(() => api.markPaid(p.id, today()), `${p.description} marked paid`)}>
                  Mark paid today
                </button>
                <button
                  className="btn"
                  onClick={() => {
                    if (confirm(`Cancel the ${gbp(p.amountPence)} invoice to ${p.payerName}?`)) {
                      run(() => api.cancelPayment(p.id), `${p.description} cancelled`)
                    }
                  }}
                >
                  Cancel
                </button>
              </>
            )
          }
        />
      </section>
    </>
  )
}

interface TotalsRow extends Totals {
  key: string | number
  name: string
  detail: string
}

const row = (key: string | number, name: string, detail: string, t: Totals): TotalsRow => ({
  key,
  name,
  detail,
  paidPence: t.paidPence,
  outstandingPence: t.outstandingPence,
})

function TotalsTable({ title, rows, empty }: { title: string; rows: TotalsRow[]; empty?: string }) {
  return (
    <section className="card table-wrap">
      <h3>{title}</h3>
      {rows.length === 0 ? (
        <p className="muted">{empty}</p>
      ) : (
        <table>
          <thead>
            <tr>
              <th>Name</th>
              <th className="num">Received</th>
              <th className="num">Outstanding</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.key}>
                <td>
                  {r.name} <span className="muted">· {r.detail}</span>
                </td>
                <td className="num">{gbp(r.paidPence)}</td>
                <td className="num">{r.outstandingPence ? gbp(r.outstandingPence) : '-'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  )
}

function NewPaymentForm({ schools, onSave }: { schools: SchoolRow[]; onSave: (p: NewPayment) => Promise<boolean> }) {
  const [payer, setPayer] = useState<'school' | 'person'>('school')
  const [schoolId, setSchoolId] = useState<number | ''>('')
  const [email, setEmail] = useState('')
  const [purpose, setPurpose] = useState<PaymentPurpose>('LICENCE')
  const [description, setDescription] = useState('')
  const [amount, setAmount] = useState('')
  const [invoicedOn, setInvoicedOn] = useState(today())
  const [reference, setReference] = useState('')
  const [alreadyPaid, setAlreadyPaid] = useState(false)
  const [paidOn, setPaidOn] = useState(today())

  async function submit(e: FormEvent) {
    e.preventDefault()
    const saved = await onSave({
      schoolId: payer === 'school' ? Number(schoolId) : null,
      payerEmail: payer === 'person' ? email.trim() : null,
      purpose,
      description: description.trim(),
      amountPence: Math.round(Number(amount) * 100),
      invoicedOn,
      reference: reference.trim() || null,
      paidOn: alreadyPaid ? paidOn : null,
    })
    if (saved) {
      setDescription('')
      setAmount('')
      setReference('')
      setAlreadyPaid(false)
    }
  }

  return (
    <form className="inline-form" onSubmit={submit}>
      <fieldset className="stage-picker form-wide">
        <legend>Paid by</legend>
        <label className="toggle">
          <input type="radio" name="payer" checked={payer === 'school'} onChange={() => setPayer('school')} /> A school
        </label>
        <label className="toggle">
          <input type="radio" name="payer" checked={payer === 'person'} onChange={() => setPayer('person')} /> A parent,
          practitioner or member of staff
        </label>
      </fieldset>
      {payer === 'school' ? (
        <label>
          School
          <select value={schoolId} onChange={(e) => setSchoolId(e.target.value ? Number(e.target.value) : '')} required>
            <option value="">Choose a school</option>
            {schools.map((s) => (
              <option key={s.id} value={s.id}>
                {s.name}
              </option>
            ))}
          </select>
        </label>
      ) : (
        <label>
          Their account email
          <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} maxLength={200} required />
        </label>
      )}
      <label>
        For
        <select value={purpose} onChange={(e) => setPurpose(e.target.value as PaymentPurpose)}>
          {PURPOSES.map(([key, label]) => (
            <option key={key} value={key}>
              {label}
            </option>
          ))}
        </select>
      </label>
      <label>
        Description
        <input
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          maxLength={200}
          placeholder="Primary core licence, 2026-27"
          required
        />
      </label>
      <label>
        Amount (£)
        <input type="number" min={0.01} step={0.01} value={amount} onChange={(e) => setAmount(e.target.value)} required />
      </label>
      <label>
        Invoice date
        <input type="date" value={invoicedOn} onChange={(e) => setInvoicedOn(e.target.value)} required />
      </label>
      <label>
        Invoice number (optional)
        <input value={reference} onChange={(e) => setReference(e.target.value)} maxLength={100} />
      </label>
      <label className="toggle">
        <input type="checkbox" checked={alreadyPaid} onChange={(e) => setAlreadyPaid(e.target.checked)} /> Already paid
      </label>
      {alreadyPaid && (
        <label>
          Paid on
          <input type="date" value={paidOn} max={today()} onChange={(e) => setPaidOn(e.target.value)} required />
        </label>
      )}
      <div className="actions form-wide">
        <button className="btn btn-primary">Record payment</button>
      </div>
    </form>
  )
}
