import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { api, type ColourStage, type NewSchool, type SchoolRow, type TrustRow } from '../api'
import StageBadge from '../components/StageBadge'
import { STAGES } from '../stages'

const PLANS = [
  ['FREE_TRIAL', 'Free trial'],
  ['EARLY_YEARS', 'Early years'],
  ['PRIMARY_CORE', 'Primary core'],
  ['FULL_PROGRAMME', 'Full programme'],
  ['TRUST', 'Trust'],
  ['FREE', 'Free'],
] as const

/** The platform team signs schools up, sets licences, and adds trusts and trust leads. */
export default function SchoolsPage() {
  const [schools, setSchools] = useState<SchoolRow[] | null>(null)
  const [trusts, setTrusts] = useState<TrustRow[]>([])
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  const load = useCallback(() => {
    Promise.all([api.schools(), api.trusts()])
      .then(([s, t]) => {
        setSchools(s)
        setTrusts(t)
      })
      .catch((e) => setError(e.message))
  }, [])

  useEffect(load, [load])

  async function run(action: () => Promise<unknown>, done: string) {
    setError(null)
    setNotice(null)
    try {
      await action()
      setNotice(done)
      load()
      return true
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Something went wrong')
      return false
    }
  }

  if (!schools) return error ? <p className="error">{error}</p> : <p className="muted">Loading…</p>

  return (
    <>
      <h1>Schools & licences</h1>
      {error && <p className="error">{error}</p>}
      {notice && <p className="banner-teal">{notice}</p>}
      <div className="card table-wrap">
        <table>
          <thead>
            <tr>
              <th>School</th>
              <th>Trust</th>
              <th>Plan</th>
              <th>Status</th>
              <th>Ends</th>
              <th>Stages</th>
              <th title="Whether staff can invite parents to the parent app">Parent app</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {schools.map((s) => (
              <tr key={s.id}>
                <td>{s.name}</td>
                <td>{s.trustName ?? '-'}</td>
                <td>{s.plan ?? '-'}</td>
                <td>{s.licenceStatus.replace('_', ' ').toLowerCase()}</td>
                <td>{s.endsOn ?? '-'}</td>
                <td className="stage-list">
                  {STAGES.filter((st) => s.stages.includes(st.key)).map((st) => (
                    <StageBadge key={st.key} stage={st.key} />
                  ))}
                </td>
                <td>
                  <label className="toggle">
                    <input
                      type="checkbox"
                      checked={s.parentLinksEnabled}
                      onChange={() =>
                        run(
                          () => api.setParentLinks(s.id, !s.parentLinksEnabled),
                          `Parent app ${s.parentLinksEnabled ? 'off' : 'on'} for ${s.name}`,
                        )
                      }
                    />{' '}
                    {s.parentLinksEnabled ? 'On' : 'Off'}
                  </label>
                </td>
                <td>
                  <Link className="btn" to={`/admin/schools/${s.id}/setup`}>
                    Set up
                  </Link>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <section className="card">
        <h3>Sign a school up</h3>
        <p className="muted">
          Then open <em>Set up</em> to add the head teacher, who adds their own staff and classes.
        </p>
        <NewSchoolForm trusts={trusts} onAdd={(s) => run(() => api.addSchool(s), `${s.name} signed up`)} />
      </section>

      <section className="card">
        <h3>Trusts</h3>
        <div className="list">
          {trusts.map((t) => (
            <TrustCard
              key={t.id}
              trust={t}
              onAddLead={(lead) =>
                run(() => api.addTrustLead(t.id, lead), `${lead.email} has been emailed a link to choose a password`)
              }
            />
          ))}
        </div>
        <NewTrustForm onAdd={(name) => run(() => api.addTrust(name), `${name} added`)} />
      </section>
    </>
  )
}

function NewSchoolForm({ trusts, onAdd }: { trusts: TrustRow[]; onAdd: (s: NewSchool) => Promise<boolean> }) {
  const today = new Date().toISOString().slice(0, 10)
  const nextYear = new Date(Date.now() + 365 * 86_400_000).toISOString().slice(0, 10)
  const [name, setName] = useState('')
  const [trustId, setTrustId] = useState<number | null>(null)
  const [plan, setPlan] = useState<string>('FREE_TRIAL')
  const [stages, setStages] = useState<ColourStage[]>(['RED', 'ORANGE', 'YELLOW'])
  const [startsOn, setStartsOn] = useState(today)
  const [endsOn, setEndsOn] = useState(nextYear)

  async function submit(e: FormEvent) {
    e.preventDefault()
    if (await onAdd({ name, trustId, plan, stages, startsOn, endsOn })) setName('')
  }

  return (
    <form onSubmit={submit}>
      <div className="inline-form">
        <label>
          School name
          <input value={name} onChange={(e) => setName(e.target.value)} maxLength={200} required />
        </label>
        <label>
          Trust
          <select value={trustId ?? ''} onChange={(e) => setTrustId(e.target.value ? Number(e.target.value) : null)}>
            <option value="">No trust</option>
            {trusts.map((t) => (
              <option key={t.id} value={t.id}>
                {t.name}
              </option>
            ))}
          </select>
        </label>
        <label>
          Plan
          <select value={plan} onChange={(e) => setPlan(e.target.value)}>
            {PLANS.map(([key, label]) => (
              <option key={key} value={key}>
                {label}
              </option>
            ))}
          </select>
        </label>
        <label>
          Starts
          <input type="date" value={startsOn} onChange={(e) => setStartsOn(e.target.value)} required />
        </label>
        <label>
          Ends
          <input type="date" value={endsOn} onChange={(e) => setEndsOn(e.target.value)} required />
        </label>
      </div>
      <fieldset className="stage-picker">
        <legend>Stages on the licence</legend>
        {STAGES.map((s) => (
          <label key={s.key} className="toggle">
            <input
              type="checkbox"
              checked={stages.includes(s.key)}
              onChange={() =>
                setStages((all) => (all.includes(s.key) ? all.filter((k) => k !== s.key) : [...all, s.key]))
              }
            />{' '}
            {s.label}
          </label>
        ))}
      </fieldset>
      <div className="actions">
        <button className="btn btn-primary" disabled={stages.length === 0}>
          Sign up school
        </button>
      </div>
    </form>
  )
}

function TrustCard({
  trust,
  onAddLead,
}: {
  trust: TrustRow
  onAddLead: (l: { email: string; displayName: string }) => Promise<boolean>
}) {
  const [email, setEmail] = useState('')
  const [displayName, setDisplayName] = useState('')
  return (
    <div className="setup-class">
      <strong>{trust.name}</strong>
      <p className="muted">
        Trust leads:{' '}
        {trust.leads.length ? trust.leads.map((l) => `${l.displayName} (${l.email})`).join(', ') : 'none yet'}
      </p>
      <form
        className="inline-form"
        onSubmit={async (e) => {
          e.preventDefault()
          if (await onAddLead({ email, displayName })) {
            setEmail('')
            setDisplayName('')
          }
        }}
      >
        <label>
          Name
          <input value={displayName} onChange={(e) => setDisplayName(e.target.value)} maxLength={100} required />
        </label>
        <label>
          Email
          <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} maxLength={200} required />
        </label>
        <button className="btn">Add trust lead</button>
      </form>
    </div>
  )
}

function NewTrustForm({ onAdd }: { onAdd: (name: string) => Promise<boolean> }) {
  const [name, setName] = useState('')
  return (
    <form
      className="inline-form"
      onSubmit={async (e) => {
        e.preventDefault()
        if (await onAdd(name)) setName('')
      }}
    >
      <label>
        New trust
        <input value={name} onChange={(e) => setName(e.target.value)} maxLength={200} required />
      </label>
      <button className="btn btn-primary">Add trust</button>
    </form>
  )
}
