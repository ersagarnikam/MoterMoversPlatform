import { useEffect, useRef, useState } from 'react'
import { useParams } from 'react-router-dom'
import { api, type LiveJoin, type LiveRegister } from '../api'
import { sessionTime, ZOOM_SDK } from '../live'

/** The parts of Zoom's Meeting SDK (component view) the room uses. */
interface ZoomEmbeddedClient {
  init(options: {
    zoomAppRoot: HTMLElement
    language: string
    patchJsMedia: boolean
    leaveOnPageUnload: boolean
    customize?: Record<string, unknown>
  }): Promise<unknown>
  join(options: {
    signature: string
    meetingNumber: string
    password: string
    userName: string
    userEmail: string
    zak?: string
  }): Promise<unknown>
  leaveMeeting(): Promise<unknown>
  /** Re-renders the video at new sizes (SDK 6.5 VideoOptions). */
  updateVideoOptions(options: { viewSizes?: { default?: VideoSize; ribbon?: VideoSize } }): void
}

interface VideoSize {
  width: number
  height: number
}

/** Zoom's video fills our box: its width, at 16:9, no taller than two thirds of the window. */
function videoSize(element: HTMLElement): VideoSize {
  const width = Math.max(320, element.clientWidth)
  return { width, height: Math.min(Math.round((width * 9) / 16), Math.round(window.innerHeight * 0.65)) }
}

declare global {
  interface Window {
    ZoomMtgEmbedded?: { createClient(): ZoomEmbeddedClient }
  }
}

let sdk: Promise<NonNullable<Window['ZoomMtgEmbedded']>> | null = null

/** One join request per session per page load (React runs effects twice in development). */
const joining = new Map<number, Promise<LiveJoin>>()
function joinOnce(sessionId: number) {
  let request = joining.get(sessionId)
  if (!request) {
    request = api.joinLive(sessionId)
    joining.set(sessionId, request)
    request.catch(() => joining.delete(sessionId))
  }
  return request
}

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
  const [zoom, setZoom] = useState<'loading' | 'waiting' | 'busy' | 'in' | 'failed'>('loading')
  const [zoomError, setZoomError] = useState<string | null>(null)
  const root = useRef<HTMLDivElement>(null)
  const client = useRef<ZoomEmbeddedClient | null>(null)
  const started = useRef(false)

  useEffect(() => {
    joinOnce(Number(sessionId))
      .then(setJoin)
      .catch((e) => setError(e.message))
  }, [sessionId])

  useEffect(() => {
    // started is set at once (client only after Zoom loads), so React running this twice in development starts
    // one client. Leaving reloads the page, so there is nothing to clean up here.
    if (!join?.embedded || !root.current || started.current) return
    started.current = true
    const element = root.current

    // Zoom's window floats and can be dragged by default; pin it to the top-left of our box at our box's size.
    const size = videoSize(element)
    element.style.minHeight = `${size.height}px`

    const enter = async (c: ZoomEmbeddedClient) => {
      try {
        await c.join({
          signature: join.signature!,
          meetingNumber: join.meetingNumber!,
          password: join.passcode ?? '',
          userName: join.userName,
          userEmail: join.userEmail,
          ...(join.zak ? { zak: join.zak } : {}),
        })
        setZoom('in')
        // The init sizes only apply to some views; set them again now the meeting is showing, and on resizing.
        const fit = () => {
          const now = videoSize(element)
          element.style.minHeight = `${now.height}px`
          c.updateVideoOptions({ viewSizes: { default: now, ribbon: now } })
        }
        fit()
        window.addEventListener('resize', fit)
      } catch (e: unknown) {
        const reason = String((e as { reason?: string })?.reason ?? (e instanceof Error ? e.message : ''))
        if (/not started/i.test(reason)) {
          // Normal for learners who arrive before the host: try again until the host starts the meeting.
          setZoom('waiting')
          setTimeout(() => enter(c), 10_000)
        } else if (/other meetings in progress/i.test(reason)) {
          setZoom('busy')
        } else {
          setZoom('failed')
          setZoomError(reason || 'Zoom did not start')
        }
      }
    }

    loadZoom()
      .then(async (Zoom) => {
        const c = Zoom.createClient()
        client.current = c
        await c.init({
          zoomAppRoot: element,
          language: 'en-US',
          patchJsMedia: true,
          leaveOnPageUnload: true,
          customize: {
            video: {
              isResizable: false,
              popper: { disableDraggable: true, anchorPosition: { top: 0, left: 0 } },
              // viewSizes.default covers the speaker and gallery views, so start in speaker view, not Zoom's
              // small ribbon (SuspensionViewType is a const enum: its value is the string).
              defaultViewType: 'speaker',
              viewSizes: { default: size, ribbon: size },
            },
            meeting: { popper: { anchorElement: element, placement: 'top-start' } },
          },
        })
        await enter(c)
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
          {join.zoomConnected && !join.embedded ? (
            <div className="zoom-offline">
              <strong>This session runs in Zoom</strong>
              <p className="muted">
                {s.host
                  ? 'Start the meeting in Zoom: it opens in a new tab, already signed in as the host. Keep this page open for the register.'
                  : 'Join the meeting in Zoom: it opens in a new tab, in the Zoom app or your browser. You do not need a Zoom account.'}
              </p>
              <p>
                <a className="btn btn-primary" href={join.zoomUrl ?? undefined} target="_blank" rel="noopener noreferrer">
                  {s.host ? 'Start the meeting in Zoom' : 'Join the meeting in Zoom'}
                </a>
              </p>
              <p className="fine">
                Meeting ID {join.meetingNumber}
                {join.passcode ? ` · passcode ${join.passcode}` : ''}
              </p>
            </div>
          ) : join.zoomConnected ? (
            <>
              {zoom === 'loading' && <p className="muted">Connecting to Zoom…</p>}
              {zoom === 'waiting' && (
                <p className="banner-teal" role="status">
                  Waiting for {s.trainerName} to start the session. You will be let in automatically.
                </p>
              )}
              {zoom === 'busy' && (
                <p className="error" role="alert">
                  You already have a Zoom meeting running. End it in Zoom (End meeting for all), then reload this page.
                </p>
              )}
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

const clock = new Intl.DateTimeFormat('en-GB', { hour: '2-digit', minute: '2-digit' })

/**
 * Who has joined, and the register. Joining is recorded when a booked learner opens the session here; it shows
 * they joined, not how long they stayed, so the host still confirms attendance by ticking. Refreshes every 15
 * seconds while the page is open, so new arrivals appear without reloading.
 */
function Register({ sessionId }: { sessionId: number }) {
  const [register, setRegister] = useState<LiveRegister | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    let stopped = false
    const load = () =>
      api
        .liveRegister(sessionId)
        .then((r) => !stopped && setRegister(r))
        .catch((e) => !stopped && setError(e.message))
    load()
    const timer = setInterval(load, 15_000)
    return () => {
      stopped = true
      clearInterval(timer)
    }
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

  async function tickJoined() {
    setError(null)
    setBusy(true)
    try {
      setRegister(await api.markJoinedPresent(sessionId))
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Something went wrong')
    } finally {
      setBusy(false)
    }
  }

  const learners = register?.learners ?? []
  const joined = learners.filter((l) => l.joinedAt)
  const toTick = joined.filter((l) => !l.present && !l.certificateIssued)

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
      ) : learners.length === 0 ? (
        <p className="muted">No one has booked this cohort.</p>
      ) : (
        <>
          <p className="joined-count" aria-live="polite">
            <strong>
              Joined: {joined.length} of {learners.length}
            </strong>{' '}
            <span className="muted">booked</span>
          </p>
          <ul className="register">
            {learners.map((l) => (
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
                <span className={l.joinedAt ? 'joined' : 'muted'}>
                  {l.joinedAt ? `joined ${clock.format(new Date(l.joinedAt))}` : 'not joined yet'}
                </span>
              </li>
            ))}
          </ul>
          {toTick.length > 0 && (
            <button className="btn" disabled={busy} onClick={tickJoined}>
              Tick everyone who joined ({toTick.length})
            </button>
          )}
          <p className="fine">
            Ticks count towards attendance and the certificate. Joined means they opened the session here, so check
            they stayed before ticking.
          </p>
        </>
      )}
    </section>
  )
}
