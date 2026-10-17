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
  name: string
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
  licenceStatus: string
  endsOn: string | null
  stages: ColourStage[]
  parentLinksEnabled: boolean
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

/** The kinds of content Pat and Laura sign off. All share the ContentItem shape. */
export type ContentKind = 'content' | 'home-activities' | 'guidance' | 'resources' | 'induction'

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
  approve: (id: number, kind: ContentKind = 'content') =>
    request<ContentItem>('POST', `/api/admin/${kind}/${id}/approve`),
  reject: (id: number, kind: ContentKind = 'content') =>
    request<ContentItem>('POST', `/api/admin/${kind}/${id}/reject`),
  schools: () => request<SchoolRow[]>('GET', '/api/admin/schools'),
  setParentLinks: (schoolId: number, enabled: boolean) =>
    request<void>('POST', `/api/admin/schools/${schoolId}/parent-links`, { enabled }),
  createParentInvite: (childId: number) => request<ParentInvite>('POST', `/api/children/${childId}/parent-invites`),
  parentLinks: (schoolId: number) => request<ParentLinkRow[]>('GET', `/api/schools/${schoolId}/parent-links`),
  removeParentLink: (schoolId: number, linkId: number) =>
    request<void>('DELETE', `/api/schools/${schoolId}/parent-links/${linkId}`),
}
