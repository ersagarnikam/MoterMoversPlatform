import type { Page } from '@playwright/test'
import { expect, expectAccessible, PASSWORD, signIn, test } from './fixtures'

/** The public website: open to everyone, the way in to the platform for every role. */

const CONTACT_EMAIL = 'movementforlearningproject@gmail.com'

const PAGES = [
  {
    path: '/',
    nav: 'Home',
    h1: 'Movement that gets children ready to learn',
    headings: [
      'Many children start school without the movement skills learning relies on',
      'Screen, move every day, see progress',
      'Stages follow development, not age',
      'More than physical skills',
      'Who Motor Movers is for',
      'Ready to start?',
    ],
  },
  {
    path: '/about',
    nav: 'About',
    h1: 'Built on the Movement for Learning research',
    headings: ['The story', 'The research', 'Four schools, 120 children', 'Fifty schools across England', 'The people behind it'],
  },
  {
    path: '/programme',
    nav: 'The programme',
    h1: 'A few minutes of the right movement, every day',
    headings: ['Seven colour stages', 'The daily session', 'Screening and readiness', 'What the platform gives you'],
  },
  {
    path: '/for-schools',
    nav: 'For schools',
    h1: 'Bring Motor Movers to your setting',
    headings: ['The licence', 'What is included', 'How to join', 'Multi-academy trusts', 'Apply for Motor Movers'],
  },
  {
    path: '/for-families',
    nav: 'For families',
    h1: 'Keep the movement going at home',
    headings: ['The parent app', 'The parent course', 'The CD and book', 'What you will see'],
  },
]

/** Each role on the chooser, the account to sign in with and where that role lands. */
const ROLES = [
  { label: 'Class teacher', key: 'teacher', email: 'teacher@oakfield.test', home: /\/class(es|\/\d+\/today)$/ },
  { label: 'Head teacher or SENCo', key: 'head', email: 'head@oakfield.test', home: /\/dashboard$/ },
  { label: 'Trust lead', key: 'trust', email: 'trust@northbridge.test', home: /\/trust$/ },
  { label: 'Practitioner', key: 'practitioner', email: 'practitioner@example.test', home: /\/training$/ },
  { label: 'Parent or carer', key: 'parent', email: 'parent@example.test', home: /\/training$/ },
  { label: 'Motor Movers team', key: 'team', email: 'admin@motormovers.test', home: /\/admin\/content$/ },
]

/** The same terms the API's WordingGuard blocks in programme content. */
const BLOCKED_TERMS = [
  'adhd', 'autism', 'autistic', 'asd', 'dyspraxia', 'dyspraxic', 'dcd', 'developmental coordination disorder',
  'dyslexia', 'dyslexic', 'disorder', 'diagnosis', 'diagnose', 'diagnosed', 'diagnostic', 'condition', 'syndrome',
  'symptom', 'deficit', 'impairment', 'disability', 'delay', 'delayed', 'abnormal', 'at risk', 'centile',
  'percentile', 'treatment', 'therapy', 'cure', 'clinical',
]

/** Saying what the platform is *not* is allowed: "Not a diagnosis", "not a clinical assessment", "never diagnoses". */
const ALLOWED_NEGATIONS = /\b(not|never) (a |an )?(diagnos\w*|clinical \w+)( or a clinical \w+)?/g

const header = (page: Page) => page.getByRole('banner')
const siteNav = (page: Page) => page.getByRole('navigation', { name: 'Website' })
const footerNav = (page: Page) => page.getByRole('navigation', { name: 'Footer' })
const h1 = (page: Page) => page.getByRole('heading', { level: 1 })

for (const p of PAGES) {
  test(`${p.path} opens directly, shows its content and is accessible`, async ({ page }) => {
    await page.goto(p.path)
    await expect(h1(page)).toHaveText(p.h1)
    for (const heading of p.headings) {
      await expect(page.getByRole('heading', { name: heading, exact: true })).toBeVisible()
    }
    // Exactly one menu item is marked as the current page, and it is this one.
    await expect(siteNav(page).locator('[aria-current="page"]')).toHaveCount(1)
    await expect(siteNav(page).getByRole('link', { name: p.nav, exact: true })).toHaveAttribute('aria-current', 'page')
    await expect(header(page).getByRole('link', { name: 'Sign in', exact: true })).toBeVisible()
    await expectAccessible(page)
  })
}

test('the header menu reaches every page', async ({ page }) => {
  await page.goto('/')
  for (const p of [...PAGES.slice(1), PAGES[0]]) {
    await siteNav(page).getByRole('link', { name: p.nav, exact: true }).click()
    await expect(page).toHaveURL(p.path)
    await expect(h1(page)).toHaveText(p.h1)
  }
})

test('the footer links reach every page, and each new page starts at the top', async ({ page }) => {
  for (const p of PAGES.slice(1)) {
    await page.goto('/')
    await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight))
    await expect.poll(() => page.evaluate(() => window.scrollY)).toBeGreaterThan(0)
    await footerNav(page).getByRole('link', { name: p.nav, exact: true }).click()
    await expect(h1(page)).toHaveText(p.h1)
    await expect.poll(() => page.evaluate(() => window.scrollY)).toBe(0)
  }
  await page.goto('/')
  await footerNav(page).getByRole('link', { name: 'Sign in' }).click()
  await expect(h1(page)).toHaveText('Who are you signing in as?')
})

test('the brand in the header goes back home', async ({ page }) => {
  await page.goto('/for-families')
  await header(page).getByRole('link', { name: 'Motor Movers', exact: true }).click()
  await expect(page).toHaveURL('/')
  await expect(h1(page)).toHaveText(PAGES[0].h1)
})

test('the home page buttons and cards lead to the right pages', async ({ page }) => {
  const links: [string, string, string][] = [
    ['Bring Motor Movers to your setting', '/for-schools', 'Bring Motor Movers to your setting'],
    ['Read about the research', '/about', 'Built on the Movement for Learning research'],
    ['See how the programme works', '/programme', 'A few minutes of the right movement, every day'],
    ['How to join', '/for-schools', 'Bring Motor Movers to your setting'],
    ['Already using Motor Movers? Sign in', '/sign-in', 'Who are you signing in as?'],
    ['Class teachers', '/programme', 'A few minutes of the right movement, every day'],
    ['Heads and SENCos', '/for-schools', 'Bring Motor Movers to your setting'],
    ['Parents and carers', '/for-families', 'Keep the movement going at home'],
  ]
  for (const [name, path, heading] of links) {
    await page.goto('/')
    await page.getByRole('main').getByRole('link', { name }).click()
    await expect(page).toHaveURL(path)
    await expect(h1(page)).toHaveText(heading)
  }
})

test('the seven colour stages appear in programme order', async ({ page }) => {
  const order = ['Red', 'Orange', 'Yellow', 'Green', 'Blue', 'Indigo', 'Violet']
  await page.goto('/')
  await expect(page.getByRole('list', { name: 'The seven colour stages' }).getByRole('listitem')).toHaveText(order)
  await page.goto('/programme')
  await expect(page.locator('.stage-ladder strong')).toHaveText(order)
  await expect(page.locator('.stage-ladder .muted')).toHaveText(order.map((_, i) => `Stage ${i + 1}`))
})

test('the programme and families pages say Motor Movers is not a diagnosis', async ({ page }) => {
  for (const path of ['/programme', '/for-families']) {
    await page.goto(path)
    await expect(page.getByRole('note')).toContainText('Not a diagnosis.')
  }
  await page.goto('/')
  await expect(page.getByText('It is not a diagnosis or a clinical assessment.')).toBeVisible()
})

test('no website page uses diagnostic wording', async ({ page }) => {
  const terms = BLOCKED_TERMS.map((t) => new RegExp(`\\b${t}\\b`))
  for (const path of [...PAGES.map((p) => p.path), '/sign-in']) {
    await page.goto(path)
    await expect(h1(page)).toBeVisible()
    const text = (await page.locator('body').innerText()).toLowerCase().replace(ALLOWED_NEGATIONS, '')
    const found = terms.filter((t) => t.test(text)).map((t) => t.source)
    expect(found, `blocked wording on ${path}`).toEqual([])
  }
})

test('enquiry links open a filled-in email to the Motor Movers team', async ({ page }) => {
  const mail = async (name: string) => {
    const href = await page.getByRole('link', { name, exact: true }).first().getAttribute('href')
    expect(href).toMatch(new RegExp(`^mailto:${CONTACT_EMAIL}`))
    return new URL(href!).searchParams
  }

  await page.goto('/for-schools')
  for (const [name, subject] of [
    ['Email us to apply', 'Apply for Motor Movers'],
    ['Ask for the price guide', 'Motor Movers price guide'],
  ]) {
    const params = await mail(name)
    expect(params.get('subject')).toBe(subject)
    for (const field of ['Name:', 'School or setting:', 'Phone:', 'Number of classes:', 'Message:']) {
      expect(params.get('body')).toContain(field)
    }
  }
  await expect(page.getByText('01202 826 718').first()).toBeVisible()

  await page.goto('/for-families')
  expect((await mail('Ask us about the CD and book')).get('subject')).toBe('Motor Movers CD and book')
  await expect(page.getByRole('contentinfo').getByRole('link', { name: CONTACT_EMAIL })).toHaveAttribute(
    'href',
    `mailto:${CONTACT_EMAIL}`,
  )
})

test('the role chooser lists every role and is accessible', async ({ page }) => {
  await page.goto('/')
  await header(page).getByRole('link', { name: 'Sign in', exact: true }).click()
  await expect(page).toHaveURL('/sign-in')
  await expect(h1(page)).toHaveText('Who are you signing in as?')
  for (const r of ROLES) {
    await expect(page.getByRole('link', { name: new RegExp(`^${r.label}`) })).toHaveAttribute('href', `/login?as=${r.key}`)
  }
  await expect(page.getByText('No account yet?')).toBeVisible()
  await expectAccessible(page)
})

for (const r of ROLES) {
  test(`a ${r.label.toLowerCase()} signs in from the website and lands on their own home`, async ({ page }) => {
    await page.goto('/sign-in')
    await page.getByRole('link', { name: new RegExp(`^${r.label}`) }).click()
    await expect(page).toHaveURL(`/login?as=${r.key}`)
    await expect(page.getByText(`Signing in as ${r.label}`)).toBeVisible()
    await expect(page.getByRole('link', { name: 'Change role' })).toBeVisible()
    await expectAccessible(page)
    await page.getByLabel('Email').fill(r.email)
    await page.getByLabel('Password').fill(PASSWORD)
    await page.getByRole('button', { name: 'Sign in' }).click()
    await expect(page.getByRole('button', { name: 'Sign out' })).toBeVisible()
    await expect(page).toHaveURL(r.home)
  })
}

test('a wrong password from a role’s sign-in page keeps the role and explains', async ({ page }) => {
  await page.goto('/login?as=head')
  await page.getByLabel('Email').fill('head@oakfield.test')
  await page.getByLabel('Password').fill('not-the-password')
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.getByText('Email or password not recognised')).toBeVisible()
  await expect(page.getByText('Signing in as Head teacher or SENCo')).toBeVisible()
})

test('changing role goes back to the chooser', async ({ page }) => {
  await page.goto('/login?as=teacher')
  await page.getByRole('link', { name: 'Change role' }).click()
  await expect(h1(page)).toHaveText('Who are you signing in as?')
  await page.getByRole('link', { name: /^Practitioner/ }).click()
  await expect(page.getByText('Signing in as Practitioner')).toBeVisible()
})

test('a sign-in link with no role, or an unknown one, asks for a role', async ({ page }) => {
  for (const path of ['/login', '/login?as=wizard']) {
    await page.goto(path)
    await expect(page.getByText('Signing in as')).toHaveCount(0)
    await expect(page.getByRole('link', { name: 'Choose your role' })).toBeVisible()
  }
  await page.getByRole('link', { name: 'Choose your role' }).click()
  await expect(h1(page)).toHaveText('Who are you signing in as?')
})

test('the sign-in page links back to the website', async ({ page }) => {
  await page.goto('/login?as=parent')
  await page.getByRole('link', { name: 'Back to the website' }).click()
  await expect(page).toHaveURL('/')
  await expect(h1(page)).toHaveText(PAGES[0].h1)
})

test('the families page links parents straight to their sign-in', async ({ page }) => {
  await page.goto('/for-families')
  await page.getByRole('link', { name: 'Sign in as a parent or carer' }).click()
  await expect(page.getByText('Signing in as Parent or carer')).toBeVisible()
})

test('signed-out visitors to platform pages are sent to sign in', async ({ page }) => {
  for (const path of ['/classes', '/dashboard', '/trust', '/admin/content', '/no-such-page']) {
    await page.goto(path)
    await expect(page).toHaveURL('/login')
    await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible()
  }
})

test('signed in, the website stays open and leads back into the platform', async ({ page }) => {
  await signIn(page, 'head@oakfield.test')
  await page.goto('/')
  await expect(h1(page)).toHaveText(PAGES[0].h1)
  await expect(header(page).getByRole('link', { name: 'Sign in', exact: true })).toHaveCount(0)
  await header(page).getByRole('link', { name: 'Open Motor Movers' }).click()
  await expect(page).toHaveURL('/dashboard')

  // The role chooser and the sign-in form are not needed once signed in.
  for (const path of ['/sign-in', '/login']) {
    await page.goto(path)
    await expect(page).toHaveURL('/dashboard')
  }

  await page.getByRole('button', { name: 'Sign out' }).click()
  await page.goto('/')
  await expect(header(page).getByRole('link', { name: 'Sign in', exact: true })).toBeVisible()
})

test('the theme switch works on the website, is remembered, and dark pages stay accessible', async ({ page }) => {
  await page.goto('/')
  await header(page).getByRole('button', { name: 'Dark' }).click()
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark')
  await page.reload()
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark')
  await expect(header(page).getByRole('button', { name: 'Dark' })).toHaveAttribute('aria-pressed', 'true')
  for (const p of PAGES) {
    await page.goto(p.path)
    await expect(h1(page)).toHaveText(p.h1)
    await expectAccessible(page)
  }
  await header(page).getByRole('button', { name: 'Light' }).click()
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'light')
})
