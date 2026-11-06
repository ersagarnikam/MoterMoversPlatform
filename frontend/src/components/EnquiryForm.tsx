import { useState, type FormEvent } from 'react'
import { api, type EnquiryKind } from '../api'
import { CONTACT_EMAIL, CONTACT_PHONE, mailto, ENQUIRY_BODY } from '../site'
import PriceChart from './PriceChart'

const TITLES: Record<EnquiryKind, string> = {
  PRICE_GUIDE: 'Ask for the price guide',
  APPLY: 'Apply for Motor Movers',
}

/**
 * The website's enquiry form: the platform emails it to the Motor Movers team, so it works without an email program
 * on the sender's computer. The email link stays as a second way in. Asking for the price guide shows the price chart
 * first, and the plan chosen there goes with the enquiry.
 */
export default function EnquiryForm({ kind, onClose }: { kind: EnquiryKind; onClose?: () => void }) {
  const [fields, setFields] = useState({ name: '', email: '', setting: '', phone: '', classes: '', stages: '', plan: '', message: '' })
  // Hidden from people; a bot that fills it in is quietly ignored by the API.
  const [website, setWebsite] = useState('')
  const [busy, setBusy] = useState(false)
  const [sent, setSent] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const set = (name: keyof typeof fields) => (e: { target: { value: string } }) =>
    setFields((f) => ({ ...f, [name]: e.target.value }))

  async function submit(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await api.sendEnquiry({ kind, ...fields, email: fields.email.trim(), website })
      setSent(true)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not send your enquiry')
    } finally {
      setBusy(false)
    }
  }

  if (sent) {
    return (
      <div className="card enquiry-form" role="status">
        <h3>Thank you, we have your enquiry</h3>
        <p>
          {kind === 'PRICE_GUIDE'
            ? `We will email the price guide to ${fields.email.trim()}, usually within a week.`
            : `We will reply to ${fields.email.trim()}, usually within a week.`}
        </p>
        {onClose && (
          <button type="button" className="btn" onClick={onClose}>
            Close
          </button>
        )}
      </div>
    )
  }

  return (
    <form className="card enquiry-form" onSubmit={submit} aria-label={TITLES[kind]}>
      <h3>{TITLES[kind]}</h3>
      {kind === 'PRICE_GUIDE' && (
        <>
          <PriceChart chosen={fields.plan} onChoose={(plan) => setFields((f) => ({ ...f, plan }))} />
          <h4>Your details</h4>
          <p className="muted enquiry-lead">
            We will email you the full price guide{fields.plan ? ` and the details of ${fields.plan}` : ''}.
          </p>
        </>
      )}
      <div className="enquiry-grid">
        <label>
          Your name
          <input value={fields.name} onChange={set('name')} autoComplete="name" maxLength={100} required />
        </label>
        <label>
          Email
          <input
            type="email"
            value={fields.email}
            onChange={set('email')}
            autoComplete="email"
            maxLength={200}
            required
          />
        </label>
        <label>
          School or setting
          <input value={fields.setting} onChange={set('setting')} autoComplete="organization" maxLength={200} required />
        </label>
        <label>
          <span>Phone <span className="hint">(optional)</span></span>
          <input type="tel" value={fields.phone} onChange={set('phone')} autoComplete="tel" maxLength={40} />
        </label>
        <label>
          <span>Number of classes <span className="hint">(optional)</span></span>
          <input value={fields.classes} onChange={set('classes')} inputMode="numeric" maxLength={40} />
        </label>
        <label>
          <span>Stages you are interested in <span className="hint">(optional)</span></span>
          <input value={fields.stages} onChange={set('stages')} placeholder="All seven, or e.g. Green and Yellow" maxLength={200} />
        </label>
      </div>
      <label>
        <span>Message <span className="hint">(optional)</span></span>
        <textarea value={fields.message} onChange={set('message')} rows={3} maxLength={2000} />
      </label>
      <label className="visually-hidden" aria-hidden="true">
        Website
        <input value={website} onChange={(e) => setWebsite(e.target.value)} tabIndex={-1} autoComplete="off" />
      </label>
      {error && (
        <p className="error" role="alert">
          {error} You can email us at <a href={`mailto:${CONTACT_EMAIL}`}>{CONTACT_EMAIL}</a> or ring {CONTACT_PHONE}.
        </p>
      )}
      <div className="row-end">
        <button className="btn btn-primary" disabled={busy}>
          {busy ? 'Sending…' : 'Send'}
        </button>
        {onClose && (
          <button type="button" className="btn" onClick={onClose}>
            Cancel
          </button>
        )}
      </div>
      <p className="fine">
        We use these details only to reply. You can also email{' '}
        <a href={mailto(kind === 'PRICE_GUIDE' ? 'Motor Movers price guide' : 'Apply for Motor Movers', ENQUIRY_BODY)}>
          {CONTACT_EMAIL}
        </a>{' '}
        or call {CONTACT_PHONE}.
      </p>
    </form>
  )
}
