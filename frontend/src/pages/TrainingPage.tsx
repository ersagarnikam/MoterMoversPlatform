import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  api,
  type Course,
  type CourseRequest,
  type Enrolment,
  type Induction,
  type LiveSession,
  type Payment,
  type QuizSummary,
  type Totals,
} from '../api'
import { sessionTime } from '../live'
import PaymentTable from '../components/PaymentTable'
import { useAuth } from '../auth'
import { gbp } from '../money'
import { day } from '../dates'
import { useConfirm, useToast } from '../components/Feedback'
import Skeleton from '../components/Skeleton'


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
          <span className="badge badge-ok">Completed {day(induction.completedOn)}</span>
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

/** The quizzes between a course's live sessions: each opens once its session has started or been attended. */
function CourseQuizzes({ enrolment }: { enrolment: Enrolment }) {
  const [quizzes, setQuizzes] = useState<QuizSummary[] | null>(null)

  useEffect(() => {
    api
      .myQuizzes(enrolment.id)
      .then(setQuizzes)
      .catch(() => setQuizzes([]))
  }, [enrolment.id])

  if (!quizzes || quizzes.length === 0) return null
  return (
    <ul className="quiz-list">
      {quizzes.map((q) => (
        <li key={q.id}>
          <span>
            Quiz after session {q.afterSession}: {q.title}
            <span className="muted">
              {' '}
              · {q.questions} questions · pass mark {q.passPercent}%
            </span>
          </span>
          {q.passed ? (
            <span className="badge badge-ok">Passed · {q.bestScore}%</span>
          ) : q.open ? (
            <Link className="btn btn-small" to={`/training/quiz/${enrolment.id}/${q.id}`}>
              {q.bestScore === null ? 'Take quiz' : `Try again (best ${q.bestScore}%)`}
            </Link>
          ) : (
            <span className="muted">Opens after session {q.afterSession}</span>
          )}
        </li>
      ))}
    </ul>
  )
}

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
  const [requests, setRequests] = useState<CourseRequest[]>([])
  const [error, setError] = useState<string | null>(null)
  const setNotice = useToast()
  const [loaded, setLoaded] = useState(false)

  const load = useCallback(() => {
    Promise.all([api.courses(), api.myEnrolments(), api.myRequests()])
      .then(([c, m, r]) => {
        setCourses(c.courses)
        setRules(c.rules)
        setMine(m)
        setRequests(r)
        setLoaded(true)
      })
      .catch((e) => setError(e.message))
  }, [])

  useEffect(load, [load])

  async function act(action: () => Promise<unknown>, done: string | ((result: unknown) => string)) {
    setError(null)
    setNotice(null)
    try {
      const result = await action()
      setNotice(typeof done === 'string' ? done : done(result))
      load()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Something went wrong')
    }
  }

  const confirm = useConfirm()

  async function cancel(e: Enrolment) {
    if (!(await confirm({ title: 'Cancel your place?', body: `On ${e.courseTitle} (${e.cohortName}).`, confirmLabel: 'Cancel place', cancelLabel: 'Keep my place', danger: true }))) return
    act(() => api.cancelPlace(e.id), (r) => (r as { message: string }).message)
  }

  if (!loaded && !error) return <Skeleton />
  const enrolledCohorts = new Set(mine.map((m) => m.cohortId))
  const waiting = new Map(requests.filter((r) => r.status === 'REQUESTED').map((r) => [r.cohortId, r]))
  // A school pays for its staff's places, so staff other than heads ask their head first.
  const needsApproval = (c: Course) => user?.schoolId != null && user.role !== 'HEAD' && c.pricePence > 0
  const undecided = requests.filter((r) => r.status === 'REQUESTED' || r.status === 'DECLINED')

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
            <div key={e.id} className="enrolment-block">
            <div className="enrolment">
              <div>
                <strong>{e.courseTitle}</strong>
                <p className="muted">
                  {e.cohortName} · starts {day(e.startsOn)} · {e.timeSlot}
                </p>
              </div>
              <div className="enrolment-status">
                <span>
                  {e.sessionsAttended} of {e.liveSessions} live sessions attended
                </span>
                {e.certificateIssuedOn ? (
                  <>
                    <span className="badge badge-ok">Certified until {day(e.certificateExpiresOn)}</span>
                    <Link className="fine-link" to={`/training/certificate/${e.id}`}>
                      View your certificate
                    </Link>
                  </>
                ) : e.track === 'PROFESSIONAL' ? (
                  <span className="muted">
                    Certificate after all sessions{e.quizzes > 0 ? ', the quizzes' : ''} and the assessment
                  </span>
                ) : null}
                {!e.certificateIssuedOn && e.sessionsAttended === 0 && (
                  <button className="btn" onClick={() => cancel(e)}>
                    Cancel place
                  </button>
                )}
              </div>
            </div>
            {e.quizzes > 0 && <CourseQuizzes enrolment={e} />}
            </div>
          ))}
        </section>
      )}

      {undecided.length > 0 && (
        <section className="card">
          <h3>Your requests</h3>
          {undecided.map((r) => (
            <div key={r.id} className="enrolment">
              <div>
                <strong>{r.courseTitle}</strong>
                <p className="muted">
                  {r.cohortName} · starts {day(r.startsOn)} · {r.timeSlot}
                </p>
              </div>
              <div className="enrolment-status">
                {r.status === 'REQUESTED' ? (
                  <>
                    <span className="badge">Waiting for your head's approval</span>
                    <button className="btn" onClick={() => act(() => api.withdrawRequest(r.id), 'Request withdrawn')}>
                      Withdraw
                    </button>
                  </>
                ) : (
                  <span className="muted">Not approved by {r.decidedBy}</span>
                )}
              </div>
            </div>
          ))}
        </section>
      )}

      {user && user.role !== 'ADMIN' && !OWN_PAYMENTS_PAGE.includes(user.role) && <YourPayments />}

      {user?.schoolId != null && (
        <p className="banner-teal">
          {user.role === 'HEAD'
            ? 'Places you book are invoiced to your school, and show on its Payments page.'
            : 'Your school pays for your places, so your head approves each one before it is booked.'}
        </p>
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
                    Starts {day(h.startsOn)} · {h.timeSlot} · led by {h.trainerName}
                  </p>
                  <p className="muted">
                    {h.seatsLeft} places left · {h.recordingAgreed ? 'sessions recorded' : 'not recorded'}
                  </p>
                </div>
                {enrolledCohorts.has(h.id) ? (
                  <span className="badge badge-ok">Booked</span>
                ) : waiting.has(h.id) ? (
                  <span className="badge">Waiting for approval</span>
                ) : needsApproval(c) ? (
                  <button
                    className="btn btn-primary"
                    disabled={h.seatsLeft <= 0}
                    onClick={() =>
                      act(() => api.requestPlace(h.id), 'Request sent. Your head has been emailed to approve it.')
                    }
                  >
                    Ask for a place
                  </button>
                ) : (
                  <button
                    className="btn btn-primary"
                    disabled={h.seatsLeft <= 0}
                    onClick={() => act(() => api.enrol(h.id), `Booked: ${c.title}, ${h.name}`)}
                  >
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
