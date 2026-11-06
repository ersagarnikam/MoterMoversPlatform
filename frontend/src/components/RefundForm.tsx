import { useState, type FormEvent } from 'react'
import { api, newClientId, type Payment } from '../api'
import { currencySymbol, money } from '../money'
import { useConfirm } from './Feedback'

/** The team refunds all or part of a paid payment. Whether a refund applies is the team's call, under its policy. */
export default function RefundForm({
  payment,
  onDone,
  onCancel,
}: {
  payment: Payment
  onDone: (message: string) => void
  onCancel: () => void
}) {
  const confirm = useConfirm()
  const left = payment.amountPence - payment.refundedPence
  const [amount, setAmount] = useState((left / 100).toFixed(2))
  const [reason, setReason] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  // One id for this form: pressing the button twice cannot refund twice.
  const [clientId] = useState(newClientId)
  const byCard = payment.paidOnline

  async function submit(e: FormEvent) {
    e.preventDefault()
    const amountPence = Math.round(Number(amount) * 100)
    const ok = await confirm({
      title: `Refund ${money(amountPence)} to ${payment.payerName}?`,
      body: byCard
        ? 'The money goes back to their card through Stripe, and they are emailed a credit note.'
        : 'This records the refund and emails a credit note. The money must be returned by hand.',
      confirmLabel: 'Refund',
      cancelLabel: 'Keep it',
      danger: true,
    })
    if (!ok) return
    setBusy(true)
    setError(null)
    try {
      const updated = await api.refundPayment(payment.id, { amountPence, reason, clientId })
      const latest = updated.refunds[updated.refunds.length - 1]
      onDone(
        latest?.byHand
          ? `${money(amountPence)} recorded as refunded. Please return the money by hand.`
          : `${money(amountPence)} refunded through Stripe`,
      )
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not refund')
      setBusy(false)
    }
  }

  return (
    <form className="inline-form card" onSubmit={submit} aria-label="Refund">
      <h3>
        Refund: {payment.description} ({payment.payerName})
      </h3>
      <p className="muted">
        {money(left)} left to refund of {money(payment.amountPence)}.
      </p>
      <label>
        Amount ({currencySymbol()})
        <input
          type="number"
          min={0.01}
          step={0.01}
          max={left / 100}
          value={amount}
          onChange={(e) => setAmount(e.target.value)}
          required
        />
      </label>
      <label>
        Reason (shown on the credit note)
        <input value={reason} onChange={(e) => setReason(e.target.value)} maxLength={500} required />
      </label>
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
      <div className="actions">
        <button className="btn btn-primary" disabled={busy}>
          {busy ? 'Refunding…' : 'Refund'}
        </button>
        <button type="button" className="btn" onClick={onCancel}>
          Cancel
        </button>
      </div>
    </form>
  )
}
