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
  await expect(page.getByRole('cell', { name: 'Oakfield Primary' })).toBeVisible()
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
