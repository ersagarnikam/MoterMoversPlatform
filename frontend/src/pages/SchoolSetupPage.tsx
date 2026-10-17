import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { useParams } from 'react-router-dom'
import {
  api,
  type ClassInput,
  type ColourStage,
  type Role,
  type SchoolSettings,
  type SchoolSetup,
  type SetupChild,
  type SetupClass,
} from '../api'
import { useAuth } from '../auth'
import { STAGES } from '../stages'

const today = () => new Date().toISOString().slice(0, 10)

/** "A.B." or "A.B., Amy Brown" per line. Blank lines are skipped. */
function parseChildren(text: string) {
  return text
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line) => {
      const [initials, ...rest] = line.split(',')
      const fullName = rest.join(',').trim()
      return { initials: initials.trim(), fullName: fullName || null }
    })
}

/** Heads set up their school here: privacy settings, staff accounts, classes and the children in each. */
export default function SchoolSetupPage() {
  const { schoolId: param } = useParams()
  const { user } = useAuth()
  const schoolId = Number(param ?? user?.schoolId)
  const [setup, setSetup] = useState<SchoolSetup | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [openClass, setOpenClass] = useState<number | null>(null)

  const load = useCallback(() => {
    api.schoolSetup(schoolId).then(setSetup).catch((e) => setError(e.message))
  }, [schoolId])

  useEffect(load, [load])

  async function run(action: () => Promise<unknown>, done?: string) {
    setError(null)
    setNotice(null)
    try {
      await action()
      if (done) setNotice(done)
      load()
      return true
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Something went wrong')
      return false
    }
  }

  if (!setup) return error ? <p className="error">{error}</p> : <p className="muted">Loading…</p>
  const teachers = setup.staff.filter((s) => s.enabled)

  return (
    <>
      <p className="eyebrow">{setup.schoolName}</p>
      <h1>School setup</h1>
      {error && <p className="error">{error}</p>}
      {notice && <p className="banner-teal">{notice}</p>}

      <SettingsCard
        settings={setup.settings}
        onSave={(s) => run(() => api.saveSchoolSettings(schoolId, s), 'Settings saved')}
      />

      <section className="card">
        <h3>Staff</h3>
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Name</th>
                <th>Email</th>
                <th>Role</th>
                <th>Induction</th>
                <th>Account</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {setup.staff.map((s) => (
                <tr key={s.id}>
                  <td>{s.displayName}</td>
                  <td>{s.email}</td>
                  <td>{s.role === 'HEAD' ? 'Head / SENCo' : 'Teacher'}</td>
                  <td>{s.inductionCompletedOn ? `Done ${s.inductionCompletedOn}` : 'Not yet'}</td>
                  <td>{s.enabled ? 'On' : 'Switched off'}</td>
                  <td className="row-actions">
                    {s.enabled && (
                      <button className="btn" onClick={() => run(() => api.resendInvite(schoolId, s.id), `Sign-in link sent to ${s.email}`)}>
                        Send sign-in link
                      </button>
                    )}
                    {s.id !== user?.id && (
                      <button className="btn" onClick={() => run(() => api.setStaffEnabled(schoolId, s.id, !s.enabled))}>
                        {s.enabled ? 'Switch off' : 'Switch on'}
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <AddStaffForm onAdd={(s) => run(() => api.addStaff(schoolId, s), `Account created. ${s.email} has been emailed a link to choose a password.`)} />
      </section>

      <section className="card">
        <h3>Classes</h3>
        <div className="list">
          {setup.classes.map((c) => (
            <ClassRow
              key={c.id}
              schoolClass={c}
              teachers={teachers}
              classes={setup.classes}
              open={openClass === c.id}
              onToggle={() => setOpenClass(openClass === c.id ? null : c.id)}
              onSave={(input) => run(() => api.updateClass(schoolId, c.id, input), 'Class saved')}
              schoolId={schoolId}
              onChanged={load}
              onError={setError}
            />
          ))}
          {setup.classes.length === 0 && <p className="muted">No classes yet.</p>}
        </div>
        <ClassForm teachers={teachers} submitLabel="Add class" onSubmit={(input) => run(() => api.addClass(schoolId, input), 'Class added')} />
      </section>
    </>
  )
}

function SettingsCard({ settings, onSave }: { settings: SchoolSettings; onSave: (s: SchoolSettings) => void }) {
  const toggle = (key: keyof SchoolSettings) => onSave({ ...settings, [key]: !settings[key] })
  return (
    <section className="card">
      <h3>Privacy</h3>
      <div className="stack">
        <label className="toggle">
          <input type="checkbox" checked={settings.fullNamesEnabled} onChange={() => toggle('fullNamesEnabled')} /> Show
          children’s full names to staff (otherwise initials and year group only)
        </label>
        <label className="toggle">
          <input type="checkbox" checked={settings.videoEnabled} onChange={() => toggle('videoEnabled')} /> Allow video of
          children, for children whose families have agreed
        </label>
        <label className="toggle">
          <input type="checkbox" checked={settings.trustCanSeeChildren} onChange={() => toggle('trustCanSeeChildren')} /> Let
          the trust lead see individual children (otherwise summaries only)
        </label>
      </div>
    </section>
  )
}

function AddStaffForm({ onAdd }: { onAdd: (s: { email: string; displayName: string; role: Role }) => Promise<boolean> }) {
  const [displayName, setDisplayName] = useState('')
  const [email, setEmail] = useState('')
  const [role, setRole] = useState<Role>('TEACHER')

  async function submit(e: FormEvent) {
    e.preventDefault()
    if (await onAdd({ email, displayName, role })) {
      setDisplayName('')
      setEmail('')
    }
  }

  return (
    <form className="inline-form" onSubmit={submit}>
      <label>
        Name
        <input value={displayName} onChange={(e) => setDisplayName(e.target.value)} maxLength={100} required />
      </label>
      <label>
        Email
        <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} maxLength={200} required />
      </label>
      <label>
        Role
        <select value={role} onChange={(e) => setRole(e.target.value as Role)}>
          <option value="TEACHER">Teacher</option>
          <option value="HEAD">Head / SENCo</option>
        </select>
      </label>
      <button className="btn btn-primary">Add and email a sign-in link</button>
    </form>
  )
}

function ClassForm({
  initial,
  teachers,
  submitLabel,
  onSubmit,
}: {
  initial?: SetupClass
  teachers: { id: number; displayName: string }[]
  submitLabel: string
  onSubmit: (input: ClassInput) => Promise<boolean>
}) {
  const [name, setName] = useState(initial?.name ?? '')
  const [yearGroup, setYearGroup] = useState(initial?.yearGroup ?? '')
  const [stage, setStage] = useState<ColourStage>(initial?.stage ?? 'RED')
  const [teacherId, setTeacherId] = useState<number | null>(initial?.teacherId ?? null)

  async function submit(e: FormEvent) {
    e.preventDefault()
    if ((await onSubmit({ name, yearGroup, stage, teacherId })) && !initial) {
      setName('')
      setYearGroup('')
    }
  }

  return (
    <form className="inline-form" onSubmit={submit}>
      <label>
        Class name
        <input value={name} onChange={(e) => setName(e.target.value)} maxLength={100} required />
      </label>
      <label>
        Year group
        <input value={yearGroup} onChange={(e) => setYearGroup(e.target.value)} maxLength={30} placeholder="Reception" required />
      </label>
      <label>
        Stage
        <select value={stage} onChange={(e) => setStage(e.target.value as ColourStage)}>
          {STAGES.map((s, i) => (
            <option key={s.key} value={s.key}>
              {i + 1} · {s.label}
            </option>
          ))}
        </select>
      </label>
      <label>
        Teacher
        <select value={teacherId ?? ''} onChange={(e) => setTeacherId(e.target.value ? Number(e.target.value) : null)}>
          <option value="">Nobody yet</option>
          {teachers.map((t) => (
            <option key={t.id} value={t.id}>
              {t.displayName}
            </option>
          ))}
        </select>
      </label>
      <button className="btn btn-primary">{submitLabel}</button>
    </form>
  )
}

function ClassRow({
  schoolClass: c,
  teachers,
  classes,
  open,
  onToggle,
  onSave,
  schoolId,
  onChanged,
  onError,
}: {
  schoolClass: SetupClass
  teachers: { id: number; displayName: string }[]
  classes: SetupClass[]
  open: boolean
  onToggle: () => void
  onSave: (input: ClassInput) => Promise<boolean>
  schoolId: number
  onChanged: () => void
  onError: (message: string) => void
}) {
  const [children, setChildren] = useState<SetupChild[] | null>(null)
  const [text, setText] = useState('')

  const loadChildren = useCallback(() => {
    api.classChildren(schoolId, c.id).then(setChildren).catch((e) => onError(e.message))
  }, [schoolId, c.id, onError])

  useEffect(() => {
    if (open) loadChildren()
  }, [open, loadChildren])

  async function act(action: () => Promise<unknown>) {
    try {
      await action()
      loadChildren()
      onChanged()
    } catch (e) {
      onError(e instanceof Error ? e.message : 'Something went wrong')
    }
  }

  return (
    <div className="setup-class">
      <div className="section-head">
        <span>
          <strong>{c.name}</strong>{' '}
          <span className="muted">
            · {c.yearGroup} · Stage {STAGES.findIndex((s) => s.key === c.stage) + 1} · {c.teacherName ?? 'no teacher'} ·{' '}
            {c.children} children
          </span>
        </span>
        <button className="btn" onClick={onToggle} aria-expanded={open}>
          {open ? 'Close' : 'Edit class and children'}
        </button>
      </div>
      {open && (
        <>
          <ClassForm initial={c} teachers={teachers} submitLabel="Save class" onSubmit={onSave} />
          <h4>Children</h4>
          {children?.length === 0 && <p className="muted">No children in this class yet.</p>}
          <ul className="setup-children">
            {children?.map((k) => (
              <li key={k.id}>
                <span>
                  {k.initials}
                  {k.fullName ? ` · ${k.fullName}` : ''}
                </span>
                <span className="row-actions">
                  <select
                    aria-label={`Move ${k.initials} to another class`}
                    value=""
                    onChange={(e) => e.target.value && act(() => api.moveChild(schoolId, k.id, Number(e.target.value)))}
                  >
                    <option value="">Move to…</option>
                    {classes
                      .filter((o) => o.id !== c.id)
                      .map((o) => (
                        <option key={o.id} value={o.id}>
                          {o.name}
                        </option>
                      ))}
                  </select>
                  <button
                    className="btn"
                    onClick={() => {
                      if (confirm(`Mark ${k.initials} as having left today? Their record is deleted automatically later.`)) {
                        act(() => api.markChildLeft(schoolId, k.id, today()))
                      }
                    }}
                  >
                    Left the school
                  </button>
                </span>
              </li>
            ))}
          </ul>
          <form
            className="stack"
            onSubmit={(e) => {
              e.preventDefault()
              const list = parseChildren(text)
              if (list.length) act(() => api.addChildren(schoolId, c.id, list)).then(() => setText(''))
            }}
          >
            <label>
              Add children, one per line: initials, then a full name only if your school shows full names
              <textarea rows={4} value={text} onChange={(e) => setText(e.target.value)} placeholder={'A.B.\nC.D., Cara Dunn'} />
            </label>
            <div>
              <button className="btn btn-primary">Add children</button>
            </div>
          </form>
        </>
      )}
    </div>
  )
}
