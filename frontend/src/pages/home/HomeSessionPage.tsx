import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api, type HomeToday } from '../../api'
import RateExercises from '../../components/RateExercises'
import Skeleton from '../../components/Skeleton'
import StageBadge from '../../components/StageBadge'
import { reviewName } from '../../homeProgramme'

/**
 * Today's home session: a few of Pat's exercises from the child's current module, one at a time. Afterwards the
 * parent rates any exercise done for the first time. With review, the end-of-module (or colour) ratings instead.
 */
export default function HomeSessionPage({ review = false }: { review?: boolean }) {
  const { childId } = useParams()
  const id = Number(childId)
  // Loaded once: the session must not change under the parent while they do it.
  const [today, setToday] = useState<HomeToday | null>(null)
  const [step, setStep] = useState(0)
  const [phase, setPhase] = useState<'doing' | 'rating' | 'done'>(review ? 'rating' : 'doing')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api
      .homeToday(id)
      .then(setToday)
      .catch((e) => setError(e.message))
  }, [id])

  if (!today) return error ? <p className="error">{error}</p> : <Skeleton />
  const back = (
    <p className="muted">
      <Link to="/home">At home</Link> / <Link to={`/home/child/${id}`}>{today.name}</Link>
    </p>
  )
  const firstTime = today.exercises.filter((e) => today.firstTime.includes(e.id))

  if (phase === 'done' || (review && !today.review)) {
    return (
      <>
        {back}
        <section className="card home-today">
          <h1>{review ? 'Thank you' : `Well done, ${today.name}!`}</h1>
          <p>
            {review
              ? `You can see how ${today.name} is getting on on their page.`
              : `That is session ${Math.min(today.sessionInModule, today.sessionsPerModule)} of ${today.sessionsPerModule} in module ${today.module}.`}
          </p>
          <Link to={`/home/child/${id}`} className="btn btn-primary">
            Back to {today.name}
          </Link>
        </section>
      </>
    )
  }

  if (phase === 'rating') {
    const r = review ? today.review! : null
    return (
      <>
        {back}
        <h1>{r ? r.label : 'How did it go?'}</h1>
        <RateExercises
          childId={id}
          point={r ? r.point : 'START'}
          exercises={r ? r.exercises : firstTime}
          levels={today.levels}
          intro={
            r
              ? `${today.name} has finished ${reviewName(r.point, today.stageName)}. Do each of these once more and choose how it went.`
              : `This was the first time ${today.name} did ${firstTime.length === 1 ? 'this one' : 'these'}. How did it go?`
          }
          onDone={() => setPhase('done')}
        />
      </>
    )
  }

  async function finish() {
    setBusy(true)
    setError(null)
    try {
      await api.recordHomeSession(id)
      setPhase(today!.canRate && firstTime.length > 0 ? 'rating' : 'done')
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not save')
    } finally {
      setBusy(false)
    }
  }

  const exercise = today.exercises[step]
  const last = step === today.exercises.length - 1

  return (
    <>
      {back}
      <div className="page-head">
        <h1>
          Module {today.module} · session {today.sessionInModule}
        </h1>
        <StageBadge stage={today.stage} label={today.stageName} />
      </div>
      <p className="muted">
        Exercise {step + 1} of {today.exercises.length}
      </p>
      <progress className="home-progress" value={step + 1} max={today.exercises.length} />
      <section className="card home-exercise">
        <h2>{exercise.title}</h2>
        <p className="pre-line">{exercise.instructions}</p>
      </section>
      <p className="muted">Video coming soon. Read each step aloud and do it together.</p>
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
      <div className="row-end">
        {step > 0 && (
          <button className="btn" onClick={() => setStep(step - 1)}>
            Back
          </button>
        )}
        {last ? (
          <button className="btn btn-primary" onClick={finish} disabled={busy}>
            {busy ? 'Saving…' : 'We did it'}
          </button>
        ) : (
          <button className="btn btn-primary" onClick={() => setStep(step + 1)}>
            Next exercise
          </button>
        )}
      </div>
    </>
  )
}
