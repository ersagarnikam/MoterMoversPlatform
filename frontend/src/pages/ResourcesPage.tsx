import { useEffect, useMemo, useState } from 'react'
import { api, type LibraryResource } from '../api'
import StageBadge from '../components/StageBadge'
import Skeleton from '../components/Skeleton'

const KINDS: { kind: LibraryResource['kind'] | 'ALL'; label: string }[] = [
  { kind: 'ALL', label: 'Everything' },
  { kind: 'RHYME', label: 'Rhymes' },
  { kind: 'BOOK', label: 'Books' },
  { kind: 'PRINTABLE', label: 'Printables' },
]

/** Rhyme recordings, the rhyme book and printables for the stages on the school's licence. */
export default function ResourcesPage() {
  const [items, setItems] = useState<LibraryResource[] | null>(null)
  const [kind, setKind] = useState<(typeof KINDS)[number]['kind']>('ALL')
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api.resources().then(setItems).catch((e) => setError(e.message))
  }, [])

  const shown = useMemo(() => (items ?? []).filter((r) => kind === 'ALL' || r.kind === kind), [items, kind])

  if (error) return <p className="error">{error}</p>
  if (!items) return <Skeleton />

  return (
    <>
      <div className="page-head">
        <h1>Resources</h1>
        <div className="chips">
          {KINDS.map((k) => (
            <button key={k.kind} className={`chip ${kind === k.kind ? 'chip-on' : ''}`} onClick={() => setKind(k.kind)}>
              {k.label}
            </button>
          ))}
        </div>
      </div>
      {shown.length === 0 && <p className="muted">Nothing here yet.</p>}
      <div className="list">
        {shown.map((r) => (
          <div key={r.id} className="card">
            <div className="section-head">
              <span>
                {r.stage && <StageBadge stage={r.stage} />} <span className="muted">{r.label}</span>
              </span>
              {r.fileUrl ? (
                r.kind === 'RHYME' ? null : (
                  <a className="btn" href={r.fileUrl} download>
                    Download
                  </a>
                )
              ) : (
                <span className="muted">File on its way</span>
              )}
            </div>
            <h3>{r.title}</h3>
            <p>{r.description}</p>
            {r.kind === 'RHYME' && r.fileUrl && <audio src={r.fileUrl} controls />}
            {r.lines.length > 0 && (
              <details>
                <summary className="btn">Words</summary>
                <p className="rhyme-words">
                  {r.lines.map((line) => (
                    <span key={line}>{line}</span>
                  ))}
                </p>
              </details>
            )}
          </div>
        ))}
      </div>
    </>
  )
}
