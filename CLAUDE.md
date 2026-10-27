# Motor Movers Platform: notes for Claude

Read this first. It records where the project stands so a new session can carry on. `README.md` has the
full feature description and demo accounts; `deploy/RUNBOOK.md` has production operations.

**A team works on this project, and this file is how everyone knows its current state.** Update it in every pull
request, not only when a feature lands: the **Current state** (with the date), **Next steps**, test counts, the latest
migration, the recent commits line, and an **In progress** note while work is under way (who, branch, pull request,
what is done and left; remove it once merged). Start from the latest `main`, and keep other people's notes.

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
- **Schema changes** are new Flyway migrations (`backend/src/main/resources/db/migration`, latest `V16`);
  never edit an applied one. JPA runs with `ddl-auto: validate`.
- **Email** goes through `common/config/Mailer`; with no SMTP configured it logs the message (dev).
- **Web CSP is strict** (no inline scripts or styles, no third-party hosts). `vite preview` sends the
  same policy as nginx, so the browser tests catch violations. Fonts are self-hosted (`@fontsource`).
- Commit style: short imperative subject, explanatory body, one logical change per commit.

## Build and test

```bash
cd backend  && ./mvnw -B verify      # 134 unit + API integration tests (H2)
cd frontend && npm test             # 48 unit tests (Vitest): money, times, offline queue, API client, assessment points
cd frontend && npm run build        # typecheck + production build
cd mobile   && npx tsc --noEmit     # parent app typecheck
# Browser tests: start the API with demo data and LOGIN_LIMIT_PER_IP=500 first (the tests sign in more than
# the 50 times in 15 minutes one address is allowed): cd backend && LOGIN_LIMIT_PER_IP=500 ./mvnw spring-boot:run
cd frontend && npm run e2e          # 65 Playwright tests; locally add PW_CHANNEL=chrome
```

The browser tests change demo data: **restart the API between e2e runs**, or tests fail on duplicates.
The mobile lock file is written with npm 11 (Node 24); npm 10 rejects it, so CI's mobile job uses Node 24.
CI (`.github/workflows/ci.yml`) runs backend, frontend (unit tests and build), e2e, loadtest and mobile jobs on every
push. The loadtest job starts the API with `LOAD_TEST_LEARNERS=100` and runs `deploy/loadtest/live-session.js` with
50 learners joining within 10 s (k6 v2.3.0); any failed, slow or unrecorded join fails the build.

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

## Current state (2026-10-27)

**In review (Claude for the owner, branch `home-programme`, pull request open, not merged, 2026-10-27): Motor Movers
at Home**, for families whose child's school is not on Motor Movers. Behind `HOME_PROGRAMME` (on in development and
the demo, off in production until the owner and Pat agree; it needs its own privacy notice first). Design note with
ten questions for Pat: `docs/home-programme-design.md` (also shared with the owner as a Claude doc).
- Data (migration V16): a child belongs to a school class **or** a family (`child.family_id`, `school_class_id` now
  nullable, a check constraint), with `age_months` on `age_recorded_on` instead of a date of birth and
  `home_check_on`. `family`, `family_member` (OWNER or CARER; one family per account), `family_invite` (one-time
  codes, 14 days), `home_programme_session`. Code that reads `child.getSchoolClass()` must allow null: use
  `child.isAtHome()`.
- Access: `AccessPolicy.requireFamilyOf` (the family's parents and carers only); `requireRecordAccess` picks family
  or class access, and the assessment endpoints use it, so a teacher, head, trust lead or the team gets 403 on a home
  child. `requireClassAccess(null)` refuses. Home children are not in a school's record access log.
- API `/api/home` (PARENT; `HomeService`, `HomeController`): the family, children (add, edit, delete with everything
  recorded), the starting check (movements in the year up to the child's age; each Yes kept as a
  `milestone_achievement` with the age), the colour, today's session, sessions (offline-safe), invites, joining,
  removing or leaving (the last member leaving deletes the family), and `join-school` (the school's parent code; the
  home record moves onto the school's record, the school's own rows winning). Ratings use
  `/api/assessment/exercise-scores`. Rules with defaults for Pat in `home/util/HomeRules.java` (colour by age, the
  check, 4 exercises a session, 10 sessions a module, Yellow in threes, recheck after 91 days); parent words and the
  gentle prompt in `home/dto/HomeViews.java`.
- Parent app: Today (a home child's session, or the starting check; colours without Pat's grid fall back to the home
  activities), `add-child`, `check/[id]`, `home-session/[id]` (then first-time ratings), `review/[id]` (end of module),
  `home-child/[id]` (progress, check again, name and age, link a school, delete), `join-school/[id]`, `family`
  (invite, join, remove). Sessions and ratings queue offline (`lib/homeOutbox.ts`).
- Demo: home@example.test (Priya Shah): Ava (Green, module 2, module 1 review due) and Leo (no check yet).
- Tests: `HomeProgrammeTest` (own context with a raised sign-in limit, so it does not use up the shared one),
  `HomeRulesTest`, `HomeProgrammeOffTest`. Tried in the app's web preview: session, ratings, check, fallback, family.

**Pat's two assessments (2026-10-27, branch `assessment-rebuild`, migration V15; the owner asked Claude to merge this
pull request).** Built from Pat's documents of 2026-10-27 (`docs/assessment-design.md`). Package `assessment`:
- Content: `backend/src/main/resources/assessment/` holds the booklet's fine (59) and gross (74) motor charts and the
  Green (27 exercises, modules 1-6) and Yellow (18 rhymes, start and end) grids as JSON, made from Pat's Word files
  (four obvious typos corrected, repeated chart rows dropped). `AssessmentContent` loads them on start-up in every
  deployment, adding only what is missing, so another colour's grid is a new `exercises-<colour>.json` plus its
  colour in `STAGES_WITH_EXERCISES`. A test checks all of it against the wording guard.
- Exercises: the teacher's **Assess** page (`/class/:id/assess`, from "Assess children") scores the whole class on one
  exercise at one point (Start, End of module n, End of the colour), 1 to 4 with Pat's four descriptions, through the
  offline queue (`outbox.ts`, now generic). A later score at the same point replaces the earlier one. The suggested
  point (`suggestedPoint` in `src/assessment.ts`) stays on a point until every child has a score there, unless half
  the class or more already has a score at a later point (so one absent child does not hold the class back).
- Developmental: `/child/:id/development`. The teacher enters the child's age in months (no date of birth is held)
  and the point in the school year (Sept-Dec start, Jan-Apr middle, May-Aug end, suggested from the date), taps each
  movement that is secure, and writes each chart's comment. Once secure a movement stays secure; one recorded by
  mistake can be taken off.
- Profile (`/child/:id`): both assessments first (movements secure, Pat's table of scores per point, start and end
  totals, the summary comments on personal, social and emotional development and on academic achievement), then the
  old 0-3 readiness check, which stays (also reachable from the Assess page) until Pat confirms it can go.
- Flag: `ExtraSupportRule` (the suggested default, still to agree with Pat): 1 or 2 on the same exercise at two
  points in a row, or 1 or 2 on half or more of the exercises (at least two) at a module end or the colour end. It
  shows as "May benefit from extra support" with its reasons, never a cause. Up to three exercises low at two points
  in a row are named one by one; more read as one line ("1 or 2 at two points in a row on 21 exercises, including
  …"); the profile's table has every score.
- Head: an Assessments section on the Progress page. Per class: children assessed at each point this school year,
  the average movements secured this year per child assessed, and how many **children** are at each exercise level
  (each child counted once, at the middle of their latest scores; `ClassOverview.childrenAtLevel`), then the list of
  flagged children (first reason, and how many more are on the profile). Each class shows its colour stage as a pill
  washed in the stage's own colour, with the row edged in it (`StageBadge`, `.stage-tint-*` in styles.css). Trust or
  group leads see per school only the share developmentally assessed this year and how many are flagged.
- Demo: Ms Hardy's Year 2 R (Yellow) has start scores and start-of-year charts; Year 3 K (Green, no teacher) is four
  modules in, with two children flagged. The last three children of each class are left to assess.
- Not built: the other five colours (waiting for Pat), editing the charts or grids on screen (change the JSON), the
  parent app view (not decided), the induction lesson on assessment, retiring the 0-3 check, regenerating the guides.

`main` is GitHub's default branch (since 2026-10-19). New work goes on short branches. Since 2026-10-26 `main` only accepts changes
through a **pull request** (a GitHub rule): push the branch and open a pull request with a detailed description. **Never merge it: the owner
merges** (asked 2026-10-26), unless the owner asks for a particular one to be merged (as with the assessment pull
request, 2026-10-27). The
GitHub CLI is installed and signed in (`C:\Program Files\GitHub CLI\gh.exe`, account ersagarnikam): open pull
requests with `gh pr create` and check CI with `gh pr checks`. GitHub also has `rahul-work`, a teammate's branch: leave it alone. One old stash remains
("zoom video size tweak (unverified)"), superseded by the merged fix; drop it if the owner agrees.
GitHub CI is green on every job (backend, frontend, e2e, loadtest, mobile). Everything below is built and passing: backend tests, web build, mobile typecheck,
65/65 browser tests, and the production compose stack run locally (HTTPS, headers, migrations,
rate limits, backup dump all checked).

**Training team (2026-10-24).** Pat has a team of trainers. `Role.TRAINER` ("Trainer") sees only Live sessions, and
runs and keeps the register of the cohorts assigned to them (`cohort.trainer_id`, migration V10); the team (ADMIN)
can run any session and reassign cohorts. Each trainer or team member can have their own Zoom login
(`app_user.zoom_email`), so sessions can run at the same time; without one, `ZOOM_HOST_EMAIL` hosts. The team adds
trainers (emailed an invite) and sets Zoom logins in the Trainers section of `/admin/courses`
(`AdminTrainerController`, which also switches a trainer's account off), and the cohort form picks a trainer. The
Courses page warns when a cohort's sessions overlap another cohort's on the same Zoom login (`AdminCohortView.clashes`;
a warning, not a refusal). Trainers also have **My cohorts** (`/trainer/cohorts`, `GET /api/trainer/cohorts`): the
bookings, attendance and the end-of-course assessment of their own cohorts (`LiveSessionService.canHost` decides).
Demo trainer: trainer@motormovers.test (Amy Clarke, Cohort B · UK morning). Each Zoom login used as a host needs its
own licensed Zoom user on the owner's account.

**Web app usability (2026-10-26).** Colour stages always show through `StageBadge` (web: a pill washed in the stage's
colour, `.stage-tint-*` classes; parent app: `StageBadge` in `components/ui.tsx`, the same look), never as plain text
(2026-10-27). Dates through `day()` in `src/dates.ts` ("Thu 5 Nov 2026"); after an action, a
toast via `useToast()` and confirmations via `useConfirm()` (`components/Feedback.tsx`; never `window.confirm`);
`components/Skeleton` while loading; `components/ListFilter` for searchable lists. The team's home is `/admin`
(`TeamOverviewService`, `GET /api/admin/overview`); courses list at `/admin/courses` with a page per course
(`/admin/courses/:id`) and trainers at `/admin/trainers`. Tables of people turn into cards under 760px
(`table.cards` with `data-label` cells). Browser tests click menu links with `exact: true` (Home cards repeat their
names).

**Card payments (2026-10-26, migration V14).** With `STRIPE_SECRET_KEY` and `STRIPE_WEBHOOK_SECRET` set (both or neither;
`ProductionChecks` refuses one), payers see **Pay by card** on what is due: parents and practitioners on `/payments`
and in the app, heads on the school's Payments page. It opens a Stripe Checkout page (`payment/stripe`,
`OnlinePaymentService.checkout`); the card never touches the platform. Stripe's signed webhook
(`POST /api/stripe/webhook`, open to Stripe only by its HMAC signature, 5-minute tolerance) marks the payment paid
with `payment.paid_online_ref`; returning to the platform proves nothing on its own. Not yet tried with a real Stripe
account (RUNBOOK, Card payments). Refunds are made in Stripe and not recorded on the platform.

**Phone notifications (2026-10-26, migration V13).** The parent app registers the phone after sign-in (`src/lib/push.ts`,
`expo-notifications`; `POST /api/me/push-tokens`, removed on sign-out) and opens the screen a notification names
(`data.url`). The API sends through Expo's push service (`notification` package: `PushService`, `ExpoPushSender`)
when `PUSH_ENABLED=true`; otherwise it only logs. Sent: a live session open to join (`SessionReminderJob.announceOpening`,
every 2 minutes, once per session, `cohort_session.opening_push_sent_at`), the day-before reminder, and a payment
recorded as due for a person. Tokens Expo reports as gone are deleted. On the web the app skips notifications (the calls throw there). Needs an EAS project id in `app.json` and a
store or development build to work (RUNBOOK, Phone notifications); not yet tried on a phone.

**Quizzes and certificates (2026-10-26, migration V12).** Short quizzes between a course's live sessions
(`course_quiz`, `quiz_question`, `quiz_attempt`; `QuizService`): the team writes them on the Courses page (Quizzes on
each course; the wording check runs on save; a quiz learners have taken cannot be deleted). A quiz opens once the
session it follows has started or the learner is marked present at it; learners take it at
`/training/quiz/:enrolmentId/:quizId`, as often as they like; the server marks it and names the questions to look at
again, never sending the answers. With quizzes on a course, all must be passed for the certificate
(`CertificateService.issueIfComplete` is the one place certificates are issued). On issue the learner is emailed with
the PDF attached (`CertificatePdf`, Apache PDFBox; also `GET /api/training/enrolments/{id}/certificate.pdf` and
Download PDF on the certificate page), and a month before expiry they are reminded once (daily at 08:00,
`enrolment.expiry_reminder_sent_on`). The starter practitioner course has three starter quizzes (80% to pass) for
Pat to check and replace. Renewing a certificate online is not built: the reminder asks them to contact the team.

**Staff course places (2026-10-26, migration V11).** A school pays for its staff's places, so staff other than heads
**ask for a place** (`course_request`); the school's heads are emailed and approve or decline on the school's Payments
page. Approving books the place and adds the school's payment, linked to the place (`enrolment.payment_id`); a head
books their own place directly (and is invoiced). Places can be **cancelled** (`POST /api/training/enrolments/{id}/cancel`):
by the learner or their head until the first session starts, by the team any time before a certificate; a linked
invoice still due is cancelled, a paid one is left for the team to refund by hand. **Reminder emails**
(`SessionReminderJob`, every 10 minutes) go to everyone booked, and the trainer, once per session a day ahead
(`cohort_session.reminder_sent_at`; moving a session sends a new one).

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
  is not built: next step if wanted. Load test `deploy/loadtest/live-session.js` (2026-10-24): 50, 99 and 500
  learners joining within a minute, and 500 within 5 s: no failures, Join under 5 ms (95%), every join on the
  register. Load-test-only settings `LOAD_TEST_LEARNERS`, `LOGIN_LIMIT_PER_IP` (production refuses a raised limit).
  The owner's Zoom plan allows 100 participants: up to 99 learners per session. The SDK and its six global libraries load from Zoom's CDN,
  pinned to 6.5.0 with SRI hashes (`src/live.ts`); only `/live/<id>` has the looser CSP and camera/microphone
  permission (`vite.config.ts` and `frontend/nginx.conf`, kept the same). Reach the room with a full page load
  (`<a href>`), never client-side routing, or the strict CSP stays in force and Zoom is blocked. The parent app
  (2026-10-26) lists live sessions on its Course tab and joins in the **Zoom app** (Join in Zoom opens the meeting's
  join link, which the API now returns to learners in both modes; Today shows a banner while a session is open). Demo: a parent cohort starts ten minutes after start-up
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
- Guides, rebuilt 2026-10-26 for all seven roles (Trainer included): the **user guide** (`docs/user-guide/`, for the
  people who use the platform: getting started, "how do I…?" tasks with numbered steps, answers) and the **flow
  guides** (`docs/flow-guides/`, for partners, investors and school leaders, plus the platform overview with safety,
  income and status). Both are built from screenshots of the demo by `docs/guide-builder/` (`capture.mjs`, then
  `build.mjs`; how-to in its README). **Rebuild them when screens or features change**, and update the status page
  in `build.mjs` (`overviewBody`). The words live in `flow-content.mjs` and `user-guide-content.mjs`.
- Parent: parent course on the web; parent app (link a child with a school code, home activities, guidance).
- Accounts: invite and reset links (hashed one-time tokens), 10-character minimum passwords, accounts
  switched off stop working within a minute (`ActiveUserFilter`), rate limits on sign-in, registration,
  resets and parent codes (`RateLimiter`, in memory).
- Go-live: `prod` profile, `ProductionChecks` refuses to start with dev settings, `AdminBootstrap`
  creates the first admin from `PLATFORM_ADMIN_EMAIL`, Docker images, Caddy + nginx, nightly backups,
  `deploy/.env.example`, `deploy/RUNBOOK.md`.

Recent commits (newest first, 2026-10-27): Motor Movers at Home (in review); clearer head assessment figures,
suggested point and flag reasons;
colour stage pills on every screen (web and parent app); Pat's two assessments (developmental charts and exercise scores);
Pat's answers on the new assessments; team home page, a page per course, searchable lists; dates, toasts,
in-page confirmations and live session labels; user guide and refreshed platform guides with their builder; parent
app no longer crashes on the web; card payments (Stripe); phone notifications; quizzes and certificate PDFs; heads
approve staff course places and trainers run their cohorts; training team.

## Next steps

Not started unless noted. Confirm priorities with the owner.

1. Real go-live inputs from the owner: UK server, domain and DNS, SMTP account with SPF/DKIM for the
   sending domain, then follow `deploy/RUNBOOK.md`.
2. Parent app store builds via EAS with `EXPO_PUBLIC_API_URL` set to the production `https://` address. `eas init`
   gives the project id phone notifications need; then set `PUSH_ENABLED=true` (RUNBOOK, Phone notifications).
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
8. Done 2026-10-21: bookings, attendance and certificates have a screen (per-session register since 2026-10-22).
   Session reminder emails done 2026-10-26. Not built: emailing the learner when their certificate is issued, and
   reminders before it expires.
9. Online card payment (Stripe Checkout) done 2026-10-26, waiting on the owner's Stripe account. Not built: refunds
   and credit notes (a paid payment is final), VAT (prices are the amounts charged,
   with no net/VAT split; ask the owner whether Motor Movers is VAT-registered), and linking a payment to a
   specific licence or course booking.
10. **Assessment rebuild: Pat replied 2026-10-27; built for Green and Yellow the same day (see Pat's two
   assessments above).** Next: send Pat the questions below, load the other colours when they arrive, agree the flag
   rule, then retire the 0-3 check and update the induction lesson. Pat's model replaces today's single 0-3
   check with two assessments: (a) a developmental assessment, with fine and gross motor milestone charts
   from birth to 7 years, done at the start, middle and end of the year, recording the child's age in months
   when each movement is secure; (b) an exercise assessment, scoring each programme exercise 1-4 (Not yet,
   Developing, Nearly there, Confident and accurate) at the start, at the end of each module (our "unit") it
   appears in, and at the end of the colour. Pat sent the assessment booklet (both charts) and the Green (27
   exercises, modules 1-6) and Yellow (18 rhymes, start and end only) grids; the documents are on the owner's
   machine in `F:\IHA\Pat`, not in the repository. Her answers: Start is the start of the colour; age in months
   (no date of birth); the profile shows both assessments; heads and leaders see progress on both; no rule for
   moving on; a pattern of low scores flags that a child needs more support. Still to settle: the other five
   colours' grids, gaps in the grids sent (Motor Spin has no descriptions; Jack and Jill and Twinkle Twinkle
   repeat another rhyme's), what counts as "a pattern of low scores", the booklet's date of birth and birth
   history fields (suggested: not held), and what parents see. Full detail, storage, screens and the order of
   work: `docs/assessment-design.md`. Screens already say "Module" for a unit (the API field is still `unit`).
11. Zoom live sessions: **working inside the portal** (tested 2026-10-22 with the owner's Zoom account). Two apps in
   that account: testApp (Server-to-Server OAuth: creates meetings, host token; scopes `meeting:write:meeting:admin`,
   `user:read:token:admin`) and "General app 239" (Meeting SDK on, under Embed; its Client ID and Secret are
   `ZOOM_SDK_KEY`/`ZOOM_SDK_SECRET`). Keys in `backend/.env` only; host sagarnikam@ihau.au for testing. Pat started a
   session as host and the meeting showed in `/live/:id` beside the rules and register. Without the General app's keys
   it falls back to opening Zoom in a new tab (also tested: parent and teacher join links accepted). Still to do: the
   video showed at Zoom's small default size; since 2026-10-26 the room starts in speaker view
   (`defaultViewType`) and resizes with `updateVideoOptions` after joining (from the SDK 6.5 types), **not yet seen in
   a real meeting**; the old `git stash` attempt is superseded. Also: a call with real people on other devices
   (including the parent app's Join in the Zoom app); recording; for go-live the same keys in `deploy/.env` and the
   General app's production credentials.
12. **Motor Movers at Home (built 2026-10-27, in review).** Before switching `HOME_PROGRAMME` on in production: Pat's
   answers to the ten questions in `docs/home-programme-design.md` (use of her materials at home, colour by age, the
   check's rule, parent wording, a safety note, videos), a privacy notice for families (Motor Movers is responsible
   for home children's data itself), and a price (card payments exist; a subscription is not built). Not built:
   subscriptions, a web version for families (the parent app only), and emailing a family code.
