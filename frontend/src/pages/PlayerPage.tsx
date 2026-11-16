import { useEffect, useMemo, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api, type Today } from '../api'
import { useToast } from '../components/Feedback'
import Film from '../components/Film'
import { rosterChild } from '../components/RosterChild'
import Skeleton from '../components/Skeleton'
import { advance, START, stepAt, ticking, type ClockEvent, type Plan, type Source } from '../playerClock'

/**
 * The class player. The video fills the screen and can be cast to the whiteboard; the side list
 * ticks through the session plan. From Green to Violet each step plays its exercise's own film, moving on when it ends;
 * otherwise the session's film (its own, or its rhyme's or module's) plays, or a timed placeholder with no film at all.
 * The rules that keep the plan and the picture together are in playerClock.ts.
 */
export default function PlayerPage() {
  const { classId } = useParams()
  const navigate = useNavigate()
  const [today, setToday] = useState<Today | null>(null)
  const [clock, setClock] = useState(START)
  const [absent, setAbsent] = useState<Set<number>>(new Set())
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  // Where the session's film had got to, so it carries on from there after a step's own film.
  const sessionFilmAt = useRef(0)
  const toast = useToast()

  useEffect(() => {
    api.today(Number(classId)).then(setToday).catch((e) => setError(e.message))
  }, [classId])

  const session = today?.nextSession ?? null
  // The module's cool-down is the last step. A rhyme colour ends in the rhyme's own words.
  const steps = useMemo(
    () =>
      session
        ? [
            ...session.steps.map((s) => ({ title: s.title, how: s.instructions, film: s.videoUrl, credit: s.filmCredit })),
            ...(session.coolDown
              ? [{ title: session.coolDown.title, how: session.coolDown.text, film: null, credit: null }]
              : []),
          ]
        : [],
    [session],
  )
  // The step's exercise film first; the cool-down and any step without one fall back to the session's film.
  const plan: Plan = useMemo(
    () => ({
      sources: steps.map((s): Source => (s.film ? 'own' : session?.videoUrl ? 'session' : 'none')),
      total: (session?.durationMinutes ?? 0) * 60,
    }),
    [steps, session],
  )
  const send = (event: ClockEvent) => setClock((c) => advance(c, event, plan))
  const counting = steps.length > 0 && ticking(clock, plan)

  useEffect(() => {
    if (!counting) return
    const t = setInterval(() => setClock((c) => advance(c, { type: 'tick' }, plan)), 1000)
    return () => clearInterval(t)
  }, [counting, plan])

  if (error) return <p className="error">{error}</p>
  if (today && !today.inductionComplete) {
    return (
      <p className="banner-warn">
        Complete the one-hour induction before running sessions. <Link to="/training">Go to the induction</Link>
      </p>
    )
  }
  if (!today) return <Skeleton />
  if (!session) {
    return (
      <p className="banner-info">
        {today.stageLicensed
          ? 'No approved sessions for this stage yet.'
          : `${today.schoolClass.stageName} is not on this school’s licence today. Please ask your head teacher.`}{' '}
        <Link to={`/class/${today.schoolClass.id}/today`}>Back to today</Link>
      </p>
    )
  }
  const { elapsed } = clock
  const total = plan.total
  const step = stepAt(elapsed, plan)
  const current = steps[step]
  const source = plan.sources[step]
  const how = current?.how
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
      await api.completeSession(today!.schoolClass.id, session!.id, [...absent])
      navigate(`/class/${today!.schoolClass.id}/today`)
    } catch (e) {
      // Not queued: the teacher sees why (for example, the licence no longer covers the colour) and can try again.
      toast(e instanceof Error ? e.message : 'Could not save', 'error')
      setSaving(false)
    }
  }

  const mmss = (s: number) => `${Math.floor(s / 60)}:${String(Math.floor(s % 60)).padStart(2, '0')}`
  const play = () => send({ type: 'play' })
  const pause = () => send({ type: 'pause' })

  return (
    <>
      <p className="eyebrow">{session.label}</p>
      <h1>{session.title}</h1>
      <div className="player">
        <div className="player-video">
          {source === 'own' ? (
            <Film
              key={`step-${step}`}
              src={current.film!}
              credit={current.credit}
              autoPlay={clock.running}
              onPlay={play}
              onPause={pause}
              onEnded={() => send({ type: 'ended' })}
            />
          ) : source === 'session' ? (
            <Film
              key="session"
              src={session.videoUrl!}
              credit={session.filmCredit}
              autoPlay={clock.running}
              startAt={sessionFilmAt.current}
              onTime={(seconds) => (sessionFilmAt.current = seconds)}
              onPlay={play}
              onPause={pause}
            />
          ) : (
            <button className="player-placeholder" onClick={() => send({ type: 'toggle' })}>
              <span className="play">{clock.running ? '❚❚' : '▶'}</span>
              <span>{current?.title}</span>
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
            {steps.map((s, i) => (
              <li key={s.title} className={i < step || finished ? 'ticked' : i === step ? 'current' : ''}>
                <button
                  type="button"
                  className="plan-step"
                  aria-current={i === step ? 'step' : undefined}
                  onClick={() => send({ type: 'go', step: i })}
                >
                  {s.title}
                </button>
              </li>
            ))}
          </ol>
          <button className="btn" onClick={() => send({ type: 'skipToEnd' })}>
            Skip to end
          </button>
        </aside>
      </div>

      {how && (
        <section className="card session-how">
          <h3>{current.title}</h3>
          {how.split('\n').map((line, i) => (
            <p key={i}>{line}</p>
          ))}
        </section>
      )}

      {session.rhyme && (
        <section className="card rhyme">
          <h3>Rhyme · {session.rhyme.title}</h3>
          {session.rhyme.audioUrl ? (
            <audio src={session.rhyme.audioUrl} controls />
          ) : (
            <p className="muted">The recording is on its way. Sing it together for now.</p>
          )}
          <p className="rhyme-words">
            {session.rhyme.lines.map((line) => (
              <span key={line}>{line}</span>
            ))}
          </p>
        </section>
      )}

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
              {...rosterChild(c, 'absent today', absent.has(c.id) ? 'Absent' : undefined).props}
            >
              {rosterChild(c, 'absent today', absent.has(c.id) ? 'Absent' : undefined).content}
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
