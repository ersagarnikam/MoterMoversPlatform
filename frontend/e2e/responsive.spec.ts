import { expect, signIn, test } from './fixtures'

/** On a phone, the main screens fit the width: nothing scrolls sideways. */
test('main screens fit a phone screen', async ({ page }) => {
  const noSideScroll = async () => {
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
    expect(overflow).toBeLessThanOrEqual(1)
  }
  for (const path of ['/', '/about', '/programme', '/for-schools', '/for-families', '/sign-in', '/login']) {
    await page.goto(path)
    await noSideScroll()
  }
  await signIn(page, 'teacher@oakfield.test')
  await noSideScroll()
  await page.getByRole('link', { name: 'Resources' }).click()
  await noSideScroll()
  await page.getByRole('link', { name: 'Training' }).click()
  await noSideScroll()
})

/** On a phone, the website menu scrolls sideways inside itself and every page can still be reached. */
test('the website menu works on a phone', async ({ page }) => {
  await page.goto('/')
  const nav = page.getByRole('navigation', { name: 'Website' })
  for (const [link, heading] of [
    ['For families', 'Keep the movement going at home'],
    ['For schools', 'Bring Motor Movers to your setting'],
    ['About', 'Built on the Movement for Learning research'],
  ]) {
    await nav.getByRole('link', { name: link, exact: true }).click()
    await expect(page.getByRole('heading', { level: 1 })).toHaveText(heading)
  }
  await page.getByRole('banner').getByRole('link', { name: 'Sign in', exact: true }).click()
  await page.getByRole('link', { name: /^Parent or carer/ }).click()
  await expect(page.getByText('Signing in as Parent or carer')).toBeVisible()
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
  expect(overflow).toBeLessThanOrEqual(1)
})
