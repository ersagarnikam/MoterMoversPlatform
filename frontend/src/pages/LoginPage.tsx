import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { homeFor } from '../App'
import { useAuth } from '../auth'
import StageStripe from '../components/StageStripe'
import ThemeSwitch from '../components/ThemeSwitch'

const DEMO_ACCOUNTS = [
  ['teacher@oakfield.test', 'Class teacher'],
  ['newteacher@oakfield.test', 'New teacher'],
  ['head@oakfield.test', 'Head teacher'],
  ['trust@northbridge.test', 'Trust lead'],
  ['practitioner@example.test', 'Practitioner'],
  ['parent@example.test', 'Parent'],
  ['admin@motormovers.test', 'Motor Movers team'],
]

export default function LoginPage() {
  const { signIn } = useAuth()
  const navigate = useNavigate()
  const [email, setEmail] = useState(import.meta.env.DEV ? 'teacher@oakfield.test' : '')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      const user = await signIn(email, password)
      navigate(homeFor(user.role), { replace: true })
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Sign-in failed')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="login">
      <div className="login-hero">
        <StageStripe />
        <h1>Motor Movers</h1>
        <p>Screening, daily sessions and progress for schools, practitioners and parents.</p>
        <div>
          <ThemeSwitch />
        </div>
      </div>
      <form className="card login-card" onSubmit={submit}>
        <h2>Sign in</h2>
        <label>
          Email
          <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="username" required />
        </label>
        <label>
          Password
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="current-password"
            required
          />
        </label>
        {error && <p className="error">{error}</p>}
        <button className="btn btn-primary" disabled={busy}>
          {busy ? 'Signing in…' : 'Sign in'}
        </button>
        <Link to="/forgot-password" className="fine-link">Forgot your password?</Link>
        {import.meta.env.DEV && (
          <div className="demo-accounts">
            <p className="muted">Demo accounts (password: <code>password</code>)</p>
            <div className="chips">
              {DEMO_ACCOUNTS.map(([addr, label]) => (
                <button
                  type="button"
                  key={addr}
                  className={`chip ${addr === email ? 'chip-on' : ''}`}
                  onClick={() => {
                    setEmail(addr)
                    setPassword('password')
                  }}
                >
                  {label}
                </button>
              ))}
            </div>
          </div>
        )}
      </form>
    </div>
  )
}
