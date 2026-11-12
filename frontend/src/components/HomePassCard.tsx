import { useEffect, useState } from 'react'
import { api, type HomePass } from '../api'
import { contactUs, passState } from '../homePass'

/**
 * The family's Motor Movers at Home pass: when it ends, or Buy or Renew, which opens Stripe's own page. The pass runs
 * once Stripe confirms the payment to the server, usually within seconds of coming back. Hidden while the pass runs
 * unless `always`.
 */
export default function HomePassCard({ always = false }: { always?: boolean }) {
  const [pass, setPass] = useState<HomePass | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api
      .homePass()
      .then(setPass)
      .catch(() => setPass(null))
  }, [])

  if (!pass) return null
  const state = passState(pass)
  if (state.kind === 'no-family' || (state.kind === 'running' && !always)) return null

  async function buy() {
    setBusy(true)
    setError(null)
    try {
      // A full page load: Stripe's page is a different site.
      window.location.assign((await api.homePassCheckout()).url)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Card payments are not available just now')
      setBusy(false)
    }
  }

  return (
    <section className={`card home-pass ${state.kind === 'running' ? '' : 'home-pass-due'}`}>
      <h2>{state.kind === 'running' ? 'Your pass' : state.kind === 'renew' ? 'Your pass has ended' : 'The 12-month pass'}</h2>
      <p>{state.text}</p>
      {state.action &&
        (pass.cardPayments ? (
          <button className="btn btn-primary" disabled={busy} onClick={buy}>
            {state.action}
          </button>
        ) : (
          <p className="muted">Paying by card is not switched on yet. {contactUs(pass)}</p>
        ))}
      {error && <p className="error">{error}</p>}
    </section>
  )
}
