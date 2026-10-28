import { useEffect, useState, type FormEvent } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { api, type HomeOverview } from '../../api'
import Skeleton from '../../components/Skeleton'
import StageBadge from '../../components/StageBadge'
import { day } from '../../dates'
import { ageLabel, toMonths } from '../../homeProgramme'

/**
 * Motor Movers at Home on the website: the family's own children (not linked to a school), and adding one. Only a
 * first name or nickname and an age are asked for; no date of birth. The same as the parent app's Today screen.
 */
export default function HomeOverviewPage() {
  const [data, setData] = useState<HomeOverview | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api
      .homeOverview()
      .then(setData)
      .catch((e) => setError(e.message))
  }, [])

  if (!data) return error ? <p className="error">{error}</p> : <Skeleton />
  // Switched off: the parent course, as before.
  if (!data.enabled) return <Navigate to="/training" replace />

  return (
    <>
      <div className="page-head">
        <h1>At home</h1>
        <Link to="/home/family" className="btn">
          Family and sharing
        </Link>
      </div>
      <p className="muted">
        A few minutes of movement most days, with the same exercises schools use. Only you, and anyone you invite, see
        your children.
      </p>

      {data.children.length > 0 && (
        <div className="home-kids">
          {data.children.map((k) => (
            <Link key={k.id} to={`/home/child/${k.id}`} className="card site-card-link home-kid">
              <h2>{k.name}</h2>
              <p className="muted">{ageLabel(k.ageMonths)}</p>
              {k.stage ? <StageBadge stage={k.stage} label={k.stageName ?? undefined} /> : null}
              <p className="muted">
                {!k.stage
                  ? 'Start with a short check'
                  : k.checkDue
                    ? 'Time for the check again'
                    : `Checked ${day(k.checkedOn)}`}
              </p>
            </Link>
          ))}
        </div>
      )}

      {data.children.length < data.maxChildren ? (
        <AddChild first={data.children.length === 0} />
      ) : (
        <p className="muted">A family can add up to {data.maxChildren} children.</p>
      )}

      {data.children.length === 0 && (
        <section className="card">
          <h3>Has someone invited you to share their children?</h3>
          <p className="muted">Enter their family code under Family and sharing.</p>
          <Link to="/home/family" className="btn">
            Enter a family code
          </Link>
        </section>
      )}
    </>
  )
}

/** Adds a child, then opens their starting check. */
function AddChild({ first }: { first: boolean }) {
  const navigate = useNavigate()
  const [name, setName] = useState('')
  const [years, setYears] = useState('')
  const [months, setMonths] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const age = toMonths(years, months)

  async function submit(e: FormEvent) {
    e.preventDefault()
    if (age === null) return
    setBusy(true)
    setError(null)
    try {
      const child = await api.addHomeChild(name.trim(), age)
      navigate(`/home/child/${child.id}/check`)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not add')
      setBusy(false)
    }
  }

  return (
    <form className="card" onSubmit={submit}>
      <h3>{first ? 'Add your child' : 'Add another child'}</h3>
      <p className="muted">Then a short check suggests the colour to start at.</p>
      <div className="inline-form">
        <label>
          First name or nickname
          <input value={name} onChange={(e) => setName(e.target.value)} maxLength={30} required />
        </label>
        <label>
          Age: years
          <input inputMode="numeric" value={years} onChange={(e) => setYears(e.target.value)} maxLength={2} />
        </label>
        <label>
          and months
          <input inputMode="numeric" value={months} onChange={(e) => setMonths(e.target.value)} maxLength={2} />
        </label>
        <button className="btn btn-primary" disabled={busy || !name.trim() || age === null}>
          {busy ? 'Adding…' : 'Add and start the check'}
        </button>
      </div>
      <p className="fine">
        {age !== null ? ageLabel(age) : 'Motor Movers is for children from 6 months to 8 years.'} We do not ask for a
        date of birth.
      </p>
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
    </form>
  )
}
