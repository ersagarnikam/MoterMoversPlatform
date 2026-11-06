import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, type Induction } from '../../api'
import Skeleton from '../../components/Skeleton'
import { Lessons } from '../TrainingPage'

/**
 * The short parent induction, done before the first home session (each carer does their own). Sessions open once the
 * last lesson is done; the API refuses a session until then.
 */
export default function HomeInductionPage() {
  const [induction, setInduction] = useState<Induction | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api
      .homeInduction()
      .then(setInduction)
      .catch((e) => setError(e.message))
  }, [])

  async function complete(lessonId: number) {
    try {
      setInduction(await api.completeHomeInductionLesson(lessonId))
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not save')
    }
  }

  if (!induction) return error ? <p className="error">{error}</p> : <Skeleton />
  return (
    <>
      <p className="muted">
        <Link to="/home">At home</Link> / Parent induction
      </p>
      <h1>Before your first session</h1>
      {induction.completedOn ? (
        <p className="done-note">
          You have finished the induction. <Link to="/home">Back to At home</Link>
        </p>
      ) : induction.lessons.length === 0 ? (
        <p>The induction is being prepared. Sessions open once it is ready.</p>
      ) : (
        <p className="muted">
          {induction.lessonsDone} of {induction.lessons.length} parts done · {induction.totalMinutes} minutes in all.
          Open each part, read it, and mark it as done. Sessions open after the last one.
        </p>
      )}
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
      <Lessons lessons={induction.lessons} onComplete={complete} />
    </>
  )
}
