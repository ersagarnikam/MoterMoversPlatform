import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from 'react'

/**
 * Feedback that appears where people are looking: short messages in the corner of the screen after an action (so a
 * "Saved" at the top of a long page is never missed), and confirmations in the page instead of the browser's own
 * pop-up, which cannot be styled and reads poorly with screen readers.
 */

type Tone = 'ok' | 'error'
interface Toast {
  id: number
  message: string
  tone: Tone
}
interface Confirmation {
  title: string
  body?: string
  confirmLabel: string
  cancelLabel?: string
  danger?: boolean
}
interface Feedback {
  toast: (message: string, tone?: Tone) => void
  confirm: (c: Confirmation) => Promise<boolean>
}

const FeedbackContext = createContext<Feedback | null>(null)

/** How long a message stays; errors stay longer, and every message can be closed. */
const SHOWN_MS = { ok: 6000, error: 10000 }

export function FeedbackProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([])
  const [asking, setAsking] = useState<(Confirmation & { resolve: (ok: boolean) => void }) | null>(null)
  const next = useRef(1)
  const dialog = useRef<HTMLDialogElement>(null)

  const dismiss = useCallback((id: number) => setToasts((all) => all.filter((t) => t.id !== id)), [])

  const toast = useCallback(
    (message: string, tone: Tone = 'ok') => {
      const id = next.current++
      setToasts((all) => [...all.slice(-2), { id, message, tone }])
      setTimeout(() => dismiss(id), SHOWN_MS[tone])
    },
    [dismiss],
  )

  const confirm = useCallback((c: Confirmation) => new Promise<boolean>((resolve) => setAsking({ ...c, resolve })), [])

  useEffect(() => {
    const d = dialog.current
    if (asking && d && !d.open) d.showModal()
  }, [asking])

  function answer(ok: boolean) {
    asking?.resolve(ok)
    setAsking(null)
    dialog.current?.close()
  }

  return (
    <FeedbackContext.Provider value={{ toast, confirm }}>
      {children}
      <div className="toasts" role="status" aria-live="polite">
        {toasts.map((t) => (
          <div key={t.id} className={`toast toast-${t.tone}`} role={t.tone === 'error' ? 'alert' : undefined}>
            <span>{t.message}</span>
            <button className="toast-close" aria-label="Close message" onClick={() => dismiss(t.id)}>
              ×
            </button>
          </div>
        ))}
      </div>
      <dialog
        ref={dialog}
        className="confirm"
        aria-labelledby="confirm-title"
        onCancel={(e) => {
          e.preventDefault()
          answer(false)
        }}
      >
        {asking && (
          <>
            <h2 id="confirm-title">{asking.title}</h2>
            {asking.body && <p>{asking.body}</p>}
            <div className="actions">
              <button className="btn" onClick={() => answer(false)} autoFocus>
                {asking.cancelLabel ?? 'Keep it'}
              </button>
              <button className={asking.danger ? 'btn btn-danger' : 'btn btn-primary'} onClick={() => answer(true)}>
                {asking.confirmLabel}
              </button>
            </div>
          </>
        )}
      </dialog>
    </FeedbackContext.Provider>
  )
}

function useFeedback(): Feedback {
  const f = useContext(FeedbackContext)
  if (!f) throw new Error('FeedbackProvider is missing')
  return f
}

/** A setter that shows a message instead of storing it; null (clearing a message) does nothing. */
export function useToast(): (message: string | null, tone?: Tone) => void {
  const { toast } = useFeedback()
  return useCallback((message: string | null, tone?: Tone) => message && toast(message, tone), [toast])
}

export function useConfirm(): Feedback['confirm'] {
  return useFeedback().confirm
}
