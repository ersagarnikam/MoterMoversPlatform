import { expect, expectAccessible, signIn, test } from './fixtures'

/** Motor Movers at Home on the website: families whose child's school is not on Motor Movers. */

test('a family signs up on the website, does the check and the first session', async ({ page }) => {
  await page.goto('/for-families')
  await expect(page.getByRole('heading', { name: 'Your child’s school is not on Motor Movers?' })).toBeVisible()
  await page.getByRole('link', { name: 'Create a parent account' }).click()

  await expect(page.getByRole('heading', { name: 'Create a parent account' })).toBeVisible()
  await expectAccessible(page)
  await page.getByLabel('Your name').fill('Sam Rivers')
  const email = `web-family-${Date.now()}@example.test`
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Password').fill('a-long-test-password')
  await page.getByRole('button', { name: 'Create account' }).click()

  await expect(page.getByRole('heading', { name: 'At home', level: 1 })).toBeVisible()
  await expect(page.getByRole('link', { name: 'At home', exact: true })).toBeVisible()
  await page.getByLabel('First name or nickname').fill('Mo')
  await page.getByLabel('Age: years').fill('4')
  await page.getByLabel('and months').fill('2')
  await page.getByRole('button', { name: 'Add and start the check' }).click()

  // The check, like the sessions, needs the parent induction first. Its lessons are placeholders until Pat's arrive.
  await expect(page.getByRole('heading', { name: 'Before the check' })).toBeVisible()
  await page.getByRole('link', { name: 'Start the induction' }).click()
  await expect(page.getByRole('heading', { name: 'Before your first session' })).toBeVisible()
  await expect(page.getByText('Placeholder lesson 1')).toBeVisible()
  await expectAccessible(page)
  for (const title of ['Placeholder lesson 1', 'Placeholder lesson 2']) {
    await page.getByText(title).click()
    await page.getByRole('button', { name: 'Mark as done' }).click()
  }
  await expect(page.getByText('You have finished the induction.')).toBeVisible()
  await page.getByRole('link', { name: 'Back to At home' }).click()

  // Then the family's 12-month pass (go-live task 26), offered at the price the team set. Card payments are off in
  // these tests, so the team extends the pass by hand, as it would for support.
  const pass = page.locator('.home-pass')
  await expect(pass.getByRole('heading', { name: 'The 12-month pass' })).toBeVisible()
  await expect(pass).toContainText('Sessions need a 12-month pass for your family')
  await expectAccessible(page)
  await page.getByRole('button', { name: 'Sign out' }).click()
  await signIn(page, 'admin@motormovers.test')
  await page.getByRole('link', { name: 'Payments', exact: true }).click()
  const support = page.locator('section', { has: page.getByRole('heading', { name: /Motor Movers at Home pass/ }) })
  await support.getByLabel('Parent or carer’s email').fill(email)
  await support.getByRole('button', { name: 'Find the family' }).click()
  await expect(support).toContainText('No pass yet.')
  const nextYear = new Date()
  nextYear.setFullYear(nextYear.getFullYear() + 1)
  await support.getByLabel('Extend to').fill(nextYear.toISOString().slice(0, 10))
  await support.getByRole('button', { name: 'Extend the pass' }).click()
  await expect(support).toContainText('Extended by the team')
  await page.getByRole('button', { name: 'Sign out' }).click()
  await signIn(page, email, 'a-long-test-password')
  await page.getByRole('link', { name: 'At home', exact: true }).click()
  await expect(pass.getByRole('heading', { name: 'Your pass' })).toBeVisible()
  await page.getByRole('link', { name: /^Mo 4 years/ }).click()
  await page.getByRole('link', { name: 'Start the check' }).first().click()

  // The starting check: movements from Pat's charts for the year up to Mo's age.
  await expect(page.getByRole('heading', { name: 'Can Mo do these?' })).toBeVisible()
  await expect(page.getByRole('note')).toHaveText(/^Not a diagnosis\. This shows/)
  await expectAccessible(page)
  for (const yes of (await page.getByRole('button', { name: 'Yes', exact: true }).all()).slice(0, 6)) await yes.click()
  await page.getByRole('button', { name: 'See where to start' }).click()
  await expect(page.getByRole('heading', { name: 'Where to start' })).toBeVisible()
  await page.getByRole('button', { name: 'Start with Green' }).click()

  // Mo (4 years 2 months) starts at Green, as children at school do. Week 1 of module 1 is Pat's first two exercises,
  // one at a time after her safety note, then Green's Cool Down 1, then the first-time ratings in the parent's words.
  await expect(page.getByRole('heading', { name: 'Mo', level: 1 })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Today’s session for Mo' })).toBeVisible()
  await expectAccessible(page)
  await page.getByRole('link', { name: 'Start', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Module 1 · week 1' })).toBeVisible()
  await expect(page.getByRole('region', { name: 'Before you start' })).toContainText('an adult is present')
  await expect(page.getByText('Exercise 1 of 2')).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Yes and No' })).toBeVisible()
  await page.getByRole('button', { name: 'Next exercise' }).click()
  await expect(page.getByRole('heading', { name: 'Flying Machines' })).toBeVisible()
  await page.getByRole('button', { name: 'Cool down' }).click()
  await expect(page.getByRole('heading', { name: 'Cool Down 1' })).toBeVisible()
  await expect(page.getByText('Stand straight, feet together. Count to 10')).toBeVisible()
  await page.getByRole('button', { name: 'We did it' }).click()

  await expect(page.getByRole('heading', { name: 'How did it go?' })).toBeVisible()
  const exercises = page.getByRole('group', { name: /^How .+ went$/ })
  await expect(exercises).toHaveCount(2)
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

test('a family whose pass has ended is offered Renew, not the check (task 82)', async ({ page }) => {
  await signIn(page, 'lapsed@example.test')
  await page.getByRole('link', { name: 'At home', exact: true }).click()
  await expect(page.locator('.home-pass').getByRole('heading', { name: 'Your pass has ended' })).toBeVisible()
  await page.getByRole('link', { name: /^Noah / }).click()
  await expect(page.getByRole('heading', { name: 'Noah', level: 1 })).toBeVisible()
  // Noah's check is due again, but the API would refuse it without a running pass, so it is not offered.
  await expect(page.getByRole('link', { name: 'Renew the pass' })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Start the check' })).toHaveCount(0)
  await expect(page.getByRole('link', { name: 'Start', exact: true })).toHaveCount(0)
})
