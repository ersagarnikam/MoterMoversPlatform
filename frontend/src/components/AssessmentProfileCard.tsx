import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, type AssessmentProfile, type ColourStage } from '../api'
import { useAuth } from '../auth'
import { pointColumns } from '../assessment'
import { day } from '../dates'
import { useToast } from './Feedback'
import NotADiagnosis from './NotADiagnosis'
import Skeleton from './Skeleton'

/** The flag lists the first few reasons; the table shows the rest. */
const SHOWN_REASONS = 3

/**
 * Pat's two assessments on a child's profile: movements secure on the developmental charts, then her table for the
 * colour (each exercise's score at each point), the totals, the extra-support note and the summary comments.
 */
export default function AssessmentProfileCard({ childId }: { childId: number }) {
  const { user } = useAuth()
  const [stage, setStage] = useState<ColourStage | undefined>(undefined)
  const [profile, setProfile] = useState<AssessmentProfile | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api.assessmentProfile(childId, stage).then(setProfile).catch((e) => setError(e.message))
  }, [childId, stage])

  if (error) return <p className="error">{error}</p>
  if (!profile) return <Skeleton />
  const columns = pointColumns(profile.exercises)
  const canWrite = user?.role === 'TEACHER' || user?.role === 'HEAD' || user?.role === 'ADMIN'

  return (
    <div className="card">
      <div className="section-head">
        <h2>Assessment · {profile.name}</h2>
        <span className="muted">
          {profile.reference} · {profile.className}
        </span>
      </div>
      <NotADiagnosis text="Progress with the programme's movements, not a clinical test." />
      {profile.extraSupport.flagged && (
        <div className="banner-info support-note" role="note">
          <strong>{profile.extraSupport.label}.</strong> A pattern of low scores:
          <ul>
            {profile.extraSupport.reasons.slice(0, SHOWN_REASONS).map((r) => (
              <li key={r}>{r}</li>
            ))}
            {profile.extraSupport.reasons.length > SHOWN_REASONS && (
              <li>and {profile.extraSupport.reasons.length - SHOWN_REASONS} more (see the table below)</li>
            )}
          </ul>
        </div>
      )}

      <h3>Developmental charts</h3>
      <div className="kpis kpis-2">
        {profile.development.map((d) => (
          <div key={d.chart} className="kpi">
            <span className="muted">{d.label}</span>
            <strong>
              {d.secure} / {d.total}
            </strong>
            <span className="muted">
              This year: {d.thisYear.START} at the start, {d.thisYear.MIDDLE} middle, {d.thisYear.END} end
            </span>
          </div>
        ))}
      </div>
      {canWrite && (
        <p>
          <Link className="fine-link" to={`/child/${childId}/development`}>
            Open the developmental charts
          </Link>
        </p>
      )}

      <div className="section-head">
        <h3>Exercises{profile.stageName ? ` · ${profile.stageName}` : ''}</h3>
        {profile.stagesWithExercises.length > 1 && (
          <span className="chips" role="group" aria-label="Colour">
            {profile.stagesWithExercises.map((s) => (
              <button key={s} className={`chip ${profile.stage === s ? 'chip-on' : ''}`} onClick={() => setStage(s)}>
                {s.charAt(0) + s.slice(1).toLowerCase()}
              </button>
            ))}
          </span>
        )}
      </div>
      {profile.exercises.length === 0 ? (
        <p className="muted">No exercise assessment for this colour yet.</p>
      ) : (
        <div className="table-wrap" tabIndex={0} role="region" aria-label="Exercise scores">
          <table className="cards score-table">
            <thead>
              <tr>
                <th>Exercise</th>
                {columns.map((c) => (
                  <th key={c.point} className="num">
                    {c.label}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {profile.exercises.map((e) => (
                <tr key={e.exerciseId}>
                  <td>
                    {e.number}. {e.title}
                  </td>
                  {columns.map((c) => {
                    const p = e.points.find((x) => x.point === c.point)
                    return (
                      <td key={c.point} className="num" data-label={c.label}>
                        {!p ? (
                          <span className="muted" aria-label="Not assessed at this point">
                            ·
                          </span>
                        ) : p.score === null ? (
                          ''
                        ) : (
                          <span title={p.assessedOn ? day(p.assessedOn) : undefined}>{p.score}</span>
                        )}
                      </td>
                    )
                  })}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {profile.totals && (
        <p className="totals">
          Start total <strong>{profile.totals.start}</strong> / {profile.totals.max}
          <span className="muted"> ({profile.totals.startAssessed} assessed)</span> · End total{' '}
          <strong>{profile.totals.end}</strong> / {profile.totals.max}
          <span className="muted"> ({profile.totals.endAssessed} assessed)</span>
        </p>
      )}
      {profile.stage && (
        <Summary
          key={`${childId}-${profile.stage}`}
          childId={childId}
          stage={profile.stage}
          summary={profile.summary}
          canWrite={canWrite}
        />
      )}
      <p className="fine">Every view of this record is logged</p>
    </div>
  )
}

function Summary(props: {
  childId: number
  stage: ColourStage
  summary: AssessmentProfile['summary']
  canWrite: boolean
}) {
  const [personal, setPersonal] = useState(props.summary?.personalComment ?? '')
  const [academic, setAcademic] = useState(props.summary?.academicComment ?? '')
  const [busy, setBusy] = useState(false)
  const toast = useToast()

  if (!props.canWrite) {
    if (!props.summary) return null
    return (
      <>
        <h3>Summary</h3>
        <p>
          <strong>Personal, social and emotional development:</strong> {props.summary.personalComment}
        </p>
        <p>
          <strong>Academic achievement:</strong> {props.summary.academicComment}
        </p>
      </>
    )
  }

  async function save() {
    setBusy(true)
    try {
      await api.writeColourSummary(props.childId, { stage: props.stage, personalComment: personal, academicComment: academic })
      toast('Summary saved')
    } catch (e) {
      toast(e instanceof Error ? e.message : 'Could not save', 'error')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="stack">
      <h3>Summary</h3>
      <label>
        Personal, social and emotional development
        <textarea rows={3} maxLength={2000} value={personal} onChange={(e) => setPersonal(e.target.value)} />
      </label>
      <label>
        Academic achievement
        <textarea rows={3} maxLength={2000} value={academic} onChange={(e) => setAcademic(e.target.value)} />
      </label>
      <div className="actions">
        {props.summary && <span className="muted">Last saved {day(props.summary.writtenOn)}</span>}
        <button className="btn" onClick={save} disabled={busy}>
          Save summary
        </button>
      </div>
    </div>
  )
}
