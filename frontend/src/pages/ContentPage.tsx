import { useEffect, useMemo, useState } from 'react'
import { api, type ContentItem } from '../api'
import StageBadge from '../components/StageBadge'

/** Nothing reaches the library without Pat or Laura’s sign-off, and nothing diagnostic gets signed off. */
export default function ContentPage() {
  const [items, setItems] = useState<ContentItem[]>([])
  const [filter, setFilter] = useState<'DRAFT' | 'ALL'>('DRAFT')
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api.content().then(setItems).catch((e) => setError(e.message))
  }, [])

  const shown = useMemo(() => (filter === 'ALL' ? items : items.filter((i) => i.status === 'DRAFT')), [items, filter])

  async function review(id: number, action: 'approve' | 'reject') {
    setError(null)
    try {
      const updated = action === 'approve' ? await api.approve(id) : await api.reject(id)
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
          <button className={`chip ${filter === 'DRAFT' ? 'chip-on' : ''}`} onClick={() => setFilter('DRAFT')}>
            Awaiting sign-off ({items.filter((i) => i.status === 'DRAFT').length})
          </button>
          <button className={`chip ${filter === 'ALL' ? 'chip-on' : ''}`} onClick={() => setFilter('ALL')}>
            All sessions ({items.length})
          </button>
        </div>
      </div>
      {error && <p className="error">{error}</p>}
      {shown.length === 0 && <p className="muted">Nothing waiting for sign-off.</p>}
      <div className="list">
        {shown.map((i) => (
          <div key={i.id} className="card content-item">
            <div className="section-head">
              <span>
                <StageBadge stage={i.stage} /> <span className="muted">{i.label}</span>
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
                {i.reviewedAt ? ` on ${i.reviewedAt.slice(0, 10)}` : ''}
              </p>
            )}
          </div>
        ))}
      </div>
    </>
  )
}
