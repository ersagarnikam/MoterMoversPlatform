import { expect, expectAccessible, signIn, test } from './fixtures'

test('the sign-in page is accessible and rejects a wrong password', async ({ page }) => {
  await page.goto('/login')
  await expectAccessible(page)
  await page.getByLabel('Email').fill('teacher@oakfield.test')
  await page.getByLabel('Password').fill('not-the-password')
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.getByText('Email or password not recognised')).toBeVisible()
})

test('forgot password gives the same answer for any email', async ({ page }) => {
  await page.goto('/login')
  await page.getByRole('link', { name: 'Forgot your password?' }).click()
  // The sign-in page has an Email field too: wait for the reset form, or the email can land on the old page.
  await expect(page.getByRole('heading', { name: 'Reset your password' })).toBeVisible()
  await page.getByLabel('Email').fill('nobody@nowhere.test')
  await page.getByRole('button', { name: 'Email me a link' }).click()
  await expect(page.getByText('If nobody@nowhere.test has a Motor Movers account')).toBeVisible()
  await expectAccessible(page)
})

test('a broken reset link explains itself', async ({ page }) => {
  await page.goto('/reset-password?token=not-a-real-token')
  await page.getByLabel('New password').fill('a-long-password')
  await page.getByLabel('The same again').fill('a-long-password')
  await page.getByRole('button', { name: 'Set password' }).click()
  await expect(page.getByText('This link has expired or has already been used')).toBeVisible()
})

test('every role lands on its own home screen', async ({ page }) => {
  const homes: [string, string][] = [
    ['head@oakfield.test', 'Oakfield Primary · Progress'],
    ['trust@northbridge.test', 'Northbridge Academy Trust'],
    ['practitioner@example.test', 'Training'],
    ['admin@motormovers.test', 'Content approval'],
  ]
  for (const [email, heading] of homes) {
    await signIn(page, email)
    await expect(page.getByRole('heading', { level: 1 })).toContainText(heading)
    await expectAccessible(page)
    await page.getByRole('button', { name: 'Sign out' }).click()
  }
})
