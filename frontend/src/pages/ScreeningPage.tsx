import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api, type RosterEntry, type ScreeningItem, type Today } from '../api'
import { enqueue, flush, pending } from '../outbox'
import { rosterChild } from '../components/RosterChild'

/** Assessment replaces the paper booklet: watch one movement, tap a score, the platform does the rest. */
export default function ScreeningPage() {
  const { classId } = useParams()
  const [today, setToday] = useState<Today | null>(null)
  const [items, setItems] = useState<ScreeningItem[]>([])
  const [child, setChild] = useState<RosterEntry | null>(null)
  const [index, setIndex] = useState(0)
  const [selected, setSelected] = useState<number | null>(null)
  const [unsent, setUnsent] = useState(pending().length)
  const [online, setOnline] = useState(navigator.onLine)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    Promise.all([api.today(Number(classId)), api.screeningItems()])
      .then(([t, i]) => {
        setToday(t)
        setItems(i)
      })
      .catch((e) => setError(e.message))
  }, [classId])

  useEffect(() => {
    const up = () => {
      setOnline(true)
      flush().then(setUnsent)
    }
    const down = () => setOnline(false)
    window.addEventListener('online', up)
    window.addEventListener('offline', down)
    flush().then(setUnsent)
    return () => {
      window.removeEventListener('online', up)
      window.removeEventListener('offline', down)
    }
  }, [])

  if (error) return <p className="error">{error}</p>
  if (!today || items.length === 0) return <p className="muted">Loading…</p>

  if (!child) {
    return (
      <>
        <div className="page-head">
          <h1>Assessment · {today.schoolClass.name}</h1>
          <Link className="btn" to={`/class/${classId}/today`}>
            Back to today
          </Link>
        </div>
        <p className="muted">Choose a child. Each assessment takes five to eight minutes.</p>
        <div className="roster">
          {today.roster.map((c) => (
            <button key={c.id} className="roster-child" onClick={() => setChild(c)} {...rosterChild(c, 'assess').props}>
              {rosterChild(c, 'assess').content}
            </button>
          ))}
        </div>
      </>
    )
  }

  const item = items[index]
  const childNumber = today.roster.findIndex((c) => c.id === child.id) + 1

  async function next() {
    if (selected === null) return
    enqueue({
      clientId: crypto.randomUUID(),
      childId: child!.id,
      itemCode: item.code,
      score: selected,
      screenedOn: new Date().toISOString().slice(0, 10),
    })
    setUnsent(pending().length)
    setSelected(null)
    if (index + 1 < items.length) {
      setIndex(index + 1)
    } else {
      setUnsent(await flush())
      setIndex(0)
      setChild(null)
    }
  }

  return (
    <div className="screening">
      <div className="card screening-card">
        <div className="section-head">
          <strong>
            Assessment · Child {childNumber} of {today.roster.length} ({child.name})
          </strong>
          <span className={online ? 'muted' : 'offline-pill'}>
            {online ? 'Online' : 'Offline'} · {unsent} saved on this device
          </span>
        </div>
        <div className="progress">
          <span style={{ width: `${(index / items.length) * 100}%` }} />
        </div>
        <p className="muted">
          Item {index + 1} of {items.length} · {item.areaLabel}
        </p>
        <h2>{item.title}</h2>
        <p>{item.instructions}</p>
        <div className="options" role="radiogroup" aria-label="Score">
          {Object.entries(item.levels).map(([score, text]) => (
            <button
              key={score}
              role="radio"
              aria-checked={selected === Number(score)}
              className={`option ${selected === Number(score) ? 'option-on' : ''}`}
              onClick={() => setSelected(Number(score))}
            >
              <span className="radio" /> {score} · {text}
            </button>
          ))}
        </div>
        <p className="dashed-note">Video recording off · needs school opt-in and consent</p>
        <div className="actions spread">
          <button
            className="btn"
            onClick={() => {
              if (index === 0) setChild(null)
              else setIndex(index - 1)
            }}
          >
            Back
          </button>
          <button className="btn btn-primary" disabled={selected === null} onClick={next}>
            {index + 1 < items.length ? 'Next item' : 'Finish child'}
          </button>
        </div>
      </div>
    </div>
  )
}
