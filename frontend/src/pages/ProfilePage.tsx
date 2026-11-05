import { useState } from 'react'
import { useParams } from 'react-router-dom'
import { api, type ParentInvite } from '../api'
import { useAuth } from '../auth'
import AssessmentProfileCard from '../components/AssessmentProfileCard'
import { day } from '../dates'

/** A child's profile: Pat's two assessments, then (for staff) a code for the parent app. */
export default function ProfilePage() {
  const { childId } = useParams()
  const { user } = useAuth()
  const id = Number(childId)

  return (
    <div className="profile">
      <AssessmentProfileCard childId={id} />
      {(user?.role === 'TEACHER' || user?.role === 'HEAD') && <ParentInviteCard childId={id} />}
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
