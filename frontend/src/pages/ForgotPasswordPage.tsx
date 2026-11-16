import { BRAND } from '../brand'
import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api'
import Logo from '../components/Logo'

/** Asks for a reset link. The answer is the same whether or not the email has an account. */
export default function ForgotPasswordPage() {
  const [email, setEmail] = useState('')
  const [sent, setSent] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await api.requestPasswordReset(email)
      setSent(true)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not send the link')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="login">
      <div className="login-hero">
        <h1>
          <Logo stacked />
        </h1>
      </div>
      <form className="card login-card" onSubmit={submit}>
        <h2>Reset your password</h2>
        {sent ? (
          <p>
            If <strong>{email}</strong> has a {BRAND} account, we have emailed it a link to choose a new
            password. The link works for one hour.
          </p>
        ) : (
          <>
            <label>
              Email
              <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="username" required />
            </label>
            {error && <p className="error">{error}</p>}
            <button className="btn btn-primary" disabled={busy}>
              {busy ? 'Sending…' : 'Email me a link'}
            </button>
          </>
        )}
        <Link to="/login" className="fine-link">
          Back to sign in
        </Link>
      </form>
    </div>
  )
}
