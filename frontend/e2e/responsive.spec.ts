import { expect, signIn, test } from './fixtures'

/** On a phone, the main screens fit the width: nothing scrolls sideways. */
test('main screens fit a phone screen', async ({ page }) => {
  const noSideScroll = async () => {
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
    expect(overflow).toBeLessThanOrEqual(1)
  }
  await page.goto('/login')
  await noSideScroll()
  await signIn(page, 'teacher@oakfield.test')
  await noSideScroll()
  await page.getByRole('link', { name: 'Resources' }).click()
  await noSideScroll()
  await page.getByRole('link', { name: 'Training' }).click()
  await noSideScroll()
})
