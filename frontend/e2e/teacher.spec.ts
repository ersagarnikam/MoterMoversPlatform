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

test('a teacher screens a child, one item at a time', async ({ page }) => {
  await signIn(page, 'teacher@oakfield.test')
  await page.getByRole('link', { name: 'Screening' }).click()
  await expect(page.getByRole('heading', { level: 1 })).toContainText('Screening')
  await page.locator('.roster-child').first().click()
  await expect(page.getByText(/Item 1 of \d+/)).toBeVisible()
  await page.getByRole('radio').nth(2).click()
  await page.getByRole('button', { name: 'Next item' }).click()
  await expect(page.getByText(/Item 2 of \d+/)).toBeVisible()
  await expectAccessible(page)
})

test('the readiness profile always says it is not a diagnosis', async ({ page }) => {
  await signIn(page, 'teacher@oakfield.test')
  await page.locator('.roster .roster-child').first().click()
  await expect(page.getByRole('heading', { name: /Readiness profile/ })).toBeVisible()
  await expect(page.getByText(/Not a diagnosis/)).toBeVisible()
  await expectAccessible(page)
})

test('resources show the rhyme book and rhyme words', async ({ page }) => {
  await signIn(page, 'teacher@oakfield.test')
  await page.getByRole('link', { name: 'Resources' }).click()
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
  await expect(page.getByText(/^Completed \d{4}-\d{2}-\d{2}$/)).toBeVisible()

  await page.getByRole('link', { name: 'My class' }).click()
  await expect(page.getByRole('button', { name: 'Start session' })).toBeVisible()
})
