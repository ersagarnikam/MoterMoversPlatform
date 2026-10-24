// Thin client for the Spring Boot API. The token is kept in sessionStorage so it ends with the tab.

export type Role = 'TEACHER' | 'HEAD' | 'TRUST_LEAD' | 'PRACTITIONER' | 'PARENT' | 'ADMIN'
export type ColourStage = 'RED' | 'ORANGE' | 'YELLOW' | 'GREEN' | 'BLUE' | 'INDIGO' | 'VIOLET'

export interface CurrentUser {
  id: number
  email: string
  displayName: string
  role: Role
  schoolId: number | null
  trustId: number | null
}

export interface ClassSummary {
  id: number
  name: string
  yearGroup: string
  schoolId: number
  schoolName: string
  teacherName: string | null
  stage: ColourStage
  stageName: string
}

export interface SessionCard {
  id: number
  label: string
  title: string
  focus: string
  durationMinutes: number
  videoUrl: string | null
  activities: string[]
  rhyme: { id: number; title: string; audioUrl: string | null; lines: string[] } | null
}

export interface RosterEntry {
  id: number
  /** The full name when the school shows full names, otherwise the initials. */
  name: string
  initials: string
  yearGroup: string
  currentStage: ColourStage | null
}

export interface Today {
  schoolClass: ClassSummary
  stageLicensed: boolean
  inductionComplete: boolean
  nextSession: SessionCard | null
  doneToday: boolean
  sessionsDone: number
  counterDays: number
  counterGrid: boolean[]
  roster: RosterEntry[]
  fullNamesEnabled: boolean
}

export interface ScreeningItem {
  code: string
  position: number
  area: string
  areaLabel: string
  title: string
  instructions: string
  levels: Record<string, string>
}

export interface Score {
  clientId: string
  childId: number
  itemCode: string
  score: number
  screenedOn: string
}

export interface Profile {
  childId: number
  reference: string
  name: string
  className: string
  banner: string
  screened: boolean
  suggestedStage: ColourStage | null
  suggestedStageName: string | null
  currentStage: ColourStage | null
  areas: { area: string; label: string; fraction: number; band: string; bandLabel: string }[]
  history: { round: number; screenedOn: string; overallFraction: number }[]
}

export interface SchoolDashboard {
  schoolId: number
  schoolName: string
  childrenScreened: number
  childrenTotal: number
  movedUpRate: number
  dailyParticipation: number
  classesActive: number
  classesTotal: number
  windowDays: number
  flagThreshold: number
  classes: { classId: number; name: string; stageName: string; participation: number; flagged: boolean }[]
}

export interface TrustDashboard {
  trustId: number
  trustName: string
  schools: {
    schoolId: number
    schoolName: string
    licenceStatus: 'ACTIVE' | 'RENEWAL_DUE' | 'FREE_TRIAL' | 'EXPIRED'
    plan: string | null
    renewalDate: string | null
    stages: string
    screenedRate: number
    participation: number
  }[]
}

export interface Course {
  id: number
  track: 'PROFESSIONAL' | 'PARENT'
  title: string
  description: string
  liveSessions: number
  sessionMinutes: number
  pricePence: number
  renewalPence: number | null
  awardsCertificate: boolean
  cohorts: { id: number; name: string; startsOn: string; timeSlot: string; trainerName: string; seatsLeft: number; recordingAgreed: boolean }[]
}

export interface Enrolment {
  id: number
  cohortId: number
  courseTitle: string
  track: 'PROFESSIONAL' | 'PARENT'
  cohortName: string
  startsOn: string
  timeSlot: string
  sessionsAttended: number
  liveSessions: number
  assessmentPassed: boolean
  certificateIssuedOn: string | null
  certificateExpiresOn: string | null
  learnerName: string
  learnerEmail: string
  /** The numbers of the live sessions the learner was marked present at, e.g. [1, 2]. */
  attendedSessions: number[]
}

export interface Induction {
  lessons: {
    id: number
    position: number
    title: string
    summary: string
    paragraphs: string[]
    minutes: number
    videoUrl: string | null
    done: boolean
  }[]
  totalMinutes: number
  lessonsDone: number
  completedOn: string | null
}

export interface LibraryResource {
  id: number
  kind: 'RHYME' | 'BOOK' | 'PRINTABLE'
  title: string
  description: string
  stage: ColourStage | null
  label: string
  fileUrl: string | null
  lines: string[]
}

export interface ContentItem {
  id: number
  stage: ColourStage | null
  label: string
  title: string
  focus: string
  durationMinutes: number
  activities: string[]
  status: 'DRAFT' | 'APPROVED' | 'REJECTED'
  reviewedBy: string | null
  reviewedAt: string | null
  wordingIssues: { text: string; term: string }[]
}

export interface SchoolRow {
  id: number
  name: string
  trustName: string | null
  region: string
  plan: string | null
  planCode: string | null
  /** What the school pays a year on its current licence, and that plan's price today. */
  agreedPricePence: number | null
  listPricePence: number | null
  licenceStatus: string
  endsOn: string | null
  stages: ColourStage[]
  parentLinksEnabled: boolean
}

export interface PriceRow {
  effectiveFrom: string
  pricePence: number
  changedBy: string
  changedAt: string
}

/** A licence plan as the team manages it. nextPrice is a change scheduled for a later date. */
export interface Plan {
  code: string
  label: string
  freeTrial: boolean
  active: boolean
  currentPricePence: number
  nextPrice: PriceRow | null
  licences: number
  history: PriceRow[]
}

export interface ParentInvite {
  code: string
  childName: string
  className: string
  expiresOn: string
}

export interface ParentLinkRow {
  linkId: number
  childId: number
  childName: string
  className: string
  parentName: string
  parentEmail: string
  linkedAt: string
}

export interface StaffMember {
  id: number
  email: string
  displayName: string
  role: Role
  enabled: boolean
  inductionCompletedOn: string | null
}

export interface SetupClass {
  id: number
  name: string
  yearGroup: string
  stage: ColourStage
  teacherId: number | null
  teacherName: string | null
  children: number
}

export interface SchoolSettings {
  fullNamesEnabled: boolean
  videoEnabled: boolean
  trustCanSeeChildren: boolean
}

export interface SchoolSetup {
  schoolId: number
  schoolName: string
  settings: SchoolSettings
  staff: StaffMember[]
  classes: SetupClass[]
}

export interface SetupChild {
  id: number
  initials: string
  fullName: string | null
  yearGroup: string
  currentStage: ColourStage | null
}

export interface ClassInput {
  name: string
  yearGroup: string
  stage: ColourStage
  teacherId: number | null
}

export interface NewSchool {
  name: string
  trustId: number | null
  /** A plan code. agreedPricePence null means the plan's price on the start date. */
  plan: string
  agreedPricePence: number | null
  stages: ColourStage[]
  startsOn: string
  endsOn: string
}

export interface TrustRow {
  id: number
  name: string
  leads: StaffMember[]
}

/** The kinds of content Pat and Laura sign off. All share the ContentItem shape. */
export type ContentKind = 'content' | 'home-activities' | 'guidance' | 'resources' | 'induction'

export type CourseTrack = 'PROFESSIONAL' | 'PARENT'

export interface CourseInput {
  track: CourseTrack
  title: string
  description: string
  liveSessions: number
  sessionMinutes: number
  pricePence: number
  renewalPence: number | null
  awardsCertificate: boolean
}

export interface CohortInput {
  name: string
  startsOn: string
  /** "19:00": when each weekly live session starts, UK time. */
  startTime: string
  timeSlot: string
  capacity: number
  trainerName: string
  recordingAgreed: boolean
}

/** A course as the team manages it: every cohort, past ones too, with places booked. */
export interface AdminCourse extends CourseInput {
  id: number
  cohorts: (CohortInput & { id: number; booked: number; sessions: SessionSummary[] })[]
}

/** One dated live session of a cohort. startsAt is an ISO instant. */
export interface SessionSummary {
  id: number
  number: number
  startsAt: string
  durationMinutes: number
}

/** A live session as someone who may join it sees it. host is true for the Motor Movers team. */
export interface LiveSession {
  id: number
  cohortId: number
  courseTitle: string
  cohortName: string
  number: number
  of: number
  startsAt: string
  durationMinutes: number
  trainerName: string
  open: boolean
  host: boolean
}

/**
 * What the room needs. Without Zoom connected, the Zoom fields are null and the session runs without video. With
 * embedded, the meeting shows in the page (Meeting SDK); otherwise zoomUrl opens it in Zoom.
 */
export interface LiveJoin {
  session: LiveSession
  zoomConnected: boolean
  embedded: boolean
  zoomUrl: string | null
  sdkKey: string | null
  signature: string | null
  meetingNumber: string | null
  passcode: string | null
  zak: string | null
  userName: string
  userEmail: string
  rules: string
  recorded: boolean
}

export interface LiveRegister {
  session: LiveSession
  learners: {
    enrolmentId: number
    learnerName: string
    learnerEmail: string
    present: boolean
    sessionsAttended: number
    certificateIssued: boolean
    /** When the learner first opened this session; null if they have not. */
    joinedAt: string | null
    lastJoinedAt: string | null
  }[]
}

export type PayerType = 'SCHOOL' | 'PARENT' | 'INDIVIDUAL'
export type PaymentPurpose = 'LICENCE' | 'COURSE' | 'OTHER'

/** One recorded payment. Amounts are in pence; payerEmail is null when a school pays. */
export interface Payment {
  id: number
  payerType: PayerType
  payerName: string
  payerEmail: string | null
  schoolId: number | null
  purpose: PaymentPurpose
  purposeLabel: string
  description: string
  amountPence: number
  status: 'DUE' | 'PAID' | 'CANCELLED'
  invoicedOn: string
  paidOn: string | null
  reference: string | null
}

/** "Paid" is money received; "outstanding" is invoiced and not yet paid. */
export interface Totals {
  paidPence: number
  outstandingPence: number
}

export interface SchoolTotal extends Totals {
  schoolId: number
  schoolName: string
  trustName: string | null
}

export interface PaymentSummary extends Totals {
  paidLast12MonthsPence: number
  byPayerType: (Totals & { key: PayerType; label: string; payments: number })[]
  byPurpose: (Totals & { key: PaymentPurpose; label: string; payments: number })[]
  /** The last 12 months, oldest first; month is YYYY-MM. */
  byMonth: { month: string; paidPence: number }[]
  bySchool: SchoolTotal[]
  byTrust: (Totals & { trustId: number; trustName: string; schools: number })[]
  byPerson: (Totals & { userId: number; name: string; email: string; payerType: PayerType })[]
}

export interface NewPayment {
  schoolId: number | null
  payerEmail: string | null
  purpose: PaymentPurpose
  description: string
  amountPence: number
  invoicedOn: string
  reference: string | null
  paidOn: string | null
}

const TOKEN_KEY = 'mm.token'

export function getToken(): string | null {
  try {
    return sessionStorage.getItem(TOKEN_KEY)
  } catch {
    return null
  }
}

export function setToken(token: string | null) {
  try {
    if (token) sessionStorage.setItem(TOKEN_KEY, token)
    else sessionStorage.removeItem(TOKEN_KEY)
  } catch {
    // storage unavailable: the user signs in again next time
  }
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
  const headers: Record<string, string> = {}
  const token = getToken()
  if (token) headers.Authorization = `Bearer ${token}`
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  const res = await fetch(path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) })
  if (res.status === 401 && path !== '/api/auth/login') {
    setToken(null)
    window.location.assign('/login')
  }
  if (!res.ok) {
    let message = res.statusText
    try {
      const problem = await res.json()
      message = problem.detail ?? message
    } catch {
      // not JSON
    }
    throw new ApiError(res.status, message)
  }
  const type = res.headers.get('Content-Type') ?? ''
  return (type.includes('json') ? res.json() : res.text()) as Promise<T>
}

export const api = {
  login: (email: string, password: string) =>
    request<{ token: string; user: CurrentUser }>('POST', '/api/auth/login', { email, password }),
  me: () => request<CurrentUser>('GET', '/api/auth/me'),
  requestPasswordReset: (email: string) => request<void>('POST', '/api/auth/password-reset', { email }),
  setPassword: (token: string, password: string) =>
    request<{ email: string }>('POST', '/api/auth/password', { token, password }),
  schoolSetup: (schoolId: number) => request<SchoolSetup>('GET', `/api/schools/${schoolId}/setup`),
  saveSchoolSettings: (schoolId: number, settings: SchoolSettings) =>
    request<SchoolSettings>('PUT', `/api/schools/${schoolId}/settings`, settings),
  addStaff: (schoolId: number, staff: { email: string; displayName: string; role: Role }) =>
    request<StaffMember>('POST', `/api/schools/${schoolId}/staff`, staff),
  setStaffEnabled: (schoolId: number, userId: number, enabled: boolean) =>
    request<StaffMember>('POST', `/api/schools/${schoolId}/staff/${userId}/enabled`, { enabled }),
  resendInvite: (schoolId: number, userId: number) =>
    request<void>('POST', `/api/schools/${schoolId}/staff/${userId}/invite`),
  addClass: (schoolId: number, c: ClassInput) => request<SetupClass>('POST', `/api/schools/${schoolId}/classes`, c),
  updateClass: (schoolId: number, classId: number, c: ClassInput) =>
    request<SetupClass>('PUT', `/api/schools/${schoolId}/classes/${classId}`, c),
  classChildren: (schoolId: number, classId: number) =>
    request<SetupChild[]>('GET', `/api/schools/${schoolId}/classes/${classId}/children`),
  addChildren: (schoolId: number, classId: number, children: { initials: string; fullName: string | null }[]) =>
    request<SetupChild[]>('POST', `/api/schools/${schoolId}/classes/${classId}/children`, { children }),
  markChildLeft: (schoolId: number, childId: number, leftOn: string) =>
    request<void>('POST', `/api/schools/${schoolId}/children/${childId}/left`, { leftOn }),
  moveChild: (schoolId: number, childId: number, classId: number) =>
    request<SetupChild>('POST', `/api/schools/${schoolId}/children/${childId}/move`, { classId }),
  addSchool: (s: NewSchool) => request<SchoolRow>('POST', '/api/admin/schools', s),
  agreeSchoolPrice: (schoolId: number, pricePence: number) =>
    request<SchoolRow>('POST', `/api/admin/schools/${schoolId}/agreed-price`, { pricePence }),
  plans: () => request<Plan[]>('GET', '/api/admin/plans'),
  addPlan: (p: { label: string; freeTrial: boolean; pricePence: number }) =>
    request<Plan>('POST', '/api/admin/plans', p),
  updatePlan: (code: string, p: { label: string; active: boolean }) =>
    request<Plan>('PUT', `/api/admin/plans/${code}`, p),
  changePlanPrice: (code: string, pricePence: number, effectiveFrom: string) =>
    request<Plan>('POST', `/api/admin/plans/${code}/prices`, { pricePence, effectiveFrom }),
  trusts: () => request<TrustRow[]>('GET', '/api/admin/trusts'),
  addTrust: (name: string) => request<TrustRow>('POST', '/api/admin/trusts', { name }),
  addTrustLead: (trustId: number, lead: { email: string; displayName: string }) =>
    request<StaffMember>('POST', `/api/admin/trusts/${trustId}/leads`, lead),
  classes: () => request<ClassSummary[]>('GET', '/api/classes'),
  today: (classId: number) => request<Today>('GET', `/api/classes/${classId}/today`),
  completeSession: (classId: number, sessionId: number, absentChildIds: number[]) =>
    request<{ date: string; present: number; absent: number }>('POST', `/api/classes/${classId}/sessions`, {
      sessionId,
      absentChildIds,
    }),
  screeningItems: () => request<ScreeningItem[]>('GET', '/api/screening/items'),
  submitScores: (scores: Score[]) =>
    request<{ saved: number; duplicates: number }>('POST', '/api/screening/results', scores),
  profile: (childId: number) => request<Profile>('GET', `/api/children/${childId}/profile`),
  schoolDashboard: (schoolId: number) => request<SchoolDashboard>('GET', `/api/schools/${schoolId}/dashboard`),
  trustDashboard: (trustId: number) => request<TrustDashboard>('GET', `/api/trusts/${trustId}/dashboard`),
  exportCsv: (schoolId: number, name: 'governor-summary' | 'research') =>
    request<string>('GET', `/api/schools/${schoolId}/exports/${name}.csv`),
  courses: () => request<{ rules: string; courses: Course[] }>('GET', '/api/training/courses'),
  enrol: (cohortId: number) => request<Enrolment>('POST', `/api/training/cohorts/${cohortId}/enrol`),
  myEnrolments: () => request<Enrolment[]>('GET', '/api/training/enrolments/mine'),
  induction: () => request<Induction>('GET', '/api/training/induction'),
  completeLesson: (lessonId: number) =>
    request<Induction>('POST', `/api/training/induction/lessons/${lessonId}/complete`),
  resources: () => request<LibraryResource[]>('GET', '/api/resources'),
  content: (kind: ContentKind = 'content') => request<ContentItem[]>('GET', `/api/admin/${kind}`),
  /** New content is saved as a draft for sign-off. The fields depend on the kind (see ContentPage). */
  createContent: (kind: ContentKind, item: Record<string, unknown>) =>
    request<ContentItem>('POST', `/api/admin/${kind}`, item),
  adminCourses: () => request<AdminCourse[]>('GET', '/api/admin/courses'),
  addCourse: (c: CourseInput) => request<AdminCourse>('POST', '/api/admin/courses', c),
  updateCourse: (id: number, c: CourseInput) => request<AdminCourse>('PUT', `/api/admin/courses/${id}`, c),
  addCohort: (courseId: number, c: CohortInput) =>
    request<AdminCourse>('POST', `/api/admin/courses/${courseId}/cohorts`, c),
  updateCohort: (id: number, c: CohortInput) => request<AdminCourse>('PUT', `/api/admin/cohorts/${id}`, c),
  cohortBookings: (cohortId: number) => request<Enrolment[]>('GET', `/api/admin/cohorts/${cohortId}/enrolments`),
  liveSessions: () => request<LiveSession[]>('GET', '/api/training/live'),
  joinLive: (sessionId: number) => request<LiveJoin>('POST', `/api/training/live/${sessionId}/join`),
  liveRegister: (sessionId: number) => request<LiveRegister>('GET', `/api/admin/live/${sessionId}/register`),
  markJoinedPresent: (sessionId: number) =>
    request<LiveRegister>('POST', `/api/admin/live/${sessionId}/register/mark-joined`),
  markPresent: (sessionId: number, enrolmentId: number, present: boolean) =>
    request<Enrolment>('PUT', `/api/admin/live/${sessionId}/register/${enrolmentId}`, { present }),
  recordAssessment: (id: number, passed: boolean) =>
    request<Enrolment>('POST', `/api/admin/enrolments/${id}/assessment`, { passed }),
  approve: (id: number, kind: ContentKind = 'content') =>
    request<ContentItem>('POST', `/api/admin/${kind}/${id}/approve`),
  reject: (id: number, kind: ContentKind = 'content') =>
    request<ContentItem>('POST', `/api/admin/${kind}/${id}/reject`),
  payments: () => request<Payment[]>('GET', '/api/admin/payments'),
  paymentSummary: () => request<PaymentSummary>('GET', '/api/admin/payments/summary'),
  recordPayment: (p: NewPayment) => request<Payment>('POST', '/api/admin/payments', p),
  markPaid: (id: number, paidOn: string) => request<Payment>('POST', `/api/admin/payments/${id}/paid`, { paidOn }),
  cancelPayment: (id: number) => request<Payment>('POST', `/api/admin/payments/${id}/cancel`),
  schoolPayments: (schoolId: number) =>
    request<Totals & { schoolId: number; schoolName: string; payments: Payment[] }>(
      'GET',
      `/api/schools/${schoolId}/payments`,
    ),
  trustPayments: (trustId: number) =>
    request<Totals & { trustId: number; trustName: string; schools: SchoolTotal[] }>(
      'GET',
      `/api/trusts/${trustId}/payments`,
    ),
  myPayments: () => request<Totals & { payments: Payment[] }>('GET', '/api/payments/mine'),
  schools: () => request<SchoolRow[]>('GET', '/api/admin/schools'),
  setParentLinks: (schoolId: number, enabled: boolean) =>
    request<void>('POST', `/api/admin/schools/${schoolId}/parent-links`, { enabled }),
  createParentInvite: (childId: number) => request<ParentInvite>('POST', `/api/children/${childId}/parent-invites`),
  parentLinks: (schoolId: number) => request<ParentLinkRow[]>('GET', `/api/schools/${schoolId}/parent-links`),
  removeParentLink: (schoolId: number, linkId: number) =>
    request<void>('DELETE', `/api/schools/${schoolId}/parent-links/${linkId}`),
}
