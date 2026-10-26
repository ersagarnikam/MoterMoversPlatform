// Walks each role's journey in the demo and saves the screenshots the guides use (docs/guide-builder/shots).
//
// Needs, freshly started (the walk changes the demo data):
//   the API with demo data and card payments switched on with dummy keys, so Pay by card shows:
//     cd backend && STRIPE_SECRET_KEY=sk_test_docs STRIPE_WEBHOOK_SECRET=whsec_docs LOGIN_LIMIT_PER_IP=500 ./mvnw spring-boot:run
//   the web app:            cd frontend && npm run dev                     (http://localhost:5173)
//   the parent app on web:  cd mobile && npx expo start --web --port 8081  (optional: app screens are skipped without it)
// Usage: node docs/guide-builder/capture.mjs
import { createRequire } from 'node:module'
import { mkdirSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const HERE = dirname(fileURLToPath(import.meta.url))
const ROOT = join(HERE, '..', '..')
const { chromium } = createRequire(join(ROOT, 'frontend', 'package.json'))('@playwright/test')
const OUT = join(HERE, 'shots')
const WEB = process.env.WEB || 'http://localhost:5173'
const APP = process.env.APP || 'http://localhost:8081'
mkdirSync(OUT, { recursive: true })

const browser = await chromium.launch({ channel: process.env.PW_CHANNEL || 'chrome' })
const failures = []

async function session(viewport, fn, scale = 1.5) {
  const ctx = await browser.newContext({ viewport, deviceScaleFactor: scale, colorScheme: 'light', locale: 'en-GB' })
  const page = await ctx.newPage()
  try {
    await fn(page)
  } catch (e) {
    failures.push(e.message.split('\n')[0])
  }
  await ctx.close()
}
const desk = (fn) => session({ width: 1280, height: 800 }, fn)
const phone = (fn) => session({ width: 390, height: 844 }, fn, 2)

async function shot(page, name, opts = {}) {
  await page.waitForTimeout(400)
  try {
    await page.screenshot({ path: join(OUT, `${name}.png`), fullPage: !!opts.full })
    console.log('ok', name)
  } catch (e) {
    failures.push(`${name}: ${e.message.split('\n')[0]}`)
  }
}
/** A screenshot of one part of the page, e.g. a card. */
async function part(locator, name) {
  try {
    await locator.scrollIntoViewIfNeeded()
    await locator.page().waitForTimeout(400)
    await locator.screenshot({ path: join(OUT, `${name}.png`) })
    console.log('ok', name)
  } catch (e) {
    failures.push(`${name}: ${e.message.split('\n')[0]}`)
  }
}
async function step(name, fn) {
  try {
    await fn()
  } catch (e) {
    failures.push(`${name}: ${e.message.split('\n')[0]}`)
  }
}
async function signIn(page, email) {
  await page.goto(`${WEB}/login`)
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Password').fill('password')
  await page.getByRole('button', { name: 'Sign in' }).click()
  await page.getByRole('button', { name: 'Sign out' }).waitFor()
}

// The API directly, for setting things up between screens (ticking attendance, taking quizzes).
async function token(email) {
  const res = await fetch(`${WEB}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password: 'password' }),
  })
  return (await res.json()).token
}
async function api(tok, method, path, body) {
  const res = await fetch(`${WEB}${path}`, {
    method,
    headers: { Authorization: `Bearer ${tok}`, ...(body ? { 'Content-Type': 'application/json' } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  })
  if (!res.ok) throw new Error(`${method} ${path}: ${res.status} ${await res.text()}`)
  const type = res.headers.get('Content-Type') ?? ''
  return type.includes('json') ? res.json() : null
}

// Public website and sign-in
await desk(async (page) => {
  await page.goto(WEB)
  await shot(page, 'site-home')
  await page.goto(`${WEB}/sign-in`)
  await shot(page, 'site-sign-in')
  await page.goto(`${WEB}/login?as=teacher`)
  await shot(page, 'site-login')
})

// New teacher: locked until the induction is done; asks for a course place the school pays for
await desk(async (page) => {
  await signIn(page, 'newteacher@oakfield.test')
  await shot(page, 'newteacher-locked')
  await step('induction', async () => {
    await page.getByRole('link', { name: 'Go to the induction' }).click()
    await page.getByRole('heading', { name: 'Motor Movers induction' }).waitFor()
    await page.locator('details.lesson').first().locator('summary').click()
    await shot(page, 'newteacher-induction')
  })
  await step('ask for a place', async () => {
    const course = page.locator('section.card', { has: page.getByRole('heading', { name: 'Motor Movers practitioner course' }) })
    await part(course, 'staff-course')
    await course.locator('.cohort', { hasText: 'Cohort A · UK evening' }).getByRole('button', { name: 'Ask for a place' }).click()
    await page.getByText('Request sent').waitFor()
    await part(page.locator('section.card', { has: page.getByRole('heading', { name: 'Your requests' }) }), 'staff-requested')
  })
})

// Teacher
await desk(async (page) => {
  await signIn(page, 'teacher@oakfield.test')
  await shot(page, 'teacher-today')
  await step('player', async () => {
    await page.getByRole('button', { name: 'Start session' }).click()
    await page.getByRole('heading', { name: /^Rhyme · / }).waitFor()
    await shot(page, 'teacher-player')
    await page.getByRole('button', { name: 'Skip to end' }).click()
    await shot(page, 'teacher-player-end')
    await page.getByRole('button', { name: /^Complete session/ }).click()
    await page.getByText('Done for today').first().waitFor()
    await shot(page, 'teacher-done')
  })
  await step('profile', async () => {
    await page.goto(`${WEB}/classes`)
    await page.locator('.roster .roster-child').first().click()
    await page.getByRole('heading', { name: /Readiness profile/ }).waitFor()
    await shot(page, 'teacher-profile', { full: true })
  })
  await step('screening', async () => {
    await page.goto(`${WEB}/classes`)
    await page.getByRole('link', { name: 'Assess children' }).click()
    await shot(page, 'teacher-screening-roster')
    await page.locator('.roster-child').first().click()
    await page.getByText(/Item 1 of \d+/).waitFor()
    await page.getByRole('radio').nth(2).click()
    await shot(page, 'teacher-screening-item')
  })
  await step('resources', async () => {
    await page.getByRole('link', { name: 'Resources' }).click()
    await page.getByRole('heading', { name: 'Motor Movers rhyme book' }).waitFor()
    await shot(page, 'teacher-resources')
  })
})

// Head teacher: approves the teacher's request, and can pay the invoice by card
await desk(async (page) => {
  await signIn(page, 'head@oakfield.test')
  await shot(page, 'head-dashboard')
  await shot(page, 'head-dashboard-full', { full: true })
  await step('setup', async () => {
    await page.getByRole('link', { name: 'School setup' }).click()
    await page.getByRole('heading', { name: 'School setup' }).waitFor()
    await shot(page, 'head-setup', { full: true })
    const row = page.locator('.setup-class', { hasText: 'Year 2 R' })
    await row.getByRole('button', { name: 'Edit class and children' }).click()
    await row.locator('.setup-children li').first().waitFor()
    await row.scrollIntoViewIfNeeded()
    await shot(page, 'head-setup-class')
  })
  await step('payments and requests', async () => {
    await page.getByRole('link', { name: 'Payments' }).click()
    await page.getByRole('heading', { name: /Payments/ }).waitFor()
    await part(page.locator('section.card', { has: page.getByRole('heading', { name: 'Course places to approve' }) }), 'head-requests')
    await page.locator('.cohort', { hasText: 'Mr Lewis' }).getByRole('button', { name: 'Approve' }).click()
    await page.getByText(/place on Motor Movers practitioner course is booked/).waitFor()
    await shot(page, 'head-payments')
  })
})

// Trust lead
await desk(async (page) => {
  await signIn(page, 'trust@northbridge.test')
  await shot(page, 'trust-dashboard', { full: true })
})

// Practitioner: books Amy's cohort, takes a quiz, and later gets the certificate
const sam = await token('practitioner@example.test')
const team = await token('admin@motormovers.test')
let place = null
let cohortB = null
await desk(async (page) => {
  await signIn(page, 'practitioner@example.test')
  await shot(page, 'practitioner-training', { full: true })
  await step('book', async () => {
    await page.locator('.cohort', { hasText: 'Cohort B · UK morning' }).getByRole('button', { name: 'Book a place' }).click()
    await page.getByText('Your courses').waitFor()
    await shot(page, 'practitioner-booked')
  })
  await step('quiz', async () => {
    const mine = await api(sam, 'GET', '/api/training/enrolments/mine')
    place = mine.find((e) => e.cohortName.startsWith('Cohort B'))
    cohortB = place.cohortId
    const courses = await api(team, 'GET', '/api/admin/courses')
    const cohort = courses.flatMap((c) => c.cohorts).find((h) => h.id === cohortB)
    // Sam attended the first live session.
    await api(team, 'PUT', `/api/admin/live/${cohort.sessions[0].id}/register/${place.id}`, { present: true })
    await page.reload()
    const block = page.locator('.enrolment-block', { hasText: 'Cohort B' })
    await part(block, 'practitioner-quizzes')
    await block.getByRole('link', { name: 'Take quiz' }).click()
    await page.getByRole('button', { name: 'Send answers' }).waitFor()
    await shot(page, 'practitioner-quiz')
    await page.getByLabel('5 minutes').check()
    await page.getByLabel('The whole class together').check()
    await page.getByLabel('Little and often is what makes the difference').check()
    await page.getByRole('button', { name: 'Send answers' }).click()
    await page.getByText('Not passed yet').waitFor()
    await shot(page, 'practitioner-quiz-result')
  })
  await step('certificate', async () => {
    const courses = await api(team, 'GET', '/api/admin/courses')
    const cohort = courses.flatMap((c) => c.cohorts).find((h) => h.id === cohortB)
    for (const s of cohort.sessions) {
      await api(team, 'PUT', `/api/admin/live/${s.id}/register/${place.id}`, { present: true })
    }
    await api(team, 'POST', `/api/admin/enrolments/${place.id}/assessment`, { passed: true })
    // The starter quizzes' right answers, in order.
    const right = [[1, 1, 1], [2, 1, 0, 0], [1, 0, 1]]
    const quizzes = await api(sam, 'GET', `/api/training/enrolments/${place.id}/quizzes`)
    for (const [i, q] of quizzes.entries()) {
      await api(sam, 'POST', `/api/training/enrolments/${place.id}/quizzes/${q.id}/attempts`, { answers: right[i] })
    }
    await page.goto(`${WEB}/training/certificate/${place.id}`)
    await page.getByRole('heading', { name: 'Certificate of completion' }).waitFor()
    await shot(page, 'practitioner-certificate')
  })
})

// Parent on the web: the live session open to join, and paying by card
await desk(async (page) => {
  await signIn(page, 'parent@example.test')
  await shot(page, 'parent-web', { full: true })
  await step('live', async () => {
    await part(page.locator('section.card', { has: page.getByRole('heading', { name: 'Your live sessions' }) }), 'parent-live')
  })
  await step('payments', async () => {
    await page.goto(`${WEB}/payments`)
    await page.getByRole('button', { name: 'Pay by card' }).first().waitFor()
    await shot(page, 'parent-payments')
  })
})

// Trainer: their own sessions and cohorts, and the register in the live room
await desk(async (page) => {
  await signIn(page, 'trainer@motormovers.test')
  await shot(page, 'trainer-live')
  await step('cohorts', async () => {
    await page.getByRole('link', { name: 'My cohorts' }).click()
    await page.getByRole('heading', { name: 'My cohorts' }).waitFor()
    await shot(page, 'trainer-cohorts', { full: true })
  })
  await step('room', async () => {
    await page.goto(`${WEB}/live`)
    await page.locator('.live-list li').first().getByRole('link', { name: 'Start session' }).click()
    await page.getByRole('heading', { name: 'Register' }).waitFor()
    await shot(page, 'live-room')
    // Without Zoom keys the meeting area only says Zoom is not connected, so the guides show the register.
    await part(page.locator('section, aside, div').filter({ has: page.getByRole('heading', { name: 'Register' }) }).last(), 'live-register')
  })
})

// Motor Movers team
await desk(async (page) => {
  await signIn(page, 'admin@motormovers.test')
  await shot(page, 'admin-content')
  await step('add content', async () => {
    await page.getByText('Add a class session').click()
    await shot(page, 'admin-content-add')
    await page.getByText('Add a class session').click()
  })
  await step('wording', async () => {
    const item = page.locator('.content-item', { hasText: 'ADHD' })
    await item.scrollIntoViewIfNeeded()
    await shot(page, 'admin-content-wording')
  })
  await step('schools', async () => {
    await page.getByRole('link', { name: 'Schools & licences' }).click()
    await page.getByRole('heading', { name: 'Schools & licences' }).waitFor()
    await shot(page, 'admin-schools', { full: true })
  })
  await step('courses', async () => {
    await page.getByRole('link', { name: 'Courses' }).click()
    await page.getByRole('heading', { name: 'Courses', exact: true }).waitFor()
    await shot(page, 'admin-courses')
    const course = page.locator('.course-admin', { hasText: 'Motor Movers practitioner course' })
    await course.getByText('Quizzes (3)').click()
    await part(course.locator('details.add-item').first(), 'admin-quizzes')
    await course.locator('.quiz-list li').first().getByRole('button', { name: 'Edit' }).click()
    await part(course.locator('form.quiz-editor'), 'admin-quiz-editor')
    await course.getByRole('button', { name: 'Cancel' }).first().click()
    const cohort = course.locator('.cohort-block', { hasText: 'Cohort B · UK morning' })
    await cohort.getByRole('button', { name: 'Bookings and attendance' }).click()
    await cohort.locator('.bookings table').waitFor()
    await part(cohort, 'admin-bookings')
    await part(page.locator('section.card', { has: page.getByRole('heading', { name: 'Trainers', exact: true }) }), 'admin-trainers')
  })
  await step('live sessions', async () => {
    await page.getByRole('link', { name: 'Live sessions' }).click()
    await page.getByRole('heading', { name: 'Live sessions' }).waitFor()
    await shot(page, 'admin-live')
  })
  await step('payments', async () => {
    await page.getByRole('link', { name: 'Payments' }).click()
    await page.getByRole('heading', { name: 'Received each month' }).waitFor()
    await shot(page, 'admin-payments')
    await shot(page, 'admin-payments-full', { full: true })
  })
})

// Parent app (phone), when it is running on the web
await phone(async (page) => {
  try {
    await page.goto(APP, { timeout: 10000 })
  } catch {
    failures.push(`parent app not running at ${APP}: app screens kept as they were`)
    return
  }
  await page.getByText('Sign in', { exact: true }).first().waitFor({ timeout: 60000 })
  await shot(page, 'app-sign-in')
  await page.locator('input').nth(0).fill('parent@example.test')
  await page.locator('input').nth(1).fill('password')
  await page.getByText('Sign in', { exact: true }).last().click()
  await page.getByText('Today', { exact: true }).last().waitFor({ timeout: 30000 })
  await page.waitForTimeout(1500)
  await shot(page, 'app-today')
  for (const tab of ['Progress', 'Guidance', 'Course', 'Account']) {
    await step(`app ${tab}`, async () => {
      await page.getByRole('tab', { name: new RegExp(tab) }).click()
      await page.waitForTimeout(1500)
      await shot(page, `app-${tab.toLowerCase()}`)
    })
  }
  await step('app payments', async () => {
    await page.goto(`${APP}/payments`)
    await page.getByText('Pay by card').first().waitFor({ timeout: 15000 })
    await page.waitForTimeout(800)
    await shot(page, 'app-payments')
  })
})

await browser.close()
console.log('FAILURES:', JSON.stringify(failures, null, 1))
