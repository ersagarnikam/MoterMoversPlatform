import { useEffect, useState, type FormEvent } from 'react'
import { api, type AdminFamilyPass, type HomePassPrices } from '../api'
import { day } from '../dates'
import { currencySymbol, money } from '../money'
import { useToast } from './Feedback'

/** Today in the browser, as YYYY-MM-DD. */
function isoDate(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

/**
 * The Motor Movers at Home pass price on Plans and prices: dated like a licence plan's, from today or later, and a
 * price in force is never rewritten. With no price the pass is not on sale.
 */
export function HomePassPriceCard() {
  const [data, setData] = useState<HomePassPrices | null>(null)
  const [pounds, setPounds] = useState('')
  const [from, setFrom] = useState(isoDate(new Date()))
  const [error, setError] = useState<string | null>(null)
  const toast = useToast()

  useEffect(() => {
    api
      .homePassPrices()
      .then(setData)
      .catch((e) => setError(e.message))
  }, [])

  async function save(e: FormEvent) {
    e.preventDefault()
    setError(null)
    try {
      const pence = Math.round(Number(pounds) * 100)
      setData(await api.changeHomePassPrice(pence, from))
      toast(`The pass costs ${money(pence)} from ${day(from)}`)
      setPounds('')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not save the price')
    }
  }

  return (
    <section className="card">
      <h3>Motor Movers at Home pass</h3>
      <p className="muted">
        Twelve months for a whole family, bought by card.{' '}
        {data?.currentPence != null ? `Today: ${money(data.currentPence)}.` : 'Not on sale until a price is set.'}
      </p>
      {data && data.prices.length > 0 && (
        <ul className="due-list">
          {data.prices.map((p) => (
            <li key={p.effectiveFrom}>
              {money(p.pricePence)} from {day(p.effectiveFrom)} <span className="muted">· set by {p.changedBy}</span>
            </li>
          ))}
        </ul>
      )}
      <form className="inline-form" onSubmit={save}>
        <label>
          Price ({currencySymbol()}, tax included)
          <input type="number" min="0.01" step="0.01" required value={pounds} onChange={(e) => setPounds(e.target.value)} />
        </label>
        <label>
          From
          <input type="date" required min={isoDate(new Date())} value={from} onChange={(e) => setFrom(e.target.value)} />
        </label>
        <div className="actions">
          <button className="btn btn-primary">Set the pass price</button>
        </div>
      </form>
      {error && <p className="error">{error}</p>}
    </section>
  )
}

/** For support: a family's pass, found by any parent's or carer's email, and extending it by hand (recorded). */
export function FamilyPassCard() {
  const [email, setEmail] = useState('')
  const [family, setFamily] = useState<AdminFamilyPass | null>(null)
  const [endsOn, setEndsOn] = useState('')
  const [note, setNote] = useState('')
  const [error, setError] = useState<string | null>(null)
  const toast = useToast()

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
      toast(`The pass now runs to ${day(endsOn)}`)
      setNote('')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not extend the pass')
    }
  }

  return (
    <section className="card">
      <h3>A family’s Motor Movers at Home pass</h3>
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
                ? `The pass runs to ${day(family.endsOn)}.`
                : `The pass ended on ${day(family.endsOn)}.`
              : 'No pass yet.'}
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
          <form className="inline-form" onSubmit={extend}>
            <label>
              Extend to
              <input type="date" required value={endsOn} onChange={(e) => setEndsOn(e.target.value)} />
            </label>
            <label>
              Why (optional)
              <input maxLength={300} value={note} onChange={(e) => setNote(e.target.value)} />
            </label>
            <div className="actions">
              <button className="btn btn-primary">Extend the pass</button>
            </div>
          </form>
        </>
      )}
      {error && <p className="error">{error}</p>}
    </section>
  )
}
