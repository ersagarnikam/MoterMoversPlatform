import { useEffect, useState } from 'react'
import { api, type ParentLinkRow, type SchoolDashboard } from '../api'
import { useAuth } from '../auth'
import { pct } from '../stages'

function download(filename: string, body: string) {
  const url = URL.createObjectURL(new Blob([body], { type: 'text/csv' }))
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}

/** Heads see which classes keep it up. */
export default function HeadDashboardPage() {
  const { user } = useAuth()
  const [data, setData] = useState<SchoolDashboard | null>(null)
  const [error, setError] = useState<string | null>(null)
  const schoolId = user?.schoolId

  useEffect(() => {
    if (schoolId) api.schoolDashboard(schoolId).then(setData).catch((e) => setError(e.message))
  }, [schoolId])

  if (!schoolId) return <p className="muted">This account is not linked to a school.</p>
  if (error) return <p className="error">{error}</p>
  if (!data) return <p className="muted">Loading…</p>

  const exportCsv = async (name: 'governor-summary' | 'research', filename: string) => {
    try {
      download(filename, await api.exportCsv(schoolId, name))
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Export failed')
    }
  }

  return (
    <>
      <div className="page-head">
        <h1>{data.schoolName} · Progress</h1>
        <span className="muted">Head and SENCo view</span>
      </div>
      <div className="kpis">
        <div className="card kpi">
          <span className="muted">Children screened</span>
          <strong>
            {data.childrenScreened} / {data.childrenTotal}
          </strong>
        </div>
        <div className="card kpi">
          <span className="muted">Moved up a stage</span>
          <strong>{pct(data.movedUpRate)}</strong>
        </div>
        <div className="card kpi">
          <span className="muted">Daily participation</span>
          <strong>{pct(data.dailyParticipation)}</strong>
        </div>
        <div className="card kpi">
          <span className="muted">Classes active</span>
          <strong>
            {data.classesActive} / {data.classesTotal}
          </strong>
        </div>
      </div>
      <div className="dash-grid">
        <section className="card">
          <h3>Participation by class, last {data.windowDays} school days</h3>
          <div className="area-bars">
            {data.classes.map((c) => (
              <div key={c.classId} className="area-row">
                <span className={c.flagged ? 'strong' : ''}>{c.name}</span>
                <span className={`bar ${c.flagged ? 'bar-flag' : ''}`}>
                  <span style={{ width: `${c.participation * 100}%` }} />
                </span>
                <span className={c.flagged ? 'strong' : 'muted'}>
                  {pct(c.participation)}
                  {c.flagged && ` · below ${pct(data.flagThreshold)}, check in`}
                </span>
              </div>
            ))}
          </div>
        </section>
        <section className="card exports">
          <h3>Exports</h3>
          <button className="btn" onClick={() => exportCsv('governor-summary', 'governor-summary.csv')}>
            Governor summary (CSV)
          </button>
          <button className="btn" onClick={() => exportCsv('research', 'anonymised-research.csv')}>
            Anonymised research data
          </button>
          <button className="btn" disabled title="Planned">
            Inspection evidence (PDF) · coming soon
          </button>
        </section>
      </div>
      <ParentLinks schoolId={schoolId} />
    </>
  )
}

/** Who can see which child in the parent app. Teachers issue codes from a child's profile. */
function ParentLinks({ schoolId }: { schoolId: number }) {
  const [links, setLinks] = useState<ParentLinkRow[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api.parentLinks(schoolId).then(setLinks).catch((e) => setError(e.message))
  }, [schoolId])

  async function remove(link: ParentLinkRow) {
    if (!window.confirm(`Remove ${link.parentName}'s access to ${link.childName}?`)) return
    try {
      await api.removeParentLink(schoolId, link.linkId)
      setLinks((all) => all?.filter((l) => l.linkId !== link.linkId) ?? null)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not remove')
    }
  }

  return (
    <section className="card parent-links">
      <h3>Parent app links</h3>
      <p className="muted">
        Parents and carers who can see a child's stage and progress. Each view is in the record access log.
      </p>
      {error && <p className="error">{error}</p>}
      {links && links.length === 0 && <p className="muted">No parents linked yet.</p>}
      {links && links.length > 0 && (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Child</th>
                <th>Class</th>
                <th>Parent or carer</th>
                <th>Linked</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {links.map((l) => (
                <tr key={l.linkId}>
                  <td>{l.childName}</td>
                  <td>{l.className}</td>
                  <td>
                    {l.parentName} <span className="muted">· {l.parentEmail}</span>
                  </td>
                  <td>{l.linkedAt.slice(0, 10)}</td>
                  <td>
                    <button className="btn" onClick={() => remove(l)}>
                      Remove
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}
