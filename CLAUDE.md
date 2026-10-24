# Motor Movers Platform: notes for Claude

Read this first. It records where the project stands so a new session can carry on. `README.md` has the
full feature description and demo accounts; `deploy/RUNBOOK.md` has production operations. Update the
**Current state** and **Next steps** sections whenever a piece of work lands.

## What this is

The platform for the Motor Movers programme (motor-skills programme for primary schools): teachers run a
daily 10-minute session and assess children, heads see progress, trusts and school groups see summaries, practitioners and
parents take courses, and parents use a phone app. UK schools first; one data region per deployment.

| Part | Where | Tech |
|---|---|---|
| API | `backend/` | Java 21, Spring Boot 4, Spring Security (JWT), Spring Data JPA, Flyway. H2 in dev, PostgreSQL in prod |
| Web app | `frontend/` | React 19, TypeScript 7, Vite 8, React Router 7 |
| Parent app | `mobile/` | Expo SDK 58, Expo Router. Read `mobile/AGENTS.md` before touching it (Expo APIs change every SDK) |
| Deployment | `deploy/`, `*/Dockerfile`, `frontend/nginx.conf` | Docker Compose: Caddy -> nginx -> API -> PostgreSQL + nightly backup |

Backend packages (`backend/src/main/java/uk/co/motormovers/platform/`) are by feature, each layered into
`model / repository / service / controller / dto`: `child, classroom, common, content, dashboard, family,
school, screening, security, seed, training, user`.

## Rules the code relies on

- **Access control lives in the API**, in `security/service/AccessPolicy.java`. Teachers see only their own
  class, heads their school, trust leads summaries only (unless the school grants more), parents only
  linked children. Every new endpoint must go through it; the UI hiding things is not enough.
- **No diagnostic wording.** The platform assesses motor readiness; it never diagnoses. Content approval
  blocks diagnostic terms (`WordingGuardTest`), and readiness profiles always say they are not a diagnosis.
  On screen it is always "assess" / "assessment", never "screen" / "screening" (Pat, 2026-10-20: screening
  sounds diagnostic). Code and API paths still say `screening`. The `TRUST_LEAD` role is shown as "Trust or
  group lead", since it covers academy trusts, other school groups and independent groups.
- **Children are identified by initials** unless a school switches full names on. While it is off, the API
  refuses new full names and never sends saved ones (they are kept, hidden, until it is on again). Leavers are deleted
  365 days after leaving (`RetentionJob`).
- **Time comes from the `Clock` bean** (`common/config/ClockConfig`), never `LocalDate.now()` without it.
- **Errors** are problem details with user-safe messages (`common/exception/ApiExceptionHandler`).
- **Schema changes** are new Flyway migrations (`backend/src/main/resources/db/migration`, latest `V9`);
  never edit an applied one. JPA runs with `ddl-auto: validate`.
- **Email** goes through `common/config/Mailer`; with no SMTP configured it logs the message (dev).
- **Web CSP is strict** (no inline scripts or styles, no third-party hosts). `vite preview` sends the
  same policy as nginx, so the browser tests catch violations. Fonts are self-hosted (`@fontsource`).
- Commit style: short imperative subject, explanatory body, one logical change per commit.

## Build and test

```bash
cd backend  && ./mvnw -B verify      # 71 unit + API integration tests (H2)
cd frontend && npm run build        # typecheck + production build
cd mobile   && npx tsc --noEmit     # parent app typecheck
# Browser tests: start the API with demo data first (cd backend && ./mvnw spring-boot:run), then
cd frontend && npm run e2e          # 58 Playwright tests; locally add PW_CHANNEL=chrome
```

The browser tests change demo data: **restart the API between e2e runs**, or tests fail on duplicates.
The mobile lock file is written with npm 11 (Node 24); npm 10 rejects it, so CI's mobile job uses Node 24.
CI (`.github/workflows/ci.yml`) runs backend, frontend, e2e and mobile jobs on every push.

### On the owner's Windows machine

- Default `java` is JDK 17, which cannot run the tests (the code targets 21). Use JDK 26:
  `C:\Users\ersag\.jdks\openjdk-26.0.1` (set `JAVA_HOME` and `PATH` for the command).
- `mvn` is not on PATH: use `backend\mvnw.cmd` (wrapper, Maven 3.9.16) from PowerShell, with `JAVA_HOME` set as
  above. Ports 8080 and 5173 may be taken by another project of the owner's: then run the API with
  `--server.port=8090` and the browser tests with `API_URL=http://localhost:8090`.
- Local secrets (Zoom keys, `JAVA_HOME`) live in `backend/.env`, which git ignores; never read or print it.
  `powershell -File backend\run-local.ps1` loads it and starts the API with demo data (it says which Zoom keys are
  set, not their values). Tests do not read it.
- Docker Desktop is installed but may need starting (`C:\Program Files\Docker\Docker\Docker Desktop.exe`).
- To try the production stack locally: write a throwaway env file with `APP_DOMAIN=localhost` (Caddy then
  issues a local certificate) and any `SPRING_MAIL_HOST`, then
  `docker compose -p mm-localtest -f deploy/docker-compose.prod.yml --env-file <file> up -d --build`;
  tear down with `down -v`.

## Current state (2026-10-22)

`main` is the only branch and GitHub's default (since 2026-10-19). New work goes on short branches merged
into `main` once CI is green; the old `claude/java-springboot-repo-setup-yoyu6y` branch is deleted.
GitHub CI is green on every job (backend, frontend, e2e, mobile). Everything below is built and passing: backend tests, web build, mobile typecheck,
58/58 browser tests, and the production compose stack run locally (HTTPS, headers, migrations,
rate limits, backup dump all checked).

DigitalOcean deployment was discussed (2026-10-21) but not started: the owner stopped before choosing how to
connect, the server size, the web address and email. Nothing was created.

Built so far:
- Public website (`frontend/src/pages/site`, `SiteLayout`, shared facts in `src/site.ts`): home, about and
  research, the programme, for schools (licence, how to join, apply by email), for families, and a
  role chooser at `/sign-in` that opens `/login?as=<role>`. Content is adapted from
  movementforlearningproject.co.uk; the contact email and phone are theirs, so confirm them with the owner.
- Teacher: today's session player with nursery rhymes, 20-day participation counter, assessment one item at
  a time, readiness profiles, one-hour staff induction gate, resource library.
- Head: progress dashboard, flagged classes, governor summary CSV, record access log, **School setup**
  (invite staff, switch accounts off, classes, add children, leavers, move children, privacy settings).
- Trust or group lead: schools, licences, summary results. Practitioner: courses, bookings and a printable
  certificate (`/training/certificate/:id`).
- Live course sessions on Zoom inside the platform (merged 2026-10-22; `training/zoom`, `LiveSessionService`,
  migration V7). Each cohort has a start time and weekly dated sessions (`cohort_session`); the register is per
  session (`session_attendance`) and feeds `enrolment.sessions_attended` and the certificate. The team (demo: Pat,
  pat@motormovers.test) starts a session at `/live` and the room `/live/:id` shows the Zoom meeting (Meeting SDK
  component view), the rules and the register; learners booked on the cohort join from their Training page from
  15 minutes before the start, without a Zoom account. The API creates each Zoom meeting on first use
  (Server-to-Server OAuth). Two modes (`ZoomProperties`): with only the Server-to-Server app (the owner's choice,
  2026-10-22) the meeting **opens in Zoom** in a new tab: the host gets `https://zoom.us/s/<id>?zak=<ZAK>` (signed in
  as host), learners the meeting's `join_url` (stored, migration V8). With a separate Meeting SDK app's keys (a General
  app; a Server-to-Server app's keys cannot sign the SDK) it shows inside the page instead. Keys are `ZOOM_*` (RUNBOOK,
  Live sessions on Zoom); without them sessions run without video. Tested for real 2026-10-22 with the owner's
  Zoom account in both modes: in the portal (host start) and opening in Zoom (parent and teacher join links).
  Who has joined (2026-10-24, migration V9 `session_join`): when a booked learner opens a session it is recorded;
  the host's register shows "Joined: X of Y", each learner's join time, refreshes every 15 seconds, and "Tick everyone
  who joined" (`POST /api/admin/live/{id}/register/mark-joined`). It records opening the session, not time in the
  call, so ticking stays the host's decision. Zoom's own attendance (report API after the meeting, or webhooks)
  is not built: next step if wanted. The SDK and its six global libraries load from Zoom's CDN,
  pinned to 6.5.0 with SRI hashes (`src/live.ts`); only `/live/<id>` has the looser CSP and camera/microphone
  permission (`vite.config.ts` and `frontend/nginx.conf`, kept the same). Reach the room with a full page load
  (`<a href>`), never client-side routing, or the strict CSP stays in force and Zoom is blocked. The parent app does
  not join live sessions. Demo: a parent cohort starts ten minutes after start-up
  with Jo booked.
- Team (admin): writing new content (saved as drafts) and content approval with wording checks, courses
  and cohorts (`/admin/courses`; the wording check runs on save; each cohort has a bookings panel to mark
  live sessions attended, with undo, and the assessment, and the certificate is then issued and locked), schools and licences, trusts or school groups and their leads.
- Payments (`payment` package, `/admin/payments`): the team records invoices and payments (a school or a
  person pays; due, paid or cancelled) and sees spend by month, payer type, purpose, trust, school and person.
  Heads see their school's payments, trust leads totals per school, parents and practitioners their own
  on a Payments page (`/payments`: due now with references and how to pay, then the history; a reminder on
  the Training page while anything is due), and parents the same in the parent app (`app/payments.tsx`, linked
  from Account and a reminder on Today). Staff see their own on the Training page. Online payment is not connected.
- Plans and prices (`/admin/plans`, `PlanService`, migration V6): licence plans live in the database with dated
  prices (`licence_plan_price`); a change applies from today or later and never rewrites a price in force, and
  each change records who made it. Plans can be added or retired. Each licence has an agreed price (default: the
  plan price on its start date; editable on Schools & licences), and the payment form fills in licence and
  course prices.
- Load test (`deploy/loadtest/`, k6): a school-day mix against the production stack with demo data. On one
  laptop (2026-10-19): 500 people with pauses at 6 ms median and no errors; ceiling about 800-900 requests a
  second with the machine's CPU saturated, never failing. Repeat from another machine against staging.
- Flow guides (`docs/flow-guides/`): PDF guides per role plus an investor and partner overview, with
  screenshots of the demo data (October 2026). Regenerate them when screens change.
- Parent: parent course on the web; parent app (link a child with a school code, home activities, guidance).
- Accounts: invite and reset links (hashed one-time tokens), 10-character minimum passwords, accounts
  switched off stop working within a minute (`ActiveUserFilter`), rate limits on sign-in, registration,
  resets and parent codes (`RateLimiter`, in memory).
- Go-live: `prod` profile, `ProductionChecks` refuses to start with dev settings, `AdminBootstrap`
  creates the first admin from `PLATFORM_ADMIN_EMAIL`, Docker images, Caddy + nginx, nightly backups,
  `deploy/.env.example`, `deploy/RUNBOOK.md`.

Recent commits (newest first): parent payments page and app screen; Maven wrapper; off-server backups and
restore test; assessment design note and Module wording; starter content; course attendance and certificates;
assess wording and trust or school group.

## Next steps

Not started unless noted. Confirm priorities with the owner.

1. Real go-live inputs from the owner: UK server, domain and DNS, SMTP account with SPF/DKIM for the
   sending domain, then follow `deploy/RUNBOOK.md`.
2. Parent app store builds via EAS with `EXPO_PUBLIC_API_URL` set to the production `https://` address.
3. Session videos are timed placeholders until filmed sessions are uploaded (`programme_session.video_url`);
   content upload and hosting for videos/audio is still to be designed.
4. Rate limits are per API instance (in memory). Move to a shared store (e.g. Redis) before running more
   than one API container.
5. Done 2026-10-21: off-server copies (`offsite` compose profile, rclone to an S3-compatible bucket) and
   `deploy/restore-test.sh`, tested against a demo dump. Still to do on the real server: create the bucket,
   fill in `BACKUP_OFFSITE_*`, and run the restore test after the first night.
6. Done 2026-10-21: Maven wrapper (`backend/mvnw`); CI uses it.
7. Done 2026-10-21: `SEED_STARTER_CONTENT=true` gives an empty deployment the starter content
   (`seed/StarterContent`, shared with the demo) as drafts for the team to approve. Still to do: the daily
   session plans (the demo ones are generated filler and are not in the starter set), and replacing the
   placeholder assessment items once Pat sends the real ones (step 10). Approving drafts is one at a time.
8. Done 2026-10-21: bookings, attendance and certificates have a screen (per-session register since 2026-10-22). Not built: emailing the learner when
   their certificate is issued, and reminders before it expires.
9. Payments are entered by hand. Not built: online payment (e.g. Stripe, which could create the same
   `payment` records), refunds and credit notes (a paid payment is final), VAT (prices are the amounts charged,
   with no net/VAT split; ask the owner whether Motor Movers is VAT-registered), and linking a payment to a
   specific licence or course booking.
10. **Assessment rebuild (waiting on Pat, asked 2026-10-21).** Pat's model replaces today's single 0-3
   check with two assessments: (a) a developmental assessment, with fine and gross motor milestone charts
   from birth to 7 years, done at the start, middle and end of the year, recording the child's age in months
   when each movement is secure; (b) an exercise assessment, scoring each programme exercise 1-4 (Not yet,
   Developing, Nearly there, Confident and accurate) at the start, at the end of each module (our "unit") it
   appears in, and at the end of the colour. Waiting for: the full charts, the exercises per module with
   their descriptors, what "Start" means, whether schools accept month and year of birth (the platform
   holds no dates of birth today), and whether the readiness profile is rebuilt from these or replaced.
   Storage, screens and suggested defaults: `docs/assessment-design.md`. Screens already say "Module" for a
   unit (the API field is still `unit`).
11. Zoom live sessions: **working inside the portal** (tested 2026-10-22 with the owner's Zoom account). Two apps in
   that account: testApp (Server-to-Server OAuth: creates meetings, host token; scopes `meeting:write:meeting:admin`,
   `user:read:token:admin`) and "General app 239" (Meeting SDK on, under Embed; its Client ID and Secret are
   `ZOOM_SDK_KEY`/`ZOOM_SDK_SECRET`). Keys in `backend/.env` only; host sagarnikam@ihau.au for testing. Pat started a
   session as host and the meeting showed in `/live/:id` beside the rules and register. Without the General app's keys
   it falls back to opening Zoom in a new tab (also tested: parent and teacher join links accepted). Still to do: the
   video shows at Zoom's small default size (a `customize.video.viewSizes` attempt did not take effect; it is in
   `git stash`, unverified); a call with real people on other devices; recording; the parent app's Join; for
   go-live the same keys in `deploy/.env` and the General app's production credentials.
