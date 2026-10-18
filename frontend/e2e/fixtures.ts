import AxeBuilder from '@axe-core/playwright'
import { expect, test as base, type Page } from '@playwright/test'

/**
 * Every test fails if the page logs an error or the Content Security Policy blocks something.
 * Failed requests the test expects (a wrong password is a 401) are left to the test's own assertions.
 */
export const test = base.extend<{ consoleErrors: string[] }>({
  consoleErrors: [
    async ({ page }, use) => {
      const errors: string[] = []
      page.on('console', (m) => {
        if (m.type() === 'error' && !m.text().startsWith('Failed to load resource')) errors.push(m.text())
      })
      page.on('pageerror', (e) => errors.push(e.message))
      await use(errors)
      expect(errors, 'browser console errors').toEqual([])
    },
    { auto: true },
  ],
})

export { expect }

export const PASSWORD = 'password'

export async function signIn(page: Page, email: string, password = PASSWORD) {
  await page.goto('/login')
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Password').fill(password)
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.getByRole('button', { name: 'Sign out' })).toBeVisible()
}

/** No serious or critical WCAG 2.2 AA problems on the page as it stands. */
export async function expectAccessible(page: Page) {
  const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa', 'wcag22aa']).analyze()
  const serious = results.violations
    .filter((v) => v.impact === 'serious' || v.impact === 'critical')
    .map((v) => `${v.id}: ${v.help} (${v.nodes.map((n) => n.target.join(' ')).slice(0, 3).join(', ')})`)
  expect(serious, 'accessibility problems').toEqual([])
}

/**
 * Sessions can only be recorded on school days. The API decides by UK time (ClockConfig), so the test must
 * too: CI runs in UTC, which is still Sunday for the first hour of a British Summer Time Monday.
 */
export const isWeekend = () =>
  ['Sat', 'Sun'].includes(
    new Intl.DateTimeFormat('en-GB', { weekday: 'short', timeZone: 'Europe/London' }).format(new Date()),
  )
