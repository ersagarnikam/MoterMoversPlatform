import { defineConfig, devices } from '@playwright/test'

/**
 * Browser tests against the production build (vite preview, with the production CSP) and a running API
 * with demo data: `cd backend && mvn spring-boot:run`, then `npm run e2e`. API_URL points elsewhere.
 * The tests change demo data, so restart the API between runs.
 * Locally they use the installed Chrome (PW_CHANNEL=chrome); CI installs Playwright's Chromium.
 */
export default defineConfig({
  testDir: './e2e',
  outputDir: './e2e-results',
  fullyParallel: false,
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: 'http://localhost:4173',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      name: 'desktop',
      use: { ...devices['Desktop Chrome'], channel: process.env.PW_CHANNEL || undefined },
    },
    {
      name: 'phone',
      testMatch: /responsive\.spec\.ts/,
      use: { ...devices['Pixel 7'], channel: process.env.PW_CHANNEL || undefined },
    },
  ],
  webServer: {
    command: 'npm run build && npx vite preview --strictPort',
    url: 'http://localhost:4173',
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
  },
})
