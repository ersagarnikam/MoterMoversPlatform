import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

// Unit tests for the web app's logic (npm test). Browser tests live in e2e/ and run with Playwright instead.
export default defineConfig({
  plugins: [react()],
  test: {
    include: ['src/**/*.test.{ts,tsx}'],
    environment: 'node',
  },
})
