import { useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { api } from '../api'
import StageStripe from '../components/StageStripe'

const MIN_LENGTH = 10

/** Where invite and reset emails land: choose a password, then sign in. */
export default function ResetPasswordPage() {
  const [params] = useSearchParams()
  const token = params.get('token') ?? ''
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [doneFor, setDoneFor] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function submit(e: FormEvent) {
    e.preventDefault()
    if (password !== confirm) {
      setError('The two passwords do not match')
      return
    }
    setBusy(true)
    setError(null)
    try {
      const { email } = await api.setPassword(token, password)
      setDoneFor(email)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not set the password')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="login">
      <div className="login-hero">
        <StageStripe />
        <h1>Motor Movers</h1>
      </div>
      <form className="card login-card" onSubmit={submit}>
        <h2>Choose a password</h2>
        {!token ? (
          <p className="error">This link is incomplete. Open it again from your email.</p>
        ) : doneFor ? (
          <p>
            Your password is set. Sign in as <strong>{doneFor}</strong>.
          </p>
        ) : (
          <>
            <label>
              New password
              <input
                type="password"
                value={password}
                minLength={MIN_LENGTH}
                onChange={(e) => setPassword(e.target.value)}
                autoComplete="new-password"
                required
              />
            </label>
            <label>
              The same again
              <input
                type="password"
                value={confirm}
                onChange={(e) => setConfirm(e.target.value)}
                autoComplete="new-password"
                required
              />
            </label>
            <p className="muted">At least {MIN_LENGTH} characters. A short phrase is easier to remember.</p>
            {error && <p className="error">{error}</p>}
            <button className="btn btn-primary" disabled={busy}>
              {busy ? 'Saving…' : 'Set password'}
            </button>
          </>
        )}
        <Link to="/login" className="fine-link">
          Go to sign in
        </Link>
      </form>
    </div>
  )
}
