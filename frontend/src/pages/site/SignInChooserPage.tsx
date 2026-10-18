import { Link, Navigate } from 'react-router-dom'
import { homeFor } from '../../App'
import { useAuth } from '../../auth'
import { SIGN_IN_ROLES } from '../../site'

/** "Who are you?" before the sign-in form, so each kind of user reaches the right place. */
export default function SignInChooserPage() {
  const { user } = useAuth()
  if (user) return <Navigate to={homeFor(user.role)} replace />
  return (
    <>
      <section className="site-page-head">
        <p className="eyebrow eyebrow-light">Sign in</p>
        <h1>Who are you signing in as?</h1>
        <p className="site-lead">Choose your role. After you sign in, you go straight to the right place.</p>
      </section>
      <section className="site-section">
        <div className="site-cards site-cards-3">
          {SIGN_IN_ROLES.map((r) => (
            <Link key={r.key} to={`/login?as=${r.key}`} className="card site-card-link">
              <h2 className="h3">{r.label}</h2>
              <p>{r.blurb}</p>
            </Link>
          ))}
        </div>
        <p className="muted">
          No account yet? Staff are invited by their head teacher or the Motor Movers team. Parents and carers can create an account in the
          parent app with the code from school.
        </p>
      </section>
    </>
  )
}
