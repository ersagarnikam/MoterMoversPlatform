import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

// The policy nginx sends in production (nginx.conf). `vite preview` sends it too, so the browser tests
// catch anything it would block.
const CSP =
  "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data: https:; media-src 'self' https:; " +
  "font-src 'self'; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'; object-src 'none'"

export default defineConfig(({ mode }) => {
  // API_URL lets you point the dev server at an API on another port or machine.
  const env = loadEnv(mode, '.', '')
  const proxy = { '/api': env.API_URL || 'http://localhost:8080' }
  return {
    plugins: [react()],
    server: {
      port: 5173,
      proxy,
    },
    preview: {
      port: 4173,
      proxy,
      headers: { 'Content-Security-Policy': CSP },
    },
  }
})
