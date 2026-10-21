import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import {
  api,
  type AdminCourse,
  type CohortInput,
  type CourseInput,
  type CourseTrack,
  type Enrolment,
  type SessionSummary,
} from '../api'
import { sessionTime } from '../live'
import { gbp } from '../money'

const TRACKS: [CourseTrack, string][] = [
  ['PROFESSIONAL', 'Professionals (teachers, TAs, therapists)'],
  ['PARENT', 'Parents and carers'],
]

const pounds = (pence: number | null) => (pence === null ? '' : (pence / 100).toString())
const pence = (pounds: string) => Math.round(Number(pounds) * 100)

/** The team sets up live courses and opens cohorts; learners book them on the Training page. */
export default function CoursesPage() {
  const [courses, setCourses] = useState<AdminCourse[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  useEffect(() => {
    api
      .adminCourses()
      .then(setCourses)
      .catch((e) => setError(e.message))
  }, [])

  /** Every change returns the whole course, which replaces (or joins) the list. */
  async function run(action: () => Promise<AdminCourse>, done: string) {
    setError(null)
    setNotice(null)
    try {
      const saved = await action()
      setCourses((all) =>
        all?.some((c) => c.id === saved.id) ? all.map((c) => (c.id === saved.id ? saved : c)) : [...(all ?? []), saved],
      )
      setNotice(done)
      return true
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Something went wrong')
      return false
    }
  }

  if (!courses) return error ? <p className="error">{error}</p> : <p className="muted">Loading…</p>

  return (
    <>
      <div className="page-head">
        <h1>Courses</h1>
        <Link className="fine-link" to="/training">
          See the catalogue as learners do
        </Link>
      </div>
      {error && <p className="error">{error}</p>}
      {notice && <p className="banner-teal">{notice}</p>}
      {courses.length === 0 && <p className="muted">No courses yet. Set one up below.</p>}

      {courses.map((c) => (
        <CourseCard key={c.id} course={c} run={run} />
      ))}

      <section className="card">
        <h3>Set up a course</h3>
        <p className="muted">
          A course shows in the catalogue as soon as it is saved. Learners can book once it has a cohort.
        </p>
        <CourseForm submitLabel="Set up course" onSave={(input) => run(() => api.addCourse(input), `${input.title} set up`)} />
      </section>
    </>
  )
}

type Run = (action: () => Promise<AdminCourse>, done: string) => Promise<boolean>

function CourseCard({ course, run }: { course: AdminCourse; run: Run }) {
  const [editing, setEditing] = useState(false)
  const [editingCohort, setEditingCohort] = useState<number | null>(null)
  const [showingBookings, setShowingBookings] = useState<number | null>(null)
  const [adding, setAdding] = useState(false)

  return (
    <section className="card course-admin">
      <div className="section-head">
        <h3>{course.title}</h3>
        <span className="price">
          {gbp(course.pricePence)}
          {course.renewalPence ? `, then ${gbp(course.renewalPence)} / year` : ''}
        </span>
      </div>
      <p className="muted">
        {course.track === 'PARENT' ? 'Parents and carers' : 'Professionals'} · {course.liveSessions} live sessions of{' '}
        {course.sessionMinutes} minutes · {course.awardsCertificate ? 'certificate' : 'no certificate'}
      </p>
      {editing ? (
        <CourseForm
          initial={course}
          submitLabel="Save course"
          onCancel={() => setEditing(false)}
          onSave={async (input) => {
            const saved = await run(() => api.updateCourse(course.id, input), `${input.title} saved`)
            if (saved) setEditing(false)
            return saved
          }}
        />
      ) : (
        <>
          <p>{course.description}</p>
          <div className="actions">
            <button className="btn" onClick={() => setEditing(true)}>
              Edit course
            </button>
          </div>
        </>
      )}

      <h4>Cohorts</h4>
      {course.cohorts.length === 0 && <p className="muted">No cohorts yet.</p>}
      <div className="cohorts">
        {course.cohorts.map((h) =>
          editingCohort === h.id ? (
            <div key={h.id} className="setup-class">
              <CohortForm
                initial={h}
                submitLabel="Save cohort"
                onCancel={() => setEditingCohort(null)}
                onSave={async (input) => {
                  const saved = await run(() => api.updateCohort(h.id, input), `${input.name} saved`)
                  if (saved) setEditingCohort(null)
                  return saved
                }}
              />
            </div>
          ) : (
            <div key={h.id} className="cohort-block">
              <div className="cohort">
                <div>
                  <strong>{h.name}</strong>
                  <p className="muted">
                    Starts {h.startsOn} · {h.timeSlot} · led by {h.trainerName}
                  </p>
                  <p className="muted">
                    {h.booked} of {h.capacity} places booked · {h.recordingAgreed ? 'sessions recorded' : 'not recorded'}
                  </p>
                </div>
                <div className="actions">
                  <button
                    className="btn"
                    aria-expanded={showingBookings === h.id}
                    onClick={() => setShowingBookings(showingBookings === h.id ? null : h.id)}
                  >
                    {showingBookings === h.id ? 'Hide bookings' : 'Bookings and attendance'}
                  </button>
                  <button className="btn" onClick={() => setEditingCohort(h.id)}>
                    Edit cohort
                  </button>
                </div>
              </div>
              {showingBookings === h.id && (
                <CohortBookings
                  cohortId={h.id}
                  cohortName={h.name}
                  sessions={h.sessions}
                  certificate={course.awardsCertificate}
                />
              )}
            </div>
          ),
        )}
      </div>
      <details className="add-item" open={adding} onToggle={(e) => setAdding(e.currentTarget.open)}>
        <summary className="btn">Open a cohort</summary>
        <CohortForm
          submitLabel="Open cohort"
          onSave={async (input) => {
            const saved = await run(() => api.addCohort(course.id, input), `${input.name} opened for booking`)
            if (saved) setAdding(false)
            return saved
          }}
        />
      </details>
    </section>
  )
}

/**
 * Who has booked a cohort. The team ticks who attended each live session (as in the live session's register) and
 * the assessment result; the platform issues the certificate once both are complete, and then it is locked.
 */
function CohortBookings({
  cohortId,
  cohortName,
  sessions,
  certificate,
}: {
  cohortId: number
  cohortName: string
  sessions: SessionSummary[]
  certificate: boolean
}) {
  const [bookings, setBookings] = useState<Enrolment[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api
      .cohortBookings(cohortId)
      .then(setBookings)
      .catch((e) => setError(e.message))
  }, [cohortId])

  async function change(action: () => Promise<Enrolment>) {
    setError(null)
    try {
      const saved = await action()
      setBookings((all) => all?.map((b) => (b.id === saved.id ? saved : b)) ?? null)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Something went wrong')
    }
  }

  if (!bookings) return error ? <p className="error">{error}</p> : <p className="muted">Loading bookings…</p>

  return (
    <div className="bookings">
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
      {bookings.length === 0 ? (
        <p className="muted">No one has booked this cohort yet.</p>
      ) : (
        <div className="table-wrap">
          <table>
            <caption className="visually-hidden">Bookings for {cohortName}</caption>
            <thead>
              <tr>
                <th>Learner</th>
                <th>Live sessions attended</th>
                {certificate && (
                  <>
                    <th>Assessment</th>
                    <th>Certificate</th>
                  </>
                )}
              </tr>
            </thead>
            <tbody>
              {bookings.map((b) => {
                const issued = b.certificateIssuedOn !== null
                const sessionsLeft = b.liveSessions - b.sessionsAttended
                return (
                  <tr key={b.id}>
                    <td>
                      <strong>{b.learnerName}</strong>
                      <br />
                      <span className="muted">{b.learnerEmail}</span>
                    </td>
                    <td>
                      <div className="session-ticks">
                        {sessions.map((s) => (
                          <label key={s.id} className="toggle" title={sessionTime(s.startsAt)}>
                            <input
                              type="checkbox"
                              checked={b.attendedSessions.includes(s.number)}
                              disabled={issued}
                              aria-label={`${b.learnerName} attended session ${s.number}`}
                              onChange={(e) => change(() => api.markPresent(s.id, b.id, e.target.checked))}
                            />
                            {s.number}
                          </label>
                        ))}
                        <span className="muted">
                          {b.sessionsAttended} of {b.liveSessions}
                        </span>
                      </div>
                    </td>
                    {certificate && (
                      <>
                        <td>
                          <label className="toggle">
                            <input
                              type="checkbox"
                              checked={b.assessmentPassed}
                              disabled={issued}
                              aria-label={`${b.learnerName} passed the assessment`}
                              onChange={() => change(() => api.recordAssessment(b.id, !b.assessmentPassed))}
                            />{' '}
                            Passed
                          </label>
                        </td>
                        <td>
                          {issued ? (
                            <span className="badge badge-ok">
                              Issued {b.certificateIssuedOn}, valid until {b.certificateExpiresOn}
                            </span>
                          ) : (
                            <span className="muted">
                              {[
                                sessionsLeft > 0 && `${sessionsLeft} ${sessionsLeft === 1 ? 'session' : 'sessions'}`,
                                !b.assessmentPassed && 'the assessment',
                              ]
                                .filter(Boolean)
                                .join(' and ')}{' '}
                              to go
                            </span>
                          )}
                        </td>
                      </>
                    )}
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
      {certificate && (
        <p className="fine">
          The certificate is issued when every live session is attended and the assessment is passed. It lasts a year,
          and once issued it cannot be taken back here.
        </p>
      )}
    </div>
  )
}

function CourseForm({
  initial,
  submitLabel,
  onSave,
  onCancel,
}: {
  initial?: AdminCourse
  submitLabel: string
  onSave: (c: CourseInput) => Promise<boolean>
  onCancel?: () => void
}) {
  const [track, setTrack] = useState<CourseTrack>(initial?.track ?? 'PROFESSIONAL')
  const [title, setTitle] = useState(initial?.title ?? '')
  const [description, setDescription] = useState(initial?.description ?? '')
  const [liveSessions, setLiveSessions] = useState(String(initial?.liveSessions ?? 6))
  const [sessionMinutes, setSessionMinutes] = useState(String(initial?.sessionMinutes ?? 60))
  const [price, setPrice] = useState(initial ? pounds(initial.pricePence) : '')
  const [renewal, setRenewal] = useState(pounds(initial?.renewalPence ?? null))
  const [certificate, setCertificate] = useState(initial?.awardsCertificate ?? true)

  async function submit(e: FormEvent) {
    e.preventDefault()
    const saved = await onSave({
      track,
      title: title.trim(),
      description: description.trim(),
      liveSessions: Number(liveSessions),
      sessionMinutes: Number(sessionMinutes),
      pricePence: pence(price),
      renewalPence: renewal.trim() === '' ? null : pence(renewal),
      // Parent courses never award a certificate.
      awardsCertificate: track === 'PROFESSIONAL' && certificate,
    })
    if (saved && !initial) {
      setTitle('')
      setDescription('')
      setPrice('')
      setRenewal('')
    }
  }

  return (
    <form className="inline-form" onSubmit={submit}>
      <label>
        For
        {/* The track decides who may book, so it is fixed once the course exists. */}
        <select value={track} onChange={(e) => setTrack(e.target.value as CourseTrack)} disabled={!!initial}>
          {TRACKS.map(([key, label]) => (
            <option key={key} value={key}>
              {label}
            </option>
          ))}
        </select>
      </label>
      <label>
        Course title
        <input value={title} onChange={(e) => setTitle(e.target.value)} maxLength={200} required />
      </label>
      <label className="form-wide">
        Description
        <textarea value={description} onChange={(e) => setDescription(e.target.value)} maxLength={1000} rows={3} required />
      </label>
      <label>
        Live sessions
        <input type="number" min={1} max={52} value={liveSessions} onChange={(e) => setLiveSessions(e.target.value)} required />
      </label>
      <label>
        Minutes per session
        <input
          type="number"
          min={5}
          max={480}
          value={sessionMinutes}
          onChange={(e) => setSessionMinutes(e.target.value)}
          required
        />
      </label>
      <label>
        Price (£)
        <input type="number" min={0} step={0.01} value={price} onChange={(e) => setPrice(e.target.value)} required />
      </label>
      <label>
        Yearly renewal (£, optional)
        <input type="number" min={0} step={0.01} value={renewal} onChange={(e) => setRenewal(e.target.value)} />
      </label>
      {track === 'PROFESSIONAL' && (
        <label className="toggle">
          <input type="checkbox" checked={certificate} onChange={(e) => setCertificate(e.target.checked)} /> Awards a
          practitioner certificate
        </label>
      )}
      <div className="actions form-wide">
        {onCancel && (
          <button type="button" className="btn" onClick={onCancel}>
            Cancel
          </button>
        )}
        <button className="btn btn-primary">{submitLabel}</button>
      </div>
    </form>
  )
}

function CohortForm({
  initial,
  submitLabel,
  onSave,
  onCancel,
}: {
  initial?: CohortInput & { booked: number }
  submitLabel: string
  onSave: (c: CohortInput) => Promise<boolean>
  onCancel?: () => void
}) {
  const today = new Date().toISOString().slice(0, 10)
  const [name, setName] = useState(initial?.name ?? '')
  const [startsOn, setStartsOn] = useState(initial?.startsOn ?? '')
  const [startTime, setStartTime] = useState(initial?.startTime?.slice(0, 5) ?? '19:00')
  const [timeSlot, setTimeSlot] = useState(initial?.timeSlot ?? '')
  const [capacity, setCapacity] = useState(String(initial?.capacity ?? 30))
  const [trainerName, setTrainerName] = useState(initial?.trainerName ?? '')
  const [recordingAgreed, setRecordingAgreed] = useState(initial?.recordingAgreed ?? false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    const saved = await onSave({
      name: name.trim(),
      startsOn,
      startTime,
      timeSlot: timeSlot.trim(),
      capacity: Number(capacity),
      trainerName: trainerName.trim(),
      recordingAgreed,
    })
    if (saved && !initial) {
      setName('')
      setStartsOn('')
      setStartTime('19:00')
      setTimeSlot('')
      setTrainerName('')
      setRecordingAgreed(false)
    }
  }

  return (
    <form className="inline-form" onSubmit={submit}>
      <label>
        Cohort name
        <input value={name} onChange={(e) => setName(e.target.value)} maxLength={100} required />
      </label>
      <label>
        Starts
        <input
          type="date"
          value={startsOn}
          min={initial ? undefined : today}
          onChange={(e) => setStartsOn(e.target.value)}
          required
        />
      </label>
      <label>
        Start time (UK)
        <input type="time" value={startTime} onChange={(e) => setStartTime(e.target.value)} required />
      </label>
      <label>
        Day and time
        <input
          value={timeSlot}
          onChange={(e) => setTimeSlot(e.target.value)}
          maxLength={100}
          placeholder="Tuesdays 7:00 pm UK"
          required
        />
      </label>
      <label>
        Places
        <input
          type="number"
          min={Math.max(1, initial?.booked ?? 0)}
          max={1000}
          value={capacity}
          onChange={(e) => setCapacity(e.target.value)}
          required
        />
      </label>
      <label>
        Trainer
        <input value={trainerName} onChange={(e) => setTrainerName(e.target.value)} maxLength={200} required />
      </label>
      <label className="toggle">
        <input type="checkbox" checked={recordingAgreed} onChange={(e) => setRecordingAgreed(e.target.checked)} />{' '}
        Sessions are recorded
      </label>
      <div className="actions form-wide">
        {onCancel && (
          <button type="button" className="btn" onClick={onCancel}>
            Cancel
          </button>
        )}
        <button className="btn btn-primary">{submitLabel}</button>
      </div>
    </form>
  )
}
