import type { Page } from '@playwright/test'
import { day } from '../src/dates'
import { expect, expectAccessible, signIn, test } from './fixtures'

test('a head sets up a class, adds children and invites a teacher', async ({ page }) => {
  await signIn(page, 'head@oakfield.test')
  await page.getByRole('link', { name: 'School setup', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'School setup' })).toBeVisible()
  await expectAccessible(page)

  const stamp = Date.now()
  const staff = page.locator('section', { has: page.getByRole('heading', { name: 'Staff' }) })
  await staff.getByLabel('Name').fill('Ms Browser')
  await staff.getByLabel('Email').fill(`browser-${stamp}@oakfield.test`)
  await staff.getByRole('button', { name: 'Add and email a sign-in link' }).click()
  await expect(page.getByText(`browser-${stamp}@oakfield.test has been emailed a link`)).toBeVisible()
  await expect(staff.getByRole('cell', { name: 'Ms Browser' })).toBeVisible()

  const classes = page.locator('section', { has: page.getByRole('heading', { name: 'Classes' }) })
  const form = classes.locator('form.inline-form').last()
  await form.getByLabel('Class name').fill(`Nursery ${stamp}`)
  await form.getByLabel('Year group').fill('Nursery')
  await form.getByRole('button', { name: 'Add class' }).click()
  await expect(page.getByText('Class added')).toBeVisible()

  const row = classes.locator('.setup-class', { hasText: `Nursery ${stamp}` })
  await row.getByRole('button', { name: 'Edit class and children' }).click()
  await row.getByLabel(/Add children, one per line/).fill('A.B.\nC.D.')
  await row.getByRole('button', { name: 'Add children' }).click()
  await expect(row.locator('.setup-children li')).toHaveCount(2)
  await expect(row.getByText(/· 2 children/)).toBeVisible()

  await row.locator('.setup-children li').first().getByRole('button', { name: 'Left the school' }).click()
  // The page asks first, in the page itself.
  await expectAccessible(page)
  await page.getByRole('dialog').getByRole('button', { name: 'Mark as left' }).click()
  await expect(row.locator('.setup-children li')).toHaveCount(1)
})

test('a full name is refused while the school uses initials only, and the typed list is kept', async ({ page }) => {
  await signIn(page, 'head@oakfield.test')
  await page.getByRole('link', { name: 'School setup', exact: true }).click()
  const row = page.locator('.setup-class', { hasText: 'Reception A' })
  await row.getByRole('button', { name: 'Edit class and children' }).click()
  const box = row.getByLabel(/initials only \(your school does not show full names\)/)
  await box.fill('E.F., Eve Fox')
  await row.getByRole('button', { name: 'Add children' }).click()
  await expect(page.getByText(/uses initials only, so full names cannot be added/)).toBeVisible()
  await expect(box).toHaveValue('E.F., Eve Fox')
})

test('a head sees progress and can download the governor summary', async ({ page }) => {
  await signIn(page, 'head@oakfield.test')
  await expect(page.getByRole('heading', { name: /Participation by class/ })).toBeVisible()
  const download = page.waitForEvent('download')
  await page.getByRole('button', { name: /Governor summary/i }).click()
  expect((await download).suggestedFilename()).toBe('governor-summary.csv')
})

test('a head sees both assessments and the children who may benefit from extra support', async ({ page }) => {
  await signIn(page, 'head@oakfield.test')
  await expect(page.getByRole('heading', { name: /^Assessments · school year/ })).toBeVisible()
  const support = page.locator('.support-list li')
  await expect(support).toHaveCount(2)
  await support.first().getByRole('link').click()
  await expect(page.getByText('May benefit from extra support.')).toBeVisible()
  await expect(page.getByRole('heading', { name: /Exercises · Stage 4 · Green/ })).toBeVisible()
  await expectAccessible(page)
})

test('the trust or group lead sees schools but no children', async ({ page }) => {
  await signIn(page, 'trust@northbridge.test')
  // The Spending table lists the same schools, so look only in the licences and results table.
  const schools = page.getByRole('table').filter({ has: page.getByRole('columnheader', { name: 'Licence' }) })
  await expect(schools.getByRole('cell', { name: 'Oakfield Primary' })).toBeVisible()
  await expect(page.getByRole('link', { name: 'School setup', exact: true })).toHaveCount(0)
})

test('the team signs off a rhyme and blocks diagnostic wording', async ({ page }) => {
  await signIn(page, 'admin@motormovers.test')
  await page.getByRole('link', { name: 'Content approval', exact: true }).click()
  await page.getByRole('button', { name: 'Resources' }).click()
  const frogs = page.locator('.content-item', { hasText: 'Five little speckled frogs' })
  await frogs.getByRole('button', { name: 'Approve' }).click()
  // Signed off, it leaves the "awaiting" list and shows as approved under All.
  await expect(frogs).toHaveCount(0)
  await page.getByRole('button', { name: /^All/ }).click()
  await expect(frogs.getByText('approved')).toBeVisible()

  await page.getByRole('button', { name: 'Class sessions' }).click()
  const adhd = page.locator('.content-item', { hasText: 'Helping children with ADHD focus' })
  await expect(adhd.getByText('Wording check:')).toBeVisible()
  await expect(adhd.getByRole('button', { name: 'Approve' })).toBeDisabled()

  await page.getByRole('button', { name: 'Staff induction' }).click()
  await page.getByRole('button', { name: /^All/ }).click()
  await expect(page.getByRole('heading', { name: 'Why movement comes first' })).toBeVisible()
  await expectAccessible(page)
})

test('the team writes a class session, which waits as a draft until approved', async ({ page }) => {
  await signIn(page, 'admin@motormovers.test')
  await page.getByRole('link', { name: 'Content approval', exact: true }).click()
  const stamp = Date.now()
  const title = `Browser session ${stamp}`
  await page.getByText('Add a class session').click()
  // Sessions are unique by stage, module and day, so each run uses its own module.
  await page.getByLabel('Module', { exact: true }).fill(String(100 + (stamp % 100_000)))
  await page.getByLabel('Title').fill(title)
  await page.getByLabel('Focus').fill('Balance and control')
  await page.getByLabel('Activities, one per line').fill('Stand on one leg\nHop to the line')
  await expectAccessible(page)
  await page.getByRole('button', { name: 'Save as draft' }).click()

  await expect(page.getByText(`“${title}” is saved as a draft`)).toBeVisible()
  const item = page.locator('.content-item', { hasText: title })
  await expect(item.getByText('draft')).toBeVisible()
  await expect(item.getByText('Stand on one leg · Hop to the line')).toBeVisible()
  await item.getByRole('button', { name: 'Approve' }).click()
  await expect(item).toHaveCount(0)
})

test('the team approves a whole module; a draft failing the wording check stays and is named', async ({ page }) => {
  await signIn(page, 'admin@motormovers.test')
  await page.getByRole('link', { name: 'Content approval', exact: true }).click()
  await page.getByLabel('Show colour').selectOption('YELLOW')
  await page.getByLabel('Show module').selectOption('19')
  await page.getByRole('button', { name: /^Approve all 2 drafts/ }).click()
  await page.getByRole('button', { name: 'Approve them' }).click()
  await expect(page.locator('.banner-warn', { hasText: 'Still drafts' })).toContainText('Helping children with ADHD focus')
  await expect(page.locator('.content-item', { hasText: 'Spinning and stopping' })).toHaveCount(0)
  await expect(page.locator('.content-item', { hasText: 'Helping children with ADHD focus' })).toHaveCount(1)
  await expectAccessible(page)
})

test('the team lands on a home page of what needs attention', async ({ page }) => {
  await signIn(page, 'admin@motormovers.test')
  await expect(page.getByRole('heading', { name: 'Hello, Laura' })).toBeVisible()
  // Courses are recorded (task 89): nothing about live sessions or Zoom shows, on the page or in the menu.
  await expect(page.getByRole('heading', { name: 'Live sessions today' })).toHaveCount(0)
  await expect(page.getByRole('heading', { name: 'Zoom clashes' })).toHaveCount(0)
  await expect(page.getByRole('link', { name: 'Live sessions', exact: true })).toHaveCount(0)
  await expect(page.getByRole('link', { name: 'Trainers', exact: true })).toHaveCount(0)
  const drafts = page.locator('section', { has: page.getByRole('heading', { name: 'Waiting for sign-off' }) })
  await expect(drafts).toContainText(/waiting to be checked and approved/)
  await drafts.getByRole('link', { name: /Content approval/ }).click()
  await expect(page.getByRole('heading', { name: 'Content approval' })).toBeVisible()
  await page.getByRole('link', { name: 'Home', exact: true }).click()
  await expectAccessible(page)
})

/** Three seconds of silence as a WAV file, so the test needs no media file in the repository. */
function silentWav() {
  const samples = 8000 * 3
  const wav = Buffer.alloc(44 + samples * 2)
  wav.write('RIFF', 0)
  wav.writeUInt32LE(36 + samples * 2, 4)
  wav.write('WAVEfmt ', 8)
  wav.writeUInt32LE(16, 16)
  wav.writeUInt16LE(1, 20)
  wav.writeUInt16LE(1, 22)
  wav.writeUInt32LE(8000, 24)
  wav.writeUInt32LE(16000, 28)
  wav.writeUInt16LE(2, 32)
  wav.writeUInt16LE(16, 34)
  wav.write('data', 36)
  wav.writeUInt32LE(samples * 2, 40)
  return wav
}

test('the team uploads a recording and a teacher plays it under the CSP', async ({ page }) => {
  await signIn(page, 'admin@motormovers.test')

  // The upload is only allowed by the content page's own policy, so the menu must load that page in full.
  const [contentPage] = await Promise.all([
    page.waitForResponse((r) => r.request().isNavigationRequest() && r.url().endsWith('/admin/content')),
    page.getByRole('link', { name: 'Content approval', exact: true }).click(),
  ])
  expect(contentPage.headers()['content-security-policy']).toContain("connect-src 'self' https://bucket.storage.test;")
  const other = await page.request.get('/admin/schools')
  expect(other.headers()['content-security-policy']).toContain("connect-src 'self';")

  const title = `Uploaded rhyme ${Date.now()}`
  await page.getByRole('button', { name: 'Resources', exact: true }).click()
  await page.getByText('Add a resource').click()
  await page.getByLabel('Kind').selectOption('RHYME')
  await page.getByLabel('Title').fill(title)
  await page.getByLabel('Description').fill('A recording uploaded by a browser test')
  await page.getByLabel(/Or upload a file/).setInputFiles({ name: 'rhyme.wav', mimeType: 'audio/wav', buffer: silentWav() })
  await expect(page.getByText('“rhyme.wav” is uploaded')).toBeVisible()
  await page.getByRole('button', { name: 'Save as draft' }).click()

  const draft = page.locator('.content-item', { hasText: title })
  await expect(draft.getByRole('link', { name: 'Open the file' })).toBeVisible()
  await expectAccessible(page)
  await draft.getByRole('button', { name: 'Approve' }).click()
  await page.getByRole('button', { name: 'Sign out' }).click()

  await signIn(page, 'teacher@oakfield.test')
  await page.getByRole('link', { name: 'Resources', exact: true }).click()
  const audio = page.locator('.card', { hasText: title }).locator('audio')
  await expect(audio).toHaveAttribute('src', /^\/api\/files\/local\/uploads\//)
  await expect.poll(() => audio.evaluate((el: HTMLAudioElement) => el.duration)).toBeCloseTo(3, 0)

  // Seeking asks for a range, and a video or recording is not much use without it.
  await audio.evaluate(
    (el: HTMLAudioElement) =>
      new Promise((resolve) => {
        el.addEventListener('seeked', resolve, { once: true })
        el.currentTime = 2
      }),
  )
  expect(await audio.evaluate((el: HTMLAudioElement) => el.currentTime)).toBeCloseTo(2, 0)
})

/** Two seconds of flashing colour, recorded in the page: a film for the tests that is nobody's recording. */
async function shortFilm(page: Page): Promise<Buffer> {
  const encoded = await page.evaluate(async () => {
    const canvas = document.createElement('canvas')
    canvas.width = 160
    canvas.height = 90
    const pen = canvas.getContext('2d')!
    const recorder = new MediaRecorder(canvas.captureStream(10), { mimeType: 'video/webm' })
    const parts: Blob[] = []
    recorder.ondataavailable = (e) => parts.push(e.data)
    const stopped = new Promise((resolve) => (recorder.onstop = resolve))
    recorder.start()
    for (let frame = 0; frame < 20; frame++) {
      pen.fillStyle = frame % 2 ? '#224466' : '#ffaa00'
      pen.fillRect(0, 0, 160, 90)
      await new Promise((resolve) => setTimeout(resolve, 100))
    }
    recorder.stop()
    await stopped
    const bytes = new Uint8Array(await new Blob(parts).arrayBuffer())
    let text = ''
    bytes.forEach((b) => (text += String.fromCharCode(b)))
    return btoa(text)
  })
  return Buffer.from(encoded, 'base64')
}

test('the team attaches a film to a rhyme and the class player plays it, and to an exercise', async ({ page }) => {
  // Which rhyme Ms Hardy's class is on today.
  await signIn(page, 'teacher@oakfield.test')
  await page.getByRole('button', { name: 'Start session' }).click()
  const heading = page.getByRole('heading', { name: /Yellow Rhyme \d+, Day \d+/ })
  await expect(heading).toBeVisible()
  const rhyme = /Rhyme (\d+)/.exec(await heading.innerText())![1]
  await expect(page.locator('.player-placeholder')).toBeVisible()
  const film = await shortFilm(page)
  await page.getByRole('button', { name: 'Sign out' }).click()

  await signIn(page, 'admin@motormovers.test')
  await Promise.all([
    page.waitForResponse((r) => r.request().isNavigationRequest() && r.url().endsWith('/admin/content')),
    page.getByRole('link', { name: 'Content approval', exact: true }).click(),
  ])
  await page.getByRole('button', { name: /^All \(/ }).click()
  await page.getByLabel('Show colour').selectOption('YELLOW')
  await page.getByLabel('Show module').selectOption(rhyme)
  const card = page.getByRole('region', { name: `Film for Yellow rhyme ${rhyme}` })
  // The card takes video only, and says so.
  await expect(card.getByText(/^MP4 or WebM video, up to \d+ MB$/)).toBeVisible()
  await card.getByLabel(/Or upload a file/).setInputFiles({ name: 'rhyme.webm', mimeType: 'video/webm', buffer: film })
  await expect(page.getByText('“rhyme.webm” is uploaded. Attach it to keep it.')).toBeVisible()
  await expect(card.getByText('One film for all 5 sessions of Yellow rhyme')).toBeVisible()
  await card.getByRole('button', { name: 'Attach the film' }).click()
  await expect(card.getByRole('link', { name: 'Open the film' })).toBeVisible()
  await expect(card.getByText('Credit line: “Film: Professor Pat Preedy Programme” (the usual one)')).toBeVisible()
  await expectAccessible(page)
  await page.getByRole('button', { name: 'Sign out' }).click()

  await signIn(page, 'teacher@oakfield.test')
  await page.getByRole('button', { name: 'Start session' }).click()
  const video = page.locator('.player-video video')
  await expect(video).toHaveAttribute('src', /^\/api\/files\/local\/uploads\/.+\.webm/)
  await expect(page.locator('.film-credit')).toHaveText('Film: Professor Pat Preedy Programme')
  await expect(page.locator('.player-placeholder')).toHaveCount(0)
  // Only the film's details load until it is played; then it plays under the strict policy.
  await expect(video).toHaveAttribute('preload', 'metadata')
  await expect.poll(() => video.evaluate((el: HTMLVideoElement) => el.readyState)).toBeGreaterThan(0)
  await video.evaluate((el: HTMLVideoElement) => {
    el.muted = true
    return el.play()
  })
  await expect.poll(() => video.evaluate((el: HTMLVideoElement) => el.currentTime)).toBeGreaterThan(0)
  await expectAccessible(page)
  await page.getByRole('button', { name: 'Sign out' }).click()

  // Removed again, the rhyme's sessions go back to the timed plan.
  await signIn(page, 'admin@motormovers.test')
  await page.getByRole('link', { name: 'Content approval', exact: true }).click()
  await page.getByLabel('Show colour').selectOption('YELLOW')
  await page.getByLabel('Show module').selectOption(rhyme)
  await card.getByRole('button', { name: 'Remove the film' }).click()
  await page.getByRole('button', { name: 'Remove the film' }).last().click()
  await expect(card.getByRole('button', { name: 'Attach the film' })).toBeVisible()

  // From Green to Violet each exercise of the grid can have its own film.
  await page.getByLabel('Show colour').selectOption('GREEN')
  await page.getByText('Films for Green exercises (0 of 27 have one)').click()
  const exercise = page.locator('.film-exercise').first()
  await exercise.locator('summary').click()
  await exercise.getByLabel('Film link').fill('https://films.example.test/green-1.mp4')
  await exercise.getByLabel(/Credit line/).fill('Filmed for the programme')
  await exercise.getByRole('button', { name: 'Attach the film' }).click()
  await expect(page.getByText('Films for Green exercises (1 of 27 have one)')).toBeVisible()
  await expect(exercise.getByText('Credit line: “Filmed for the programme”.')).toBeVisible()
  await expectAccessible(page)
  await exercise.getByRole('button', { name: 'Remove the film' }).click()
  await page.getByRole('button', { name: 'Remove the film' }).last().click()
  await expect(page.getByText('Films for Green exercises (0 of 27 have one)')).toBeVisible()
})

test('the team sets up a recorded course, and a learner does its lessons in order', async ({ page }) => {
  await signIn(page, 'admin@motormovers.test')
  await page.getByRole('link', { name: 'Courses', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Courses', exact: true })).toBeVisible()

  const title = `Browser course ${Date.now()}`
  const setUp = page.locator('section', { has: page.getByRole('heading', { name: 'Set up a course' }) })
  // Nothing about live sessions on the form.
  await expect(setUp.getByLabel('Live sessions')).toHaveCount(0)
  await setUp.getByLabel('Course title').fill(title)
  await setUp.getByLabel('Description').fill('A short recorded course for early-years staff.')
  await setUp.getByLabel('Price (£)').fill('0')
  await setUp.getByRole('button', { name: 'Set up course' }).click()
  await expect(page.getByText(`${title} set up`)).toBeVisible()

  const course = page.locator('.course-admin', { hasText: title })
  await expect(course.getByText('No lessons yet.')).toBeVisible()
  await expect(course.getByText('Nobody has a place yet.')).toBeVisible()
  await expect(course.getByRole('heading', { name: 'Cohorts' })).toHaveCount(0)
  await expectAccessible(page)

  // Lessons are written on Content approval, the page that can send a file to the store.
  const film = await shortFilm(page)
  await course.getByRole('link', { name: 'Write and approve lessons' }).click()
  await expect(page.getByRole('heading', { name: 'Content approval' })).toBeVisible()
  await expect(page.getByRole('combobox')).toContainText(title)
  await page.getByRole('button', { name: 'Add a lesson' }).click()
  const form = page.getByRole('form', { name: 'Lesson' })
  await form.getByLabel('Title').fill('Watch and move')
  await form.getByLabel(/^Text/).fill('Watch the film, then try it yourself.')
  await form.locator('input[type=file]').first().setInputFiles({ name: 'lesson.webm', mimeType: 'video/webm', buffer: film })
  await expect(page.getByText('“lesson.webm” is uploaded')).toBeVisible()
  await expectAccessible(page)
  await form.getByRole('button', { name: 'Save as a draft' }).click()
  const first = page.locator('section', { has: page.getByRole('heading', { name: 'Lesson 1: Watch and move' }) })
  await expect(first.getByText('Draft', { exact: true })).toBeVisible()
  await first.getByRole('button', { name: 'Approve' }).click()
  await expect(first.getByText('Approved', { exact: true })).toBeVisible()
  // A second lesson with no film, which blocks on the wording check until it is changed.
  await page.getByRole('button', { name: 'Add a lesson' }).click()
  await form.getByLabel('Title').fill('Spotting ADHD')
  await form.getByRole('button', { name: 'Save as a draft' }).click()
  const second = page.locator('section', { has: page.getByRole('heading', { name: /^Lesson 2:/ }) })
  await expect(second.getByText('Wording to change before it can be approved')).toBeVisible()
  await second.getByRole('button', { name: 'Edit' }).click()
  await form.getByLabel('Title').fill('Try it together')
  await form.getByRole('button', { name: 'Save the lesson' }).click()
  await second.getByRole('button', { name: 'Approve' }).click()
  await expect(second.getByText('Approved', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'Sign out' }).click()

  // A practitioner books it and starts at once.
  await signIn(page, 'practitioner@example.test')
  const card = page.locator('section', { has: page.getByRole('heading', { name: title }) })
  await expect(card).toContainText('2 recorded lessons')
  await card.getByRole('button', { name: 'Book a place' }).click()
  await expect(card.getByText('Booked', { exact: true })).toBeVisible()
  const place = page.locator('.enrolment', { hasText: title })
  await expect(place).toContainText('0 of 2 lessons done')
  await place.getByRole('link', { name: 'Start the course' }).click()
  await expect(page.getByRole('heading', { name: title, level: 1 })).toBeVisible()

  // The first lesson can be marked done only once its film has played to the end; the second waits for the first.
  const done1 = page.getByRole('button', { name: 'Mark lesson 1 as done' })
  await expect(done1).toBeDisabled()
  await expect(page.getByText('Watch the film to the end, then mark the lesson as done.')).toBeVisible()
  await expect(page.getByText('Opens after lesson 1')).toBeVisible()
  await expectAccessible(page)
  const video = page.locator('.lesson video')
  await expect.poll(() => video.evaluate((el: HTMLVideoElement) => el.readyState)).toBeGreaterThan(0)
  await video.evaluate((el: HTMLVideoElement) => {
    el.muted = true
    return el.play()
  })
  await expect(done1).toBeEnabled({ timeout: 15_000 })
  await done1.click()
  await expect(page.getByText('Lesson 1 is done')).toBeVisible()
  // The second has no film, so it can be marked at once.
  await page.getByRole('button', { name: 'Mark lesson 2 as done' }).click()
  await expect(page.getByText('Every lesson is done')).toBeVisible()
  await expect(page.getByText('2 of 2 lessons done')).toBeVisible()
  await page.getByRole('button', { name: 'Sign out' }).click()

  // The team sees the learner and how far they have got.
  await signIn(page, 'admin@motormovers.test')
  await page.getByRole('link', { name: 'Courses', exact: true }).click()
  await page.getByRole('link', { name: new RegExp(title) }).click()
  await expect(page.getByRole('cell', { name: /Sam Rivers/ })).toBeVisible()
  await expect(page.getByRole('cell', { name: '2 of 2 lessons done' })).toBeVisible()
})

test('a trainer account has nothing to sign in to while courses are recorded', async ({ page }) => {
  await signIn(page, 'trainer@motormovers.test')
  await expect(page.getByRole('heading', { name: 'Courses are recorded' })).toBeVisible()
  await expect(page.getByText('There is nothing for a trainer account to do here.')).toBeVisible()
  await expect(page.getByRole('link', { name: 'Live sessions', exact: true })).toHaveCount(0)
  await expect(page.getByRole('link', { name: 'My cohorts', exact: true })).toHaveCount(0)
  await expectAccessible(page)
  await page.goto('/trainer/cohorts')
  await expect(page.getByRole('heading', { name: 'Courses are recorded' })).toBeVisible()
})

test('a teacher asks for a course place, the head approves it, and the teacher can cancel it', async ({ page }) => {
  await signIn(page, 'newteacher@oakfield.test')
  await page.getByRole('link', { name: 'Training', exact: true }).click()
  await expect(page.getByText('your head approves each one')).toBeVisible()
  const card = page.locator('section', { has: page.getByRole('heading', { name: 'Practitioner course' }) })
  await expect(card).toContainText('3 recorded lessons, about 60 minutes in all, to take in your own time, then a test')
  await card.getByRole('button', { name: 'Ask for a place' }).click()
  await expect(page.getByText('Request sent')).toBeVisible()
  await expect(card.getByText('Waiting for approval')).toBeVisible()
  await expectAccessible(page)
  await page.getByRole('button', { name: 'Sign out' }).click()

  await signIn(page, 'head@oakfield.test')
  await page.getByRole('link', { name: 'Payments', exact: true }).click()
  const toApprove = page.locator('section', { has: page.getByRole('heading', { name: 'Course places to approve' }) })
  const request = toApprove.locator('.cohort', { hasText: 'Mr Lewis' })
  await expectAccessible(page)
  await request.getByRole('button', { name: 'Approve' }).click()
  await expect(page.getByText("Mr Lewis's place on Practitioner course is booked")).toBeVisible()
  await expect(page.getByRole('cell', { name: 'Practitioner course · Mr Lewis' })).toBeVisible()
  await page.getByRole('button', { name: 'Sign out' }).click()

  await signIn(page, 'newteacher@oakfield.test')
  await page.getByRole('link', { name: 'Training', exact: true }).click()
  const place = page.locator('.enrolment', { hasText: 'Practitioner course' })
  await expect(place).toContainText('0 of 3 lessons done, then the test')
  await place.getByRole('button', { name: 'Cancel place' }).click()
  // Changing your mind keeps the place.
  await page.getByRole('dialog').getByRole('button', { name: 'Keep my place' }).click()
  await expect(page.getByRole('dialog')).toBeHidden()
  await place.getByRole('button', { name: 'Cancel place' }).click()
  await page.getByRole('dialog').getByRole('button', { name: 'Cancel place' }).click()
  await expect(page.getByText(/is cancelled\. The school's invoice for it is cancelled/)).toBeVisible()
  await expect(card.getByRole('button', { name: 'Ask for a place' })).toBeVisible()
})

test('a practitioner finishes the lessons, passes the final test and gets the certificate', async ({ page }) => {
  // Sam, in the demo, has done the first of the Practitioner course's three lessons.
  await signIn(page, 'practitioner@example.test')
  const place = page.locator('.enrolment', { hasText: 'Practitioner course' })
  await expect(place).toContainText('1 of 3 lessons done, then the test')
  // Started, so the place is no longer Sam's to cancel.
  await expect(place.getByRole('button', { name: 'Cancel place' })).toHaveCount(0)
  await expect(page.locator('.quiz-list li', { hasText: 'Final test' })).toContainText('Opens once every lesson is done')
  await place.getByRole('link', { name: 'Continue the course' }).click()
  await expect(page.getByRole('heading', { name: 'Practitioner course', level: 1 })).toBeVisible()
  await expect(page.getByText('Not open yet')).toBeVisible()
  await expectAccessible(page)
  // The placeholder lessons have no film, so each can be marked done in turn.
  await page.getByRole('button', { name: 'Mark lesson 2 as done' }).click()
  await expect(page.getByText('Lesson 2 is done')).toBeVisible()
  await page.getByRole('button', { name: 'Mark lesson 3 as done' }).click()
  await expect(page.getByText('Every lesson is done')).toBeVisible()

  // Ten questions, eight right to pass. First seven right: not enough.
  await page.getByRole('link', { name: 'Take the test' }).click()
  await expect(page.getByRole('heading', { name: 'Final test (placeholder questions)' })).toBeVisible()
  const right = page.getByLabel('Placeholder answer A (the right one)')
  const wrong = page.getByLabel('Placeholder answer B')
  await expect(right).toHaveCount(10)
  for (let i = 0; i < 10; i++) await (i < 7 ? right : wrong).nth(i).check()
  await expectAccessible(page)
  await page.getByRole('button', { name: 'Send answers' }).click()
  await expect(page.getByText('Not passed yet: 70%')).toBeVisible()
  await page.getByRole('button', { name: 'Try again' }).click()
  for (let i = 0; i < 10; i++) await (i < 8 ? right : wrong).nth(i).check()
  await page.getByRole('button', { name: 'Send answers' }).click()
  await expect(page.getByText('Passed: 80%')).toBeVisible()

  // The test completed the course.
  await page.getByRole('link', { name: 'see your certificate' }).click()
  await expect(page.getByRole('heading', { name: 'Certificate of completion' })).toBeVisible()
  const certificate = page.getByRole('article', { name: 'Certificate' })
  await expect(certificate.getByText('Sam Rivers')).toBeVisible()
  await expect(certificate.getByText('Practitioner course')).toBeVisible()
  await expect(certificate).toContainText('has completed every lesson and passed the test')
  const download = page.waitForEvent('download')
  await page.getByRole('button', { name: 'Download PDF' }).click()
  expect((await download).suggestedFilename()).toBe('certificate.pdf')
  await expectAccessible(page)
  await page.getByRole('button', { name: 'Sign out' }).click()

  // The team sees it issued.
  await signIn(page, 'admin@motormovers.test')
  await page.getByRole('link', { name: 'Courses', exact: true }).click()
  await page.getByRole('link', { name: /Practitioner course/ }).click()
  const sam = page.getByRole('row', { name: /Sam Rivers/ })
  await expect(sam).toContainText('3 of 3 lessons done, test passed')
  await expect(sam).toContainText(/Issued \w{3} \d{1,2} \w{3} \d{4}/)
  await expect(sam.getByRole('button', { name: 'Cancel place' })).toHaveCount(0)
})

test('the team adds a trust and opens a school’s setup', async ({ page }) => {
  await signIn(page, 'admin@motormovers.test')
  await page.getByRole('link', { name: 'Schools & licences', exact: true }).click()
  await expectAccessible(page)
  const name = `Browser Trust ${Date.now()}`
  await page.getByLabel('New trust or school group').fill(name)
  await page.getByRole('button', { name: 'Add trust or group', exact: true }).click()
  await expect(page.getByText(`${name} added`)).toBeVisible()

  await page.getByRole('row', { name: /Marlow Infants/ }).getByRole('link', { name: 'Set up' }).click()
  await expect(page.getByText('Marlow Infants')).toBeVisible()
  await expect(page.getByRole('heading', { name: 'School setup' })).toBeVisible()
})

test('the team records an invoice, marks it paid and sees spending', async ({ page }) => {
  await signIn(page, 'admin@motormovers.test')
  await page.getByRole('link', { name: 'Payments', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Payments', exact: true })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Received each month' })).toBeVisible()
  await expectAccessible(page)

  const description = `Browser licence ${Date.now()}`
  const form = page.locator('section', { has: page.getByRole('heading', { name: 'Record a payment' }) })
  await form.getByRole('combobox', { name: /^School/ }).selectOption({ label: 'Ferrers Park' })
  await form.getByLabel('Description').fill(description)
  await form.getByLabel('Amount (£)').fill('450')
  await form.getByRole('button', { name: 'Record payment' }).click()
  await expect(page.getByText('£450 from the school recorded')).toBeVisible()

  const row = page.getByRole('row', { name: new RegExp(description) })
  await expect(row.getByText('Due', { exact: true })).toBeVisible()
  await row.getByRole('button', { name: 'Mark paid today' }).click()
  await expect(page.getByText(`${description} marked paid`)).toBeVisible()
  await expect(row.getByText('Paid', { exact: true })).toBeVisible()
  await expect(row.getByRole('button', { name: 'Mark paid today' })).toHaveCount(0)
})

test('the team refunds part of a paid payment, and the credit note can be downloaded', async ({ page }) => {
  await signIn(page, 'admin@motormovers.test')
  await page.getByRole('link', { name: 'Payments', exact: true }).click()
  const description = `Browser refund ${Date.now()}`
  const form = page.locator('section', { has: page.getByRole('heading', { name: 'Record a payment' }) })
  await form.getByRole('combobox', { name: /^School/ }).selectOption({ label: 'Ferrers Park' })
  await form.getByLabel('Description').fill(description)
  await form.getByLabel('Amount (£)').fill('120')
  await form.getByRole('button', { name: 'Record payment' }).click()
  const row = page.getByRole('row', { name: new RegExp(description) })
  await row.getByRole('button', { name: 'Mark paid today' }).click()

  await row.getByRole('button', { name: 'Refund' }).click()
  const refund = page.getByRole('form', { name: 'Refund' })
  await expect(refund.getByText('£120 left to refund of £120.')).toBeVisible()
  await expectAccessible(page)
  await refund.getByLabel('Amount (£)').fill('30')
  await refund.getByLabel('Reason').fill('Place cancelled')
  await refund.getByRole('button', { name: 'Refund', exact: true }).click()
  await page.getByRole('dialog').getByRole('button', { name: 'Refund', exact: true }).click()

  // Paid by bank transfer, so it is recorded and the money goes back by hand. Part of it, so it stays paid.
  await expect(page.getByText('£30 recorded as refunded. Please return the money by hand.')).toBeVisible()
  await expect(row.getByText('Part refunded')).toBeVisible()
  await expect(row.getByText('Paid', { exact: true })).toBeVisible()
  await expect(row.getByText(/Refunded £30 .* by hand · includes VAT £5/)).toBeVisible()
  const download = page.waitForEvent('download')
  await row.getByRole('button', { name: /^CN-[0-9]{6}$/ }).click()
  expect((await download).suggestedFilename()).toMatch(/^CN-[0-9]{6}[.]pdf$/)
})

test('a head sees their school’s payments and a trust or group lead sees spending by school', async ({ page }) => {
  await signIn(page, 'head@oakfield.test')
  await page.getByRole('link', { name: 'Payments', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Oakfield Primary · Payments' })).toBeVisible()
  await expect(page.getByRole('cell', { name: /Printed session cards/ })).toBeVisible()
  await expect(page.getByText('Parent course')).toHaveCount(0)
  await expectAccessible(page)

  await page.getByRole('button', { name: 'Sign out' }).click()
  await signIn(page, 'trust@northbridge.test')
  const spending = page.locator('section', { has: page.getByRole('heading', { name: 'Spending' }) })
  await expect(spending.getByRole('row', { name: /Brookhouse/ })).toContainText('£571.50')
  await expect(spending.getByRole('row', { name: /All schools/ })).toBeVisible()
})

test('a parent sees what is due, how to pay it, and what they have paid', async ({ page }) => {
  await signIn(page, 'parent@example.test')
  await expect(page.getByText('You have £15 to pay.')).toBeVisible()
  await page.getByRole('link', { name: 'See what it is for and how to pay' }).click()

  await expect(page.getByRole('heading', { name: 'Payments', level: 1 })).toBeVisible()
  await expect(page.locator('.kpi', { hasText: 'Paid in total' })).toContainText('£55')
  await expect(page.locator('.kpi', { hasText: 'Due now' })).toContainText('£15')
  const toPay = page.locator('section', { has: page.getByRole('heading', { name: 'To pay' }) })
  await expect(toPay.getByText('Rhyme book and CD')).toBeVisible()
  await expect(toPay.getByText(/reference PPP-1033/)).toBeVisible()
  const history = page.locator('section', { has: page.getByRole('heading', { name: 'Payment history' }) })
  await expect(history.getByRole('cell', { name: /Parent course/ })).toBeVisible()
  await expectAccessible(page)
})

test('the team schedules a price rise, and past prices stay in the history', async ({ page }) => {
  await signIn(page, 'admin@motormovers.test')
  await page.getByRole('link', { name: 'Plans and prices', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Plans and prices' })).toBeVisible()
  // A retired plan shows no price (task 88): its old one is not on offer.
  await expect(page.locator('.plan-retired').first()).toContainText('No longer on sale')
  await expectAccessible(page)

  const plan = page.locator('.plan-card', { has: page.getByRole('heading', { name: /^School/ }) })
  const from = new Date(Date.now() + 40 * 86_400_000).toISOString().slice(0, 10)
  await plan.getByLabel('New yearly price (£)').fill('950')
  await plan.getByLabel('From').fill(from)
  await plan.getByRole('button', { name: 'Set price' }).click()
  await expect(page.getByText(`School: £950 a year from ${day(from)}`)).toBeVisible()
  await expect(plan.getByText(`changes to £950 from ${day(from)}`)).toBeVisible()
  // Today's price is unchanged until then.
  await expect(plan.getByText('£635 a year', { exact: true })).toBeVisible()
  await plan.getByText('Price history').click()
  await expect(plan.getByRole('cell', { name: 'Before launch' })).toBeVisible()
})

test('the team signs a school up at an agreed price and changes it', async ({ page }) => {
  await signIn(page, 'admin@motormovers.test')
  await page.getByRole('link', { name: 'Schools & licences', exact: true }).click()
  const name = `Discount Nursery ${Date.now()}`
  const form = page.locator('section', { has: page.getByRole('heading', { name: 'Sign a school up' }) })
  await form.getByLabel('School name').fill(name)
  await form.getByLabel('Plan').selectOption({ label: 'Childcare centre or kindergarten (£395)' })
  await form.getByLabel('Agreed yearly price (£, optional)').fill('350')
  await form.getByRole('button', { name: 'Sign up school' }).click()
  await expect(page.getByText(`${name} signed up`)).toBeVisible()

  const row = page.getByRole('row', { name: new RegExp(name) })
  await expect(row).toContainText('£350 (plan £395)')
  await row.getByRole('button', { name: `Change the yearly price for ${name}` }).click()
  await row.getByLabel(`Yearly price for ${name} (£)`).fill('330')
  await row.getByRole('button', { name: 'Save' }).click()
  await expect(page.getByText(`${name} now pays £330 a year`)).toBeVisible()
  await expect(row).toContainText('£330 (plan £395)')
})

test('recording a payment fills in the licence or course price', async ({ page }) => {
  await signIn(page, 'admin@motormovers.test')
  await page.getByRole('link', { name: 'Payments', exact: true }).click()
  const form = page.locator('section', { has: page.getByRole('heading', { name: 'Record a payment' }) })
  await form.getByRole('combobox', { name: /^School/ }).selectOption({ label: 'Oakfield Primary' })
  await expect(form.getByLabel('Amount (£)')).toHaveValue('571.5')
  await expect(form.getByLabel('Description')).toHaveValue('Group of schools licence')

  await form.getByLabel(/A parent, practitioner or member of staff/).check()
  await form.getByRole('combobox', { name: /^For/ }).selectOption('COURSE')
  await form.getByRole('combobox', { name: /^Course/ }).selectOption({ label: 'Practitioner course, yearly renewal (£30)' })
  await expect(form.getByLabel('Amount (£)')).toHaveValue('30')
  await expect(form.getByLabel('Description')).toHaveValue('Practitioner course, yearly renewal')
})
