import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import {
  api,
  type ColourStage,
  type FamilyActivities,
  type HomeProgress,
  type HomeToday,
} from '../../api'
import { useConfirm, useToast } from '../../components/Feedback'
import NotADiagnosis from '../../components/NotADiagnosis'
import Skeleton from '../../components/Skeleton'
import StageBadge from '../../components/StageBadge'
import { CHART_NAMES, ageLabel, reviewName, toMonths } from '../../homeProgramme'
import { STAGES } from '../../stages'

/**
 * One child on Motor Movers at Home: today's session (Pat's exercises, or the colour's home activities while her grid
 * for that colour is still to come), how they are getting on in the parent's words, and their settings.
 */
export default function HomeChildPage() {
  const { childId } = useParams()
  const id = Number(childId)
  const [progress, setProgress] = useState<HomeProgress | null>(null)
  const [today, setToday] = useState<HomeToday | null>(null)
  const [activities, setActivities] = useState<FamilyActivities | null>(null)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    try {
      const p = await api.homeProgress(id)
      setProgress(p)
      if (p.child.stage) {
        const t = await api.homeToday(id)
        setToday(t)
        setActivities(t.hasExercises ? null : await api.familyActivities(id))
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not load')
    }
  }, [id])

  useEffect(() => {
    void load()
  }, [load])

  if (!progress) return error ? <p className="error">{error}</p> : <Skeleton />
  const { child } = progress

  return (
    <>
      <p className="muted">
        <Link to="/home">At home</Link> / {child.name}
      </p>
      <div className="page-head">
        <div>
          <h1>{child.name}</h1>
          <p className="muted">
            {ageLabel(child.ageMonths)} · {progress.sessionsDone === 1 ? '1 session' : `${progress.sessionsDone} sessions`}{' '}
            done
          </p>
        </div>
        {child.stage && <StageBadge stage={child.stage} label={child.stageName ?? undefined} />}
      </div>
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}

      {!child.stage ? (
        <section className="card home-today">
          <h2>Start with a short check</h2>
          <p>
            A few questions about what {child.name} can do now. It takes about five minutes and suggests the colour to
            start at.
          </p>
          <Link to={`/home/child/${id}/check`} className="btn btn-primary">
            Start the check
          </Link>
        </section>
      ) : (
        <>
          {child.checkDue && (
            <p className="banner-info">
              It has been a few months: time to do {child.name}’s check again.{' '}
              <Link to={`/home/child/${id}/check`}>Start the check</Link>
            </p>
          )}
          {today?.review && (
            <p className="banner-teal">
              {child.name} has finished {reviewName(today.review.point, today.stageName)}.{' '}
              <Link to={`/home/child/${id}/review`}>Rate {today.review.exercises.length} exercises</Link>
            </p>
          )}
          <div className="today-grid">
            {today && today.hasExercises && <SessionCard today={today} />}
            {activities && <ActivityCard data={activities} childId={id} onDone={load} />}
            {today && (
              <Counter
                grid={activities?.counterGrid ?? today.counterGrid}
                done={activities?.daysDone ?? today.daysDone}
                days={activities?.counterDays ?? today.counterDays}
              />
            )}
          </div>
        </>
      )}

      <NotADiagnosis text={progress.banner} />
      {progress.support.show && progress.support.message && <p className="banner-info">{progress.support.message}</p>}

      <h2>Movements</h2>
      <div className="home-charts">
        {progress.charts.map((c) => (
          <section key={c.chart} className="card">
            <div className="row-end section-head">
              <h3>{CHART_NAMES[c.chart]}</h3>
              <span className="muted">
                {c.secure} of {c.total}
              </span>
            </div>
            {c.recentlySecure.length > 0 && (
              <>
                <p className="muted">New in the last few months:</p>
                <ul className="site-list">
                  {c.recentlySecure.slice(0, 5).map((m) => (
                    <li key={m}>{m}</li>
                  ))}
                </ul>
                {c.recentlySecure.length > 5 && <p className="muted">and {c.recentlySecure.length - 5} more</p>}
              </>
            )}
          </section>
        ))}
      </div>

      {progress.exercises.length > 0 && (
        <section className="card">
          <h3>Exercises{progress.stageName ? ` · ${progress.stageName}` : ''}</h3>
          <p className="muted">How {child.name} is getting on with each one, from the first time to the latest.</p>
          <ul className="home-rows">
            {progress.exercises.map((e) => (
              <li key={e.exerciseId}>
                <span>{e.title}</span>
                <span className="muted nowrap">
                  {e.first === e.latest ? e.latest : `${e.first} → ${e.latest}`}
                  {e.improved ? ' ✓' : ''}
                </span>
              </li>
            ))}
          </ul>
        </section>
      )}

      <h2>Settings</h2>
      <Settings progress={progress} onChange={load} />
    </>
  )
}

/** Today's session with Pat's exercises (or, once done, the next one). */
function SessionCard({ today }: { today: HomeToday }) {
  return (
    <section className="card home-today">
      <p className="muted">
        {today.colourComplete
          ? `All ${today.modules} modules done: keep practising`
          : `Module ${today.module} of ${today.modules} · session ${today.sessionInModule} of ${today.sessionsPerModule}`}{' '}
        · about 10 minutes
      </p>
      <h2>{today.doneToday ? 'Done today' : `Today’s session for ${today.name}`}</h2>
      {/* Once today's is done, the exercises shown are the next session's. */}
      <p>
        {today.doneToday ? 'Tomorrow: ' : ''}
        {today.exercises.map((e) => e.title).join(' · ')}
      </p>
      {today.doneToday && <p className="done-note">Done for today. Nice work, see you tomorrow.</p>}
      <Link to={`/home/child/${today.childId}/session`} className={`btn ${today.doneToday ? '' : 'btn-primary'}`}>
        {today.doneToday ? 'Do it again' : 'Start'}
      </Link>
    </section>
  )
}

/** A colour without Pat's grid yet: the colour's home activities, as for a parent whose child is at school. */
function ActivityCard({ data, childId, onDone }: { data: FamilyActivities; childId: number; onDone: () => void }) {
  const toast = useToast()
  const [busy, setBusy] = useState(false)
  if (!data.next) {
    return (
      <section className="card home-today">
        <p className="muted">Home activities for {data.stageName} are on their way.</p>
      </section>
    )
  }
  const next = data.next

  async function done() {
    setBusy(true)
    try {
      await api.recordFamilyActivity(childId, next.id)
      toast('Saved. Well done!')
      onDone()
    } catch (e) {
      toast(e instanceof Error ? e.message : 'Could not save', 'error')
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="card home-today">
      <p className="muted">
        {data.doneToday ? 'Done today' : 'Today’s activity'} · {next.durationMinutes} minutes
      </p>
      <h2>{next.title}</h2>
      <p className="muted">{next.focus}</p>
      <ol className="home-steps">
        {next.steps.map((s) => (
          <li key={s}>{s}</li>
        ))}
      </ol>
      <button className={`btn ${data.doneToday ? '' : 'btn-primary'}`} onClick={done} disabled={busy}>
        {busy ? 'Saving…' : data.doneToday ? 'We did another' : 'We did it'}
      </button>
    </section>
  )
}

function Counter({ grid, done, days }: { grid: boolean[]; done: number; days: number }) {
  return (
    <section className="card counter-card">
      <p className="muted">Last {days} days</p>
      <p className="big-number">
        {done} / {days}
      </p>
      <div className="counter-grid" role="img" aria-label={`Sessions on ${done} of the last ${days} days`}>
        {grid.map((on, i) => (
          <span key={i} className={on ? 'on' : ''} />
        ))}
      </div>
      <p className="muted">
        {done >= days / 1.5
          ? 'Brilliant. Little and often is exactly right.'
          : done >= days / 3
            ? 'Good going. Most days is the aim.'
            : 'Five minutes most days makes the biggest difference.'}
      </p>
    </section>
  )
}

/** Name and age, colour, linking a school, and deleting the child. */
function Settings({ progress, onChange }: { progress: HomeProgress; onChange: () => void }) {
  const { child } = progress
  const navigate = useNavigate()
  const toast = useToast()
  const confirm = useConfirm()
  const [name, setName] = useState(child.name)
  const [years, setYears] = useState(String(Math.floor(child.ageMonths / 12)))
  const [months, setMonths] = useState(String(child.ageMonths % 12))
  const [code, setCode] = useState('')
  const [agreed, setAgreed] = useState(false)
  const [busy, setBusy] = useState(false)
  const age = toMonths(years, months)

  async function run(action: () => Promise<unknown>, ok: string) {
    setBusy(true)
    try {
      await action()
      toast(ok)
      onChange()
    } catch (e) {
      toast(e instanceof Error ? e.message : 'Something went wrong', 'error')
    } finally {
      setBusy(false)
    }
  }

  function saveDetails(e: FormEvent) {
    e.preventDefault()
    if (age !== null) void run(() => api.updateHomeChild(child.id, name.trim(), age), 'Saved')
  }

  async function linkSchool(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    try {
      const linked = await api.joinSchool(child.id, code)
      toast(`${child.name} is now linked to ${linked.schoolName}. Their progress is in the parent app.`)
      navigate('/home')
    } catch (err) {
      toast(err instanceof Error ? err.message : 'Could not link', 'error')
      setBusy(false)
    }
  }

  async function remove() {
    const yes = await confirm({
      title: `Delete ${child.name}?`,
      body: 'This deletes everything recorded and cannot be undone. Anyone you share with loses it too.',
      confirmLabel: `Delete ${child.name}`,
      danger: true,
    })
    if (!yes) return
    setBusy(true)
    try {
      await api.deleteHomeChild(child.id)
      toast(`${child.name} deleted`)
      navigate('/home')
    } catch (e) {
      toast(e instanceof Error ? e.message : 'Could not delete', 'error')
      setBusy(false)
    }
  }

  return (
    <>
      <form className="card" onSubmit={saveDetails}>
        <h3>Name and age</h3>
        <div className="inline-form">
          <label>
            First name or nickname
            <input value={name} onChange={(e) => setName(e.target.value)} maxLength={30} required />
          </label>
          <label>
            Age: years
            <input inputMode="numeric" value={years} onChange={(e) => setYears(e.target.value)} maxLength={2} />
          </label>
          <label>
            and months
            <input inputMode="numeric" value={months} onChange={(e) => setMonths(e.target.value)} maxLength={2} />
          </label>
          <button className="btn" disabled={busy || !name.trim() || age === null}>
            Save
          </button>
        </div>
      </form>

      {child.stage && (
        <section className="card">
          <h3>Colour</h3>
          <p className="muted">
            Each colour builds on the one before. Change it if the exercises feel too easy or too hard, or{' '}
            <Link to={`/home/child/${child.id}/check`}>do the check again</Link>.
          </p>
          <div className="chips">
            {STAGES.map((s) => (
              <button
                key={s.key}
                type="button"
                className={`chip ${child.stage === s.key ? 'chip-on' : ''}`}
                aria-pressed={child.stage === s.key}
                disabled={busy}
                onClick={() => run(() => api.setHomeStage(child.id, s.key as ColourStage), `${s.label} from now on`)}
              >
                {s.label}
              </button>
            ))}
          </div>
        </section>
      )}

      <form className="card" onSubmit={linkSchool}>
        <h3>{child.name}’s school has joined Motor Movers</h3>
        <p className="muted">Ask the school for a parent code. It looks like ABCD-EFGH and works once. When you link:</p>
        <ul className="site-list">
          <li>The class teacher sees the movements and ratings you recorded at home.</li>
          <li>The school’s record takes over: the teacher does the assessing from now on.</li>
          <li>You see {child.name}’s progress in the parent app, as other parents do.</li>
        </ul>
        <div className="inline-form">
          <label>
            Parent code from the school
            <input
              value={code}
              onChange={(e) => setCode(e.target.value)}
              placeholder="ABCD-EFGH"
              maxLength={12}
              autoComplete="off"
            />
          </label>
          <label className="toggle">
            <input type="checkbox" checked={agreed} onChange={() => setAgreed(!agreed)} /> I agree to share what I
            recorded at home
          </label>
          <button className="btn" disabled={busy || !agreed || code.replace(/[^a-z0-9]/gi, '').length !== 8}>
            Link the school
          </button>
        </div>
      </form>

      <section className="card">
        <h3>Delete {child.name}</h3>
        <p className="muted">Deletes {child.name} and everything recorded for them.</p>
        <button className="btn btn-danger" onClick={remove} disabled={busy}>
          Delete {child.name}
        </button>
      </section>
    </>
  )
}
