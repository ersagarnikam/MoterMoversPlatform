import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { api, type Payment } from '../api'

/** Whether Pay by card is switched on (Stripe keys set on the server). False until known, and on any error. */
export function useCardPayments(): boolean {
  const [enabled, setEnabled] = useState(false)
  useEffect(() => {
    api
      .onlinePayments()
      .then((r) => setEnabled(r.enabled))
      .catch(() => setEnabled(false))
  }, [])
  return enabled
}

/**
 * Sends the payer to Stripe's own page to pay by card; the card details never touch the platform. The payment
 * shows as paid once Stripe confirms it to the server, usually within seconds.
 */
export function PayByCard({ payment, onError }: { payment: Payment; onError: (message: string) => void }) {
  const [busy, setBusy] = useState(false)

  async function pay() {
    setBusy(true)
    try {
      // A full page load: Stripe's page is a different site.
      window.location.assign((await api.checkout(payment.id)).url)
    } catch (e) {
      onError(e instanceof Error ? e.message : 'Card payments are not available just now')
      setBusy(false)
    }
  }

  return (
    <button className="btn btn-primary btn-small" disabled={busy} onClick={pay}>
      Pay by card
    </button>
  )
}

/** After coming back from Stripe: thanks (it shows as paid once Stripe confirms), or that nothing was taken. */
export function CardPaymentReturn() {
  const [params] = useSearchParams()
  if (params.get('paid')) {
    return (
      <p className="banner-teal" role="status">
        Thank you. Your card payment is being confirmed and shows as paid here in a moment.
      </p>
    )
  }
  if (params.get('cancelled')) {
    return (
      <p className="banner-info" role="status">
        The card payment was cancelled, and nothing was taken.
      </p>
    )
  }
  return null
}
