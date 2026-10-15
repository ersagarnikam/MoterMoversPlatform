import { useEffect, useState } from 'react'
import { Link, Navigate } from 'react-router-dom'
import { api, type ClassSummary } from '../api'
import { useAuth } from '../auth'
import StageBadge from '../components/StageBadge'

export default function ClassListPage() {
  const { user } = useAuth()
  const [classes, setClasses] = useState<ClassSummary[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api.classes().then(setClasses).catch((e) => setError(e.message))
  }, [])

  if (error) return <p className="error">{error}</p>
  if (!classes) return <p className="muted">Loading…</p>
  // One class, one session, one button: a teacher with a single class goes straight to it.
  if (user?.role === 'TEACHER' && classes.length === 1) return <Navigate to={`/class/${classes[0].id}/today`} replace />

  return (
    <>
      <h1>Classes</h1>
      {classes.length === 0 && <p className="muted">No classes yet.</p>}
      <div className="list">
        {classes.map((c) => (
          <Link key={c.id} to={`/class/${c.id}/today`} className="list-row">
            <span>
              <strong>{c.name}</strong> <span className="muted">· {c.schoolName}</span>
            </span>
            <span className="row-end">
              <span className="muted">{c.teacherName ?? 'No teacher assigned'}</span>
              <StageBadge stage={c.stage} label={c.stageName} />
            </span>
          </Link>
        ))}
      </div>
    </>
  )
}
