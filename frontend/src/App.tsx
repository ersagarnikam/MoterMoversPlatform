import { Navigate, Route, Routes } from 'react-router-dom'
import type { Role } from './api'
import { useAuth } from './auth'
import Layout from './components/Layout'
import LoginPage from './pages/LoginPage'
import TodayPage from './pages/TodayPage'
import PlayerPage from './pages/PlayerPage'
import ScreeningPage from './pages/ScreeningPage'
import ProfilePage from './pages/ProfilePage'
import ClassListPage from './pages/ClassListPage'
import HeadDashboardPage from './pages/HeadDashboardPage'
import TrustDashboardPage from './pages/TrustDashboardPage'
import TrainingPage from './pages/TrainingPage'
import ResourcesPage from './pages/ResourcesPage'
import SchoolSetupPage from './pages/SchoolSetupPage'
import ForgotPasswordPage from './pages/ForgotPasswordPage'
import ResetPasswordPage from './pages/ResetPasswordPage'
import ContentPage from './pages/ContentPage'
import SchoolsPage from './pages/SchoolsPage'

/** Where each kind of user lands after signing in. */
export function homeFor(role: Role): string {
  switch (role) {
    case 'TEACHER':
      return '/classes'
    case 'HEAD':
      return '/dashboard'
    case 'TRUST_LEAD':
      return '/trust'
    case 'ADMIN':
      return '/admin/content'
    default:
      return '/training'
  }
}

export default function App() {
  const { user, loading } = useAuth()
  if (loading) return <div className="page-loading">Loading…</div>
  if (!user) {
    return (
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/forgot-password" element={<ForgotPasswordPage />} />
        <Route path="/reset-password" element={<ResetPasswordPage />} />
        <Route path="*" element={<Navigate to="/login" replace />} />
      </Routes>
    )
  }
  return (
    <Routes>
      <Route path="/reset-password" element={<ResetPasswordPage />} />
      <Route element={<Layout />}>
        <Route path="/classes" element={<ClassListPage />} />
        <Route path="/class/:classId/today" element={<TodayPage />} />
        <Route path="/class/:classId/play" element={<PlayerPage />} />
        <Route path="/class/:classId/screening" element={<ScreeningPage />} />
        <Route path="/child/:childId" element={<ProfilePage />} />
        <Route path="/dashboard" element={<HeadDashboardPage />} />
        <Route path="/trust" element={<TrustDashboardPage />} />
        <Route path="/training" element={<TrainingPage />} />
        <Route path="/resources" element={<ResourcesPage />} />
        <Route path="/school/setup" element={<SchoolSetupPage />} />
        <Route path="/admin/schools/:schoolId/setup" element={<SchoolSetupPage />} />
        <Route path="/admin/content" element={<ContentPage />} />
        <Route path="/admin/schools" element={<SchoolsPage />} />
        <Route path="*" element={<Navigate to={homeFor(user.role)} replace />} />
      </Route>
    </Routes>
  )
}
