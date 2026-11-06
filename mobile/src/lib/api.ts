// Client for the Motor Movers API, parent endpoints only.
import Constants from 'expo-constants'
import { Platform } from 'react-native'

export type ColourStage = 'RED' | 'ORANGE' | 'YELLOW' | 'GREEN' | 'BLUE' | 'INDIGO' | 'VIOLET'
export type Role = 'TEACHER' | 'HEAD' | 'TRUST_LEAD' | 'PRACTITIONER' | 'PARENT' | 'ADMIN'

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

/** Movements secure on one of Pat's charts, and those secured in the last few months. */
export interface ChartProgress {
  chart: 'FINE' | 'GROSS'
  label: string
  secure: number
  total: number
  recentlySecure: string[]
}

/** An exercise's first and latest rating in the parent's words. */
export interface ExerciseProgress {
  exerciseId: number
  number: number
  title: string
  first: string
  latest: string
  improved: boolean
}

export interface Progress {
  child: LinkedChild
  banner: string
  classSessionsDone: number
  counterDays: number
  /** The school's assessments, in plain words (never the scores or the extra-support flag). */
  charts: ChartProgress[]
  exerciseStage: ColourStage | null
  exerciseStageName: string | null
  exercises: ExerciseProgress[]
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

// --- Motor Movers at Home ------------------------------------------------------------------------------------------

export interface HomeChild {
  id: number
  name: string
  ageMonths: number
  stage: ColourStage | null
  stageName: string | null
  checkedOn: string | null
  /** The starting check has not been done, or was done a few months ago. */
  checkDue: boolean
}

export interface HomeOverview {
  /** The home programme is switched on for this deployment. */
  enabled: boolean
  children: HomeChild[]
  members: { userId: number; name: string; owner: boolean; you: boolean }[]
  owner: boolean
  maxChildren: number
}

export type CheckAnswer = 'YES' | 'NOT_YET' | 'NOT_SURE'

export interface HomeCheck {
  childId: number
  name: string
  ageMonths: number
  banner: string
  questions: {
    milestoneId: number
    chart: 'FINE' | 'GROSS'
    chartLabel: string
    band: string
    description: string
    secure: boolean
  }[]
}

export interface CheckResult {
  /** The colour to start at, from the child's age (the answers are kept as secure movements). */
  suggested: ColourStage
  suggestedName: string
  yes: number
  notYet: number
  notSure: number
  explanation: string
}

export interface HomeExercise {
  id: number
  stage: ColourStage
  number: number
  title: string
  instructions: string
  /** Pat's description of each score, 1 to 4. */
  levels: Record<string, string>
  points: { point: string; label: string }[]
}

export interface HomeToday {
  childId: number
  name: string
  stage: ColourStage
  stageName: string
  /** Pat's exercises exist for this colour; otherwise the colour's home activities are used. */
  hasExercises: boolean
  /** A rhyme colour (Red, Orange, Yellow): module is the rhyme, done five times in a week. */
  rhymes: boolean
  module: number
  modules: number
  /** The week within a module, which builds up over `weeks` (1 for a rhyme). */
  week: number
  weeks: number
  sessionInModule: number
  sessionsPerModule: number
  colourComplete: boolean
  /** "Module 2 of 6 · week 3 of 5", "Rhyme 4 of 18 · session 2 of 5" or the colour done. */
  progressLabel: string
  /** "Module 2 · week 3", "Rhyme 4 · session 2". */
  sessionTitle: string
  exercises: HomeExercise[]
  /** The exercises added to the session this week. */
  newThisWeek: number[]
  /** Exercises to rate after doing them for the first time. */
  firstTime: number[]
  /** False for a child linked to a school: the teacher does the rating. */
  canRate: boolean
  doneToday: boolean
  counterGrid: boolean[]
  daysDone: number
  counterDays: number
  sessionsDone: number
  review: { point: string; label: string; exercises: HomeExercise[] } | null
  checkDue: boolean
  support: { show: boolean; message: string | null }
  levels: { score: number; label: string }[]
  /** Pat's safety note, shown before the first session. */
  safetyNote: string
  /** Ends a session from Green to Indigo; a rhyme ends with its own, in its instructions. */
  coolDown: { title: string; text: string } | null
}

export interface HomeSessionEntry {
  clientId: string
  childId: number
  doneOn: string
}

export interface ExerciseRating {
  clientId: string
  childId: number
  exerciseId: number
  point: string
  score: number
  assessedOn: string
}

export interface HomeProgress {
  child: HomeChild
  banner: string
  charts: ChartProgress[]
  stage: ColourStage | null
  stageName: string | null
  exercises: ExerciseProgress[]
  sessionsDone: number
  support: { show: boolean; message: string | null }
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
  /** The tax included in amountPence; null on payments from before it was kept. */
  taxPence: number | null
  refundedPence: number
  /** "Refunded" or "Part refunded"; null with nothing refunded. */
  refundLabel: string | null
  refunds: { id: number; amountPence: number; taxPence: number | null; refundedOn: string; creditNote: string; failed: boolean }[]
  id: number
  purposeLabel: string
  description: string
  amountPence: number
  status: 'DUE' | 'PAID' | 'CANCELLED'
  invoicedOn: string
  paidOn: string | null
  reference: string | null
  /** Paid by card on the platform. */
  paidOnline: boolean
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
  /** Whether Pay by card is switched on. */
  onlinePayments: () => request<{ enabled: boolean }>('GET', '/api/payments/online'),
  /** The address of the payment provider's page to pay this payment by card. */
  checkout: (paymentId: number) => request<{ url: string }>('POST', `/api/payments/${paymentId}/checkout`),
  // Motor Movers at Home: a family's own children, not linked to a school.
  homeOverview: () => request<HomeOverview>('GET', '/api/home'),
  addHomeChild: (name: string, ageMonths: number) =>
    request<HomeChild>('POST', '/api/home/children', { name, ageMonths }),
  updateHomeChild: (id: number, name: string, ageMonths: number) =>
    request<HomeChild>('PUT', `/api/home/children/${id}`, { name, ageMonths }),
  deleteHomeChild: (id: number) => request<void>('DELETE', `/api/home/children/${id}`),
  homeProgress: (id: number) => request<HomeProgress>('GET', `/api/home/children/${id}`),
  homeCheck: (id: number) => request<HomeCheck>('GET', `/api/home/children/${id}/check`),
  submitHomeCheck: (id: number, answers: { milestoneId: number; answer: CheckAnswer }[]) =>
    request<CheckResult>('POST', `/api/home/children/${id}/check`, { answers }),
  setHomeStage: (id: number, stage: ColourStage) =>
    request<HomeChild>('PUT', `/api/home/children/${id}/stage`, { stage }),
  homeToday: (id: number) => request<HomeToday>('GET', `/api/home/children/${id}/today`),
  recordHomeSessions: (sessions: HomeSessionEntry[]) =>
    request<{ saved: number; duplicates: number }>('POST', '/api/home/sessions', sessions),
  /** Ratings use the assessment endpoint, which checks that the child is the family's. */
  rateExercises: (ratings: ExerciseRating[]) =>
    request<{ saved: number; duplicates: number }>('POST', '/api/assessment/exercise-scores', ratings),
  /** With an email address the code is also emailed there, with how to use it. */
  inviteCarer: (email?: string) =>
    request<{ code: string; expiresOn: string; sentTo: string | null }>(
      'POST',
      '/api/home/invites',
      email ? { email } : undefined,
    ),
  joinFamily: (code: string) => request<HomeOverview>('POST', '/api/home/join', { code }),
  removeFamilyMember: (userId: number) => request<void>('DELETE', `/api/home/members/${userId}`),
  joinSchool: (id: number, code: string) =>
    request<LinkedChild>('POST', `/api/home/children/${id}/join-school`, { code }),

  /** Open to anyone: the server's country settings (currency, tax, year names). */
  settings: () =>
    request<{ currency: string; taxLabel: string; legalLine: string | null }>('GET', '/api/settings'),

  /** Emails the credit note to the parent: the app cannot save a file. */
  emailCreditNote: (refundId: number) => request<void>('POST', `/api/payments/refunds/${refundId}/credit-note/email`),

  registerPushToken: (token: string, platform: string) =>
    request<void>('POST', '/api/me/push-tokens', { token, platform }),
  removePushToken: (token: string) => request<void>('POST', '/api/me/push-tokens/remove', { token }),
}

/** The symbols of the deployments' currencies (go-live task 25); another currency shows its code. */
const CURRENCY_SYMBOLS: Record<string, string> = { GBP: '£', EUR: '€', AUD: '$' }

let currencySymbol = '£'
let taxLabel = 'VAT'
let legal: string | null = null

/** The server's currency, tax label and legal entity from GET /api/settings; until known, amounts show in pounds. */
export function setCountry(settings: { currency: string; taxLabel: string; legalLine: string | null }): void {
  currencySymbol = CURRENCY_SYMBOLS[settings.currency] ?? `${settings.currency} `
  taxLabel = settings.taxLabel
  legal = settings.legalLine
}

/** Who the invoices come from, in one line; null while the legal entity is a placeholder. */
export function legalLine(): string | null {
  return legal
}

/** Amounts in the minor unit (pence or cents): 4500 -> "£45", 1550 -> "£15.50", 175000 -> "£1,750". */
export function money(minor: number): string {
  const units = minor % 100 === 0 ? String(minor / 100) : (minor / 100).toFixed(2)
  return currencySymbol + units.replace(/B(?=(d{3})+(?!d))/g, ',')
}

/** "includes VAT £75"; empty for a payment with no tax kept or none in it. */
export function taxLine(taxPence: number | null | undefined): string {
  return taxPence ? `includes ${taxLabel} ${money(taxPence)}` : ''
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
