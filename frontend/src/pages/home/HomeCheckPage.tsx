import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api, type CheckAnswer, type CheckResult, type ColourStage, type HomeCheck } from '../../api'
import NotADiagnosis from '../../components/NotADiagnosis'
import Skeleton from '../../components/Skeleton'
import StageBadge from '../../components/StageBadge'
import { CHECK_ANSWERS, ageLabel } from '../../homeProgramme'
import { STAGES, stageInfo } from '../../stages'

/**
 * The starting check: movements from Pat's charts that children usually manage around this age. The parent answers
 * Yes, Not yet or Not sure for each, and the check suggests a colour to start at. Done again every few months.
 */
export default function HomeCheckPage() {
  const { childId } = useParams()
  const id = Number(childId)
  const [check, setCheck] = useState<HomeCheck | null>(null)
  const [answers, setAnswers] = useState<Record<number, CheckAnswer>>({})
  const [result, setResult] = useState<CheckResult | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api
      .homeCheck(id)
      .then((c) => {
        setCheck(c)
        // Movements already secure start as Yes.
        setAnswers(Object.fromEntries(c.questions.filter((q) => q.secure).map((q) => [q.milestoneId, 'YES'])))
      })
      .catch((e) => setError(e.message))
  }, [id])

  if (!check) return error ? <p className="error">{error}</p> : <Skeleton />
  if (result) return <Result childId={id} name={check.name} result={result} />

  const answered = Object.keys(answers).length
  const charts = [...new Set(check.questions.map((q) => q.chartLabel))]

  async function submit() {
    setBusy(true)
    setError(null)
    try {
      setResult(
        await api.submitHomeCheck(
          id,
          Object.entries(answers).map(([milestoneId, answer]) => ({ milestoneId: Number(milestoneId), answer })),
        ),
      )
      window.scrollTo(0, 0)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not save')
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <p className="muted">
        <Link to="/home">At home</Link> / <Link to={`/home/child/${id}`}>{check.name}</Link> / Check
      </p>
      <h1>Can {check.name} do these?</h1>
      <p>
        These are movements children often manage by {ageLabel(check.ageMonths)}. Answer from what you have seen. If you
        are not sure, try it together, or choose Not sure.
      </p>
      <NotADiagnosis text={check.banner} />
      {charts.map((chart) => (
        <section key={chart} className="card">
          <h2>{chart === 'Fine motor' ? 'Hands and fingers (fine motor)' : 'Whole body (gross motor)'}</h2>
          <ul className="check-list">
            {check.questions
              .filter((q) => q.chartLabel === chart)
              .map((q) => (
                <li key={q.milestoneId}>
                  <div>
                    <p>{q.description}</p>
                    <p className="muted">Usually by {q.band}</p>
                  </div>
                  <div className="chips" role="group" aria-label={q.description}>
                    {CHECK_ANSWERS.map((a) => (
                      <button
                        key={a.value}
                        type="button"
                        className={`chip ${answers[q.milestoneId] === a.value ? 'chip-on' : ''}`}
                        aria-pressed={answers[q.milestoneId] === a.value}
                        onClick={() => setAnswers((all) => ({ ...all, [q.milestoneId]: a.value }))}
                      >
                        {a.label}
                      </button>
                    ))}
                  </div>
                </li>
              ))}
          </ul>
        </section>
      ))}
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
      <div className="row-end">
        <button className="btn btn-primary" onClick={submit} disabled={busy || answered === 0}>
          {busy ? 'Saving…' : 'See where to start'}
        </button>
        <span className="muted">
          {answered} of {check.questions.length} answered. You can leave some out.
        </span>
      </div>
    </>
  )
}

/** The suggested colour, with the choice to start there or pick another. */
function Result({ childId, name, result }: { childId: number; name: string; result: CheckResult }) {
  const navigate = useNavigate()
  const [stage, setStage] = useState<ColourStage>(result.suggested)
  const [choosing, setChoosing] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function start() {
    setBusy(true)
    try {
      await api.setHomeStage(childId, stage)
      navigate(`/home/child/${childId}`)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not save')
      setBusy(false)
    }
  }

  return (
    <>
      <h1>Where to start</h1>
      <section className="card">
        <p className="muted">Suggested for {name}</p>
        <StageBadge stage={result.suggested} label={result.suggestedName} />
        <p>{result.explanation}</p>
        <p className="muted">
          {result.yes} yes · {result.notYet} not yet · {result.notSure} not sure
        </p>
      </section>
      <p>Each colour builds on the one before, and there is no rush. Every few months we will suggest the check again.</p>
      {choosing && (
        <div className="chips" role="group" aria-label="Choose a colour">
          {STAGES.map((s) => (
            <button
              key={s.key}
              type="button"
              className={`chip ${stage === s.key ? 'chip-on' : ''}`}
              aria-pressed={stage === s.key}
              onClick={() => setStage(s.key)}
            >
              {s.label}
            </button>
          ))}
        </div>
      )}
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
      <div className="row-end">
        <button className="btn btn-primary" onClick={start} disabled={busy}>
          Start with {stageInfo(stage)?.label ?? 'this colour'}
        </button>
        {!choosing && (
          <button className="btn" onClick={() => setChoosing(true)}>
            Choose another colour
          </button>
        )}
      </div>
    </>
  )
}
