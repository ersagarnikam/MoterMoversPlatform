import { defineConfig, loadEnv, type Plugin } from 'vite'
import react from '@vitejs/plugin-react'

// The policy nginx sends in production (nginx.conf). `vite preview` sends it too, so the browser tests
// catch anything it would block.
const CSP =
  "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data: https:; media-src 'self' https:; " +
  "font-src 'self'; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'; object-src 'none'"

// A live course session (/live/<id>) runs Zoom's Meeting SDK, which loads from Zoom, evaluates code, uses workers and
// WebAssembly, injects styles and talks to Zoom's servers, so that page alone gets this policy and may use the camera
// and microphone. Keep it the same as the /live/ location in nginx.conf.
const ZOOM = 'https://source.zoom.us https://dmogdx0jrul3u.cloudfront.net'
const LIVE_CSP =
  "default-src 'self'; " +
  `script-src 'self' ${ZOOM} 'unsafe-eval' 'wasm-unsafe-eval' blob:; worker-src 'self' blob: ${ZOOM}; ` +
  "style-src 'self' 'unsafe-inline' https://source.zoom.us; img-src 'self' data: blob: https:; media-src 'self' blob: https:; " +
  `font-src 'self' data: https://source.zoom.us; connect-src 'self' https://*.zoom.us wss://*.zoom.us ${ZOOM} blob: data:; ` +
  "frame-src 'self' https://*.zoom.us; frame-ancestors 'none'; base-uri 'self'; form-action 'self'; object-src 'none'"
const PERMISSIONS = 'camera=(), microphone=(), geolocation=(), payment=()'
const LIVE_PERMISSIONS = 'camera=(self), microphone=(self), display-capture=(self), geolocation=(), payment=()'
const LIVE_PAGE = /^\/live\/\d+\/?(\?|$)/

/** Sends the production security headers from vite preview, with the live session page's own policy. */
function securityHeaders(): Plugin {
  return {
    name: 'security-headers',
    configurePreviewServer(server) {
      server.middlewares.use((req, res, next) => {
        const live = LIVE_PAGE.test((req as { url?: string }).url ?? '')
        res.setHeader('Content-Security-Policy', live ? LIVE_CSP : CSP)
        res.setHeader('Permissions-Policy', live ? LIVE_PERMISSIONS : PERMISSIONS)
        next()
      })
    },
  }
}

export default defineConfig(({ mode }) => {
  // API_URL lets you point the dev server at an API on another port or machine.
  const env = loadEnv(mode, '.', '')
  const proxy = { '/api': env.API_URL || 'http://localhost:8080' }
  return {
    plugins: [react(), securityHeaders()],
    server: {
      port: 5173,
      proxy,
    },
    preview: {
      port: 4173,
      proxy,
      // Lets a Cloudflare quick tunnel (cloudflared tunnel --url http://localhost:4173) reach the preview
      // for demos over the internet.
      allowedHosts: ['.trycloudflare.com'],
    },
  }
})
