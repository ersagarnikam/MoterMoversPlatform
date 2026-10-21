import { expect, expectAccessible, signIn, test } from './fixtures'

test('a head sets up a class, adds children and invites a teacher', async ({ page }) => {
  await signIn(page, 'head@oakfield.test')
  await page.getByRole('link', { name: 'School setup' }).click()
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

  page.once('dialog', (d) => d.accept())
  await row.locator('.setup-children li').first().getByRole('button', { name: 'Left the school' }).click()
  await expect(row.locator('.setup-children li')).toHaveCount(1)
})

test('a full name is refused while the school uses initials only, and the typed list is kept', async ({ page }) => {
  await signIn(page, 'head@oakfield.test')
  await page.getByRole('link', { name: 'School setup' }).click()
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

test('the trust or group lead sees schools but no children', async ({ page }) => {
  await signIn(page, 'trust@northbridge.test')
  // The Spending table lists the same schools, so look only in the licences and results table.
  const schools = page.getByRole('table').filter({ has: page.getByRole('columnheader', { name: 'Licence' }) })
  await expect(schools.getByRole('cell', { name: 'Oakfield Primary' })).toBeVisible()
  await expect(page.getByRole('link', { name: 'School setup' })).toHaveCount(0)
})

test('the team signs off a rhyme and blocks diagnostic wording', async ({ page }) => {
  await signIn(page, 'admin@motormovers.test')
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
  const stamp = Date.now()
  const title = `Browser session ${stamp}`
  await page.getByText('Add a class session').click()
  // Sessions are unique by stage, module and day, so each run uses its own module.
  await page.getByLabel('Module').fill(String(100 + (stamp % 100_000)))
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

test('the team sets up a course and opens a cohort for booking', async ({ page }) => {
  await signIn(page, 'admin@motormovers.test')
  await page.getByRole('link', { name: 'Courses' }).click()
  await expect(page.getByRole('heading', { name: 'Courses', exact: true })).toBeVisible()

  const title = `Browser course ${Date.now()}`
  const setUp = page.locator('section', { has: page.getByRole('heading', { name: 'Set up a course' }) })
  await setUp.getByLabel('Course title').fill(title)
  await setUp.getByLabel('Description').fill('A short course for early-years staff.')
  await setUp.getByLabel('Price (£)').fill('45')
  await setUp.getByRole('button', { name: 'Set up course' }).click()
  await expect(page.getByText(`${title} set up`)).toBeVisible()

  const course = page.locator('.course-admin', { hasText: title })
  await expect(course.getByText('No cohorts yet.')).toBeVisible()
  await course.getByText('Open a cohort').click()
  const startsOn = new Date(Date.now() + 30 * 86_400_000).toISOString().slice(0, 10)
  await course.getByLabel('Cohort name').fill('Browser cohort')
  await course.getByLabel('Starts').fill(startsOn)
  await course.getByLabel('Day and time').fill('Wednesdays 7:00 pm UK')
  await course.getByLabel('Trainer').fill('Pat')
  await expectAccessible(page)
  await course.getByRole('button', { name: 'Open cohort' }).click()
  await expect(course.getByText('0 of 30 places booked')).toBeVisible()

  await course.getByRole('button', { name: 'Edit cohort' }).click()
  const edit = course.locator('form', { has: page.getByRole('button', { name: 'Save cohort' }) })
  await edit.getByLabel('Places').fill('12')
  await edit.getByRole('button', { name: 'Save cohort' }).click()
  await expect(course.getByText('0 of 12 places booked')).toBeVisible()

  await page.getByRole('link', { name: 'See the catalogue as learners do' }).click()
  const listed = page.locator('section', { has: page.getByRole('heading', { name: title }) })
  await expect(listed.getByText('12 places left')).toBeVisible()
})

test('the team marks attendance and the assessment, and the learner gets a certificate', async ({ page }) => {
  const cohortName = 'Cohort B · UK morning / AU evening'
  await signIn(page, 'practitioner@example.test')
  await page.locator('.cohort', { hasText: cohortName }).getByRole('button', { name: 'Book a place' }).click()
  await expect(page.locator('.cohort', { hasText: cohortName }).getByText('Booked')).toBeVisible()
  await page.getByRole('button', { name: 'Sign out' }).click()

  await signIn(page, 'admin@motormovers.test')
  await page.getByRole('link', { name: 'Courses' }).click()
  const cohort = page.locator('.cohort-block', { hasText: cohortName })
  await cohort.getByRole('button', { name: 'Bookings and attendance' }).click()
  const mark = cohort.getByRole('button', { name: 'Mark a session attended for Sam Rivers' })
  await mark.click()
  await mark.click()
  await expect(cohort.getByText('2 of 4')).toBeVisible()
  // A session marked by mistake comes back off.
  await cohort.getByRole('button', { name: 'Take a session off Sam Rivers' }).click()
  await expect(cohort.getByText('1 of 4')).toBeVisible()
  for (let i = 0; i < 3; i++) await mark.click()
  await expect(cohort.getByText('4 of 4')).toBeVisible()
  await expect(cohort.getByText('the assessment to go')).toBeVisible()
  await expectAccessible(page)
  const passed = cohort.getByLabel('Sam Rivers passed the assessment')
  await passed.click()
  await expect(cohort.getByText(/^Issued \d{4}-\d{2}-\d{2}, valid until/)).toBeVisible()
  // Once issued, the certificate cannot be taken back here.
  await expect(passed).toBeDisabled()
  await page.getByRole('button', { name: 'Sign out' }).click()

  await signIn(page, 'practitioner@example.test')
  await page.getByRole('link', { name: 'View your certificate' }).click()
  await expect(page.getByRole('heading', { name: 'Certificate of completion' })).toBeVisible()
  const certificate = page.getByRole('article', { name: 'Certificate' })
  await expect(certificate.getByText('Sam Rivers')).toBeVisible()
  await expect(certificate.getByText('Motor Movers practitioner course')).toBeVisible()
  await expectAccessible(page)
})

test('the team adds a trust and opens a school’s setup', async ({ page }) => {
  await signIn(page, 'admin@motormovers.test')
  await page.getByRole('link', { name: 'Schools & licences' }).click()
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
  await page.getByRole('link', { name: 'Payments' }).click()
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

test('a head sees their school’s payments and a trust or group lead sees spending by school', async ({ page }) => {
  await signIn(page, 'head@oakfield.test')
  await page.getByRole('link', { name: 'Payments' }).click()
  await expect(page.getByRole('heading', { name: 'Oakfield Primary · Payments' })).toBeVisible()
  await expect(page.getByRole('cell', { name: /Printed session cards/ })).toBeVisible()
  await expect(page.getByText('Motor Movers parent course')).toHaveCount(0)
  await expectAccessible(page)

  await page.getByRole('button', { name: 'Sign out' }).click()
  await signIn(page, 'trust@northbridge.test')
  const spending = page.locator('section', { has: page.getByRole('heading', { name: 'Spending' }) })
  await expect(spending.getByRole('row', { name: /Brookhouse/ })).toContainText('£1,487.50')
  await expect(spending.getByRole('row', { name: /All schools/ })).toBeVisible()
})

test('a parent sees what they have paid', async ({ page }) => {
  await signIn(page, 'parent@example.test')
  const mine = page.locator('section', { has: page.getByRole('heading', { name: 'Your payments' }) })
  await expect(mine.getByRole('cell', { name: /Motor Movers parent course/ })).toBeVisible()
  await expect(mine.getByText('£45 paid')).toBeVisible()
})

test('the team schedules a price rise, and past prices stay in the history', async ({ page }) => {
  await signIn(page, 'admin@motormovers.test')
  await page.getByRole('link', { name: 'Plans and prices' }).click()
  await expect(page.getByRole('heading', { name: 'Plans and prices' })).toBeVisible()
  await expectAccessible(page)

  const plan = page.locator('.plan-card', { has: page.getByRole('heading', { name: /^Primary core/ }) })
  const from = new Date(Date.now() + 40 * 86_400_000).toISOString().slice(0, 10)
  await plan.getByLabel('New yearly price (£)').fill('950')
  await plan.getByLabel('From').fill(from)
  await plan.getByRole('button', { name: 'Set price' }).click()
  await expect(page.getByText(`Primary core: £950 a year from ${from}`)).toBeVisible()
  await expect(plan.getByText(`changes to £950 from ${from}`)).toBeVisible()
  // Today's price is unchanged until then.
  await expect(plan.getByText('£900 a year', { exact: true })).toBeVisible()
  await plan.getByText('Price history').click()
  await expect(plan.getByRole('cell', { name: 'Launch' })).toBeVisible()
})

test('the team signs a school up at an agreed price and changes it', async ({ page }) => {
  await signIn(page, 'admin@motormovers.test')
  await page.getByRole('link', { name: 'Schools & licences' }).click()
  const name = `Discount Nursery ${Date.now()}`
  const form = page.locator('section', { has: page.getByRole('heading', { name: 'Sign a school up' }) })
  await form.getByLabel('School name').fill(name)
  await form.getByLabel('Plan').selectOption({ label: 'Early years (£450)' })
  await form.getByLabel('Agreed yearly price (£, optional)').fill('400')
  await form.getByRole('button', { name: 'Sign up school' }).click()
  await expect(page.getByText(`${name} signed up`)).toBeVisible()

  const row = page.getByRole('row', { name: new RegExp(name) })
  await expect(row).toContainText('£400 (plan £450)')
  await row.getByRole('button', { name: `Change the yearly price for ${name}` }).click()
  await row.getByLabel(`Yearly price for ${name} (£)`).fill('380')
  await row.getByRole('button', { name: 'Save' }).click()
  await expect(page.getByText(`${name} now pays £380 a year`)).toBeVisible()
  await expect(row).toContainText('£380 (plan £450)')
})

test('recording a payment fills in the licence or course price', async ({ page }) => {
  await signIn(page, 'admin@motormovers.test')
  await page.getByRole('link', { name: 'Payments' }).click()
  const form = page.locator('section', { has: page.getByRole('heading', { name: 'Record a payment' }) })
  await form.getByRole('combobox', { name: /^School/ }).selectOption({ label: 'Oakfield Primary' })
  await expect(form.getByLabel('Amount (£)')).toHaveValue('1487.5')
  await expect(form.getByLabel('Description')).toHaveValue('Trust licence')

  await form.getByLabel(/A parent, practitioner or member of staff/).check()
  await form.getByRole('combobox', { name: /^For/ }).selectOption('COURSE')
  await form.getByRole('combobox', { name: /^Course/ }).selectOption({ label: 'Motor Movers practitioner course, yearly renewal (£60)' })
  await expect(form.getByLabel('Amount (£)')).toHaveValue('60')
  await expect(form.getByLabel('Description')).toHaveValue('Motor Movers practitioner course, yearly renewal')
})
