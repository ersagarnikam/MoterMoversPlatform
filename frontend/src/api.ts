// Thin client for the Spring Boot API. The token is kept in sessionStorage so it ends with the tab.

export type Role = 'TEACHER' | 'HEAD' | 'TRUST_LEAD' | 'PRACTITIONER' | 'PARENT' | 'ADMIN' | 'TRAINER'
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

// Pat's two assessments (docs/assessment-design.md): developmental charts and 1 to 4 exercise scores.
export type Chart = 'FINE' | 'GROSS'
export type YearPoint = 'START' | 'MIDDLE' | 'END'

export interface AssessmentExercise {
  id: number
  stage: ColourStage
  number: number
  title: string
  instructions: string
  /** Score (1 to 4) to its description. A description Pat has not sent yet is missing. */
  levels: Record<string, string>
  points: { point: string; label: string }[]
}

export interface ExerciseScoreIn {
  clientId: string
  childId: number
  exerciseId: number
  point: string
  score: number
  assessedOn: string
}

export interface AchievementIn {
  clientId: string
  childId: number
  milestoneId: number
  ageMonths: number
  yearPoint: YearPoint
  achievedOn: string
}

export interface ClassSheet {
  classId: number
  className: string
  stage: ColourStage
  stageName: string
  exercises: AssessmentExercise[]
  children: {
    id: number
    name: string
    scores: { exerciseId: number; point: string; score: number; assessedOn: string }[]
    extraSupport: boolean
  }[]
}

export interface Development {
  childId: number
  name: string
  className: string
  banner: string
  schoolYear: number
  suggestedYearPoint: YearPoint
  charts: {
    chart: Chart
    label: string
    secure: number
    total: number
    milestones: {
      id: number
      position: number
      band: string
      ageFromMonths: number
      ageToMonths: number
      description: string
      achieved: {
        ageMonths: number
        yearPoint: YearPoint
        yearPointLabel: string
        schoolYear: number
        achievedOn: string
      } | null
    }[]
    comments: { yearPoint: YearPoint; yearPointLabel: string; schoolYear: number; note: string; writtenOn: string }[]
  }[]
}

export interface AssessmentProfile {
  childId: number
  reference: string
  name: string
  className: string
  banner: string
  currentStage: ColourStage | null
  stage: ColourStage | null
  stageName: string | null
  stagesWithExercises: ColourStage[]
  development: { chart: Chart; label: string; secure: number; total: number; thisYear: Record<YearPoint, number> }[]
  exercises: {
    exerciseId: number
    number: number
    title: string
    points: { point: string; label: string; score: number | null; assessedOn: string | null }[]
  }[]
  totals: { start: number; startAssessed: number; end: number; endAssessed: number; max: number } | null
  extraSupport: { flagged: boolean; label: string; reasons: string[] }
  summary: { personalComment: string | null; academicComment: string | null; writtenOn: string } | null
}

export interface AssessmentOverview {
  schoolId: number
  schoolName: string
  schoolYear: number
  classes: {
    classId: number
    name: string
    stage: ColourStage
    stageName: string
    children: number
    developmental: Record<YearPoint, number>
    /** Average movements secured this school year per child assessed. */
    movementsPerChild: number
    /** Children at each exercise level (1 to 4), each counted once at the middle of their latest scores. */
    childrenAtLevel: Record<string, number>
    extraSupport: number
  }[]
  extraSupport: { childId: number; name: string; className: string; reasons: string[] }[]
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
    /** Share of children with a developmental assessment this school year. */
    developmentalRate: number
    /** How many children may benefit from extra support. */
    extraSupport: number
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
  /** Quizzes between sessions passed, of how many the course has (0: none). */
  quizzesPassed: number
  quizzes: number
}

/** A quiz of a learner's course. open: its live session has started, or they attended it. */
export interface QuizSummary {
  id: number
  afterSession: number
  title: string
  questions: number
  passPercent: number
  open: boolean
  bestScore: number | null
  passed: boolean
}

/** A quiz to take: the questions without their answers. */
export interface QuizPaper {
  id: number
  title: string
  passPercent: number
  questions: { number: number; text: string; options: string[] }[]
}

/** wrong: the numbers of the questions answered wrongly. */
export interface QuizResult {
  scorePercent: number
  passed: boolean
  correct: number
  questions: number
  wrong: number[]
  certificateIssued: boolean
}

/** A quiz as the team writes it. correctOption counts from 0. */
export interface QuizInput {
  afterSession: number
  title: string
  passPercent: number
  questions: { text: string; options: string[]; correctOption: number }[]
}

export interface AdminQuiz extends QuizInput {
  id: number
  attempts: number
}

/** A member of staff's request for a course place their school pays for; their head approves or declines it. */
export interface CourseRequest {
  id: number
  cohortId: number
  courseTitle: string
  cohortName: string
  startsOn: string
  timeSlot: string
  pricePence: number
  learnerName: string
  learnerEmail: string
  status: 'REQUESTED' | 'APPROVED' | 'DECLINED' | 'WITHDRAWN'
  requestedAt: string
  decidedBy: string | null
  decidedAt: string | null
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
  /** The trainer or team member who runs the live sessions; null: the team. */
  trainerUserId: number | null
}

/** Someone who can run live sessions: a trainer, or a member of the team (team: true). */
export interface Trainer {
  id: number
  displayName: string
  email: string
  /** The Zoom login they host with; null: the platform's default Zoom host. */
  zoomEmail: string | null
  team: boolean
  enabled: boolean
}

/** A course as the team manages it: every cohort, past ones too, with places booked. */
export interface AdminCourse extends CourseInput {
  id: number
  /** clashes: sessions still to come that overlap another cohort's on the same Zoom login. */
  cohorts: (CohortInput & { id: number; booked: number; sessions: SessionSummary[]; clashes: string[] })[]
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
  /** How many are booked on the cohort; for whoever runs the session only (null for learners). */
  booked: number | null
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
  /** Paid by card on the platform (Stripe), not marked paid by hand. */
  paidOnline: boolean
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

/** What needs the team's attention today (their home page). Dates are calendar dates, startsAt an instant. */
export interface TeamOverview {
  draftsAwaiting: number
  sessionsToday: {
    id: number
    courseTitle: string
    cohortName: string
    number: number
    startsAt: string
    trainerName: string
    booked: number
  }[]
  overdue: { id: number; payerName: string; description: string; amountPence: number; invoicedOn: string }[]
  dueTotalPence: number
  renewals: { schoolId: number; schoolName: string; endsOn: string }[]
  clashingCohorts: number
  courseRequestsWaiting: number
  certificatesExpiring: { enrolmentId: number; learnerName: string; courseTitle: string; expiresOn: string }[]
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

// --- Motor Movers at Home (families without a school) -----------------------------------------------------------

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

export interface FamilyMember {
  userId: number
  name: string
  owner: boolean
  you: boolean
}

export interface HomeOverview {
  /** Motor Movers at Home is switched on for this deployment. */
  enabled: boolean
  children: HomeChild[]
  members: FamilyMember[]
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
  suggested: ColourStage
  suggestedName: string
  forAge: ColourStage
  forAgeName: string
  earlier: boolean
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
  module: number
  modules: number
  sessionInModule: number
  sessionsPerModule: number
  colourComplete: boolean
  exercises: HomeExercise[]
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

/** A colour's home activities, used while Pat's exercise grid for that colour is still to come. */
export interface HomeActivity {
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

export interface FamilyActivities {
  stage: ColourStage
  stageName: string
  next: HomeActivity | null
  activities: HomeActivity[]
  doneToday: boolean
  daysDone: number
  counterDays: number
  counterGrid: boolean[]
  totalSessions: number
}

export interface FamilyInvite {
  code: string
  expiresOn: string
  sentTo: string | null
}

/** A one-off id for a record sent to the API, so sending it twice saves it once. */
export function newClientId(): string {
  return crypto.randomUUID()
}

/** Today in the browser's time zone, as yyyy-mm-dd. */
export function localDate(d: Date = new Date()): string {
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${m}-${day}`
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

/** A file from the API, e.g. a certificate PDF, saved by the browser under the given name. */
async function downloadFile(path: string, filename: string) {
  const token = getToken()
  const res = await fetch(path, { headers: token ? { Authorization: `Bearer ${token}` } : {} })
  if (!res.ok) throw new ApiError(res.status, res.status === 403 ? 'Not your file' : 'Could not download the file')
  const url = URL.createObjectURL(await res.blob())
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
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
  exerciseSheet: (classId: number, stage?: ColourStage) =>
    request<ClassSheet>('GET', `/api/classes/${classId}/exercise-sheet${stage ? `?stage=${stage}` : ''}`),
  submitExerciseScores: (scores: ExerciseScoreIn[]) =>
    request<{ saved: number; duplicates: number }>('POST', '/api/assessment/exercise-scores', scores),
  development: (childId: number) => request<Development>('GET', `/api/children/${childId}/development`),
  submitAchievements: (records: AchievementIn[]) =>
    request<{ saved: number; duplicates: number }>('POST', '/api/assessment/milestones', records),
  clearAchievement: (childId: number, milestoneId: number) =>
    request<void>('DELETE', `/api/children/${childId}/milestones/${milestoneId}`),
  writeDevelopmentComment: (childId: number, c: { chart: Chart; yearPoint: YearPoint; note: string }) =>
    request<void>('PUT', `/api/children/${childId}/development-comments`, c),
  assessmentProfile: (childId: number, stage?: ColourStage) =>
    request<AssessmentProfile>('GET', `/api/children/${childId}/assessment${stage ? `?stage=${stage}` : ''}`),
  writeColourSummary: (childId: number, s: { stage: ColourStage; personalComment: string; academicComment: string }) =>
    request<void>('PUT', `/api/children/${childId}/colour-summary`, s),
  assessmentOverview: (schoolId: number) =>
    request<AssessmentOverview>('GET', `/api/schools/${schoolId}/assessment-overview`),
  schoolDashboard: (schoolId: number) => request<SchoolDashboard>('GET', `/api/schools/${schoolId}/dashboard`),
  trustDashboard: (trustId: number) => request<TrustDashboard>('GET', `/api/trusts/${trustId}/dashboard`),
  exportCsv: (schoolId: number, name: 'governor-summary' | 'research') =>
    request<string>('GET', `/api/schools/${schoolId}/exports/${name}.csv`),
  courses: () => request<{ rules: string; courses: Course[] }>('GET', '/api/training/courses'),
  enrol: (cohortId: number) => request<Enrolment>('POST', `/api/training/cohorts/${cohortId}/enrol`),
  myEnrolments: () => request<Enrolment[]>('GET', '/api/training/enrolments/mine'),
  requestPlace: (cohortId: number) => request<CourseRequest>('POST', `/api/training/cohorts/${cohortId}/request`),
  myRequests: () => request<CourseRequest[]>('GET', '/api/training/requests/mine'),
  withdrawRequest: (id: number) => request<CourseRequest>('POST', `/api/training/requests/${id}/withdraw`),
  schoolRequests: (schoolId: number) => request<CourseRequest[]>('GET', `/api/schools/${schoolId}/course-requests`),
  approveRequest: (id: number) => request<CourseRequest>('POST', `/api/course-requests/${id}/approve`),
  declineRequest: (id: number) => request<CourseRequest>('POST', `/api/course-requests/${id}/decline`),
  cancelPlace: (enrolmentId: number) =>
    request<{ message: string }>('POST', `/api/training/enrolments/${enrolmentId}/cancel`),
  trainerCohorts: () => request<AdminCourse[]>('GET', '/api/trainer/cohorts'),
  courseQuizzes: (courseId: number) => request<AdminQuiz[]>('GET', `/api/admin/courses/${courseId}/quizzes`),
  addQuiz: (courseId: number, q: QuizInput) => request<AdminQuiz>('POST', `/api/admin/courses/${courseId}/quizzes`, q),
  updateQuiz: (id: number, q: QuizInput) => request<AdminQuiz>('PUT', `/api/admin/quizzes/${id}`, q),
  deleteQuiz: (id: number) => request<void>('DELETE', `/api/admin/quizzes/${id}`),
  myQuizzes: (enrolmentId: number) => request<QuizSummary[]>('GET', `/api/training/enrolments/${enrolmentId}/quizzes`),
  quizPaper: (enrolmentId: number, quizId: number) =>
    request<QuizPaper>('GET', `/api/training/enrolments/${enrolmentId}/quizzes/${quizId}`),
  submitQuiz: (enrolmentId: number, quizId: number, answers: number[]) =>
    request<QuizResult>('POST', `/api/training/enrolments/${enrolmentId}/quizzes/${quizId}/attempts`, { answers }),
  certificatePdf: (enrolmentId: number) =>
    downloadFile(`/api/training/enrolments/${enrolmentId}/certificate.pdf`, 'motor-movers-certificate.pdf'),
  induction: () => request<Induction>('GET', '/api/training/induction'),
  completeLesson: (lessonId: number) =>
    request<Induction>('POST', `/api/training/induction/lessons/${lessonId}/complete`),
  resources: () => request<LibraryResource[]>('GET', '/api/resources'),
  content: (kind: ContentKind = 'content') => request<ContentItem[]>('GET', `/api/admin/${kind}`),
  /** New content is saved as a draft for sign-off. The fields depend on the kind (see ContentPage). */
  createContent: (kind: ContentKind, item: Record<string, unknown>) =>
    request<ContentItem>('POST', `/api/admin/${kind}`, item),
  teamOverview: () => request<TeamOverview>('GET', '/api/admin/overview'),
  adminCourses: () => request<AdminCourse[]>('GET', '/api/admin/courses'),
  addCourse: (c: CourseInput) => request<AdminCourse>('POST', '/api/admin/courses', c),
  updateCourse: (id: number, c: CourseInput) => request<AdminCourse>('PUT', `/api/admin/courses/${id}`, c),
  addCohort: (courseId: number, c: CohortInput) =>
    request<AdminCourse>('POST', `/api/admin/courses/${courseId}/cohorts`, c),
  updateCohort: (id: number, c: CohortInput) => request<AdminCourse>('PUT', `/api/admin/cohorts/${id}`, c),
  trainers: () => request<Trainer[]>('GET', '/api/admin/trainers'),
  addTrainer: (t: { email: string; displayName: string; zoomEmail: string }) =>
    request<Trainer>('POST', '/api/admin/trainers', t),
  setTrainerEnabled: (userId: number, enabled: boolean) =>
    request<Trainer>('PUT', `/api/admin/trainers/${userId}/enabled`, { enabled }),
  setTrainerZoomEmail: (userId: number, zoomEmail: string) =>
    request<Trainer>('PUT', `/api/admin/trainers/${userId}/zoom-email`, { zoomEmail }),
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
  /** Whether Pay by card (Stripe) is switched on. */
  onlinePayments: () => request<{ enabled: boolean }>('GET', '/api/payments/online'),
  /** The address of Stripe's page to pay this payment by card. */
  checkout: (paymentId: number) => request<{ url: string }>('POST', `/api/payments/${paymentId}/checkout`),
  schools: () => request<SchoolRow[]>('GET', '/api/admin/schools'),
  setParentLinks: (schoolId: number, enabled: boolean) =>
    request<void>('POST', `/api/admin/schools/${schoolId}/parent-links`, { enabled }),
  createParentInvite: (childId: number) => request<ParentInvite>('POST', `/api/children/${childId}/parent-invites`),
  parentLinks: (schoolId: number) => request<ParentLinkRow[]>('GET', `/api/schools/${schoolId}/parent-links`),
  removeParentLink: (schoolId: number, linkId: number) =>
    request<void>('DELETE', `/api/schools/${schoolId}/parent-links/${linkId}`),
  /** Parents and carers create their own account. */
  register: (email: string, password: string, displayName: string) =>
    request<{ token: string; user: CurrentUser }>('POST', '/api/auth/register', { email, password, displayName }),
  /** Open to anyone: whether Motor Movers at Home is switched on. */
  homeAvailable: () => request<{ enabled: boolean }>('GET', '/api/home/available'),
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
  recordHomeSession: (childId: number) =>
    request<{ saved: number; duplicates: number }>('POST', '/api/home/sessions', [
      { clientId: newClientId(), childId, doneOn: localDate() },
    ]),
  /** Ratings use the assessment endpoint, which checks that the child is the family's. */
  rateExercises: (childId: number, point: string, scores: Record<number, number>) =>
    request<{ saved: number; duplicates: number }>(
      'POST',
      '/api/assessment/exercise-scores',
      Object.entries(scores).map(([exerciseId, score]) => ({
        clientId: newClientId(),
        childId,
        exerciseId: Number(exerciseId),
        point,
        score,
        assessedOn: localDate(),
      })),
    ),
  /** A colour's home activities for a child, while Pat's grid for that colour is still to come. */
  familyActivities: (childId: number) => request<FamilyActivities>('GET', `/api/family/home?childId=${childId}`),
  recordFamilyActivity: (childId: number, activityId: number) =>
    request<{ saved: number; duplicates: number }>('POST', '/api/family/home/sessions', [
      { clientId: newClientId(), activityId, childId, doneOn: localDate() },
    ]),
  /** With an email address the code is also emailed there, with how to use it. */
  inviteCarer: (email?: string) => request<FamilyInvite>('POST', '/api/home/invites', email ? { email } : undefined),
  joinFamily: (code: string) => request<HomeOverview>('POST', '/api/home/join', { code }),
  removeFamilyMember: (userId: number) => request<void>('DELETE', `/api/home/members/${userId}`),
  joinSchool: (id: number, code: string) =>
    request<{ childId: number; schoolName: string }>('POST', `/api/home/children/${id}/join-school`, { code }),
}
