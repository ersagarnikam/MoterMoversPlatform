import { useEffect, useState, type FormEvent } from 'react'
import { api, type AdminCourse, type AdminQuiz, type QuizInput } from '../api'
import { useConfirm, useToast } from './Feedback'

/**
 * Short quizzes between a course's live sessions. Learners take each once its session has started (or they attended
 * it); with quizzes on a course, every one must be passed before the certificate. The wording check runs on save.
 */
export default function CourseQuizzes({ course }: { course: AdminCourse }) {
  const [quizzes, setQuizzes] = useState<AdminQuiz[] | null>(null)
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<number | 'new' | null>(null)
  const [error, setError] = useState<string | null>(null)
  const setNotice = useToast()

  useEffect(() => {
    api
      .courseQuizzes(course.id)
      .then(setQuizzes)
      .catch((e) => setError(e.message))
  }, [course.id])

  async function save(action: () => Promise<AdminQuiz>) {
    setError(null)
    setNotice(null)
    try {
      const saved = await action()
      setQuizzes((all) => {
        const rest = (all ?? []).filter((q) => q.id !== saved.id)
        return [...rest, saved].sort((a, b) => a.afterSession - b.afterSession || a.id - b.id)
      })
      setEditing(null)
      setNotice(`${saved.title} saved`)
      return true
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Something went wrong')
      return false
    }
  }

  const confirm = useConfirm()

  async function remove(q: AdminQuiz) {
    if (!(await confirm({ title: `Delete the quiz "${q.title}"?`, body: 'Nobody has taken it yet, so nothing is lost.', confirmLabel: 'Delete quiz', danger: true }))) return
    setError(null)
    try {
      await api.deleteQuiz(q.id)
      setQuizzes((all) => all?.filter((x) => x.id !== q.id) ?? null)
      setNotice(`${q.title} deleted`)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Something went wrong')
    }
  }

  if (!quizzes) return error ? <p className="error">{error}</p> : null
  return (
    <details className="add-item" open={open} onToggle={(e) => setOpen(e.currentTarget.open)}>
      <summary className="btn">Quizzes ({quizzes.length})</summary>
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
      {quizzes.length === 0 && <p className="muted">No quizzes. Add one to check learning between sessions.</p>}
      <ul className="quiz-list">
        {quizzes.map((q) =>
          editing === q.id ? (
            <li key={q.id}>
              <QuizForm
                initial={q}
                liveSessions={course.liveSessions}
                onCancel={() => setEditing(null)}
                onSave={(input) => save(() => api.updateQuiz(q.id, input))}
              />
            </li>
          ) : (
            <li key={q.id}>
              <span>
                After session {q.afterSession}: <strong>{q.title}</strong>
                <span className="muted">
                  {' '}
                  · {q.questions.length} questions · pass mark {q.passPercent}% · taken {q.attempts} times
                </span>
              </span>
              <span className="actions">
                <button className="btn btn-small" onClick={() => setEditing(q.id)}>
                  Edit
                </button>
                {q.attempts === 0 && (
                  <button className="btn btn-small" onClick={() => remove(q)}>
                    Delete
                  </button>
                )}
              </span>
            </li>
          ),
        )}
      </ul>
      {editing === 'new' ? (
        <QuizForm
          liveSessions={course.liveSessions}
          onCancel={() => setEditing(null)}
          onSave={(input) => save(() => api.addQuiz(course.id, input))}
        />
      ) : (
        <button className="btn" onClick={() => setEditing('new')}>
          Add a quiz
        </button>
      )}
    </details>
  )
}

type DraftQuestion = { text: string; options: string; correctOption: number }

const lines = (options: string) =>
  options
    .split('\n')
    .map((o) => o.trim())
    .filter(Boolean)

function QuizForm({
  initial,
  liveSessions,
  onSave,
  onCancel,
}: {
  initial?: QuizInput
  liveSessions: number
  onSave: (q: QuizInput) => Promise<boolean>
  onCancel: () => void
}) {
  const [afterSession, setAfterSession] = useState(String(initial?.afterSession ?? 1))
  const [title, setTitle] = useState(initial?.title ?? '')
  const [passPercent, setPassPercent] = useState(String(initial?.passPercent ?? 80))
  const [questions, setQuestions] = useState<DraftQuestion[]>(
    initial?.questions.map((q) => ({ text: q.text, options: q.options.join('\n'), correctOption: q.correctOption })) ?? [
      { text: '', options: '', correctOption: 0 },
    ],
  )

  const change = (i: number, patch: Partial<DraftQuestion>) =>
    setQuestions((all) => all.map((q, j) => (j === i ? { ...q, ...patch } : q)))

  async function submit(e: FormEvent) {
    e.preventDefault()
    await onSave({
      afterSession: Number(afterSession),
      title: title.trim(),
      passPercent: Number(passPercent),
      questions: questions.map((q) => ({ text: q.text.trim(), options: lines(q.options), correctOption: q.correctOption })),
    })
  }

  return (
    <form className="quiz-editor" onSubmit={submit}>
      <div className="inline-form">
        <label>
          Quiz title
          <input value={title} onChange={(e) => setTitle(e.target.value)} maxLength={200} required />
        </label>
        <label>
          After live session
          <select value={afterSession} onChange={(e) => setAfterSession(e.target.value)}>
            {Array.from({ length: liveSessions }, (_, i) => (
              <option key={i + 1} value={i + 1}>
                {i + 1}
              </option>
            ))}
          </select>
        </label>
        <label>
          Pass mark (%)
          <input
            type="number"
            min={1}
            max={100}
            value={passPercent}
            onChange={(e) => setPassPercent(e.target.value)}
            required
          />
        </label>
      </div>
      {questions.map((q, i) => (
        <div key={i} className="question inline-form">
          <label className="form-wide">
            Question {i + 1}
            <input value={q.text} onChange={(e) => change(i, { text: e.target.value })} maxLength={500} required />
          </label>
          <label className="form-wide">
            Answers for question {i + 1}, one per line
            <textarea rows={3} value={q.options} onChange={(e) => change(i, { options: e.target.value })} required />
          </label>
          <label>
            Right answer to question {i + 1}
            <select value={q.correctOption} onChange={(e) => change(i, { correctOption: Number(e.target.value) })}>
              {lines(q.options).map((o, n) => (
                <option key={n} value={n}>
                  {o}
                </option>
              ))}
            </select>
          </label>
          {questions.length > 1 && (
            <button
              type="button"
              className="btn btn-small"
              onClick={() => setQuestions((all) => all.filter((_, j) => j !== i))}
            >
              Remove question {i + 1}
            </button>
          )}
        </div>
      ))}
      <div className="actions">
        <button
          type="button"
          className="btn"
          onClick={() => setQuestions((all) => [...all, { text: '', options: '', correctOption: 0 }])}
        >
          Add a question
        </button>
        <button type="button" className="btn" onClick={onCancel}>
          Cancel
        </button>
        <button className="btn btn-primary">Save quiz</button>
      </div>
    </form>
  )
}
