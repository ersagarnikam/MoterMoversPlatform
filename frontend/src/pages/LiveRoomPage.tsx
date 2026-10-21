import { useEffect, useRef, useState } from 'react'
import { useParams } from 'react-router-dom'
import { api, type LiveJoin, type LiveRegister } from '../api'
import { sessionTime, ZOOM_SDK } from '../live'

/** The parts of Zoom's Meeting SDK (component view) the room uses. */
interface ZoomEmbeddedClient {
  init(options: { zoomAppRoot: HTMLElement; language: string; patchJsMedia: boolean; leaveOnPageUnload: boolean }): Promise<unknown>
  join(options: {
    signature: string
    meetingNumber: string
    password: string
    userName: string
    userEmail: string
    zak?: string
  }): Promise<unknown>
  leaveMeeting(): Promise<unknown>
}

declare global {
  interface Window {
    ZoomMtgEmbedded?: { createClient(): ZoomEmbeddedClient }
  }
}

let sdk: Promise<NonNullable<Window['ZoomMtgEmbedded']>> | null = null

function loadScript(src: string, integrity: string) {
  return new Promise<void>((resolve, reject) => {
    const script = document.createElement('script')
    script.src = src
    script.integrity = integrity
    script.crossOrigin = 'anonymous'
    script.onload = () => resolve()
    script.onerror = () => reject(new Error('Zoom could not be reached'))
    document.head.appendChild(script)
  })
}

/** Loads Zoom's SDK once, from Zoom's CDN, in order and checked against the pinned hashes. */
function loadZoom() {
  sdk ??= (async () => {
    for (const { src, integrity } of ZOOM_SDK) await loadScript(src, integrity)
    if (!window.ZoomMtgEmbedded) throw new Error('Zoom did not load')
    return window.ZoomMtgEmbedded
  })().catch((e: unknown) => {
    sdk = null
    throw e
  })
  return sdk
}

/**
 * A live course session: the Zoom meeting inside the page, the session rules, and for the Motor Movers team the
 * register. Reached with a full page load, because this page alone allows Zoom's scripts (see vite.config.ts).
 */
export default function LiveRoomPage() {
  const { sessionId } = useParams()
  const [join, setJoin] = useState<LiveJoin | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [zoom, setZoom] = useState<'loading' | 'in' | 'failed'>('loading')
  const [zoomError, setZoomError] = useState<string | null>(null)
  const root = useRef<HTMLDivElement>(null)
  const client = useRef<ZoomEmbeddedClient | null>(null)

  useEffect(() => {
    api
      .joinLive(Number(sessionId))
      .then(setJoin)
      .catch((e) => setError(e.message))
  }, [sessionId])

  useEffect(() => {
    if (!join?.zoomConnected || !root.current || client.current) return
    const element = root.current
    loadZoom()
      .then(async (Zoom) => {
        const c = Zoom.createClient()
        client.current = c
        await c.init({ zoomAppRoot: element, language: 'en-US', patchJsMedia: true, leaveOnPageUnload: true })
        await c.join({
          signature: join.signature!,
          meetingNumber: join.meetingNumber!,
          password: join.passcode ?? '',
          userName: join.userName,
          userEmail: join.userEmail,
          ...(join.zak ? { zak: join.zak } : {}),
        })
        setZoom('in')
      })
      .catch((e: unknown) => {
        setZoom('failed')
        setZoomError(e instanceof Error ? e.message : String((e as { reason?: string })?.reason ?? 'Zoom did not start'))
      })
  }, [join])

  async function leave() {
    try {
      await client.current?.leaveMeeting()
    } catch {
      // Leaving is best effort: the page load below ends the meeting for this browser anyway.
    }
    // A full page load, so the next page gets the platform's strict security policy back.
    window.location.assign(join?.session.host ? '/live' : '/training')
  }

  if (error) {
    return (
      <>
        <p className="error">{error}</p>
        <a href="/training">Back to training</a>
      </>
    )
  }
  if (!join) return <p className="muted">Opening the session…</p>
  const s = join.session

  return (
    <>
      <div className="page-head">
        <div>
          <p className="eyebrow">
            {s.courseTitle} · {s.cohortName}
          </p>
          <h1>
            Live session {s.number} of {s.of}
          </h1>
          <p className="muted">
            {sessionTime(s.startsAt)} · {s.durationMinutes} minutes · led by {s.trainerName}
          </p>
        </div>
        <button className="btn" onClick={leave}>
          Leave session
        </button>
      </div>

      <div className="live-room">
        <section className="card live-video" aria-label="Zoom meeting">
          {join.zoomConnected ? (
            <>
              {zoom === 'loading' && <p className="muted">Connecting to Zoom…</p>}
              {zoom === 'failed' && (
                <p className="error" role="alert">
                  Zoom did not start: {zoomError}. Check your connection, allow the camera and microphone, and reload
                  the page.
                </p>
              )}
              <div ref={root} className="zoom-root" />
            </>
          ) : (
            <div className="zoom-offline">
              <strong>Zoom is not connected yet</strong>
              <p className="muted">
                When the Motor Movers Zoom keys are set, the meeting appears here. Everything else on this page works
                already{s.host ? ', including the register' : ''}.
              </p>
            </div>
          )}
        </section>

        <aside className="live-side">
          <section className="card">
            <h3>Rules for every live session</h3>
            <p>{join.rules}</p>
            <p className="muted">{join.recorded ? 'This session is recorded.' : 'This session is not recorded.'}</p>
          </section>
          {s.host && <Register sessionId={s.id} />}
        </aside>
      </div>
    </>
  )
}

/** Tick who is here. Each tick counts towards the learner's attendance and certificate. */
function Register({ sessionId }: { sessionId: number }) {
  const [register, setRegister] = useState<LiveRegister | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api
      .liveRegister(sessionId)
      .then(setRegister)
      .catch((e) => setError(e.message))
  }, [sessionId])

  async function mark(enrolmentId: number, present: boolean) {
    setError(null)
    try {
      const saved = await api.markPresent(sessionId, enrolmentId, present)
      setRegister((r) =>
        r && {
          ...r,
          learners: r.learners.map((l) =>
            l.enrolmentId === enrolmentId ? { ...l, present, sessionsAttended: saved.sessionsAttended } : l,
          ),
        },
      )
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Something went wrong')
    }
  }

  return (
    <section className="card">
      <h3>Register</h3>
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
      {!register ? (
        <p className="muted">Loading…</p>
      ) : register.learners.length === 0 ? (
        <p className="muted">No one has booked this cohort.</p>
      ) : (
        <ul className="register">
          {register.learners.map((l) => (
            <li key={l.enrolmentId}>
              <label className="toggle">
                <input
                  type="checkbox"
                  checked={l.present}
                  disabled={l.certificateIssued}
                  onChange={(e) => mark(l.enrolmentId, e.target.checked)}
                />{' '}
                {l.learnerName}
              </label>
              <span className="muted">{l.sessionsAttended} attended</span>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
