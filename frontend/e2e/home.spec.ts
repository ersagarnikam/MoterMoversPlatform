import { expect, expectAccessible, signIn, test } from './fixtures'

/** Motor Movers at Home on the website: families whose child's school is not on Motor Movers. */

test('a family signs up on the website, does the check and the first session', async ({ page }) => {
  await page.goto('/for-families')
  await expect(page.getByRole('heading', { name: 'Your child’s school is not on Motor Movers?' })).toBeVisible()
  await page.getByRole('link', { name: 'Create a parent account' }).click()

  await expect(page.getByRole('heading', { name: 'Create a parent account' })).toBeVisible()
  await expectAccessible(page)
  await page.getByLabel('Your name').fill('Sam Rivers')
  await page.getByLabel('Email').fill(`web-family-${Date.now()}@example.test`)
  await page.getByLabel('Password').fill('a-long-test-password')
  await page.getByRole('button', { name: 'Create account' }).click()

  await expect(page.getByRole('heading', { name: 'At home', level: 1 })).toBeVisible()
  await expect(page.getByRole('link', { name: 'At home', exact: true })).toBeVisible()
  await page.getByLabel('First name or nickname').fill('Mo')
  await page.getByLabel('Age: years').fill('4')
  await page.getByLabel('and months').fill('2')
  await page.getByRole('button', { name: 'Add and start the check' }).click()

  // The starting check: movements from Pat's charts for the year up to Mo's age.
  await expect(page.getByRole('heading', { name: 'Can Mo do these?' })).toBeVisible()
  await expect(page.getByRole('note')).toHaveText(/^Not a diagnosis\. This shows/)
  await expectAccessible(page)
  for (const yes of (await page.getByRole('button', { name: 'Yes', exact: true }).all()).slice(0, 6)) await yes.click()
  await page.getByRole('button', { name: 'See where to start' }).click()
  await expect(page.getByRole('heading', { name: 'Where to start' })).toBeVisible()
  await page.getByRole('button', { name: 'Start with Green' }).click()

  // Today's session: Pat's Green exercises one at a time, then the first-time ratings in the parent's words.
  await expect(page.getByRole('heading', { name: 'Mo', level: 1 })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Today’s session for Mo' })).toBeVisible()
  await expectAccessible(page)
  await page.getByRole('link', { name: 'Start', exact: true }).click()
  await expect(page.getByText('Exercise 1 of 4')).toBeVisible()
  for (let i = 0; i < 3; i++) await page.getByRole('button', { name: 'Next exercise' }).click()
  await page.getByRole('button', { name: 'We did it' }).click()

  await expect(page.getByRole('heading', { name: 'How did it go?' })).toBeVisible()
  const exercises = page.getByRole('group', { name: /^How .+ went$/ })
  await expect(exercises).toHaveCount(4)
  for (const exercise of await exercises.all()) await exercise.getByRole('button', { name: 'Got it' }).click()
  await expectAccessible(page)
  await page.getByRole('button', { name: 'Save' }).click()
  await expect(page.getByRole('heading', { name: 'Well done, Mo!' })).toBeVisible()

  await page.getByRole('link', { name: 'Back to Mo' }).click()
  await expect(page.getByRole('heading', { name: 'Done today' })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Exercises · Stage 4 · Green' })).toBeVisible()
  // No scores: the parent's words only.
  await expect(page.locator('main')).not.toContainText(/\b[1-4] of 4\b/)
})

test('a parent rates the end of a module and emails a family code', async ({ page }) => {
  await signIn(page, 'home@example.test')
  await page.getByRole('link', { name: 'At home', exact: true }).click()
  await page.getByRole('link', { name: /^Ava/ }).click()

  await expect(page.getByRole('heading', { name: 'Ava', level: 1 })).toBeVisible()
  await page.getByRole('link', { name: /^Rate \d+ exercises$/ }).click()
  await expect(page.getByText('Ava has finished module 1.')).toBeVisible()
  const exercises = page.getByRole('group', { name: /^How .+ went$/ })
  await expect(exercises.first()).toBeVisible()
  for (const exercise of await exercises.all()) await exercise.getByRole('button', { name: 'Nearly' }).click()
  await page.getByRole('button', { name: 'Save' }).click()
  await expect(page.getByRole('heading', { name: 'Thank you' })).toBeVisible()

  await page.goto('/home/family')
  await expect(page.getByRole('heading', { name: 'Family and sharing', level: 1 })).toBeVisible()
  await expectAccessible(page)
  await page.getByLabel('Their email (optional)').fill('gran@example.test')
  await page.getByRole('button', { name: 'Email a code' }).click()
  await expect(page.getByText(/^Emailed to gran@example\.test\. Works once/)).toBeVisible()
})
