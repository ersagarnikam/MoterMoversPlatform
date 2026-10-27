import { expect, expectAccessible, isWeekend, signIn, test } from './fixtures'

test('a trained teacher runs today’s session with its rhyme', async ({ page }) => {
  await signIn(page, 'teacher@oakfield.test')
  await expect(page.getByRole('heading', { level: 1 })).toContainText('Year 2 R · Today’s session')
  await expectAccessible(page)

  await page.getByRole('button', { name: 'Start session' }).click()
  await expect(page.getByRole('heading', { name: /^Rhyme · / })).toBeVisible()
  await expect(page.getByText('The recording is on its way')).toBeVisible()
  await expectAccessible(page)

  await page.getByRole('button', { name: 'Skip to end' }).click()
  await page.getByRole('button', { name: /^Complete session/ }).click()
  if (isWeekend()) {
    await expect(page.getByText('Sessions are recorded on school days only')).toBeVisible()
  } else {
    await expect(page.getByText('✓ Done for today')).toBeVisible()
  }
})

test('a teacher scores the class on an exercise, 1 to 4', async ({ page }) => {
  await signIn(page, 'teacher@oakfield.test')
  await page.getByRole('link', { name: 'Assess children' }).click()
  await expect(page.getByRole('heading', { level: 1 })).toContainText('Assess')
  await expect(page.getByLabel('Exercise')).toHaveValue(/\d+/)
  await expect(page.getByText('Confident and accurate', { exact: false }).first()).toBeVisible()
  // The last children of the demo class have no scores yet.
  const last = page.locator('.score-buttons').last()
  await last.getByRole('radio', { name: '3' }).click()
  await expect(last.getByRole('radio', { name: '3' })).toHaveAttribute('aria-checked', 'true')
  await expect(page.getByText('0 saved on this device')).toBeVisible()
  await expectAccessible(page)
})

test('a teacher records movements on the developmental charts with the age in months', async ({ page }) => {
  await signIn(page, 'teacher@oakfield.test')
  await page.getByRole('link', { name: 'Assess children' }).click()
  await page.getByRole('button', { name: 'Developmental charts' }).click()
  await page.getByRole('link', { name: /developmental charts$/ }).last().click()
  await expect(page.getByRole('heading', { level: 1 })).toContainText('Developmental charts')
  await expect(page.getByText('Not a diagnosis.')).toBeVisible()
  await page.getByLabel('Age in months').fill('80')
  const movement = page.getByRole('switch', { name: /Handedness well established/ })
  await movement.click()
  await expect(movement).toHaveAttribute('aria-checked', 'true')
  await expect(page.getByText(/Secure at 80 months/)).toBeVisible()
  await expectAccessible(page)

  await page.getByRole('link', { name: 'Assessment profile' }).click()
  await expect(page.getByRole('heading', { name: /^Assessment · / })).toBeVisible()
  await expect(page.getByText('Fine motor')).toBeVisible()
})

test('a teacher can still use the readiness check, one movement at a time', async ({ page }) => {
  await signIn(page, 'teacher@oakfield.test')
  await page.getByRole('link', { name: 'Assess children' }).click()
  await page.getByRole('link', { name: 'Readiness check (0 to 3)' }).click()
  await expect(page.getByRole('heading', { level: 1 })).toContainText('Assessment')
  await page.locator('.roster-child').first().click()
  await expect(page.getByText(/Item 1 of \d+/)).toBeVisible()
  await page.getByRole('radio').nth(2).click()
  await page.getByRole('button', { name: 'Next item' }).click()
  await expect(page.getByText(/Item 2 of \d+/)).toBeVisible()
  await expectAccessible(page)
})

test('the roster shows initials, with the full name on hover only when the school shows names', async ({ page }) => {
  // Initials only (the demo default): no full name anywhere, not even on hover.
  await signIn(page, 'teacher@oakfield.test')
  const first = page.locator('.roster .roster-child').first()
  await expect(first).not.toHaveAttribute('title')
  await expect(first).toHaveAccessibleName(/^[A-Z]\.[A-Z]\., open readiness profile$/)

  // The head switches full names on and adds a child with a name to the same class.
  await page.getByRole('button', { name: 'Sign out' }).click()
  await signIn(page, 'head@oakfield.test')
  await page.getByRole('link', { name: 'School setup', exact: true }).click()
  const names = page.getByLabel(/Show children’s full names to staff/)
  // The box reflects the saved setting, so it ticks once the server has saved it.
  await names.click()
  await expect(names).toBeChecked()
  try {
    const row = page.locator('.setup-class', { hasText: 'Year 2 R' })
    await row.getByRole('button', { name: 'Edit class and children' }).click()
    await row.getByLabel(/Add children, one per line/).fill('Z.Z., Zara Zed')
    await row.getByRole('button', { name: 'Add children' }).click()
    await expect(row.getByText('Z.Z. · Zara Zed')).toBeVisible()

    await page.getByRole('button', { name: 'Sign out' }).click()
    await signIn(page, 'teacher@oakfield.test')
    const zara = page.getByRole('link', { name: 'Zara Zed, open readiness profile' })
    await expect(zara).toHaveAttribute('title', 'Zara Zed')
    await expect(zara).toContainText('ZZ')
    await expect(zara).toContainText('Z.Z.')
    await expect(zara).not.toContainText('Zara')
  } finally {
    // Put the demo school back to initials only for the other tests.
    await page.getByRole('button', { name: 'Sign out' }).click()
    await signIn(page, 'head@oakfield.test')
    await page.getByRole('link', { name: 'School setup', exact: true }).click()
    await page.getByLabel(/Show children’s full names to staff/).click()
    await expect(page.getByLabel(/Show children’s full names to staff/)).not.toBeChecked()
  }
})

test('the profile shows both assessments and always says it is not a diagnosis', async ({ page }) => {
  await signIn(page, 'teacher@oakfield.test')
  await page.locator('.roster .roster-child').first().click()
  await expect(page.getByRole('heading', { name: /^Assessment · / })).toBeVisible()
  await expect(page.getByRole('heading', { name: /^Exercises · Stage / })).toBeVisible()
  await expect(page.getByRole('heading', { name: /Readiness check/ })).toBeVisible()
  await expect(page.getByText('Not a diagnosis.')).toHaveCount(2)
  await expectAccessible(page)
})

test('resources show the rhyme book and rhyme words', async ({ page }) => {
  await signIn(page, 'teacher@oakfield.test')
  await page.getByRole('link', { name: 'Resources', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Motor Movers rhyme book' })).toBeVisible()
  await page.getByRole('button', { name: 'Rhymes' }).click()
  await expect(page.getByRole('heading', { name: 'Humpty Dumpty' })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Motor Movers rhyme book' })).toBeHidden()
  await expectAccessible(page)
})

test('a new teacher must finish the induction before running sessions', async ({ page }) => {
  await signIn(page, 'newteacher@oakfield.test')
  await expect(page.getByText('Complete the one-hour induction before running sessions')).toBeVisible()
  await expect(page.getByRole('button', { name: 'Start session' })).toHaveCount(0)

  await page.getByRole('link', { name: 'Go to the induction' }).click()
  await expect(page.getByRole('heading', { name: 'Motor Movers induction' })).toBeVisible()
  await expect(page.getByText('0 of 6 parts done · 60 minutes in all')).toBeVisible()
  await expectAccessible(page)
  for (let part = 1; part <= 6; part++) {
    await page.locator('details.lesson').nth(part - 1).locator('summary').click()
    await page.getByRole('button', { name: 'Mark as done' }).click()
  }
  await expect(page.getByText(/^Completed \w{3} \d{1,2} \w{3} \d{4}$/)).toBeVisible()

  await page.getByRole('link', { name: 'My class' }).click()
  await expect(page.getByRole('button', { name: 'Start session' })).toBeVisible()
})
