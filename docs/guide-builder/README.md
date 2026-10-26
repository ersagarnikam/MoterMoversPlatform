# Guide builder

Builds the PDF guides in `docs/` from the demo data, so they show the real screens:

| Output | Words | For |
|---|---|---|
| `docs/user-guide/` | `user-guide-content.mjs` | The people who use the platform: everyday tasks step by step, per role |
| `docs/flow-guides/` | `flow-content.mjs` (and the overview pages in `build.mjs`) | Partners, investors and school leaders |

Rebuild them when screens or features change. Edit the words in the two content files; keep to the words on the
screen (button names as they appear), British English, and the platform's wording rules (assess, never screen or
diagnose).

## 1. Take the screenshots

`capture.mjs` signs in as each kind of user and walks through their screens, saving PNGs to `shots/` (not kept in
git). It changes the demo data as it goes (a teacher asks for a place, a learner gets certified), so start
everything fresh:

```bash
# API with demo data, card payments switched on with dummy keys (so Pay by card shows) and a raised sign-in limit
cd backend
STRIPE_SECRET_KEY=sk_test_docs STRIPE_WEBHOOK_SECRET=whsec_docs LOGIN_LIMIT_PER_IP=500 ./mvnw spring-boot:run

cd frontend && npm run dev                        # web app on http://localhost:5173
cd mobile && npx expo start --web --port 8081     # parent app on the web (optional: phone screens are skipped)

node docs/guide-builder/capture.mjs               # from the repository root; needs Chrome
```

It ends with a list of failures (empty when every screen was captured). Without Zoom keys the live room shows
"Zoom is not connected yet", so the guides use the register part of it (`live-register`).

## 2. Build the PDFs

```bash
node docs/guide-builder/build.mjs          # both sets; or: build.mjs user | build.mjs flow
```

The HTML behind each PDF is kept in `html/` (not in git) for checking a layout in a browser.
