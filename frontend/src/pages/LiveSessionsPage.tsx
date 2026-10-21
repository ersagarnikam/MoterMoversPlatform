import { useEffect, useState } from 'react'
import { api, type LiveSession } from '../api'
import { sessionTime } from '../live'

/** The Motor Movers team's upcoming live course sessions, to start on Zoom and keep the register. */
export default function LiveSessionsPage() {
  const [sessions, setSessions] = useState<LiveSession[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api
      .liveSessions()
      .then(setSessions)
      .catch((e) => setError(e.message))
  }, [])

  if (error) return <p className="error">{error}</p>
  if (!sessions) return <p className="muted">Loading…</p>

  return (
    <>
      <h1>Live sessions</h1>
      <p className="muted">
        Start a session to open its Zoom meeting here, with the register beside it. Learners booked on the cohort can
        join from 15 minutes before the start.
      </p>
      {sessions.length === 0 ? (
        <p className="muted">No live sessions coming up. Open a cohort on the Courses page.</p>
      ) : (
        <ul className="live-list">
          {sessions.map((s) => (
            <li key={s.id} className="card">
              <div>
                <strong>
                  {s.courseTitle} · {s.cohortName}
                </strong>
                <p className="muted">
                  Session {s.number} of {s.of} · {sessionTime(s.startsAt)} · {s.durationMinutes} minutes · led by{' '}
                  {s.trainerName}
                </p>
              </div>
              {/* A full page load: only the session page allows Zoom's scripts. */}
              <a className="btn btn-primary" href={`/live/${s.id}`}>
                Start session
              </a>
            </li>
          ))}
        </ul>
      )}
    </>
  )
}
