import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, type Course, type Enrolment, type Induction, type LiveSession, type Payment, type Totals } from '../api'
import { sessionTime } from '../live'
import PaymentTable from '../components/PaymentTable'
import { useAuth } from '../auth'
import { gbp } from '../money'


/** The one-hour induction school staff complete before running sessions. */
function InductionSection() {
  const [induction, setInduction] = useState<Induction | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api.induction().then(setInduction).catch((e) => setError(e.message))
  }, [])

  async function complete(lessonId: number) {
    setError(null)
    try {
      setInduction(await api.completeLesson(lessonId))
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not save')
    }
  }

  if (error) return <p className="error">{error}</p>
  if (!induction) return null
  return (
    <section className="card">
      <div className="section-head">
        <h3>Motor Movers induction</h3>
        {induction.completedOn ? (
          <span className="badge badge-ok">Completed {induction.completedOn}</span>
        ) : (
          <span className="muted">
            {induction.lessonsDone} of {induction.lessons.length} parts done · {induction.totalMinutes} minutes in all
          </span>
        )}
      </div>
      {!induction.completedOn && (
        <p className="banner-info">Complete every part before running the daily sessions with your class.</p>
      )}
      <div className="cohorts">
        {induction.lessons.map((l) => (
          <details key={l.id} className="lesson">
            <summary>
              <span>
                <strong>
                  {l.position}. {l.title}
                </strong>
                <span className="muted"> · {l.minutes} min · {l.summary}</span>
              </span>
              {l.done && <span className="badge badge-ok">Done</span>}
            </summary>
            {l.videoUrl && <video src={l.videoUrl} controls />}
            {l.paragraphs.map((p) => (
              <p key={p}>{p}</p>
            ))}
            {!l.done && (
              <div className="actions">
                <button className="btn btn-primary" onClick={() => complete(l.id)}>
                  Mark as done
                </button>
              </div>
            )}
          </details>
        ))}
      </div>
    </section>
  )
}

/** What the signed-in person has paid for themselves. Hidden until there is something to show. */
/** Parents and practitioners have their own Payments page; staff see their payments here. */
const OWN_PAYMENTS_PAGE = ['PARENT', 'PRACTITIONER']

/** The learner's coming live sessions, with Join once a session opens (15 minutes before the start). */
function YourLiveSessions() {
  const [sessions, setSessions] = useState<LiveSession[]>([])

  useEffect(() => {
    // A failure here should not hide the courses, so it is left silent.
    api.liveSessions().then(setSessions).catch(() => setSessions([]))
  }, [])

  if (sessions.length === 0) return null
  return (
    <section className="card">
      <h3>Your live sessions</h3>
      <ul className="live-list">
        {sessions.slice(0, 4).map((s) => (
          <li key={s.id}>
            <div>
              <strong>
                {s.courseTitle} · session {s.number} of {s.of}
              </strong>
              <p className="muted">
                {sessionTime(s.startsAt)} · {s.durationMinutes} minutes on Zoom, here in Motor Movers
              </p>
            </div>
            {s.open ? (
              // A full page load: only the session page allows Zoom's scripts.
              <a className="btn btn-primary" href={`/live/${s.id}`}>
                Join live session
              </a>
            ) : (
              <span className="muted">Opens 15 minutes before the start</span>
            )}
          </li>
        ))}
      </ul>
    </section>
  )
}

/** A reminder at the top of the page while anything is still to pay. */
function PaymentDue() {
  const [due, setDue] = useState(0)

  useEffect(() => {
    // A failure here should not hide the courses, so it is left silent.
    api
      .myPayments()
      .then((p) => setDue(p.outstandingPence))
      .catch(() => setDue(0))
  }, [])

  if (due === 0) return null
  return (
    <p className="banner-warn">
      You have {gbp(due)} to pay. <Link to="/payments">See what it is for and how to pay</Link>
    </p>
  )
}

function YourPayments() {
  const [data, setData] = useState<(Totals & { payments: Payment[] }) | null>(null)

  useEffect(() => {
    // A failure here should not hide the courses, so it is left silent.
    api.myPayments().then(setData).catch(() => setData(null))
  }, [])

  if (!data || data.payments.length === 0) return null
  return (
    <section className="card">
      <div className="section-head">
        <h3>Your payments</h3>
        <span className="muted">
          {gbp(data.paidPence)} paid{data.outstandingPence ? ` · ${gbp(data.outstandingPence)} due` : ''}
        </span>
      </div>
      <PaymentTable payments={data.payments} />
    </section>
  )
}

/** Live courses for professionals and parents. */
export default function TrainingPage() {
  const { user } = useAuth()
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
      {user && OWN_PAYMENTS_PAGE.includes(user.role) && <PaymentDue />}
      {user?.role !== 'ADMIN' && <YourLiveSessions />}
      {user && ['TEACHER', 'HEAD', 'PRACTITIONER'].includes(user.role) && <InductionSection />}

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
                  <>
                    <span className="badge badge-ok">Certified until {e.certificateExpiresOn}</span>
                    <Link className="fine-link" to={`/training/certificate/${e.id}`}>
                      View your certificate
                    </Link>
                  </>
                ) : e.track === 'PROFESSIONAL' ? (
                  <span className="muted">Certificate after all sessions and the assessment</span>
                ) : null}
              </div>
            </div>
          ))}
        </section>
      )}

      {user && user.role !== 'ADMIN' && !OWN_PAYMENTS_PAGE.includes(user.role) && <YourPayments />}

      {user?.schoolId != null && (
        <p className="banner-teal">Places you book are invoiced to your school, and show on its Payments page.</p>
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
