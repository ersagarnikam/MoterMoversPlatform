// Client for the Motor Movers API, parent endpoints only.
import Constants from 'expo-constants'
import { Platform } from 'react-native'

export type ColourStage = 'RED' | 'ORANGE' | 'YELLOW' | 'GREEN' | 'BLUE' | 'INDIGO' | 'VIOLET'
export type Role = 'TEACHER' | 'HEAD' | 'TRUST_LEAD' | 'PRACTITIONER' | 'PARENT' | 'ADMIN'
export type Band = 'EMERGING' | 'DEVELOPING' | 'SECURE'

export interface CurrentUser {
  id: number
  email: string
  displayName: string
  role: Role
}

export interface LinkedChild {
  childId: number
  name: string
  schoolName: string
  className: string
  yearGroup: string
  currentStage: ColourStage | null
  currentStageName: string | null
  startingStage: ColourStage | null
  startingStageName: string | null
  movedUp: boolean
  linkedOn: string
}

export interface Progress {
  child: LinkedChild
  banner: string
  screened: boolean
  areas: { area: string; label: string; band: Band; bandLabel: string }[]
  screenings: string[]
  classSessionsDone: number
  counterDays: number
}

export interface Activity {
  id: number
  stage: ColourStage
  stageName: string
  position: number
  title: string
  focus: string
  durationMinutes: number
  videoUrl: string | null
  steps: string[]
}

export interface Home {
  stage: ColourStage
  stageName: string
  next: Activity | null
  activities: Activity[]
  doneToday: boolean
  daysDone: number
  counterDays: number
  counterGrid: boolean[]
  totalSessions: number
}

export interface HomeEntry {
  clientId: string
  activityId: number
  childId: number | null
  doneOn: string
}

export interface ArticleSummary {
  id: number
  title: string
  summary: string
  stage: ColourStage | null
  stageName: string | null
  minutesToRead: number
}

export interface Article extends ArticleSummary {
  paragraphs: string[]
}

export interface Course {
  id: number
  track: 'PROFESSIONAL' | 'PARENT'
  title: string
  description: string
  liveSessions: number
  sessionMinutes: number
  pricePence: number
  awardsCertificate: boolean
  cohorts: { id: number; name: string; startsOn: string; timeSlot: string; trainerName: string; seatsLeft: number; recordingAgreed: boolean }[]
}

export interface Enrolment {
  id: number
  cohortId: number
  courseTitle: string
  cohortName: string
  startsOn: string
  timeSlot: string
  sessionsAttended: number
  liveSessions: number
}

/** A live session of a booked course. open: it can be joined now (from 15 minutes before the start). */
export interface LiveSession {
  id: number
  courseTitle: string
  cohortName: string
  number: number
  of: number
  /** An ISO instant. */
  startsAt: string
  durationMinutes: number
  trainerName: string
  open: boolean
}

/** Joining a live session. zoomUrl opens the meeting in the Zoom app; null while Zoom is not connected. */
export interface LiveJoin {
  zoomConnected: boolean
  zoomUrl: string | null
  rules: string
}

/** One of the signed-in person's own payments. Amounts are in pence. */
export interface Payment {
  id: number
  purposeLabel: string
  description: string
  amountPence: number
  status: 'DUE' | 'PAID' | 'CANCELLED'
  invoicedOn: string
  paidOn: string | null
  reference: string | null
}

/** "Paid" is money received; "outstanding" is invoiced and not yet paid. */
export interface MyPayments {
  paidPence: number
  outstandingPence: number
  payments: Payment[]
}

/**
 * Where the API lives. Set EXPO_PUBLIC_API_URL for real environments. In development the app uses
 * the machine running the Expo dev server, so a phone on the same Wi-Fi finds the local API.
 */
function resolveBaseUrl(): string {
  const configured = process.env.EXPO_PUBLIC_API_URL
  if (configured) return configured.replace(/\/$/, '')
  const devHost = Constants.expoConfig?.hostUri?.split(':')[0]
  if (Platform.OS !== 'web' && devHost) return `http://${devHost}:8080`
  if (Platform.OS === 'android') return 'http://10.0.2.2:8080'
  return 'http://localhost:8080'
}

export const API_URL = resolveBaseUrl()

let token: string | null = null
let onUnauthorized: (() => void) | null = null

export function setApiToken(value: string | null) {
  token = value
}

/** Called when the API rejects the saved sign-in, e.g. after it expires. */
export function setUnauthorizedHandler(handler: (() => void) | null) {
  onUnauthorized = handler
}

export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message)
  }
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (token) headers.Authorization = `Bearer ${token}`
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  let res: Response
  try {
    res = await fetch(API_URL + path, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch {
    throw new ApiError(0, 'Could not reach Motor Movers. Check your connection and try again.')
  }
  if (res.status === 401 && token && !path.startsWith('/api/auth/')) {
    onUnauthorized?.()
  }
  if (!res.ok) {
    let message = res.status === 401 ? 'Email or password not recognised' : `Something went wrong (${res.status})`
    try {
      const problem = await res.json()
      if (problem?.detail && problem.detail !== 'Invalid request content.') message = problem.detail
    } catch {
      // not JSON
    }
    throw new ApiError(res.status, message)
  }
  if (res.status === 204) return undefined as T
  return (await res.json()) as T
}

export const api = {
  login: (email: string, password: string) =>
    request<{ token: string; user: CurrentUser }>('POST', '/api/auth/login', { email, password }),
  register: (displayName: string, email: string, password: string) =>
    request<{ token: string; user: CurrentUser }>('POST', '/api/auth/register', { displayName, email, password }),
  me: () => request<CurrentUser>('GET', '/api/auth/me'),
  requestPasswordReset: (email: string) => request<void>('POST', '/api/auth/password-reset', { email }),

  children: () => request<LinkedChild[]>('GET', '/api/family/children'),
  linkChild: (code: string) => request<LinkedChild>('POST', '/api/family/children', { code }),
  unlinkChild: (childId: number) => request<void>('DELETE', `/api/family/children/${childId}`),
  progress: (childId: number) => request<Progress>('GET', `/api/family/children/${childId}/progress`),

  home: (params: { stage?: ColourStage; childId?: number }) => {
    const query = new URLSearchParams()
    if (params.stage) query.set('stage', params.stage)
    if (params.childId) query.set('childId', String(params.childId))
    const qs = query.toString()
    return request<Home>('GET', `/api/family/home${qs ? `?${qs}` : ''}`)
  },
  activity: (id: number) => request<Activity>('GET', `/api/family/activities/${id}`),
  recordHome: (entries: HomeEntry[]) =>
    request<{ saved: number; duplicates: number }>('POST', '/api/family/home/sessions', entries),

  guidance: () => request<ArticleSummary[]>('GET', '/api/family/guidance'),
  article: (id: number) => request<Article>('GET', `/api/family/guidance/${id}`),

  courses: () => request<{ rules: string; courses: Course[] }>('GET', '/api/training/courses'),
  enrol: (cohortId: number) => request<Enrolment>('POST', `/api/training/cohorts/${cohortId}/enrol`),
  myEnrolments: () => request<Enrolment[]>('GET', '/api/training/enrolments/mine'),
  liveSessions: () => request<LiveSession[]>('GET', '/api/training/live'),
  /** Records that the learner joined, for the trainer's register. */
  joinLive: (sessionId: number) => request<LiveJoin>('POST', `/api/training/live/${sessionId}/join`),

  myPayments: () => request<MyPayments>('GET', '/api/payments/mine'),
}

/** 4500 -> "£45", 1550 -> "£15.50" */
export function gbp(pence: number): string {
  return `£${pence % 100 === 0 ? pence / 100 : (pence / 100).toFixed(2)}`
}

/** Today's date on this phone, as the API expects it (YYYY-MM-DD). */
export function localDate(date = new Date()): string {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

/** "2026-10-23" -> "Thu 23 Oct" */
export function formatDate(iso: string): string {
  const [y, m, d] = iso.split('-').map(Number)
  const date = new Date(y, m - 1, d)
  const days = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat']
  const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']
  return `${days[date.getDay()]} ${d} ${months[m - 1]}`
}

/** A live session's start in the phone's own time zone, e.g. "Tue 3 Nov, 19:00". */
export function sessionTime(iso: string): string {
  const date = new Date(iso)
  const day = formatDate(
    `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`,
  )
  return `${day}, ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}

export function newClientId(): string {
  const c = globalThis.crypto as { randomUUID?: () => string } | undefined
  if (c?.randomUUID) return c.randomUUID()
  const rand = () => Math.random().toString(36).slice(2, 10)
  return `${Date.now().toString(36)}-${rand()}-${rand()}-${rand()}`
}
