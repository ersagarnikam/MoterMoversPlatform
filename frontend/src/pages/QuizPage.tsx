import { useEffect, useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api, type QuizPaper, type QuizResult } from '../api'

/**
 * Taking a quiz between live sessions. The server marks it and says which questions to look at again, without
 * giving the answers away; it can be taken again until passed (and after).
 */
export default function QuizPage() {
  const { enrolmentId, quizId } = useParams()
  const [paper, setPaper] = useState<QuizPaper | null>(null)
  const [answers, setAnswers] = useState<(number | null)[]>([])
  const [result, setResult] = useState<QuizResult | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [sending, setSending] = useState(false)

  useEffect(() => {
    api
      .quizPaper(Number(enrolmentId), Number(quizId))
      .then((p) => {
        setPaper(p)
        setAnswers(p.questions.map(() => null))
      })
      .catch((e) => setError(e.message))
  }, [enrolmentId, quizId])

  async function submit(e: FormEvent) {
    e.preventDefault()
    if (answers.some((a) => a === null)) {
      setError('Answer every question, then send your answers.')
      return
    }
    setError(null)
    setSending(true)
    try {
      setResult(await api.submitQuiz(Number(enrolmentId), Number(quizId), answers as number[]))
      window.scrollTo(0, 0)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not send your answers')
    } finally {
      setSending(false)
    }
  }

  function again() {
    setResult(null)
    setAnswers(paper?.questions.map(() => null) ?? [])
  }

  if (!paper) return error ? <p className="error">{error}</p> : <p className="muted">Loading…</p>

  return (
    <>
      <div className="page-head">
        <h1>{paper.title}</h1>
        <Link className="fine-link" to="/training">
          Back to training
        </Link>
      </div>
      {result && (
        <section className={result.passed ? 'banner-teal' : 'banner-warn'} role="status">
          <p>
            <strong>
              {result.passed ? 'Passed' : 'Not passed yet'}: {result.scorePercent}%
            </strong>{' '}
            ({result.correct} of {result.questions} right; the pass mark is {paper.passPercent}%).
          </p>
          {result.wrong.length > 0 && (
            <p>Look again at question{result.wrong.length > 1 ? 's' : ''} {result.wrong.join(', ')}.</p>
          )}
          {result.certificateIssued && (
            <p>
              That completes your course: <Link to={`/training/certificate/${enrolmentId}`}>see your certificate</Link>.
            </p>
          )}
          <div className="actions">
            {!result.passed && (
              <button className="btn btn-primary" onClick={again}>
                Try again
              </button>
            )}
            <Link className="btn" to="/training">
              Back to training
            </Link>
          </div>
        </section>
      )}
      {!result && (
        <form className="card" onSubmit={submit}>
          <p className="muted">
            {paper.questions.length} questions · pass mark {paper.passPercent}%. You can take it again as often as you
            like.
          </p>
          {paper.questions.map((q, i) => (
            <fieldset key={q.number} className="quiz-question">
              <legend>
                {q.number}. {q.text}
              </legend>
              {q.options.map((option, o) => (
                <label key={option}>
                  <input
                    type="radio"
                    name={`q${q.number}`}
                    checked={answers[i] === o}
                    onChange={() => setAnswers((all) => all.map((a, j) => (j === i ? o : a)))}
                  />
                  {option}
                </label>
              ))}
            </fieldset>
          ))}
          {error && (
            <p className="error" role="alert">
              {error}
            </p>
          )}
          <div className="actions">
            <button className="btn btn-primary" disabled={sending}>
              Send answers
            </button>
          </div>
        </form>
      )}
    </>
  )
}
