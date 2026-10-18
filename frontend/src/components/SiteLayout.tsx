import { useEffect } from 'react'
import { Link, NavLink, Outlet, useLocation } from 'react-router-dom'
import { homeFor } from '../App'
import { useAuth } from '../auth'
import { CONTACT_EMAIL, CONTACT_PHONE } from '../site'
import StageStripe from './StageStripe'
import ThemeSwitch from './ThemeSwitch'

const NAV = [
  { to: '/', label: 'Home' },
  { to: '/about', label: 'About' },
  { to: '/programme', label: 'The programme' },
  { to: '/for-schools', label: 'For schools' },
  { to: '/for-families', label: 'For families' },
]

/** The public website: open to everyone, signed in or not. */
export default function SiteLayout() {
  const { user } = useAuth()
  const { pathname } = useLocation()

  // A new page starts at the top, as it would on an ordinary website.
  useEffect(() => {
    window.scrollTo(0, 0)
  }, [pathname])

  return (
    <div className="site">
      <header className="site-header">
        <Link to="/" className="brand site-brand">
          <StageStripe />
          <span>Motor Movers</span>
        </Link>
        <nav className="site-nav" aria-label="Website">
          {NAV.map((item) => (
            <NavLink key={item.to} to={item.to} end>
              {item.label}
            </NavLink>
          ))}
        </nav>
        <div className="site-actions">
          <ThemeSwitch />
          {user ? (
            <Link to={homeFor(user.role)} className="btn btn-light">
              Open Motor Movers
            </Link>
          ) : (
            <Link to="/sign-in" className="btn btn-light">
              Sign in
            </Link>
          )}
        </div>
      </header>
      <main className="site-main">
        <Outlet />
      </main>
      <footer className="site-footer">
        <div className="site-footer-inner">
          <div>
            <p className="site-footer-brand">
              <StageStripe /> Motor Movers
            </p>
            <p className="muted-on-dark">A programme from the Movement for Learning Project.</p>
          </div>
          <nav aria-label="Footer">
            {NAV.slice(1).map((item) => (
              <Link key={item.to} to={item.to}>
                {item.label}
              </Link>
            ))}
            <Link to="/sign-in">Sign in</Link>
          </nav>
          <div>
            <p>
              <a href={`mailto:${CONTACT_EMAIL}`}>{CONTACT_EMAIL}</a>
            </p>
            <p className="muted-on-dark">{CONTACT_PHONE}</p>
          </div>
        </div>
      </footer>
    </div>
  )
}
