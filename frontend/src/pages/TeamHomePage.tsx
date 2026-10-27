import { useEffect, useState, type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { api, type TeamOverview } from '../api'
import { useAuth } from '../auth'
import Skeleton from '../components/Skeleton'
import { day } from '../dates'
import { sessionTime, startsIn } from '../live'
import { gbp } from '../money'

/**
 * The Motor Movers team's home: what needs attention today, each with a link to where it is done. Things that need
 * doing come first; anything with nothing to do says so.
 */
export default function TeamHomePage() {
  const { user } = useAuth()
  const [data, setData] = useState<TeamOverview | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api
      .teamOverview()
      .then(setData)
      .catch((e) => setError(e.message))
  }, [])

  if (error) return <p className="error">{error}</p>
  if (!data) return <Skeleton lines={4} />

  const cards: { busy: boolean; node: ReactNode }[] = [
    {
      busy: data.sessionsToday.length > 0,
      node: (
        <HomeCard key="live" title="Live sessions today" link="/live" linkLabel="All live sessions" count={data.sessionsToday.length}>
          {data.sessionsToday.length === 0 ? (
            <p className="muted">No live sessions today.</p>
          ) : (
            <ul className="home-list">
              {data.sessionsToday.map((s) => (
                <li key={s.id}>
                  <span>
                    <strong>{sessionTime(s.startsAt)}</strong> · {s.courseTitle} · {s.cohortName}, session {s.number}
                    <span className="muted">
                      {' '}
                      · {s.trainerName} · {s.booked} booked
                    </span>
                  </span>
                  {startsIn(s.startsAt, 60) && <span className="badge badge-warn">{startsIn(s.startsAt, 60)}</span>}
                </li>
              ))}
            </ul>
          )}
        </HomeCard>
      ),
    },
    {
      busy: data.draftsAwaiting > 0,
      node: (
        <HomeCard key="drafts" title="Waiting for sign-off" link="/admin/content" linkLabel="Content approval" count={data.draftsAwaiting}>
          <p className={data.draftsAwaiting ? undefined : 'muted'}>
            {data.draftsAwaiting === 0
              ? 'Nothing waiting: every draft is signed off.'
              : `${data.draftsAwaiting} ${data.draftsAwaiting === 1 ? 'draft is' : 'drafts are'} waiting to be checked and approved.`}
          </p>
        </HomeCard>
      ),
    },
    {
      busy: data.overdue.length > 0,
      node: (
        <HomeCard key="overdue" title="Invoices overdue" link="/admin/payments" linkLabel="Payments" count={data.overdue.length}>
          {data.overdue.length === 0 ? (
            <p className="muted">Nothing overdue. {gbp(data.dueTotalPence)} is due in all.</p>
          ) : (
            <>
              <ul className="home-list">
                {data.overdue.slice(0, 5).map((p) => (
                  <li key={p.id}>
                    <span>
                      <strong>{p.payerName}</strong> · {p.description}
                      <span className="muted"> · sent {day(p.invoicedOn)}</span>
                    </span>
                    <strong className="nowrap">{gbp(p.amountPence)}</strong>
                  </li>
                ))}
              </ul>
              <p className="muted">Unpaid over 30 days. {gbp(data.dueTotalPence)} is due in all.</p>
            </>
          )}
        </HomeCard>
      ),
    },
    {
      busy: data.renewals.length > 0,
      node: (
        <HomeCard key="renewals" title="Licences to renew" link="/admin/schools" linkLabel="Schools & licences" count={data.renewals.length}>
          {data.renewals.length === 0 ? (
            <p className="muted">No licence ends in the next 60 days.</p>
          ) : (
            <ul className="home-list">
              {data.renewals.map((r) => (
                <li key={r.schoolId}>
                  <strong>{r.schoolName}</strong>
                  <span className="muted">ends {day(r.endsOn)}</span>
                </li>
              ))}
            </ul>
          )}
        </HomeCard>
      ),
    },
    {
      busy: data.clashingCohorts > 0,
      node: (
        <HomeCard key="clashes" title="Zoom clashes" link="/admin/courses" linkLabel="Courses" count={data.clashingCohorts}>
          <p className={data.clashingCohorts ? undefined : 'muted'}>
            {data.clashingCohorts === 0
              ? 'No cohorts share a Zoom login at the same time.'
              : `${data.clashingCohorts} ${data.clashingCohorts === 1 ? 'cohort has' : 'cohorts have'} sessions at the same time as another on the same Zoom login. Give one a trainer with their own Zoom login, or move it.`}
          </p>
        </HomeCard>
      ),
    },
    {
      busy: data.certificatesExpiring.length > 0,
      node: (
        <HomeCard key="certs" title="Certificates running out" link="/admin/courses" linkLabel="Courses" count={data.certificatesExpiring.length}>
          {data.certificatesExpiring.length === 0 ? (
            <p className="muted">No certificate runs out in the next 30 days.</p>
          ) : (
            <ul className="home-list">
              {data.certificatesExpiring.map((c) => (
                <li key={c.enrolmentId}>
                  <span>
                    <strong>{c.learnerName}</strong> · {c.courseTitle}
                  </span>
                  <span className="muted">{day(c.expiresOn)}</span>
                </li>
              ))}
            </ul>
          )}
          <p className="fine">Learners are reminded by email a month before.</p>
        </HomeCard>
      ),
    },
    {
      busy: data.courseRequestsWaiting > 0,
      node: (
        <HomeCard key="requests" title="Staff course requests" count={data.courseRequestsWaiting}>
          <p className="muted">
            {data.courseRequestsWaiting === 0
              ? 'No requests waiting.'
              : `${data.courseRequestsWaiting} waiting for their head teacher to approve. Heads decide; nothing to do here unless they ask.`}
          </p>
        </HomeCard>
      ),
    },
  ]

  return (
    <>
      <div className="page-head">
        <h1>Hello, {user?.displayName.split(' ')[0]}</h1>
        <span className="muted">What needs attention today</span>
      </div>
      <div className="home-grid">
        {cards.filter((c) => c.busy).map((c) => c.node)}
        {cards.filter((c) => !c.busy).map((c) => c.node)}
      </div>
    </>
  )
}

function HomeCard({
  title,
  count,
  link,
  linkLabel,
  children,
}: {
  title: string
  count: number
  link?: string
  linkLabel?: string
  children: ReactNode
}) {
  return (
    <section className={count > 0 ? 'card home-card home-card-busy' : 'card home-card'}>
      <div className="section-head">
        <h3>{title}</h3>
        {count > 0 ? <span className="badge badge-warn">{count}</span> : <span className="badge badge-ok">All clear</span>}
      </div>
      {children}
      {link && (
        <Link className="fine-link" to={link}>
          {linkLabel} →
        </Link>
      )}
    </section>
  )
}
