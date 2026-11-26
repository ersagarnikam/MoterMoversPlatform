import { useAuth } from '../auth'

/**
 * What a live-course page says where live courses are switched off (go-live task 89): courses are recorded, so there
 * are no live sessions, cohorts or trainers. A trainer's account has nothing to do here.
 */
export default function LiveCoursesOff() {
  const { user } = useAuth()
  return (
    <>
      <h1>Courses are recorded</h1>
      <p className="banner-info">
        Courses are now recorded lessons that learners take in their own time, so there are no live sessions to run.
        {user?.role === 'TRAINER'
          ? ' There is nothing for a trainer account to do here. Please contact the programme team if you have a question.'
          : ' Each course’s lessons, tests and learners are on the Courses page.'}
      </p>
    </>
  )
}
