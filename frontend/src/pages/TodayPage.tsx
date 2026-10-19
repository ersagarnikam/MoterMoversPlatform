import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api, type Today } from '../api'
import StageBadge from '../components/StageBadge'
import { rosterChild } from '../components/RosterChild'

/** A teacher's day starts with one button. */
export default function TodayPage() {
  const { classId } = useParams()
  const navigate = useNavigate()
  const [today, setToday] = useState<Today | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api.today(Number(classId)).then(setToday).catch((e) => setError(e.message))
  }, [classId])

  if (error) return <p className="error">{error}</p>
  if (!today) return <p className="muted">Loading…</p>
  const { schoolClass, nextSession } = today

  return (
    <>
      <div className="page-head">
        <div>
          <p className="eyebrow">{schoolClass.schoolName}</p>
          <h1>{schoolClass.name} · Today’s session</h1>
        </div>
        <div className="tabs">
          <Link to={`/class/${schoolClass.id}/screening`} className="btn">
            Screening
          </Link>
        </div>
      </div>

      <div className="today-grid">
        <section className="card session-card">
          {!today.stageLicensed && (
            <p className="banner-info">{schoolClass.stageName} is not on this school’s licence.</p>
          )}
          {today.stageLicensed && !nextSession && <p className="muted">No approved sessions for this stage yet.</p>}
          {nextSession && (
            <>
              <p className="eyebrow">{nextSession.label}</p>
              <h2>{nextSession.title}</h2>
              <p className="muted">
                {nextSession.durationMinutes} minutes · warm up, {Math.max(0, nextSession.activities.length - 2)}{' '}
                activities, cool down
              </p>
              <div className="session-actions">
                <div className="video-thumb" aria-hidden="true">
                  <span className="play">▶</span>
                </div>
                <div className="stack">
                  {today.doneToday ? (
                    <p className="done-note">✓ Done for today</p>
                  ) : !today.inductionComplete ? (
                    <p className="banner-warn">
                      Complete the one-hour induction before running sessions.{' '}
                      <Link to="/training">Go to the induction</Link>
                    </p>
                  ) : (
                    <button className="btn btn-primary btn-big" onClick={() => navigate(`/class/${schoolClass.id}/play`)}>
                      Start session
                    </button>
                  )}
                  <details>
                    <summary className="btn">Preview activities</summary>
                    <ol className="plan">
                      {nextSession.activities.map((a) => (
                        <li key={a}>{a}</li>
                      ))}
                    </ol>
                  </details>
                </div>
              </div>
            </>
          )}
        </section>

        <section className="card counter-card">
          <p className="muted">This half-term</p>
          <p className="big-number">
            {today.sessionsDone} / {today.counterDays}
          </p>
          <p className="muted">daily sessions done</p>
          <div className="counter-grid" role="img" aria-label={`${today.sessionsDone} of the last ${today.counterDays} school days`}>
            {today.counterGrid.map((done, i) => (
              <span key={i} className={done ? 'on' : ''} />
            ))}
          </div>
        </section>
      </div>

      <section className="card">
        <div className="section-head">
          <h3>Class roster · {today.roster.length} children</h3>
          <StageBadge stage={schoolClass.stage} label={schoolClass.stageName} />
        </div>
        <div className="roster">
          {today.roster.map((c) => (
            <Link key={c.id} to={`/child/${c.id}`} className="roster-child" {...rosterChild(c, 'open readiness profile').props}>
              {rosterChild(c, 'open readiness profile').content}
            </Link>
          ))}
        </div>
        {!today.fullNamesEnabled && (
          <p className="banner-info">Initials and year group only. Full names are switched off for this school.</p>
        )}
      </section>
    </>
  )
}
