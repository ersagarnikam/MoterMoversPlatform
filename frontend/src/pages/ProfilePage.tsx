import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { api, type ParentInvite, type Profile } from '../api'
import { useAuth } from '../auth'
import NotADiagnosis from '../components/NotADiagnosis'
import Sparkline from '../components/Sparkline'
import StageBadge from '../components/StageBadge'
import { day } from '../dates'
import Skeleton from '../components/Skeleton'

/** Readiness profile: six areas, the suggested starting stage and the change over the year. */
export default function ProfilePage() {
  const { childId } = useParams()
  const { user } = useAuth()
  const [profile, setProfile] = useState<Profile | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api.profile(Number(childId)).then(setProfile).catch((e) => setError(e.message))
  }, [childId])

  if (error) return <p className="error">{error}</p>
  if (!profile) return <Skeleton />

  return (
    <div className="profile">
      <div className="card">
        <div className="section-head">
          <h2>Readiness profile · {profile.name}</h2>
          <span className="muted">
            {profile.reference} · {profile.className}
          </span>
        </div>
        <NotADiagnosis />
        {!profile.screened ? (
          <p className="muted">Not assessed yet.</p>
        ) : (
          <>
            <p className="muted">Suggested starting stage</p>
            <p className="stage-title">
              <StageBadge stage={profile.suggestedStage} label={profile.suggestedStageName ?? undefined} />
            </p>
            <p className="muted">
              Working at: <StageBadge stage={profile.currentStage} />
            </p>
            <div className="area-bars">
              {profile.areas.map((a) => (
                <div key={a.area} className="area-row">
                  <span>{a.label}</span>
                  <span className="bar">
                    <span style={{ width: `${Math.max(6, a.fraction * 100)}%` }} />
                  </span>
                  <span className={`band band-${a.band.toLowerCase()}`}>{a.bandLabel}</span>
                </div>
              ))}
            </div>
            <h3>Change since the first assessment</h3>
            <Sparkline
              values={profile.history.map((h) => h.overallFraction)}
              labels={profile.history.map((h) => `Assessment ${h.round} · ${day(h.screenedOn)}`)}
            />
          </>
        )}
        <p className="fine">Consent recorded · every view of this record is logged</p>
      </div>
      {(user?.role === 'TEACHER' || user?.role === 'HEAD') && <ParentInviteCard childId={profile.childId} />}
    </div>
  )
}

/** Issues a one-time code the parent enters in the Motor Movers parent app. */
function ParentInviteCard({ childId }: { childId: number }) {
  const [invite, setInvite] = useState<ParentInvite | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function create() {
    setBusy(true)
    setError(null)
    try {
      setInvite(await api.createParentInvite(childId))
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not create a code')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="card">
      <h3>Parent app</h3>
      <p className="muted">
        Give a parent or carer a code so they can see this child's stage and progress in the parent app. Each code
        works once.
      </p>
      {invite ? (
        <>
          <p className="invite-code">{invite.code}</p>
          <p className="muted">
            For the parent or carer of {invite.childName} · {invite.className} · expires {day(invite.expiresOn)}
          </p>
        </>
      ) : (
        <button className="btn btn-primary" onClick={create} disabled={busy}>
          Create a parent code
        </button>
      )}
      {error && <p className="error">{error}</p>}
    </div>
  )
}
