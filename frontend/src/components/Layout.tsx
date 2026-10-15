import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import type { Role } from '../api'
import { useAuth } from '../auth'
import StageStripe from './StageStripe'

const NAV: Record<Role, { to: string; label: string }[]> = {
  TEACHER: [
    { to: '/classes', label: 'My class' },
    { to: '/training', label: 'Training' },
  ],
  HEAD: [
    { to: '/dashboard', label: 'Progress' },
    { to: '/classes', label: 'Classes' },
    { to: '/training', label: 'Training' },
  ],
  TRUST_LEAD: [{ to: '/trust', label: 'Trust schools' }],
  PRACTITIONER: [{ to: '/training', label: 'My course' }],
  PARENT: [{ to: '/training', label: 'Parent course' }],
  ADMIN: [
    { to: '/admin/content', label: 'Content approval' },
    { to: '/admin/schools', label: 'Schools & licences' },
    { to: '/classes', label: 'All classes' },
    { to: '/training', label: 'Courses' },
  ],
}

const ROLE_LABEL: Record<Role, string> = {
  TEACHER: 'Class teacher',
  HEAD: 'Head teacher / SENCo',
  TRUST_LEAD: 'Trust lead',
  PRACTITIONER: 'Practitioner',
  PARENT: 'Parent or carer',
  ADMIN: 'Motor Movers team',
}

export default function Layout() {
  const { user, signOut } = useAuth()
  const navigate = useNavigate()
  const { pathname } = useLocation()
  if (!user) return null
  return (
    <div className="shell">
      <header className="topbar">
        <div className="brand">
          <StageStripe />
          <span>Motor Movers</span>
        </div>
        <div className="who">
          <span>
            {user.displayName} · <span className="muted-on-dark">{ROLE_LABEL[user.role]}</span>
          </span>
          <button
            className="btn btn-ghost-dark"
            onClick={() => {
              signOut()
              navigate('/login')
            }}
          >
            Sign out
          </button>
        </div>
      </header>
      <div className="body">
        <nav className="sidenav" aria-label="Main">
          {NAV[user.role].map((item) => (
            <NavLink key={item.to} to={item.to} className={({ isActive }) =>
                isActive || (item.to === '/classes' && /^\/(class|child)\//.test(pathname)) ? 'active' : ''
              }>
              {item.label}
            </NavLink>
          ))}
        </nav>
        <main className="content">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
