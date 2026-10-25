import { useEffect, useState } from 'react'
import { api, type AdminCourse } from '../api'
import { CohortBookings } from './CoursesPage'

/**
 * A trainer's own cohorts: who has booked, attendance at each live session, and the end-of-course assessment, which
 * with full attendance issues the certificate.
 */
export default function TrainerCohortsPage() {
  const [courses, setCourses] = useState<AdminCourse[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api
      .trainerCohorts()
      .then(setCourses)
      .catch((e) => setError(e.message))
  }, [])

  if (error) return <p className="error">{error}</p>
  if (!courses) return <p className="muted">Loading…</p>

  return (
    <>
      <h1>My cohorts</h1>
      {courses.length === 0 && (
        <p className="muted">No cohorts are given to you yet. The Motor Movers team will let you know.</p>
      )}
      {courses.map((c) =>
        c.cohorts.map((h) => (
          <section key={h.id} className="card">
            <div className="section-head">
              <h3>
                {c.title} · {h.name}
              </h3>
              <span className="muted">
                {h.booked} of {h.capacity} places booked
              </span>
            </div>
            <p className="muted">
              Starts {h.startsOn} · {h.timeSlot}
            </p>
            <CohortBookings cohortId={h.id} cohortName={h.name} sessions={h.sessions} certificate={c.awardsCertificate} />
          </section>
        )),
      )}
    </>
  )
}
