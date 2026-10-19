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

test('a head sees progress and can download the governor summary', async ({ page }) => {
  await signIn(page, 'head@oakfield.test')
  await expect(page.getByRole('heading', { name: /Participation by class/ })).toBeVisible()
  const download = page.waitForEvent('download')
  await page.getByRole('button', { name: /Governor summary/i }).click()
  expect((await download).suggestedFilename()).toBe('governor-summary.csv')
})

test('the trust lead sees schools but no children', async ({ page }) => {
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
  // Sessions are unique by stage, unit and day, so each run uses its own unit.
  await page.getByLabel('Unit').fill(String(100 + (stamp % 100_000)))
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

test('the team adds a trust and opens a school’s setup', async ({ page }) => {
  await signIn(page, 'admin@motormovers.test')
  await page.getByRole('link', { name: 'Schools & licences' }).click()
  await expectAccessible(page)
  const name = `Browser Trust ${Date.now()}`
  await page.getByLabel('New trust').fill(name)
  await page.getByRole('button', { name: 'Add trust', exact: true }).click()
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

test('a head sees their school’s payments and a trust lead sees spending by school', async ({ page }) => {
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
