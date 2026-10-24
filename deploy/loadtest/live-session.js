// Load test: a live course session starting. Every booked learner opens their Training page and presses Join
// within about a minute, while the host's register refreshes every 15 seconds. It measures the platform only:
// run the API without Zoom keys, so no Zoom meetings are created. See README.md in this folder.
//
//   k6 run -e LEARNERS=200 live-session.js     LEARNERS learners (default 50)
//   -e RUSH=60                                 seconds over which they arrive (default 60)
//   -e BASE=http://localhost:8080              target (default: the API on this machine)
//
// The API must be started with demo data, LOAD_TEST_LEARNERS of at least LEARNERS (that many extra parents are
// booked on the demo's "Cohort A · starting now"), and LOGIN_LIMIT_PER_IP above LEARNERS + 10, and the test
// must start within about an hour of the API (the cohort's first session starts ten minutes after start-up).
import http from 'k6/http'
import exec from 'k6/execution'
import { check, sleep } from 'k6'
import { Counter } from 'k6/metrics'

const BASE = __ENV.BASE || 'http://localhost:8080'
const LEARNERS = Number(__ENV.LEARNERS || 50)
const RUSH = Number(__ENV.RUSH || 60)
const COHORT = 'Cohort A · starting now'
const JSON_HEADERS = { 'Content-Type': 'application/json' }

const joins = new Counter('joins_ok')

export const options = {
  setupTimeout: '10m',
  scenarios: {
    learners: {
      executor: 'per-vu-iterations',
      vus: LEARNERS,
      iterations: 1,
      maxDuration: `${RUSH + 120}s`,
      exec: 'learner',
    },
    host: { executor: 'constant-vus', vus: 1, duration: `${RUSH + 30}s`, exec: 'host' },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    checks: ['rate==1'],
    joins_ok: [`count==${LEARNERS}`],
    'http_req_duration{name:join}': ['p(95)<1000'],
    'http_req_duration{name:register}': ['p(95)<1000'],
    'http_req_duration{name:training page}': ['p(95)<1000'],
  },
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
}

const email = (i) => `learner${String(i).padStart(3, '0')}@loadtest.test`
const auth = (token, name) => ({ headers: { Authorization: `Bearer ${token}` }, tags: { name } })

function login(address) {
  const res = http.post(`${BASE}/api/auth/login`, JSON.stringify({ email: address, password: 'password' }), {
    headers: JSON_HEADERS,
    tags: { name: 'sign in' },
  })
  if (res.status !== 200) throw new Error(`sign-in failed for ${address}: ${res.status} ${res.body}`)
  return res.json().token
}

/** Finds the session and signs everyone in first, so the rush measures joining rather than signing in. */
export function setup() {
  const host = login('pat@motormovers.test')
  const session = http
    .get(`${BASE}/api/training/live`, auth(host, 'live sessions'))
    .json()
    .find((s) => s.cohortName === COHORT && s.number === 1)
  if (!session) throw new Error(`No session 1 of "${COHORT}": is the API running with demo data?`)

  const tokens = []
  for (let start = 1; start <= LEARNERS; start += 50) {
    const batch = []
    for (let i = start; i < Math.min(start + 50, LEARNERS + 1); i++) {
      batch.push(['POST', `${BASE}/api/auth/login`, JSON.stringify({ email: email(i), password: 'password' }),
        { headers: JSON_HEADERS, tags: { name: 'sign in' } }])
    }
    for (const res of http.batch(batch)) {
      if (res.status !== 200) {
        throw new Error(`learner sign-in failed: ${res.status} ${res.body}. Start the API with ` +
          `LOAD_TEST_LEARNERS>=${LEARNERS} and LOGIN_LIMIT_PER_IP>${LEARNERS + 10}.`)
      }
      tokens.push(res.json().token)
    }
  }
  return { host, session: session.id, tokens }
}

/** One learner: arrives during the rush, opens the Training page, then presses Join. */
export function learner(data) {
  const token = data.tokens[exec.scenario.iterationInTest]
  sleep(Math.random() * RUSH)
  for (const path of ['/api/training/courses', '/api/training/enrolments/mine', '/api/payments/mine',
    '/api/training/live']) {
    check(http.get(`${BASE}${path}`, auth(token, 'training page')), { 'training page 200': (r) => r.status === 200 })
  }
  sleep(1 + Math.random() * 2)
  const res = http.post(`${BASE}/api/training/live/${data.session}/join`, null, auth(token, 'join'))
  if (check(res, { 'join 200': (r) => r.status === 200 })) joins.add(1)
}

/** The host: the register in the session room refreshes every 15 seconds. */
export function host(data) {
  const res = http.get(`${BASE}/api/admin/live/${data.session}/register`, auth(data.host, 'register'))
  check(res, { 'register 200': (r) => r.status === 200 })
  sleep(15)
}

/** Afterwards the register must show every learner as joined. */
export function teardown(data) {
  const register = http.get(`${BASE}/api/admin/live/${data.session}/register`, auth(data.host, 'register')).json()
  const joined = register.learners.filter((l) => l.learnerEmail.endsWith('@loadtest.test') && l.joinedAt).length
  console.log(`Register after the rush: ${joined} of ${LEARNERS} load-test learners shown as joined ` +
    `(${register.learners.length} booked in all).`)
  check(register, { 'register shows every learner joined': () => joined === LEARNERS })
}
