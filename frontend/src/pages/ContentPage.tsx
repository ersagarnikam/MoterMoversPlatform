import { Fragment, useEffect, useMemo, useState, type FormEvent } from 'react'
import {
  api,
  type ApprovedAll,
  type ColourStage,
  type ContentItem,
  type ContentKind,
  type FilmExercise,
  type ProgrammeFilm,
  type UploadRules,
} from '../api'
import StageBadge from '../components/StageBadge'
import { STAGES } from '../stages'
import { day } from '../dates'
import { useConfirm, useToast } from '../components/Feedback'
import { uploadFile } from '../upload'

const KINDS: { kind: ContentKind; label: string }[] = [
  { kind: 'content', label: 'Class sessions' },
  { kind: 'home-activities', label: 'Home activities' },
  { kind: 'guidance', label: 'Parent guidance' },
  { kind: 'resources', label: 'Resources' },
  { kind: 'induction', label: 'Staff induction' },
  { kind: 'parent-induction', label: 'Parent induction' },
]

/**
 * One input on a new-item form. `lines` sends a textarea as a list, one entry per line. Lengths match the
 * database columns; an empty optional field is sent as null.
 */
interface Field {
  name: string
  label: string
  type: 'text' | 'textarea' | 'number' | 'url' | 'stage' | 'resource-kind'
  required?: boolean
  max?: number
  initial?: string
  hint?: string
  lines?: boolean
  /** A link field that can take an uploaded file instead: the request field for its key, and what it accepts. */
  upload?: { keyField: string; accept: (type: string) => boolean }
}

const TITLE: Field = { name: 'title', label: 'Title', type: 'text', required: true, max: 200 }
const VIDEO: Field = {
  name: 'videoUrl',
  label: 'Video link (optional)',
  type: 'url',
  max: 500,
  hint: 'An https:// address',
  upload: { keyField: 'videoKey', accept: (type) => type.startsWith('video/') },
}
const PARAGRAPHS = 'Leave a blank line between paragraphs.'

const INDUCTION_FIELDS: Field[] = [
  { name: 'position', label: 'Part number', type: 'number', required: true, initial: '1' },
  { name: 'minutes', label: 'Minutes', type: 'number', required: true, initial: '10' },
  TITLE,
  { name: 'summary', label: 'Summary', type: 'textarea', required: true, max: 500 },
  { name: 'body', label: 'Lesson', type: 'textarea', required: true, max: 8000, hint: PARAGRAPHS },
  VIDEO,
]

const FORMS: Record<ContentKind, { heading: string; fields: Field[] }> = {
  content: {
    heading: 'Add a class session',
    fields: [
      { name: 'stage', label: 'Stage', type: 'stage', required: true, initial: 'RED' },
      { name: 'unit', label: 'Module', type: 'number', required: true, initial: '1' },
      { name: 'day', label: 'Day', type: 'number', required: true, initial: '1' },
      { name: 'durationMinutes', label: 'Minutes', type: 'number', required: true, initial: '10' },
      TITLE,
      { name: 'focus', label: 'Focus', type: 'textarea', required: true, max: 500 },
      { name: 'activities', label: 'Activities, one per line', type: 'textarea', lines: true, max: 3000 },
      VIDEO,
    ],
  },
  'home-activities': {
    heading: 'Add a home activity',
    fields: [
      { name: 'stage', label: 'Stage', type: 'stage', required: true, initial: 'RED' },
      { name: 'position', label: 'Order within the stage', type: 'number', required: true, initial: '1' },
      { name: 'durationMinutes', label: 'Minutes', type: 'number', required: true, initial: '10' },
      TITLE,
      { name: 'focus', label: 'Focus', type: 'textarea', required: true, max: 500 },
      { name: 'steps', label: 'Steps, one per line', type: 'textarea', lines: true, max: 3000 },
      VIDEO,
    ],
  },
  guidance: {
    heading: 'Add a parent guidance article',
    fields: [
      { name: 'stage', label: 'Stage (optional)', type: 'stage' },
      { name: 'minutesToRead', label: 'Minutes to read', type: 'number', required: true, initial: '3' },
      TITLE,
      { name: 'summary', label: 'Summary', type: 'textarea', required: true, max: 500 },
      { name: 'body', label: 'Article', type: 'textarea', required: true, max: 8000, hint: PARAGRAPHS },
    ],
  },
  resources: {
    heading: 'Add a resource',
    fields: [
      { name: 'kind', label: 'Kind', type: 'resource-kind', required: true, initial: 'PRINTABLE' },
      { name: 'stage', label: 'Stage (optional)', type: 'stage' },
      TITLE,
      { name: 'description', label: 'Description', type: 'textarea', required: true, max: 500 },
      {
        name: 'fileUrl',
        label: 'File or recording link (optional)',
        type: 'url',
        max: 500,
        hint: 'An https:// address',
        upload: { keyField: 'fileKey', accept: () => true },
      },
      { name: 'words', label: 'Words of a rhyme, one line per line (optional)', type: 'textarea', max: 4000 },
    ],
  },
  induction: { heading: 'Add an induction part', fields: INDUCTION_FIELDS },
  'parent-induction': { heading: 'Add a parent induction part', fields: INDUCTION_FIELDS },
}

const RESOURCE_KINDS = [
  ['PRINTABLE', 'Printable'],
  ['RHYME', 'Rhyme recording'],
  ['BOOK', 'Book'],
] as const

/** Pat's plans are about 990 sessions, so the page shows this many cards until the list is narrowed. */
const SHOWN_AT_ONCE = 100

const initialValues = (fields: Field[]): Record<string, string> =>
  Object.fromEntries(
    fields.flatMap((f): [string, string][] => [
      [f.name, f.initial ?? ''],
      ...(f.upload ? ([[f.upload.keyField, '']] as [string, string][]) : []),
    ]),
  )

/** Turns the form's text values into the request the API expects for this kind. */
function toRequest(fields: Field[], values: Record<string, string>): Record<string, unknown> {
  return Object.fromEntries(
    fields.flatMap((f): [string, unknown][] => {
      const value = values[f.name].trim()
      if (f.lines) return [[f.name, value.split('\n').map((l) => l.trim()).filter(Boolean)]]
      if (f.type === 'number') return [[f.name, Number(value)]]
      const entry: [string, unknown] = [f.name, value === '' ? null : value]
      return f.upload ? [entry, [f.upload.keyField, values[f.upload.keyField] || null]] : [entry]
    }),
  )
}

/** Choosing a file sends it straight away, so saving the form only has to name it. */
function FileChoice({
  rules,
  accept,
  onUploaded,
  onBusy,
  then = 'Save the draft to keep it.',
}: {
  rules: UploadRules
  accept: (type: string) => boolean
  onUploaded: (key: string) => void
  onBusy: (busy: boolean) => void
  /** What to do next, said once the file is uploaded. */
  then?: string
}) {
  const [percent, setPercent] = useState<number | null>(null)
  const [uploaded, setUploaded] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const setNotice = useToast()

  async function choose(file: File | undefined) {
    if (!file) return
    setError(null)
    setPercent(0)
    onBusy(true)
    try {
      onUploaded(await uploadFile(file, setPercent))
      setUploaded(file.name)
      setNotice(`“${file.name}” is uploaded. ${then}`)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not upload the file')
    } finally {
      setPercent(null)
      onBusy(false)
    }
  }

  return (
    <div className="form-wide">
      {uploaded ? (
        <p>
          Uploaded: {uploaded}{' '}
          <button
            type="button"
            className="btn"
            onClick={() => {
              setUploaded(null)
              onUploaded('')
            }}
          >
            Remove
          </button>
        </p>
      ) : (
        <label>
          Or upload a file
          <span className="hint">
            {rules.formats}, up to {rules.maxMegabytes} MB
          </span>
          <input
            type="file"
            accept={rules.types.filter(accept).join(',')}
            disabled={percent !== null}
            onChange={(e) => {
              void choose(e.target.files?.[0])
              e.target.value = ''
            }}
          />
        </label>
      )}
      {percent !== null && <progress value={percent} max={100} aria-label="Upload progress" />}
      {error && <p className="error">{error}</p>}
    </div>
  )
}

/** A new item goes in as a draft; it reaches teachers or parents only once it is approved below. */
function NewItemForm({
  kind,
  rules,
  onSave,
}: {
  kind: ContentKind
  rules: UploadRules | null
  onSave: (item: Record<string, unknown>) => Promise<boolean>
}) {
  const { heading, fields } = FORMS[kind]
  const [values, setValues] = useState(() => initialValues(fields))
  const [open, setOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [uploading, setUploading] = useState(false)
  // Bumped after a save so the file chooser starts empty too.
  const [round, setRound] = useState(0)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setSaving(true)
    const saved = await onSave(toRequest(fields, values))
    setSaving(false)
    if (saved) {
      setValues(initialValues(fields))
      setRound((r) => r + 1)
      setOpen(false)
    }
  }

  const set = (name: string, value: string) => setValues((v) => ({ ...v, [name]: value }))

  return (
    <details className="card add-item" open={open} onToggle={(e) => setOpen(e.currentTarget.open)}>
      <summary className="btn btn-primary">{heading}</summary>
      <form className="inline-form" onSubmit={submit}>
        {fields.map((f) => (
          <Fragment key={f.name}>
            <label className={f.type === 'textarea' ? 'form-wide' : undefined}>
              {f.label}
              {f.hint && <span className="hint">{f.hint}</span>}
              {f.type === 'textarea' ? (
                <textarea
                  value={values[f.name]}
                  onChange={(e) => set(f.name, e.target.value)}
                  maxLength={f.max}
                  required={f.required}
                  rows={f.max && f.max > 1000 ? 8 : 3}
                />
              ) : f.type === 'stage' ? (
                <select value={values[f.name]} onChange={(e) => set(f.name, e.target.value)} required={f.required}>
                  {!f.required && <option value="">All stages</option>}
                  {STAGES.map((s) => (
                    <option key={s.key} value={s.key}>
                      {s.label}
                    </option>
                  ))}
                </select>
              ) : f.type === 'resource-kind' ? (
                <select value={values[f.name]} onChange={(e) => set(f.name, e.target.value)}>
                  {RESOURCE_KINDS.map(([key, label]) => (
                    <option key={key} value={key}>
                      {label}
                    </option>
                  ))}
                </select>
              ) : (
                <input
                  type={f.type}
                  value={values[f.name]}
                  onChange={(e) => set(f.name, e.target.value)}
                  maxLength={f.max}
                  min={f.type === 'number' ? 1 : undefined}
                  pattern={f.type === 'url' ? 'https://.*' : undefined}
                  required={f.required}
                />
              )}
            </label>
            {f.upload && rules?.available && (
              <FileChoice
                key={round}
                rules={rules}
                accept={f.upload.accept}
                onBusy={setUploading}
                onUploaded={(key) => set(f.upload!.keyField, key)}
              />
            )}
          </Fragment>
        ))}
        <div className="actions form-wide">
          <button className="btn btn-primary" disabled={saving || uploading}>
            Save as draft
          </button>
        </div>
      </form>
    </details>
  )
}

const RHYME_COLOURS: ColourStage[] = ['RED', 'ORANGE', 'YELLOW']

/** What a film belongs to: a rhyme or module of a colour, or one exercise of its grid. */
type FilmTarget = { stage: ColourStage; unit: number } | { exerciseId: number }

/**
 * One place a film can go, with the film it has or the form to attach one. `what` names it ("Orange rhyme 4",
 * "Motor Spin") and `reach` says who will see it.
 */
function FilmSlot({
  target,
  what,
  reach,
  film,
  rules,
  onChange,
}: {
  target: FilmTarget
  what: string
  reach: string
  film: ProgrammeFilm | null
  rules: UploadRules | null
  onChange: (film: ProgrammeFilm | null) => void
}) {
  const [link, setLink] = useState('')
  const [key, setKey] = useState('')
  const [credit, setCredit] = useState('')
  const [busy, setBusy] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const setNotice = useToast()
  const confirm = useConfirm()

  async function attach(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      onChange(
        await api.attachProgrammeFilm({
          ...target,
          videoKey: key || null,
          videoUrl: key ? null : link.trim() || null,
          credit: credit.trim() || null,
        }),
      )
      setLink('')
      setKey('')
      setCredit('')
      setNotice(`The film is attached to ${what}.`)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not attach the film')
    } finally {
      setBusy(false)
    }
  }

  async function remove() {
    const sure = await confirm({
      title: `Remove the film from ${what}?`,
      body: 'It stops playing at school and at home. Attach another at any time.',
      confirmLabel: 'Remove the film',
    })
    if (!sure) return
    setBusy(true)
    setError(null)
    try {
      await api.removeProgrammeFilm(target)
      onChange(null)
      setNotice(`The film is removed from ${what}.`)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not remove the film')
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      {film ? (
        <>
          <p>
            {reach} {film.uploaded ? 'An uploaded file' : 'A link'}, attached by {film.attachedBy} on{' '}
            {day(film.attachedAt)}.
          </p>
          <p className="muted">
            Credit line: “{film.creditShown}”{film.credit ? '' : ' (the usual one)'}. To change it, remove the film and
            attach it again.
          </p>
          <div className="actions">
            <a className="btn" href={film.fileLink} target="_blank" rel="noreferrer">
              Open the film
            </a>
            <button className="btn" disabled={busy} onClick={remove}>
              Remove the film
            </button>
          </div>
        </>
      ) : (
        <form className="inline-form" onSubmit={attach}>
          <p className="muted form-wide">{reach} Paste a link or upload the file.</p>
          <label className="form-wide">
            Film link
            <span className="hint">An https:// address</span>
            <input
              type="url"
              value={link}
              onChange={(e) => setLink(e.target.value)}
              maxLength={500}
              pattern="https://.*"
              disabled={key !== ''}
            />
          </label>
          {rules?.available && (
            <FileChoice
              rules={rules}
              accept={(type) => type.startsWith('video/')}
              onBusy={setUploading}
              onUploaded={setKey}
              then="Attach it to keep it."
            />
          )}
          <label className="form-wide">
            Credit line (optional)
            <span className="hint">Shown under the film in place of the usual one</span>
            <input type="text" value={credit} onChange={(e) => setCredit(e.target.value)} maxLength={200} />
          </label>
          <div className="actions form-wide">
            <button className="btn btn-primary" disabled={busy || uploading || (!key && !link.trim())}>
              Attach the film
            </button>
          </div>
        </form>
      )}
      {error && <p className="error">{error}</p>}
    </>
  )
}

/**
 * The films of the colour being looked at. With a rhyme or module chosen, its one film, which every session of it
 * plays. From Green to Violet, also a film for each exercise of the colour's grid, which each step of a session plays as
 * its own. Sessions added later have them too.
 */
function FilmsPanel({
  stage,
  unit,
  sessions,
  rules,
}: {
  stage: ColourStage
  unit: number | null
  sessions: number
  rules: UploadRules | null
}) {
  const [films, setFilms] = useState<ProgrammeFilm[] | null>(null)
  const [exercises, setExercises] = useState<FilmExercise[]>([])
  const [error, setError] = useState<string | null>(null)
  const rhymes = RHYME_COLOURS.includes(stage)
  const colour = STAGES.find((s) => s.key === stage)?.label
  const what = `${colour} ${rhymes ? 'rhyme' : 'module'} ${unit}`

  useEffect(() => {
    let current = true
    setFilms(null)
    setExercises([])
    Promise.all([api.programmeFilms(), rhymes ? Promise.resolve([]) : api.filmExercises(stage)])
      .then(([all, grid]) => {
        if (!current) return
        setFilms(all)
        setExercises(grid)
      })
      .catch((e) => current && setError(e.message))
    return () => {
      current = false
    }
  }, [stage, rhymes])

  if (error) return <p className="error">{error}</p>
  if (!films) return <p className="muted">Loading the films…</p>

  const ofUnit = films.find((f) => f.stage === stage && f.unit === unit) ?? null
  const ofExercise = (id: number) => films.find((f) => f.exerciseId === id) ?? null
  const replace = (matches: (f: ProgrammeFilm) => boolean, film: ProgrammeFilm | null) =>
    setFilms((all) => [...(all ?? []).filter((f) => !matches(f)), ...(film ? [film] : [])])
  const withFilm = exercises.filter((e) => ofExercise(e.id)).length

  return (
    <>
      {unit !== null && (
        <section className="card film-slot" aria-label={`Film for ${what}`}>
          <h3>Film for {what}</h3>
          <FilmSlot
            key={`${stage}-${unit}`}
            target={{ stage, unit }}
            what={what}
            reach={
              rhymes
                ? `One film for all ${sessions} sessions of ${what}, and any added later, at school and at home.`
                : `One film for all ${sessions} sessions of ${what}, at school and at home. A step whose exercise has a film of its own plays that instead.`
            }
            film={ofUnit}
            rules={rules}
            onChange={(film) => replace((f) => f.stage === stage && f.unit === unit, film)}
          />
        </section>
      )}
      {!rhymes && (
        <details className="card film-slot">
          <summary>
            Films for {colour} exercises ({withFilm} of {exercises.length} have one)
          </summary>
          <p className="muted">
            Each exercise can have its own film. A session then shows, step by step, the film of the exercise being done.
          </p>
          {exercises.map((e) => (
            <details key={e.id} className="film-exercise">
              <summary>
                {e.number}. {e.title} <span className="muted">· {ofExercise(e.id) ? 'has a film' : 'no film yet'}</span>
              </summary>
              <FilmSlot
                target={{ exerciseId: e.id }}
                what={e.title}
                reach={`Plays in the ${e.title} step of every ${colour} session with it, at school and at home.`}
                film={ofExercise(e.id)}
                rules={rules}
                onChange={(film) => replace((f) => f.exerciseId === e.id, film)}
              />
            </details>
          ))}
        </details>
      )}
    </>
  )
}

/** Nothing reaches the library without Pat or Laura’s sign-off, and nothing diagnostic gets signed off. */
export default function ContentPage() {
  const [kind, setKind] = useState<ContentKind>('content')
  const [items, setItems] = useState<ContentItem[]>([])
  const [filter, setFilter] = useState<'DRAFT' | 'ALL'>('DRAFT')
  const [error, setError] = useState<string | null>(null)
  const [rules, setRules] = useState<UploadRules | null>(null)
  const [stage, setStage] = useState<ColourStage | ''>('')
  const [unit, setUnit] = useState('')
  const [approving, setApproving] = useState(false)
  const [left, setLeft] = useState<ApprovedAll['left']>([])
  const setNotice = useToast()
  const confirm = useConfirm()

  useEffect(() => {
    // Without the rules the form still works: the team pastes a link.
    api.uploadRules().then(setRules).catch(() => setRules(null))
  }, [])

  useEffect(() => {
    // Ignore a slow response for a tab the user has already left.
    let current = true
    setItems([])
    setError(null)
    setNotice(null)
    setStage('')
    setUnit('')
    setLeft([])
    api
      .content(kind)
      .then((list) => current && setItems(list))
      .catch((e) => current && setError(e.message))
    return () => {
      current = false
    }
  }, [kind])

  const narrowed = useMemo(
    () => items.filter((i) => (!stage || i.stage === stage) && (!unit || i.unit === Number(unit))),
    [items, stage, unit],
  )
  const drafts = useMemo(() => narrowed.filter((i) => i.status === 'DRAFT'), [narrowed])
  // Drafts first, so what needs signing off is never hidden behind the first page of approved sessions.
  const shown = filter === 'ALL' ? [...drafts, ...narrowed.filter((i) => i.status !== 'DRAFT')] : drafts
  const modules = useMemo(
    () => [...new Set(items.filter((i) => i.stage === stage).flatMap((i) => i.unit ?? []))].sort((a, b) => a - b),
    [items, stage],
  )

  async function create(item: Record<string, unknown>) {
    setError(null)
    setNotice(null)
    try {
      const created = await api.createContent(kind, item)
      setItems((all) => [created, ...all])
      setFilter('DRAFT')
      setNotice(`“${created.title}” is saved as a draft. Check it below, then approve it to publish it.`)
      return true
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not save')
      return false
    }
  }

  async function review(id: number, action: 'approve' | 'reject') {
    setError(null)
    setNotice(null)
    try {
      const updated = action === 'approve' ? await api.approve(id, kind) : await api.reject(id, kind)
      setItems((all) => all.map((i) => (i.id === id ? updated : i)))
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Review failed')
    }
  }

  async function approveAll() {
    if (!stage) return
    const name = STAGES.find((s) => s.key === stage)?.label
    const what = unit ? `${name} module ${unit}` : `all of ${name}`
    const sure = await confirm({
      title: `Approve ${drafts.length} drafts in ${what}?`,
      body: 'Teachers can run them as soon as they are approved. Any that fail the wording check stay as drafts.',
      confirmLabel: 'Approve them',
    })
    if (!sure) return
    setApproving(true)
    setError(null)
    setLeft([])
    try {
      const result = await api.approveAllSessions(stage, unit ? Number(unit) : null)
      setItems(await api.content('content'))
      setLeft(result.left)
      setNotice(`${result.approved} approved in ${what}.`)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not approve them')
    } finally {
      setApproving(false)
    }
  }

  return (
    <>
      <div className="page-head">
        <h1>Content approval</h1>
        <div className="chips">
          {KINDS.map((k) => (
            <button key={k.kind} className={`chip ${kind === k.kind ? 'chip-on' : ''}`} onClick={() => setKind(k.kind)}>
              {k.label}
            </button>
          ))}
        </div>
      </div>
      <div className="chips">
        <button className={`chip ${filter === 'DRAFT' ? 'chip-on' : ''}`} onClick={() => setFilter('DRAFT')}>
          Awaiting sign-off ({drafts.length})
        </button>
        <button className={`chip ${filter === 'ALL' ? 'chip-on' : ''}`} onClick={() => setFilter('ALL')}>
          All ({narrowed.length})
        </button>
      </div>
      {/* Keyed by kind so switching tabs starts a fresh form. */}
      <NewItemForm key={kind} kind={kind} rules={rules} onSave={create} />
      {kind === 'content' && (
        <div className="card inline-form">
          <label>
            Show colour
            <select
              value={stage}
              onChange={(e) => {
                setStage(e.target.value as ColourStage | '')
                setUnit('')
              }}
            >
              <option value="">All colours</option>
              {STAGES.map((s) => (
                <option key={s.key} value={s.key}>
                  {s.label}
                </option>
              ))}
            </select>
          </label>
          <label>
            Show module
            <select value={unit} onChange={(e) => setUnit(e.target.value)} disabled={!stage}>
              <option value="">All modules</option>
              {modules.map((m) => (
                <option key={m} value={m}>
                  {m}
                </option>
              ))}
            </select>
          </label>
          {stage && (
            <div className="actions form-wide">
              <button className="btn btn-primary" disabled={approving || drafts.length === 0} onClick={approveAll}>
                {approving ? 'Approving…' : `Approve all ${drafts.length} drafts shown`}
              </button>
            </div>
          )}
        </div>
      )}
      {kind === 'content' && stage && (
        <FilmsPanel stage={stage} unit={unit ? Number(unit) : null} sessions={narrowed.length} rules={rules} />
      )}
      {error && <p className="error">{error}</p>}
      {left.length > 0 && (
        <div className="banner-warn">
          <strong>Still drafts, because of the wording check:</strong> {left.map((l) => `${l.title} (${l.label})`).join('; ')}
        </div>
      )}
      {shown.length === 0 && <p className="muted">Nothing waiting for sign-off.</p>}
      {shown.length > SHOWN_AT_ONCE && (
        <p className="muted">
          Showing the first {SHOWN_AT_ONCE} of {shown.length}. Choose a colour or module to see the rest.
        </p>
      )}
      <div className="list">
        {shown.slice(0, SHOWN_AT_ONCE).map((i) => (
          <div key={i.id} className="card content-item">
            <div className="section-head">
              <span>
                {i.stage && <StageBadge stage={i.stage} />} <span className="muted">{i.label}</span>
              </span>
              <span className={`status status-${i.status.toLowerCase()}`}>{i.status.toLowerCase()}</span>
            </div>
            <h3>{i.title}</h3>
            <p>{i.focus}</p>
            <p className="muted">{i.activities.join(' · ')}</p>
            {i.fileLink && (
              <p>
                <a className="btn" href={i.fileLink} target="_blank" rel="noreferrer">
                  Open the file
                </a>
              </p>
            )}
            {i.wordingIssues.length > 0 && (
              <div className="banner-warn">
                <strong>Wording check:</strong>{' '}
                {i.wordingIssues.map((w) => `“${w.term}” in “${w.text}”`).join('; ')}. Reword before approval.
              </div>
            )}
            {i.status === 'DRAFT' && (
              <div className="actions">
                <button className="btn" onClick={() => review(i.id, 'reject')}>
                  Send back
                </button>
                <button
                  className="btn btn-primary"
                  disabled={i.wordingIssues.length > 0}
                  onClick={() => review(i.id, 'approve')}
                >
                  Approve
                </button>
              </div>
            )}
            {i.reviewedBy && (
              <p className="fine">
                Reviewed by {i.reviewedBy}
                {i.reviewedAt ? ` on ${day(i.reviewedAt)}` : ''}
              </p>
            )}
          </div>
        ))}
      </div>
    </>
  )
}
