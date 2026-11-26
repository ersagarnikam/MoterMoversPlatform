import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api, type CourseLesson, type CoursePlayer, type QuizSummary } from '../api'
import Film from '../components/Film'
import { useToast } from '../components/Feedback'
import Skeleton from '../components/Skeleton'
import { canMarkDone, markDoneHint, nextLesson } from '../courseLessons'
import { day } from '../dates'

/**
 * A learner's place on a recorded course (go-live task 89): its lessons in order. Each lesson is a film, with any
 * text and slides; it can be marked done once its film has played to the end, which opens the next one. When every
 * lesson is done the course's test opens, and passing it gives the certificate.
 */
export default function CourseLessonsPage() {
  const { enrolmentId } = useParams()
  const id = Number(enrolmentId)
  const [player, setPlayer] = useState<CoursePlayer | null>(null)
  const [quizzes, setQuizzes] = useState<QuizSummary[]>([])
  const [error, setError] = useState<string | null>(null)
  /** The lessons whose film has played to the end in this visit. */
  const [ended, setEnded] = useState<Set<number>>(new Set())
  const [showing, setShowing] = useState<number | null>(null)
  const [saving, setSaving] = useState(false)
  const toast = useToast()

  const load = useCallback(() => {
    api
      .coursePlayer(id)
      .then((p) => {
        setPlayer(p)
        setShowing((current) => current ?? nextLesson(p.lessons)?.id ?? null)
      })
      .catch((e) => setError(e.message))
    // A failure here should not hide the lessons, so it is left silent.
    api.myQuizzes(id).then(setQuizzes).catch(() => setQuizzes([]))
  }, [id])

  useEffect(load, [load])

  async function complete(lesson: CourseLesson) {
    setSaving(true)
    setError(null)
    try {
      const next = await api.completeCourseLesson(id, lesson.id)
      setPlayer(next)
      setShowing(nextLesson(next.lessons)?.id ?? null)
      toast(next.quizzesOpen ? 'Every lesson is done' : `Lesson ${lesson.position} is done`)
      api.myQuizzes(id).then(setQuizzes).catch(() => setQuizzes([]))
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not save')
    } finally {
      setSaving(false)
    }
  }

  if (!player) return error ? <p className="error">{error}</p> : <Skeleton />
  const allDone = player.lessons.length > 0 && player.lessonsDone === player.lessons.length

  return (
    <>
      <p className="eyebrow">
        <Link to="/training">Training</Link> / {player.courseTitle}
      </p>
      <div className="page-head">
        <h1>{player.courseTitle}</h1>
        <span className="muted">
          {player.lessonsDone} of {player.lessons.length} lessons done · about {player.totalMinutes} minutes in all
        </span>
      </div>
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
      {player.certificateIssuedOn && (
        <p className="banner-teal">
          You completed this course on {day(player.certificateIssuedOn)}.{' '}
          <Link to={`/training/certificate/${player.enrolmentId}`}>View your certificate</Link>
        </p>
      )}

      <div className="cohorts">
        {player.lessons.map((l) => {
          const filmEnded = ended.has(l.id)
          const hint = markDoneHint(l, filmEnded)
          return (
            <details
              key={l.id}
              className="lesson"
              open={showing === l.id}
              onToggle={(e) => {
                if (e.currentTarget.open) setShowing(l.id)
                else if (showing === l.id) setShowing(null)
              }}
            >
              <summary>
                <span>
                  <strong>
                    {l.position}. {l.title}
                  </strong>
                  <span className="muted"> · {l.minutes} min</span>
                </span>
                {l.done ? (
                  <span className="badge badge-ok">Done</span>
                ) : l.open ? (
                  <span className="badge">Next</span>
                ) : (
                  <span className="muted">Opens after lesson {l.position - 1}</span>
                )}
              </summary>
              {l.open && showing === l.id && (
                <>
                  {l.videoUrl && (
                    <Film
                      src={l.videoUrl}
                      credit={null}
                      onEnded={() => setEnded((prev) => new Set(prev).add(l.id))}
                    />
                  )}
                  {l.paragraphs.map((p) => (
                    <p key={p}>{p}</p>
                  ))}
                  {l.slidesUrl && (
                    <p>
                      <a href={l.slidesUrl} target="_blank" rel="noreferrer">
                        Open the slides for this lesson
                      </a>
                    </p>
                  )}
                  {!l.done && (
                    <div className="actions">
                      <button
                        className="btn btn-primary"
                        disabled={saving || !canMarkDone(l, filmEnded)}
                        onClick={() => complete(l)}
                      >
                        Mark lesson {l.position} as done
                      </button>
                      {hint && <span className="hint">{hint}</span>}
                    </div>
                  )}
                </>
              )}
            </details>
          )
        })}
      </div>

      {quizzes.length > 0 && (
        <section className="card">
          <h3>{quizzes.length === 1 ? 'The test' : 'The tests'}</h3>
          {!allDone && <p className="muted">Opens once every lesson is done.</p>}
          <ul className="quiz-list">
            {quizzes.map((q) => (
              <li key={q.id}>
                <span>
                  {q.title}
                  <span className="muted">
                    {' '}
                    · {q.questions} questions · pass mark {q.passPercent}%
                  </span>
                </span>
                {q.passed ? (
                  <span className="badge badge-ok">Passed · {q.bestScore}%</span>
                ) : q.open ? (
                  <Link className="btn btn-primary" to={`/training/quiz/${id}/${q.id}`}>
                    {q.bestScore === null ? 'Take the test' : `Try again (best ${q.bestScore}%)`}
                  </Link>
                ) : (
                  <span className="muted">Not open yet</span>
                )}
              </li>
            ))}
          </ul>
        </section>
      )}
    </>
  )
}
