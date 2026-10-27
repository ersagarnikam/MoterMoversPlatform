import { useEffect, useState } from 'react'
import { api, type LiveSession } from '../api'
import { useAuth } from '../auth'
import Skeleton from '../components/Skeleton'
import { relativeDay } from '../dates'
import { sessionTime, startsIn } from '../live'

/**
 * Upcoming live course sessions, to start on Zoom and keep the register: every session for the Motor Movers team,
 * and a trainer's own cohorts for a trainer. The next one stands out, with how soon it starts and how many are booked.
 */
export default function LiveSessionsPage() {
  const { user } = useAuth()
  const [sessions, setSessions] = useState<LiveSession[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [now, setNow] = useState(() => new Date())

  useEffect(() => {
    api
      .liveSessions()
      .then(setSessions)
      .catch((e) => setError(e.message))
    // Keeps "Starts in 10 minutes" true while the page stays open.
    const tick = setInterval(() => setNow(new Date()), 30_000)
    return () => clearInterval(tick)
  }, [])

  if (error) return <p className="error">{error}</p>
  if (!sessions) return <Skeleton lines={4} />

  return (
    <>
      <h1>Live sessions</h1>
      <p className="muted">
        Start a session to open its Zoom meeting here, with the register beside it. Learners booked on the cohort can
        join from 15 minutes before the start.
      </p>
      {sessions.length === 0 ? (
        <p className="muted">
          {user?.role === 'ADMIN'
            ? 'No live sessions coming up. Open a cohort on the Courses page.'
            : 'No live sessions coming up. The team will let you know when a cohort is given to you.'}
        </p>
      ) : (
        <ul className="live-list">
          {sessions.map((s, i) => {
            const soon = startsIn(s.startsAt, s.durationMinutes, now)
            const dayLabel = relativeDay(s.startsAt, now)
            return (
              <li key={s.id} className={i === 0 ? 'card live-next' : 'card'}>
                <div>
                  <div className="live-labels">
                    {i === 0 && <span className="badge badge-ok">Next</span>}
                    {dayLabel && <span className="badge">{dayLabel}</span>}
                    {soon && <span className={soon === 'On now' ? 'badge badge-live' : 'badge badge-warn'}>{soon}</span>}
                  </div>
                  <strong>
                    {s.courseTitle} · {s.cohortName}
                  </strong>
                  <p className="muted">
                    Session {s.number} of {s.of} · {sessionTime(s.startsAt)} · {s.durationMinutes} minutes · led by{' '}
                    {s.trainerName}
                    {s.booked !== null && ` · ${s.booked} booked`}
                  </p>
                </div>
                {/* A full page load: only the session page allows Zoom's scripts. */}
                <a className="btn btn-primary" href={`/live/${s.id}`}>
                  Start session
                </a>
              </li>
            )
          })}
        </ul>
      )}
    </>
  )
}
