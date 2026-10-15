import { useCallback, useEffect, useState } from 'react'
import { api, type Course, type Enrolment } from '../api'

const gbp = (pence: number) => `£${(pence / 100).toFixed(pence % 100 === 0 ? 0 : 2)}`

/** Live courses for professionals and parents. */
export default function TrainingPage() {
  const [courses, setCourses] = useState<Course[]>([])
  const [rules, setRules] = useState('')
  const [mine, setMine] = useState<Enrolment[]>([])
  const [error, setError] = useState<string | null>(null)
  const [loaded, setLoaded] = useState(false)

  const load = useCallback(() => {
    Promise.all([api.courses(), api.myEnrolments()])
      .then(([c, m]) => {
        setCourses(c.courses)
        setRules(c.rules)
        setMine(m)
        setLoaded(true)
      })
      .catch((e) => setError(e.message))
  }, [])

  useEffect(load, [load])

  async function enrol(cohortId: number) {
    setError(null)
    try {
      await api.enrol(cohortId)
      load()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not enrol')
    }
  }

  if (!loaded && !error) return <p className="muted">Loading…</p>
  const enrolledCohorts = new Set(mine.map((m) => m.cohortId))

  return (
    <>
      <h1>Training</h1>
      {error && <p className="error">{error}</p>}

      {mine.length > 0 && (
        <section className="card">
          <h3>Your courses</h3>
          {mine.map((e) => (
            <div key={e.id} className="enrolment">
              <div>
                <strong>{e.courseTitle}</strong>
                <p className="muted">
                  {e.cohortName} · starts {e.startsOn} · {e.timeSlot}
                </p>
              </div>
              <div className="enrolment-status">
                <span>
                  {e.sessionsAttended} of {e.liveSessions} live sessions attended
                </span>
                {e.certificateIssuedOn ? (
                  <span className="badge badge-ok">Certified until {e.certificateExpiresOn}</span>
                ) : e.track === 'PROFESSIONAL' ? (
                  <span className="muted">Certificate after all sessions and the assessment</span>
                ) : null}
              </div>
            </div>
          ))}
        </section>
      )}

      {courses.map((c) => (
        <section key={c.id} className="card">
          <div className="section-head">
            <h3>{c.title}</h3>
            <span className="price">
              {gbp(c.pricePence)}
              {c.track === 'PARENT' ? ' per family' : ''}
              {c.renewalPence ? `, then ${gbp(c.renewalPence)} / year` : ''}
            </span>
          </div>
          <p>{c.description}</p>
          <p className="muted">
            {c.liveSessions} weekly live sessions of {c.sessionMinutes} minutes · join from a browser, nothing to install
          </p>
          <div className="cohorts">
            {c.cohorts.map((h) => (
              <div key={h.id} className="cohort">
                <div>
                  <strong>{h.name}</strong>
                  <p className="muted">
                    Starts {h.startsOn} · {h.timeSlot} · led by {h.trainerName}
                  </p>
                  <p className="muted">
                    {h.seatsLeft} places left · {h.recordingAgreed ? 'sessions recorded' : 'not recorded'}
                  </p>
                </div>
                {enrolledCohorts.has(h.id) ? (
                  <span className="badge badge-ok">Booked</span>
                ) : (
                  <button className="btn btn-primary" disabled={h.seatsLeft <= 0} onClick={() => enrol(h.id)}>
                    Book a place
                  </button>
                )}
              </div>
            ))}
            {c.cohorts.length === 0 && <p className="muted">No upcoming cohorts.</p>}
          </div>
        </section>
      ))}
      {rules && (
        <p className="banner-teal">
          <strong>Rules for every live session:</strong> {rules}
        </p>
      )}
    </>
  )
}
