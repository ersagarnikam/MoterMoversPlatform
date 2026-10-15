import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { api, type Today } from '../api'

/**
 * The class player. The video fills the screen and can be cast to the whiteboard; the side list
 * ticks through the session plan. Until filmed sessions are uploaded, a timed placeholder runs.
 */
export default function PlayerPage() {
  const { classId } = useParams()
  const navigate = useNavigate()
  const [today, setToday] = useState<Today | null>(null)
  const [elapsed, setElapsed] = useState(0)
  const [playing, setPlaying] = useState(false)
  const [absent, setAbsent] = useState<Set<number>>(new Set())
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api.today(Number(classId)).then(setToday).catch((e) => setError(e.message))
  }, [classId])

  useEffect(() => {
    if (!playing) return
    const t = setInterval(() => setElapsed((s) => s + 1), 1000)
    return () => clearInterval(t)
  }, [playing])

  if (error) return <p className="error">{error}</p>
  if (!today?.nextSession) return <p className="muted">Loading…</p>
  const session = today.nextSession
  const total = session.durationMinutes * 60
  const perStep = total / session.activities.length
  const step = Math.min(session.activities.length - 1, Math.floor(elapsed / perStep))
  const finished = elapsed >= total

  function toggleAbsent(id: number) {
    setAbsent((prev) => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  async function complete() {
    setSaving(true)
    try {
      await api.completeSession(today!.schoolClass.id, session.id, [...absent])
      navigate(`/class/${today!.schoolClass.id}/today`)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not save')
      setSaving(false)
    }
  }

  const mmss = (s: number) => `${Math.floor(s / 60)}:${String(Math.floor(s % 60)).padStart(2, '0')}`

  return (
    <>
      <p className="eyebrow">{session.label}</p>
      <h1>{session.title}</h1>
      <div className="player">
        <div className="player-video">
          {session.videoUrl ? (
            <video src={session.videoUrl} controls onPlay={() => setPlaying(true)} onPause={() => setPlaying(false)} />
          ) : (
            <button className="player-placeholder" onClick={() => setPlaying((p) => !p)}>
              <span className="play">{playing ? '❚❚' : '▶'}</span>
              <span>{session.activities[step]}</span>
              <span className="muted-on-dark">
                {mmss(Math.min(elapsed, total))} / {mmss(total)}
              </span>
            </button>
          )}
          <div className="progress">
            <span style={{ width: `${Math.min(100, (elapsed / total) * 100)}%` }} />
          </div>
        </div>
        <aside className="card player-plan">
          <h3>Session plan</h3>
          <ol className="plan">
            {session.activities.map((a, i) => (
              <li key={a} className={i < step || finished ? 'ticked' : i === step ? 'current' : ''}>
                {a}
              </li>
            ))}
          </ol>
          <button className="btn" onClick={() => setElapsed(total)}>
            Skip to end
          </button>
        </aside>
      </div>

      <section className="card">
        <h3>Who took part</h3>
        <p className="muted">Tap a child to mark them absent today.</p>
        <div className="roster">
          {today.roster.map((c) => (
            <button
              key={c.id}
              className={`roster-child ${absent.has(c.id) ? 'absent' : ''}`}
              onClick={() => toggleAbsent(c.id)}
              aria-pressed={absent.has(c.id)}
            >
              <span className="avatar">{c.name.replace(/\./g, '').slice(0, 2)}</span>
              <span>{absent.has(c.id) ? 'Absent' : c.name}</span>
            </button>
          ))}
        </div>
        <div className="actions">
          <button className="btn btn-primary btn-big" disabled={saving} onClick={complete}>
            {saving ? 'Saving…' : `Complete session · ${today.roster.length - absent.size} took part`}
          </button>
        </div>
      </section>
    </>
  )
}
