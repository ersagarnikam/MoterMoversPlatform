// Load test: a busy school day against the full stack (Caddy HTTPS -> nginx -> API -> PostgreSQL).
// Each simulated user signs in once at the start (sign-in is rate limited), then repeats a realistic
// journey. See README.md in this folder.
//
//   k6 run school-day.js                      normal day: up to PEAK people (default 500), pausing 1-3 s
//   k6 run -e TEST=stress school-day.js       no pauses, journeys per second rising to RATE (default 1000)
//   k6 run -e TEST=fixed -e RATE=400 ...      no pauses, RATE journeys per second for 90 s
//   -e BASE=https://staging.example.org       target (default https://localhost)
import http from 'k6/http'
import { check, sleep } from 'k6'

const BASE = __ENV.BASE || 'https://localhost'
const TEST = __ENV.TEST || 'day'
const PEAK = Number(__ENV.PEAK || 500)
const RATE = Number(__ENV.RATE || (TEST === 'fixed' ? 400 : 1000))
const PAUSES = TEST === 'day'

const SCENARIOS = {
  day: {
    executor: 'ramping-vus',
    startVUs: 0,
    stages: [0.1, 0.3, 0.6, 1].flatMap((share) => [
      { duration: '30s', target: Math.round(PEAK * share) },
      { duration: '1m', target: Math.round(PEAK * share) },
    ]).concat([{ duration: '20s', target: 0 }]),
    gracefulRampDown: '10s',
  },
  stress: {
    executor: 'ramping-arrival-rate',
    startRate: 20,
    timeUnit: '1s',
    preAllocatedVUs: 200,
    maxVUs: 3000,
    stages: [0.1, 0.25, 0.5, 0.75, 1]
      .map((share) => ({ duration: '1m', target: Math.round(RATE * share) }))
      .concat([{ duration: '30s', target: RATE }]),
  },
  fixed: {
    executor: 'constant-arrival-rate',
    rate: RATE,
    timeUnit: '1s',
    duration: '90s',
    preAllocatedVUs: 500,
    maxVUs: 3000,
  },
}

/** Every page the journeys use, so the summary reports each one separately. */
const PAGES = ['sign in', 'classes', 'today', 'readiness profile', 'save screening', 'school dashboard',
  'school payments', 'trust dashboard', 'trust payments', 'website home', 'parent home', 'parent children',
  'parent guidance']

export const options = {
  // Allows Caddy's local certificate when testing on localhost. Remove for a server with a real certificate.
  insecureSkipTLSVerify: BASE.includes('localhost'),
  scenarios: { [TEST]: SCENARIOS[TEST] },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{expected_response:true}': ['p(95)<1000'],
    ...Object.fromEntries(PAGES.map((page) => [`http_req_duration{name:${page}}`, ['p(95)<5000']])),
  },
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
}

function login(email) {
  const res = http.post(`${BASE}/api/auth/login`, JSON.stringify({ email, password: 'password' }), {
    headers: { 'Content-Type': 'application/json' },
    tags: { name: 'sign in' },
  })
  if (res.status !== 200) throw new Error(`sign-in failed for ${email}: ${res.status} ${res.body}`)
  return res.json()
}

/** Signs in each demo role once and collects the classes and children the journeys use. */
export function setup() {
  const roles = {}
  for (const [key, email] of [
    ['admin', 'admin@motormovers.test'],
    ['head', 'head@oakfield.test'],
    ['trust', 'trust@northbridge.test'],
    ['parent', 'parent@example.test'],
  ]) {
    roles[key] = login(email)
  }
  const auth = { headers: { Authorization: `Bearer ${roles.admin.token}` } }
  const classes = http.get(`${BASE}/api/classes`, auth).json()
  const children = []
  for (const c of classes) {
    for (const k of http.get(`${BASE}/api/classes/${c.id}/today`, auth).json().roster) children.push(k.id)
  }
  const items = http.get(`${BASE}/api/screening/items`, auth).json().map((i) => i.code)
  // Teacher journeys use the team account so they spread across every class; it goes through the same
  // access checks and queries as each class's own teacher.
  return { roles, classIds: classes.map((c) => c.id), children, items }
}

const pick = (list) => list[Math.floor(Math.random() * list.length)]
const think = () => PAUSES && sleep(1 + Math.random() * 2)
const get = (path, token, name) =>
  http.get(`${BASE}${path}`, { headers: { Authorization: `Bearer ${token}` }, tags: { name } })
const ok = (res) => check(res, { 'status 2xx': (r) => r.status >= 200 && r.status < 300 })
const clientId = () => `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}-${__VU}-${__ITER}`

export default function (data) {
  const { roles, classIds, children, items } = data
  const roll = Math.random()

  if (roll < 0.6) {
    // A teacher: their classes, today's session, then a child's readiness profile (logged as a view).
    const t = roles.admin.token
    ok(get('/api/classes', t, 'classes'))
    think()
    ok(get(`/api/classes/${pick(classIds)}/today`, t, 'today'))
    think()
    ok(get(`/api/children/${pick(children)}/profile`, t, 'readiness profile'))
  } else if (roll < 0.7) {
    // Screening: one child's scores for every item, saved together as the device syncs.
    const child = pick(children)
    const today = new Date().toISOString().slice(0, 10)
    const scores = items.map((code) => ({
      clientId: clientId(),
      childId: child,
      itemCode: code,
      score: Math.floor(Math.random() * 4),
      screenedOn: today,
    }))
    ok(http.post(`${BASE}/api/screening/results`, JSON.stringify(scores), {
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${roles.admin.token}` },
      tags: { name: 'save screening' },
    }))
  } else if (roll < 0.8) {
    // A head teacher: the progress dashboard, then payments.
    const h = roles.head
    ok(get(`/api/schools/${h.user.schoolId}/dashboard`, h.token, 'school dashboard'))
    think()
    ok(get(`/api/schools/${h.user.schoolId}/payments`, h.token, 'school payments'))
  } else if (roll < 0.85) {
    // A trust lead: schools and spending.
    const tr = roles.trust
    ok(get(`/api/trusts/${tr.user.trustId}/dashboard`, tr.token, 'trust dashboard'))
    think()
    ok(get(`/api/trusts/${tr.user.trustId}/payments`, tr.token, 'trust payments'))
  } else {
    // A parent: the website, then the app's home, children and guidance.
    ok(http.get(`${BASE}/`, { tags: { name: 'website home' } }))
    const p = roles.parent.token
    ok(get('/api/family/home', p, 'parent home'))
    think()
    ok(get('/api/family/children', p, 'parent children'))
    ok(get('/api/family/guidance', p, 'parent guidance'))
  }
  think()
}
