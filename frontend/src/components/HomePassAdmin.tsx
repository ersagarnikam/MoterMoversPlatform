import { HOME_PROGRAMME } from '../brand'
import { useEffect, useState, type FormEvent } from 'react'
import { api, type AdminFamilyPass, type HomePassPrices } from '../api'
import { day } from '../dates'
import { currencySymbol, money } from '../money'
import { useConfirm, useToast } from './Feedback'

/** Today in the browser, as YYYY-MM-DD. */
function isoDate(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

/**
 * The Programme at Home's monthly price on Plans and prices (go-live task 68): dated like a licence plan's, from
 * today or later, and a price in force is never rewritten. With no price, monthly is not on sale. A change applies to
 * new subscriptions only.
 */
export function HomeMonthlyPriceCard() {
  const [data, setData] = useState<HomePassPrices | null>(null)
  const [pounds, setPounds] = useState('')
  const [from, setFrom] = useState(isoDate(new Date()))
  const [error, setError] = useState<string | null>(null)
  const toast = useToast()

  useEffect(() => {
    api
      .homeMonthlyPrices()
      .then(setData)
      .catch((e) => setError(e.message))
  }, [])

  async function save(e: FormEvent) {
    e.preventDefault()
    setError(null)
    try {
      const pence = Math.round(Number(pounds) * 100)
      setData(await api.changeHomeMonthlyPrice(pence, from))
      toast(`New subscriptions cost ${money(pence)} a month from ${day(from)}`)
      setPounds('')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not save the price')
    }
  }

  return (
    <section className="card">
      <h3>{HOME_PROGRAMME}: monthly price</h3>
      <p className="muted">
        A family subscribes by card, and pays each month until it cancels.{' '}
        {data?.currentPence != null
          ? `Today: ${money(data.currentPence)} a month.`
          : 'Not on sale until a price is set.'}{' '}
        A change applies to new subscriptions only: a family that already subscribes keeps its price.
      </p>
      {data && data.prices.length > 0 && (
        <ul className="due-list">
          {data.prices.map((p) => (
            <li key={p.effectiveFrom}>
              {money(p.pricePence)} a month from {day(p.effectiveFrom)}{' '}
              <span className="muted">· set by {p.changedBy}</span>
            </li>
          ))}
        </ul>
      )}
      <form className="inline-form" onSubmit={save}>
        <label>
          Price a month ({currencySymbol()}, tax included)
          <input type="number" min="0.01" step="0.01" required value={pounds} onChange={(e) => setPounds(e.target.value)} />
        </label>
        <label>
          From
          <input type="date" required min={isoDate(new Date())} value={from} onChange={(e) => setFrom(e.target.value)} />
        </label>
        <div className="actions">
          <button className="btn btn-primary">Set the monthly price</button>
        </div>
      </form>
      {error && <p className="error">{error}</p>}
    </section>
  )
}

const SUBSCRIPTION_STATE: Record<string, string> = {
  ACTIVE: 'running',
  CANCELLING: 'cancelled, ending with the paid month',
  PAST_DUE: 'a payment has failed',
  ENDED: 'ended',
}

/**
 * For support: a family's cover and subscription, found by any parent's or carer's email; extending the cover by
 * hand, and cancelling the subscription for the family (both recorded).
 */
export function FamilyPassCard() {
  const [email, setEmail] = useState('')
  const [family, setFamily] = useState<AdminFamilyPass | null>(null)
  const [endsOn, setEndsOn] = useState('')
  const [note, setNote] = useState('')
  const [why, setWhy] = useState('')
  const [error, setError] = useState<string | null>(null)
  const toast = useToast()
  const confirm = useConfirm()

  async function find(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setFamily(null)
    try {
      setFamily(await api.familyPass(email.trim()))
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not find that family')
    }
  }

  async function extend(e: FormEvent) {
    e.preventDefault()
    setError(null)
    try {
      setFamily(await api.extendFamilyPass(email.trim(), endsOn, note))
      toast(`The family is now covered to ${day(endsOn)}`)
      setNote('')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not extend the cover')
    }
  }

  async function cancel(e: FormEvent) {
    e.preventDefault()
    const sub = family?.subscription
    if (!sub) return
    const ok = await confirm({
      title: 'Cancel this family’s subscription?',
      body:
        sub.state === 'PAST_DUE'
          ? 'Its payment has failed, so it ends now. No more payments are tried.'
          : `No more payments are taken. The family keeps the month it has paid for${sub.paidTo ? `, to ${day(sub.paidTo)}` : ''}, and is emailed.`,
      confirmLabel: 'Cancel the subscription',
      cancelLabel: 'Keep it',
      danger: true,
    })
    if (!ok) return
    setError(null)
    try {
      setFamily(await api.cancelFamilySubscription(email.trim(), why))
      toast('The subscription is cancelled')
      setWhy('')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not cancel the subscription')
    }
  }

  const sub = family?.subscription ?? null
  return (
    <section className="card">
      <h3>A family’s {HOME_PROGRAMME} subscription</h3>
      <form className="inline-form" onSubmit={find}>
        <label>
          Parent or carer’s email
          <input type="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
        </label>
        <div className="actions">
          <button className="btn">Find the family</button>
        </div>
      </form>
      {family && (
        <>
          <p>
            {family.members.map((m) => `${m.name} (${m.role.toLowerCase()})`).join(', ')}.{' '}
            {family.endsOn
              ? family.running
                ? `Covered to ${day(family.endsOn)}.`
                : `Cover ended on ${day(family.endsOn)}.`
              : 'No cover yet.'}
          </p>
          <p>
            {sub
              ? `Subscription: ${SUBSCRIPTION_STATE[sub.state] ?? sub.state}. ${money(sub.pricePence)} a month, paid by ${sub.payerEmail ?? 'someone no longer here'}${sub.paidTo ? `, paid to ${day(sub.paidTo)}` : ''}.`
              : 'No subscription.'}
            {sub?.cancelledBy ? ` Cancelled by ${sub.cancelledBy}${sub.cancelNote ? `: ${sub.cancelNote}` : ''}.` : ''}
          </p>
          {family.history.length > 0 && (
            <ul className="due-list">
              {family.history.map((h, i) => (
                <li key={i}>
                  {day(h.startsOn)} to {day(h.endsOn)} · {h.how}
                  <span className="muted">
                    {' '}
                    · {h.addedBy}
                    {h.note ? ` · ${h.note}` : ''}
                  </span>
                </li>
              ))}
            </ul>
          )}
          {sub && (sub.state === 'ACTIVE' || sub.state === 'PAST_DUE') && (
            <form className="inline-form" onSubmit={cancel} aria-label="Cancel the subscription">
              <label>
                Why it is being cancelled
                <input required maxLength={300} value={why} onChange={(e) => setWhy(e.target.value)} />
              </label>
              <div className="actions">
                <button className="btn btn-danger">Cancel the subscription</button>
              </div>
            </form>
          )}
          <form className="inline-form" onSubmit={extend} aria-label="Extend the cover">
            <label>
              Extend to
              <input type="date" required value={endsOn} onChange={(e) => setEndsOn(e.target.value)} />
            </label>
            <label>
              Why (optional)
              <input maxLength={300} value={note} onChange={(e) => setNote(e.target.value)} />
            </label>
            <div className="actions">
              <button className="btn btn-primary">Extend the cover</button>
            </div>
          </form>
        </>
      )}
      {error && <p className="error">{error}</p>}
    </section>
  )
}
