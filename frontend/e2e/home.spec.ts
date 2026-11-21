import { expect, expectAccessible, signIn, test } from './fixtures'

/** The Programme at Home on the website: families whose child's school is not on the programme. */

test('a family signs up on the website, does the check and the first session', async ({ page }) => {
  await page.goto('/for-families')
  await expect(page.getByRole('heading', { name: 'Your child’s school is not on the programme?' })).toBeVisible()
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
    // Each part by itself: a part just marked keeps its button for a moment while the page refreshes.
    const part = page.getByRole('group').filter({ hasText: title })
    await part.getByText(title).click()
    await part.getByRole('button', { name: 'Mark as done' }).click()
    await expect(part.getByRole('button', { name: 'Mark as done' })).toHaveCount(0)
  }
  await expect(page.getByText('You have finished the induction.')).toBeVisible()
  await page.getByRole('link', { name: 'Back to At home' }).click()

  // Then the family's monthly subscription (go-live task 68). No country has a monthly price yet, so the team sets
  // one on Plans and prices first (once: a second run against the same API finds it set).
  const pass = page.locator('.home-pass')
  await expect(pass).toBeVisible()
  await page.getByRole('button', { name: 'Sign out' }).click()
  await signIn(page, 'admin@motormovers.test')
  await page.getByRole('link', { name: 'Plans and prices', exact: true }).click()
  const monthly = page.locator('section', { has: page.getByRole('heading', { name: /monthly price/ }) })
  await expect(monthly).toContainText(/Not on sale until a price is set|Today: /)
  if (await monthly.getByText('Not on sale until a price is set').isVisible()) {
    await monthly.getByLabel(/Price a month/).fill('14.50')
    await monthly.getByRole('button', { name: 'Set the monthly price' }).click()
  }
  await expect(monthly).toContainText('Today: £14.50 a month.')
  await expect(monthly).toContainText('a family that already subscribes keeps its price')
  await page.getByRole('button', { name: 'Sign out' }).click()

  // The family subscribes. These tests run with the stand-in for Stripe (STRIPE_FAKE=true on the API), whose page
  // "pays" at once and comes back as Stripe does; the platform hears of it as it would from Stripe.
  await signIn(page, email, 'a-long-test-password')
  await page.getByRole('link', { name: 'At home', exact: true }).click()
  await expect(pass.getByRole('heading', { name: 'Monthly subscription' })).toBeVisible()
  await expect(pass).toContainText('Sessions need a subscription for your family: £14.50 a month')
  await expectAccessible(page)
  await pass.getByRole('button', { name: 'Subscribe for £14.50 a month' }).click()
  await expect(pass.getByRole('heading', { name: 'Your subscription', exact: true })).toBeVisible()
  await expect(pass).toContainText('£14.50 a month. The next payment is taken on')
  await expectAccessible(page)

  // Manage opens the payment provider's own page, where the payer cancels: the family keeps the month it paid for.
  await pass.getByRole('button', { name: 'Manage' }).click()
  await expect(page.getByRole('heading', { name: 'Stand-in for Stripe' })).toBeVisible()
  await page.getByRole('link', { name: 'Cancel at the end of the paid month' }).click()
  await expect(page.locator('#state')).toContainText('cancels at the end of the month')
  await page.getByRole('link', { name: 'Back to the platform' }).click()
  await expect(pass.getByRole('heading', { name: 'Your subscription is ending' })).toBeVisible()
  await expect(pass).toContainText('Sessions stay open until')

  // The team sees the month as a payment with its tax, and the subscription on the family's card.
  await page.getByRole('button', { name: 'Sign out' }).click()
  await signIn(page, 'admin@motormovers.test')
  await page.getByRole('link', { name: 'Payments', exact: true }).click()
  const support = page.locator('section', { has: page.getByRole('heading', { name: /Programme at Home subscription/ }) })
  await support.getByLabel('Parent or carer’s email').fill(email)
  await support.getByRole('button', { name: 'Find the family' }).click()
  await expect(support).toContainText('Subscription: cancelled, ending with the paid month. £14.50 a month')
  await expect(support).toContainText('Monthly payment')
  await page.getByRole('button', { name: 'Sign out' }).click()
  await signIn(page, email, 'a-long-test-password')
  await page.getByRole('link', { name: 'At home', exact: true }).click()
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

test('a family whose pass has ended is told to subscribe, and is not offered the check (tasks 82 and 68)', async ({ page }) => {
  await signIn(page, 'lapsed@example.test')
  await page.getByRole('link', { name: 'At home', exact: true }).click()
  // The 12-month pass is no longer sold: the card is about the monthly subscription.
  await expect(page.locator('.home-pass')).toBeVisible()
  await page.getByRole('link', { name: /^Noah / }).click()
  await expect(page.getByRole('heading', { name: 'Noah', level: 1 })).toBeVisible()
  // Noah's check is due again, but the API would refuse it while the family is not covered, so it is not offered.
  await expect(page.getByRole('link', { name: 'Subscribe to carry on' })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Start the check' })).toHaveCount(0)
  await expect(page.getByRole('link', { name: 'Start', exact: true })).toHaveCount(0)
})
