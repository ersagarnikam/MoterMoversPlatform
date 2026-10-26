import { useEffect, useState, type FormEvent } from 'react'
import { api, type Plan } from '../api'
import { gbp } from '../money'
import { day } from '../dates'
import { useToast } from '../components/Feedback'
import Skeleton from '../components/Skeleton'

const today = () => new Date().toISOString().slice(0, 10)
const pence = (pounds: string) => Math.round(Number(pounds) * 100)

/**
 * The team sets licence plans and their yearly prices. A price change applies from a date, so licences already
 * sold and invoices already sent keep their price; each school's own agreed price is set on Schools & licences.
 */
export default function PlansPage() {
  const [plans, setPlans] = useState<Plan[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const setNotice = useToast()

  useEffect(() => {
    api
      .plans()
      .then(setPlans)
      .catch((e) => setError(e.message))
  }, [])

  /** Every change returns the whole plan, which replaces (or joins) the list. */
  async function run(action: () => Promise<Plan>, done: string) {
    setError(null)
    setNotice(null)
    try {
      const saved = await action()
      setPlans((all) =>
        all?.some((p) => p.code === saved.code) ? all.map((p) => (p.code === saved.code ? saved : p)) : [...(all ?? []), saved],
      )
      setNotice(done)
      return true
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Something went wrong')
      return false
    }
  }

  if (!plans) return error ? <p className="error">{error}</p> : <Skeleton />

  return (
    <>
      <div className="page-head">
        <h1>Plans and prices</h1>
        <span className="muted">Yearly licence prices · a change applies from its date</span>
      </div>
      {error && <p className="error">{error}</p>}

      {plans.map((p) => (
        <PlanCard key={p.code} plan={p} run={run} />
      ))}

      <section className="card">
        <h3>Add a plan</h3>
        <NewPlanForm onAdd={(p) => run(() => api.addPlan(p), `${p.label} added at ${gbp(p.pricePence)} a year`)} />
      </section>
    </>
  )
}

type Run = (action: () => Promise<Plan>, done: string) => Promise<boolean>

function PlanCard({ plan: p, run }: { plan: Plan; run: Run }) {
  const [label, setLabel] = useState(p.label)
  const [price, setPrice] = useState('')
  const [from, setFrom] = useState(today())

  async function changePrice(e: FormEvent) {
    e.preventDefault()
    const when = from === today() ? 'from today' : `from ${day(from)}`
    if (await run(() => api.changePlanPrice(p.code, pence(price), from), `${p.label}: ${gbp(pence(price))} a year ${when}`)) {
      setPrice('')
    }
  }

  return (
    <section className={`card plan-card${p.active ? '' : ' plan-retired'}`}>
      <div className="section-head">
        <h3>
          {p.label} {!p.active && <span className="badge">Retired</span>} {p.freeTrial && <span className="badge">Free trial</span>}
        </h3>
        <span className="price">{gbp(p.currentPricePence)} a year</span>
      </div>
      <p className="muted">
        {p.licences} {p.licences === 1 ? 'licence' : 'licences'} on this plan
        {p.nextPrice && ` · changes to ${gbp(p.nextPrice.pricePence)} from ${day(p.nextPrice.effectiveFrom)}`}
      </p>

      {!p.freeTrial && (
        <form className="inline-form" onSubmit={changePrice}>
          <label>
            New yearly price (£)
            <input type="number" min={0} step={0.01} value={price} onChange={(e) => setPrice(e.target.value)} required />
          </label>
          <label>
            From
            <input type="date" min={today()} value={from} onChange={(e) => setFrom(e.target.value)} required />
          </label>
          <button className="btn btn-primary">Set price</button>
        </form>
      )}

      <form
        className="inline-form"
        onSubmit={(e) => {
          e.preventDefault()
          run(() => api.updatePlan(p.code, { label: label.trim(), active: p.active }), 'Plan renamed')
        }}
      >
        <label>
          Plan name
          <input value={label} onChange={(e) => setLabel(e.target.value)} maxLength={100} required />
        </label>
        <button className="btn" disabled={label.trim() === p.label}>
          Rename
        </button>
        <button
          type="button"
          className="btn"
          onClick={() =>
            run(
              () => api.updatePlan(p.code, { label: p.label, active: !p.active }),
              p.active ? `${p.label} is retired: existing licences keep it` : `${p.label} is on sale again`,
            )
          }
        >
          {p.active ? 'Retire plan' : 'Put back on sale'}
        </button>
      </form>

      <details className="price-history">
        <summary className="fine-link">Price history</summary>
        <table>
          <thead>
            <tr>
              <th>From</th>
              <th className="num">Yearly price</th>
              <th>Set by</th>
            </tr>
          </thead>
          <tbody>
            {p.history.map((h) => (
              <tr key={h.effectiveFrom}>
                <td>{h.effectiveFrom === '2000-01-01' ? 'Launch' : day(h.effectiveFrom)}</td>
                <td className="num">{gbp(h.pricePence)}</td>
                <td>
                  {h.changedBy} <span className="muted">· {day(h.changedAt)}</span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </details>
    </section>
  )
}

function NewPlanForm({ onAdd }: { onAdd: (p: { label: string; freeTrial: boolean; pricePence: number }) => Promise<boolean> }) {
  const [label, setLabel] = useState('')
  const [price, setPrice] = useState('')
  const [freeTrial, setFreeTrial] = useState(false)

  return (
    <form
      className="inline-form"
      onSubmit={async (e) => {
        e.preventDefault()
        if (await onAdd({ label: label.trim(), freeTrial, pricePence: freeTrial ? 0 : pence(price) })) {
          setLabel('')
          setPrice('')
          setFreeTrial(false)
        }
      }}
    >
      <label>
        Plan name
        <input value={label} onChange={(e) => setLabel(e.target.value)} maxLength={100} required />
      </label>
      <label>
        Yearly price (£)
        <input
          type="number"
          min={0}
          step={0.01}
          value={freeTrial ? '0' : price}
          disabled={freeTrial}
          onChange={(e) => setPrice(e.target.value)}
          required
        />
      </label>
      <label className="toggle">
        <input type="checkbox" checked={freeTrial} onChange={(e) => setFreeTrial(e.target.checked)} /> Free trial
      </label>
      <button className="btn btn-primary">Add plan</button>
    </form>
  )
}
