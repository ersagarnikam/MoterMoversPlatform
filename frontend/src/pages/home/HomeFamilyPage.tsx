import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { api, type FamilyInvite, type FamilyMember, type HomeOverview } from '../../api'
import { useConfirm, useToast } from '../../components/Feedback'
import Skeleton from '../../components/Skeleton'
import { day } from '../../dates'

/**
 * Who shares a family's home children: invite a second parent or carer with a code (shown, or emailed to them), join
 * someone else's family, or remove someone. The person who added the children can remove others; anyone can leave.
 */
export default function HomeFamilyPage() {
  const navigate = useNavigate()
  const toast = useToast()
  const confirm = useConfirm()
  const [data, setData] = useState<HomeOverview | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [invite, setInvite] = useState<FamilyInvite | null>(null)
  const [email, setEmail] = useState('')
  const [code, setCode] = useState('')
  const [busy, setBusy] = useState(false)

  const load = useCallback(() => {
    api
      .homeOverview()
      .then(setData)
      .catch((e) => setError(e.message))
  }, [])

  useEffect(load, [load])

  if (!data) return error ? <p className="error">{error}</p> : <Skeleton />

  async function run(action: () => Promise<unknown>, ok: string) {
    setBusy(true)
    try {
      await action()
      toast(ok)
      load()
    } catch (e) {
      toast(e instanceof Error ? e.message : 'Something went wrong', 'error')
    } finally {
      setBusy(false)
    }
  }

  async function remove(m: FamilyMember) {
    const alone = data!.members.length === 1
    const yes = await confirm({
      title: m.you ? 'Leave the family?' : `Remove ${m.name}?`,
      body: m.you
        ? alone
          ? 'You are the only one: leaving deletes your children and everything recorded.'
          : 'You will no longer see the children.'
        : `${m.name} will no longer see the children.`,
      confirmLabel: m.you ? 'Leave' : `Remove ${m.name}`,
      danger: true,
    })
    if (!yes) return
    await run(() => api.removeFamilyMember(m.userId), m.you ? 'You have left' : 'Removed')
    if (m.you) navigate('/home')
  }

  function sendInvite(e: FormEvent) {
    e.preventDefault()
    const address = email.trim()
    void run(async () => {
      setInvite(await api.inviteCarer(address || undefined))
      setEmail('')
    }, address ? `Code emailed to ${address}` : 'Code ready')
  }

  function join(e: FormEvent) {
    e.preventDefault()
    void run(async () => {
      await api.joinFamily(code)
      setCode('')
    }, 'You have joined')
  }

  return (
    <>
      <p className="muted">
        <Link to="/home">At home</Link> / Family and sharing
      </p>
      <h1>Family and sharing</h1>

      {data.members.length > 0 && (
        <section className="card">
          <h3>Sharing your children</h3>
          <ul className="home-rows">
            {data.members.map((m) => (
              <li key={m.userId}>
                <span>
                  {m.name}
                  {m.you ? ' (you)' : ''} <span className="muted">· {m.owner ? 'Added the children' : 'Carer'}</span>
                </span>
                {(m.you || data.owner) && data.members.length > 1 && (
                  <button className="btn btn-small" onClick={() => remove(m)} disabled={busy}>
                    {m.you ? 'Leave' : 'Remove'}
                  </button>
                )}
              </li>
            ))}
          </ul>
        </section>
      )}

      {data.children.length > 0 && (
        <form className="card" onSubmit={sendInvite}>
          <h3>Invite someone</h3>
          <p className="muted">
            A second parent, carer or grandparent can share the sessions and see progress, on the website or in the
            parent app.
          </p>
          {invite && (
            <div className="stack">
              <p className="invite-code">{invite.code}</p>
              <p className="muted">
                {invite.sentTo ? `Emailed to ${invite.sentTo}. ` : ''}Works once, until {day(invite.expiresOn)}. They
                enter it under Family and sharing.
              </p>
            </div>
          )}
          <div className="inline-form">
            <label>
              Their email (optional)
              <input
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="name@example.com"
                maxLength={200}
                autoComplete="off"
              />
            </label>
            <button className="btn btn-primary" disabled={busy}>
              {email.trim() ? 'Email a code' : invite ? 'Get another code' : 'Get a code'}
            </button>
          </div>
          <p className="fine">We email them the code and how to use it. Or leave it empty and pass the code on yourself.</p>
        </form>
      )}

      <form className="card" onSubmit={join}>
        <h3>Join a family</h3>
        <p className="muted">Has someone given you a code to share their children? Enter it here.</p>
        <div className="inline-form">
          <label>
            Family code
            <input
              value={code}
              onChange={(e) => setCode(e.target.value)}
              placeholder="ABCD-EFGH"
              maxLength={12}
              autoComplete="off"
            />
          </label>
          <button className="btn" disabled={busy || code.replace(/[^a-z0-9]/gi, '').length !== 8}>
            Join
          </button>
        </div>
      </form>
    </>
  )
}
