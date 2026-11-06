import { useEffect, useState } from 'react'
import { api, type PublicPlan } from '../api'
import { money } from '../money'

/**
 * The licence plans and what each costs a year from today, as the team sets them on Plans & prices. Choosing one
 * adds it to the enquiry; the team still confirms the right plan with the school.
 */
export default function PriceChart({ chosen, onChoose }: { chosen: string; onChoose: (label: string) => void }) {
  const [plans, setPlans] = useState<PublicPlan[] | null>(null)
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    api.plansForSale().then(setPlans, () => setFailed(true))
  }, [])

  // Without the chart, the form still asks for the price guide.
  if (failed || plans?.length === 0) return null
  if (!plans) return <p className="muted">Loading prices…</p>

  return (
    <section className="price-chart" aria-labelledby="price-chart-title">
      <h3 id="price-chart-title">Plans and prices</h3>
      <p className="muted">A yearly licence for your setting. Choose the one you are looking at, if you know.</p>
      <div className="price-plans" role="group" aria-label="Plans">
        {plans.map((p) => (
          <button
            key={p.code}
            type="button"
            className={`card price-plan ${chosen === p.label ? 'price-plan-on' : ''}`}
            aria-pressed={chosen === p.label}
            onClick={() => onChoose(chosen === p.label ? '' : p.label)}
          >
            <span className="price-plan-name">{p.label}</span>
            <span className="price-plan-price">{p.freeTrial ? 'Free' : money(p.annualPricePence)}</span>
            <span className="muted">{p.freeTrial ? 'to try it out' : 'a year'}</span>
          </button>
        ))}
      </div>
      <p className="fine">Prices for UK settings, as charged. We confirm the right plan and stages with you.</p>
    </section>
  )
}
