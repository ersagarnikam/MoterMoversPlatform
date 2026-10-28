import { useState } from 'react'
import { api, type HomeExercise, type HomeToday } from '../api'

/**
 * Rates exercises with the four levels in the parent's words ("Not yet" to "Got it"). Choosing one shows Pat's own
 * description of that level, so parents can check what it looks like. The same as the parent app's.
 */
export default function RateExercises({
  childId,
  point,
  exercises,
  levels,
  intro,
  onDone,
}: {
  childId: number
  point: string
  exercises: HomeExercise[]
  levels: HomeToday['levels']
  intro: string
  onDone: () => void
}) {
  const [scores, setScores] = useState<Record<number, number>>({})
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const done = exercises.every((e) => scores[e.id])

  async function save() {
    setBusy(true)
    setError(null)
    try {
      await api.rateExercises(childId, point, scores)
      onDone()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not save')
      setBusy(false)
    }
  }

  return (
    <>
      <p>{intro}</p>
      {exercises.map((e) => {
        const chosen = scores[e.id]
        return (
          <section key={e.id} className="card">
            <h3>{e.title}</h3>
            <div className="chips" role="group" aria-label={`How ${e.title} went`}>
              {levels.map((l) => (
                <button
                  key={l.score}
                  type="button"
                  className={`chip ${chosen === l.score ? 'chip-on' : ''}`}
                  aria-pressed={chosen === l.score}
                  onClick={() => setScores((all) => ({ ...all, [e.id]: l.score }))}
                >
                  {l.label}
                </button>
              ))}
            </div>
            {chosen ? (
              <p className="muted">{e.levels[String(chosen)] ?? 'Pat’s description for this one is on its way.'}</p>
            ) : null}
          </section>
        )
      })}
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
      <div className="row-end">
        <button className="btn btn-primary" onClick={save} disabled={!done || busy}>
          {busy ? 'Saving…' : 'Save'}
        </button>
        {!done && <span className="muted">Choose one for each exercise. Go with what you saw today.</span>}
      </div>
    </>
  )
}
