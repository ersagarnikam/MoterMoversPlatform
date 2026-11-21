import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { api, type HomePass } from '../api'
import { contactUs, needsAttention, passState } from '../homePass'

/**
 * The family's cover on the Programme at Home: Subscribe, which opens Stripe's own page, then where the subscription
 * stands, with Manage (Stripe's page again) to cancel or change the card. Sessions open once Stripe confirms the
 * first payment to the server, usually within seconds of coming back. Shown only when the family has something to
 * do, unless `always`.
 */
export default function HomePassCard({ always = false }: { always?: boolean }) {
  const [pass, setPass] = useState<HomePass | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [params] = useSearchParams()
  // Back from Stripe's page: the first payment may still be on its way.
  const justSubscribed = params.get('subscribed') === '1'
  const waiting = justSubscribed && !!pass && pass.subscription === 'NONE' && !pass.running
  const [tries, setTries] = useState(0)

  useEffect(() => {
    api
      .homePass()
      .then(setPass)
      .catch(() => setPass(null))
  }, [tries])

  useEffect(() => {
    if (!waiting || tries >= 10) return
    const timer = window.setTimeout(() => setTries((n) => n + 1), 2000)
    return () => window.clearTimeout(timer)
  }, [waiting, tries])

  if (!pass) return null
  const state = passState(pass)
  if (state.kind === 'no-family' || (!always && !needsAttention(pass))) return null

  async function open(page: () => Promise<{ url: string }>) {
    setBusy(true)
    setError(null)
    try {
      // A full page load: Stripe's page is a different site.
      window.location.assign((await page()).url)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Card payments are not available just now')
      setBusy(false)
    }
  }

  return (
    <section className={`card home-pass ${needsAttention(pass) ? 'home-pass-due' : ''}`}>
      <h2>{state.heading}</h2>
      {waiting ? (
        <p role="status">
          Thank you. Your subscription starts as soon as your payment is confirmed, usually within a few seconds.
        </p>
      ) : (
        <p>{state.text}</p>
      )}
      {!waiting &&
        state.action &&
        (pass.cardPayments ? (
          <button
            className={state.action === 'subscribe' ? 'btn btn-primary' : 'btn'}
            disabled={busy}
            onClick={() => open(state.action === 'subscribe' ? api.homeSubscribe : api.homeManageSubscription)}
          >
            {state.actionLabel}
          </button>
        ) : (
          <p className="muted">Paying by card is not switched on yet. {contactUs(pass)}</p>
        ))}
      {state.action === 'manage' && !waiting && (
        <p className="fine">Manage opens our payment provider’s page, where you can cancel or change your card.</p>
      )}
      {error && <p className="error">{error}</p>}
    </section>
  )
}
