import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { api, type ContentItem, type ContentKind } from '../api'
import StageBadge from '../components/StageBadge'
import { STAGES } from '../stages'
import { day } from '../dates'
import { useToast } from '../components/Feedback'

const KINDS: { kind: ContentKind; label: string }[] = [
  { kind: 'content', label: 'Class sessions' },
  { kind: 'home-activities', label: 'Home activities' },
  { kind: 'guidance', label: 'Parent guidance' },
  { kind: 'resources', label: 'Resources' },
  { kind: 'induction', label: 'Staff induction' },
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
}

const TITLE: Field = { name: 'title', label: 'Title', type: 'text', required: true, max: 200 }
const VIDEO: Field = { name: 'videoUrl', label: 'Video link (optional)', type: 'url', max: 500, hint: 'An https:// address' }
const PARAGRAPHS = 'Leave a blank line between paragraphs.'

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
      { name: 'fileUrl', label: 'File or recording link (optional)', type: 'url', max: 500, hint: 'An https:// address' },
      { name: 'words', label: 'Words of a rhyme, one line per line (optional)', type: 'textarea', max: 4000 },
    ],
  },
  induction: {
    heading: 'Add an induction part',
    fields: [
      { name: 'position', label: 'Part number', type: 'number', required: true, initial: '1' },
      { name: 'minutes', label: 'Minutes', type: 'number', required: true, initial: '10' },
      TITLE,
      { name: 'summary', label: 'Summary', type: 'textarea', required: true, max: 500 },
      { name: 'body', label: 'Lesson', type: 'textarea', required: true, max: 8000, hint: PARAGRAPHS },
      VIDEO,
    ],
  },
}

const RESOURCE_KINDS = [
  ['PRINTABLE', 'Printable'],
  ['RHYME', 'Rhyme recording'],
  ['BOOK', 'Book'],
] as const

const initialValues = (fields: Field[]) => Object.fromEntries(fields.map((f) => [f.name, f.initial ?? '']))

/** Turns the form's text values into the request the API expects for this kind. */
function toRequest(fields: Field[], values: Record<string, string>): Record<string, unknown> {
  return Object.fromEntries(
    fields.map((f) => {
      const value = values[f.name].trim()
      if (f.lines) return [f.name, value.split('\n').map((l) => l.trim()).filter(Boolean)]
      if (f.type === 'number') return [f.name, Number(value)]
      return [f.name, value === '' ? null : value]
    }),
  )
}

/** A new item goes in as a draft; it reaches teachers or parents only once it is approved below. */
function NewItemForm({ kind, onSave }: { kind: ContentKind; onSave: (item: Record<string, unknown>) => Promise<boolean> }) {
  const { heading, fields } = FORMS[kind]
  const [values, setValues] = useState(() => initialValues(fields))
  const [open, setOpen] = useState(false)
  const [saving, setSaving] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setSaving(true)
    const saved = await onSave(toRequest(fields, values))
    setSaving(false)
    if (saved) {
      setValues(initialValues(fields))
      setOpen(false)
    }
  }

  const set = (name: string, value: string) => setValues((v) => ({ ...v, [name]: value }))

  return (
    <details className="card add-item" open={open} onToggle={(e) => setOpen(e.currentTarget.open)}>
      <summary className="btn btn-primary">{heading}</summary>
      <form className="inline-form" onSubmit={submit}>
        {fields.map((f) => (
          <label key={f.name} className={f.type === 'textarea' ? 'form-wide' : undefined}>
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
        ))}
        <div className="actions form-wide">
          <button className="btn btn-primary" disabled={saving}>
            Save as draft
          </button>
        </div>
      </form>
    </details>
  )
}

/** Nothing reaches the library without Pat or Laura’s sign-off, and nothing diagnostic gets signed off. */
export default function ContentPage() {
  const [kind, setKind] = useState<ContentKind>('content')
  const [items, setItems] = useState<ContentItem[]>([])
  const [filter, setFilter] = useState<'DRAFT' | 'ALL'>('DRAFT')
  const [error, setError] = useState<string | null>(null)
  const setNotice = useToast()

  useEffect(() => {
    // Ignore a slow response for a tab the user has already left.
    let current = true
    setItems([])
    setError(null)
    setNotice(null)
    api
      .content(kind)
      .then((list) => current && setItems(list))
      .catch((e) => current && setError(e.message))
    return () => {
      current = false
    }
  }, [kind])

  const shown = useMemo(() => (filter === 'ALL' ? items : items.filter((i) => i.status === 'DRAFT')), [items, filter])

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
          Awaiting sign-off ({items.filter((i) => i.status === 'DRAFT').length})
        </button>
        <button className={`chip ${filter === 'ALL' ? 'chip-on' : ''}`} onClick={() => setFilter('ALL')}>
          All ({items.length})
        </button>
      </div>
      {/* Keyed by kind so switching tabs starts a fresh form. */}
      <NewItemForm key={kind} kind={kind} onSave={create} />
      {error && <p className="error">{error}</p>}
      {shown.length === 0 && <p className="muted">Nothing waiting for sign-off.</p>}
      <div className="list">
        {shown.map((i) => (
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
