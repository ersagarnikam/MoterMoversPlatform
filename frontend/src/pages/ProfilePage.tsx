import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { api, type Profile } from '../api'
import NotADiagnosis from '../components/NotADiagnosis'
import Sparkline from '../components/Sparkline'
import StageBadge from '../components/StageBadge'

/** Readiness profile: six areas, the suggested starting stage and the change over the year. */
export default function ProfilePage() {
  const { childId } = useParams()
  const [profile, setProfile] = useState<Profile | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api.profile(Number(childId)).then(setProfile).catch((e) => setError(e.message))
  }, [childId])

  if (error) return <p className="error">{error}</p>
  if (!profile) return <p className="muted">Loading…</p>

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
          <p className="muted">Not screened yet.</p>
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
            <h3>Change since the first screening</h3>
            <Sparkline
              values={profile.history.map((h) => h.overallFraction)}
              labels={profile.history.map((h) => `Screening ${h.round} · ${h.screenedOn}`)}
            />
          </>
        )}
        <p className="fine">Consent recorded · every view of this record is logged</p>
      </div>
    </div>
  )
}
