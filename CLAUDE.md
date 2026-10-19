# Motor Movers Platform: notes for Claude

Read this first. It records where the project stands so a new session can carry on. `README.md` has the
full feature description and demo accounts; `deploy/RUNBOOK.md` has production operations. Update the
**Current state** and **Next steps** sections whenever a piece of work lands.

## What this is

The platform for the Motor Movers programme (motor-skills programme for primary schools): teachers run a
daily 10-minute session and screen children, heads see progress, trusts see summaries, practitioners and
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
- **No diagnostic wording.** The platform screens motor readiness; it never diagnoses. Content approval
  blocks diagnostic terms (`WordingGuardTest`), and readiness profiles always say they are not a diagnosis.
- **Children are identified by initials** unless a school switches full names on. While it is off, the API
  refuses new full names and never sends saved ones (they are kept, hidden, until it is on again). Leavers are deleted
  365 days after leaving (`RetentionJob`).
- **Time comes from the `Clock` bean** (`common/config/ClockConfig`), never `LocalDate.now()` without it.
- **Errors** are problem details with user-safe messages (`common/exception/ApiExceptionHandler`).
- **Schema changes** are new Flyway migrations (`backend/src/main/resources/db/migration`, latest `V5`);
  never edit an applied one. JPA runs with `ddl-auto: validate`.
- **Email** goes through `common/config/Mailer`; with no SMTP configured it logs the message (dev).
- **Web CSP is strict** (no inline scripts or styles, no third-party hosts). `vite preview` sends the
  same policy as nginx, so the browser tests catch violations. Fonts are self-hosted (`@fontsource`).
- Commit style: short imperative subject, explanatory body, one logical change per commit.

## Build and test

```bash
cd backend  && mvn -B verify        # 54 unit + API integration tests (H2)
cd frontend && npm run build        # typecheck + production build
cd mobile   && npx tsc --noEmit     # parent app typecheck
# Browser tests: start the API with demo data first (cd backend && mvn spring-boot:run), then
cd frontend && npm run e2e          # 52 Playwright tests; locally add PW_CHANNEL=chrome
```

The browser tests change demo data: **restart the API between e2e runs**, or tests fail on duplicates.
The mobile lock file is written with npm 11 (Node 24); npm 10 rejects it, so CI's mobile job uses Node 24.
CI (`.github/workflows/ci.yml`) runs backend, frontend, e2e and mobile jobs on every push.

### On the owner's Windows machine

- Default `java` is JDK 17, which cannot run the tests (the code targets 21). Use JDK 26:
  `C:\Users\ersag\.jdks\openjdk-26.0.1` (set `JAVA_HOME` and `PATH` for the command).
- `mvn` is not on PATH and there is no `mvnw`. Use
  `C:\Users\ersag\.m2\wrapper\dists\apache-maven-3.9.16-bin\5grr65jo27hi51sujmtcldfovl\apache-maven-3.9.16\bin\mvn.cmd`
  from PowerShell.
- Docker Desktop is installed but may need starting (`C:\Program Files\Docker\Docker\Docker Desktop.exe`).
- To try the production stack locally: write a throwaway env file with `APP_DOMAIN=localhost` (Caddy then
  issues a local certificate) and any `SPRING_MAIL_HOST`, then
  `docker compose -p mm-localtest -f deploy/docker-compose.prod.yml --env-file <file> up -d --build`;
  tear down with `down -v`.

## Current state (2026-10-19)

`main` is the only branch and GitHub's default (since 2026-10-19). New work goes on short branches merged
into `main` once CI is green; the old `claude/java-springboot-repo-setup-yoyu6y` branch is deleted.
GitHub CI is green on every job (backend, frontend, e2e, mobile). Everything below is built and passing: backend tests, web build, mobile typecheck,
52/52 browser tests, and the production compose stack run locally (HTTPS, headers, migrations,
rate limits, backup dump all checked).

Built so far:
- Public website (`frontend/src/pages/site`, `SiteLayout`, shared facts in `src/site.ts`): home, about and
  research, the programme, for schools (licence, how to join, apply by email), for families, and a
  role chooser at `/sign-in` that opens `/login?as=<role>`. Content is adapted from
  movementforlearningproject.co.uk; the contact email and phone are theirs, so confirm them with the owner.
- Teacher: today's session player with nursery rhymes, 20-day participation counter, screening one item at
  a time, readiness profiles, one-hour staff induction gate, resource library.
- Head: progress dashboard, flagged classes, governor summary CSV, record access log, **School setup**
  (invite staff, switch accounts off, classes, add children, leavers, move children, privacy settings).
- Trust lead: schools, licences, summary results. Practitioner: courses and bookings.
- Team (admin): writing new content (saved as drafts) and content approval with wording checks, courses
  and cohorts (`/admin/courses`; the wording check runs on save), schools and licences, trusts and trust leads.
- Payments (`payment` package, `/admin/payments`): the team records invoices and payments (a school or a
  person pays; due, paid or cancelled) and sees spend by month, payer type, purpose, trust, school and person.
  Heads see their school's payments, trust leads totals per school, parents and practitioners their own
  (Training page). Online payment is not connected.
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

Recent commits (newest first): browser tests + CI; production deployment; web/app setup and password
screens; accounts, password links, rate limits and school setup API; API input hardening.

## Next steps

Not started unless noted. Confirm priorities with the owner.

1. Real go-live inputs from the owner: UK server, domain and DNS, SMTP account with SPF/DKIM for the
   sending domain, then follow `deploy/RUNBOOK.md`.
2. Parent app store builds via EAS with `EXPO_PUBLIC_API_URL` set to the production `https://` address.
3. Session videos are timed placeholders until filmed sessions are uploaded (`programme_session.video_url`);
   content upload and hosting for videos/audio is still to be designed.
4. Rate limits are per API instance (in memory). Move to a shared store (e.g. Redis) before running more
   than one API container.
5. Off-server backup copies (RUNBOOK suggests rclone) and a tested restore.
6. Nice to have: a Maven wrapper (`mvnw`) so builds do not depend on a local Maven install.
7. A real deployment starts with no programme content or courses (demo data is off in `prod`). The team
   enters them through Content approval and Courses, or starter content is loaded with a migration.
8. Cohort bookings, attendance and assessment results have API endpoints (`/api/admin/cohorts/{id}/enrolments`,
   `/api/admin/enrolments/{id}/...`) but no screen yet, so certificates cannot be issued from the web app.
9. Payments are entered by hand. Not built: online payment (e.g. Stripe, which could create the same
   `payment` records), refunds and credit notes (a paid payment is final), VAT, and linking a payment to a
   specific licence or course booking.
