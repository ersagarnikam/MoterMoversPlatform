import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../auth'
import StageStripe from '../components/StageStripe'
import ThemeSwitch from '../components/ThemeSwitch'
import { useHomeAvailable } from '../homeProgramme'

/**
 * Parents and carers create their own account (every other role is set up by a school or the Motor Movers team).
 * Then Motor Movers at Home if it is on, otherwise the parent course (see App's /join route).
 */
export default function RegisterPage() {
  const { register } = useAuth()
  const home = useHomeAvailable()
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      // Signed in, the app's routes take over and send /join on to At home (or the parent course).
      await register(email.trim(), password, name.trim())
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not create your account')
      setBusy(false)
    }
  }

  return (
    <div className="login">
      <div className="login-hero">
        <StageStripe />
        <h1>Motor Movers</h1>
        <p>
          {home
            ? 'Short daily movement sessions at home, with the same exercises schools use.'
            : 'The parent course and, if your child’s school does Motor Movers, the parent app.'}
        </p>
        <div className="login-hero-links">
          <ThemeSwitch />
          <Link to="/" className="login-back">
            Back to the website
          </Link>
        </div>
      </div>
      <form className="card login-card" onSubmit={submit}>
        <h2>Create a parent account</h2>
        <p className="muted">
          For parents and carers. Already have one? <Link to="/login?as=parent" className="fine-link">Sign in</Link>
        </p>
        <label>
          Your name
          <input value={name} onChange={(e) => setName(e.target.value)} autoComplete="name" maxLength={100} required />
        </label>
        <label>
          Email
          <input
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            autoComplete="email"
            maxLength={200}
            required
          />
        </label>
        <label>
          Password
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="new-password"
            minLength={10}
            maxLength={200}
            required
          />
          <span className="hint">At least 10 characters.</span>
        </label>
        {error && (
          <p className="error" role="alert">
            {error}
          </p>
        )}
        <button className="btn btn-primary" disabled={busy}>
          {busy ? 'Creating…' : 'Create account'}
        </button>
        <p className="fine">
          We keep your name and email to run your account. Children are added by first name or nickname and age only.
        </p>
      </form>
    </div>
  )
}
