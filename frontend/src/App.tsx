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
import CertificatePage from './pages/CertificatePage'
import MyPaymentsPage from './pages/MyPaymentsPage'
import LiveRoomPage from './pages/LiveRoomPage'
import LiveSessionsPage from './pages/LiveSessionsPage'
import TrainerCohortsPage from './pages/TrainerCohortsPage'
import TeamHomePage from './pages/TeamHomePage'
import QuizPage from './pages/QuizPage'
import ResourcesPage from './pages/ResourcesPage'
import SchoolSetupPage from './pages/SchoolSetupPage'
import ForgotPasswordPage from './pages/ForgotPasswordPage'
import ResetPasswordPage from './pages/ResetPasswordPage'
import ContentPage from './pages/ContentPage'
import SchoolsPage from './pages/SchoolsPage'
import CoursesPage, { CoursePage, TrainersPage } from './pages/CoursesPage'
import PaymentsPage from './pages/PaymentsPage'
import PlansPage from './pages/PlansPage'
import SchoolPaymentsPage from './pages/SchoolPaymentsPage'
import SiteLayout from './components/SiteLayout'
import HomePage from './pages/site/HomePage'
import AboutPage from './pages/site/AboutPage'
import ProgrammePage from './pages/site/ProgrammePage'
import ForSchoolsPage from './pages/site/ForSchoolsPage'
import ForFamiliesPage from './pages/site/ForFamiliesPage'
import SignInChooserPage from './pages/site/SignInChooserPage'

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
      return '/admin'
    case 'TRAINER':
      return '/live'
    default:
      return '/training'
  }
}

export default function App() {
  const { user, loading } = useAuth()
  if (loading) return <div className="page-loading">Loading…</div>
  // The public website, the same whether or not someone is signed in.
  const site = (
    <Route element={<SiteLayout />}>
      <Route index element={<HomePage />} />
      <Route path="/about" element={<AboutPage />} />
      <Route path="/programme" element={<ProgrammePage />} />
      <Route path="/for-schools" element={<ForSchoolsPage />} />
      <Route path="/for-families" element={<ForFamiliesPage />} />
      <Route path="/sign-in" element={<SignInChooserPage />} />
    </Route>
  )
  if (!user) {
    return (
      <Routes>
        {site}
        <Route path="/login" element={<LoginPage />} />
        <Route path="/forgot-password" element={<ForgotPasswordPage />} />
        <Route path="/reset-password" element={<ResetPasswordPage />} />
        <Route path="*" element={<Navigate to="/login" replace />} />
      </Routes>
    )
  }
  return (
    <Routes>
      {site}
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
        <Route path="/training/certificate/:enrolmentId" element={<CertificatePage />} />
        <Route path="/payments" element={<MyPaymentsPage />} />
        <Route path="/live" element={<LiveSessionsPage />} />
        <Route path="/live/:sessionId" element={<LiveRoomPage />} />
        <Route path="/trainer/cohorts" element={<TrainerCohortsPage />} />
        <Route path="/training/quiz/:enrolmentId/:quizId" element={<QuizPage />} />
        <Route path="/resources" element={<ResourcesPage />} />
        <Route path="/school/setup" element={<SchoolSetupPage />} />
        <Route path="/admin/schools/:schoolId/setup" element={<SchoolSetupPage />} />
        <Route path="/admin" element={<TeamHomePage />} />
        <Route path="/admin/content" element={<ContentPage />} />
        <Route path="/admin/schools" element={<SchoolsPage />} />
        <Route path="/admin/courses" element={<CoursesPage />} />
        <Route path="/admin/courses/:courseId" element={<CoursePage />} />
        <Route path="/admin/trainers" element={<TrainersPage />} />
        <Route path="/admin/payments" element={<PaymentsPage />} />
        <Route path="/admin/plans" element={<PlansPage />} />
        <Route path="/school/payments" element={<SchoolPaymentsPage />} />
        <Route path="*" element={<Navigate to={homeFor(user.role)} replace />} />
      </Route>
    </Routes>
  )
}
