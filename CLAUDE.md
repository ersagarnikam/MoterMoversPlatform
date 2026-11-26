# Motor Movers Platform: notes for Claude

(added by Dev, 2026-11-16) The product is now the **Professor Pat Preedy Programme** (go-live task 84). "Motor Movers" remains only
as a code name (this repository, the package, the demo domains) and in the dated notes, which are left as written.

Read this first. It records where the project stands so a new session can carry on. `README.md` has the
full feature description and demo accounts; `deploy/RUNBOOK.md` has production operations; `docs/history.md` has the dated notes of every
task and Tester report (moved there on 2026-11-27 to keep this file short).

**A team works on this project, and this file is how everyone knows its current state.** Update it in every pull
request, not only when a feature lands: the **Current state** (with the date), **Next steps**, test counts, the latest
migration, the recent commits line, and an **In progress** note while work is under way (who, branch, pull request,
what is done and left; remove it once merged). Start from the latest `main`, and keep other people's notes.
(added by Dev, 2026-11-05) Several agents write here; the Developer Agent marks what it adds with "(added by Dev, date)".

[PM: Claude, 2026-11-10] **One task, one branch, one pull request (the owner's decision, 2026-11-10).** The Developer
opens the pull request and stops pushing when CI is green; the Project manager asks the Tester to test that branch,
before it merges. Only one agent works on the branch at a time. A bug goes back to the Developer and is fixed on the
same branch, then rechecked. The Tester commits its results (`docs/history.md` and `CLAUDE.md` only) to that branch, with no separate pull
request. It merges only with the Tester's pass and every CI check green on the latest commit; if `main` has moved on,
the branch is updated and CI runs again first. Pull requests that only change notes need no Tester. A pass across the
whole platform that belongs to no one task still runs on `main`, with its own results pull request. `gh pr merge --auto`
does not wait for CI here (`main` has no required checks), so do not use it.

## What this is

The platform for the Professor Pat Preedy Programme (motor-skills programme for primary schools): teachers run a
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
school, security, seed, training, user`, plus `assessment` (Pat's two assessments), `home` (Motor Movers at Home) and others.

## Rules the code relies on

- **Access control lives in the API**, in `security/service/AccessPolicy.java`. Teachers see only their own
  class, heads their school, trust leads summaries only (unless the school grants more), parents only
  linked children. Every new endpoint must go through it; the UI hiding things is not enough.
- **No diagnostic wording.** The platform assesses motor readiness; it never diagnoses. Content approval
  blocks diagnostic terms (`WordingGuardTest`), and assessment profiles always say they are not a diagnosis.
  On screen it is always "assess" / "assessment", never "screen" / "screening" (Pat, 2026-10-20: screening
  sounds diagnostic). The old `screening` package (the 0 to 3 check) is gone since 2026-11-04. The `TRUST_LEAD` role is shown as "Trust or
  group lead", since it covers academy trusts, other school groups and independent groups.
- (added by Dev, 2026-11-16) **The name is the Professor Pat Preedy Programme, never "Motor Movers"** (go-live task 84). On screens, in
  emails, PDFs and notifications: the name in full in headers, titles, emails and the store listing; "the programme" inside a
  sentence; "the Programme at Home" for the home programme; "Programme team" for the ADMIN role. Each app writes the names once:
  `common/Brand.java` (API), `frontend/src/brand.ts` (web) and `mobile/src/lib/brand.ts` (parent app, with `name` in `app.json`).
  Use those, never the words themselves. `BrandTest` (API and parent app) and `brand.test.ts` (web) fail on a "Motor Movers" or a
  written-out name anywhere else. **Code names keep the old name** and are not renamed: the package `uk.co.motormovers`, the
  repository, environment variables, the demo domains (`motormovers.test`), the app's slug, scheme and store ids
  and applied migrations. Certificate numbers and payment references start "PPP-" (`Brand.REFERENCE_PREFIX`; "MM-" until 2026-11-16).
- **Children are identified by initials** unless a school switches full names on. While it is off, the API
  refuses new full names and never sends saved ones (they are kept, hidden, until it is on again). Leavers are deleted
  365 days after leaving (`RetentionJob`).
- **Time comes from the `Clock` bean** (`common/config/ClockConfig`), never `LocalDate.now()` without it.
  (added by Dev, 2026-11-12) A test that changes the clock or the region (a fixed `Clock`, `PLATFORM_REGION`) must use its own H2
  database (`spring.datasource.url=jdbc:h2:mem:<name>;...`): the demo loads once per database, on the clock of the first context to
  start, and the other tests expect today's demo (task 83).
- **Errors** are problem details with user-safe messages (`common/exception/ApiExceptionHandler`).
- **Schema changes** are new Flyway migrations (`backend/src/main/resources/db/migration`, latest `V27`, added by Dev 2026-11-26);
  never edit an applied one. JPA runs with `ddl-auto: validate`.
- **Email** goes through `common/config/Mailer`; with no SMTP configured it logs the message (dev).
- (added by Dev, 2026-11-06) **No personal data or tokens in logs.** Never log an email address, a name, a child's initials, a
  token, a link that carries one, a subject line or an exception's message: messages quote what they are about. Log
  counts, ids and `SafeErrors.describe(e)` (type and place only). Mailer's whole-email logging is development only:
  `ProductionChecks` refuses to start without a mail server. `LogSafetyTest` checks this.
- **Web CSP is strict** (no inline scripts or styles, no third-party hosts). `vite preview` sends the
  same policy as nginx, so the browser tests catch violations. (added by Dev, 2026-11-06) It sets headers another way from
  nginx, so a page with its own headers needs `try_files /index.html =404`, never `$uri /index.html`, whose internal redirect
  swaps in `location /`'s headers; the `web-headers` CI job checks nginx itself. Fonts are self-hosted (`@fontsource`).
- Commit style: short imperative subject, explanatory body, one logical change per commit.

## Build and test

```bash
cd backend  && ./mvnw -B verify      # (added by Dev, 2026-11-26) 327 unit + API integration tests (H2)
cd frontend && npm test             # (added by Dev, 2026-11-26) 101 unit tests (Vitest): money, times, offline queue, API client, assessment points, home ages, the brand, the player's clock, the home subscription card, labels
cd frontend && npm run build        # typecheck + production build
cd mobile   && npx tsc --noEmit     # parent app typecheck
# Browser tests: start the API with demo data and LOGIN_LIMIT_PER_IP=500 first (the tests sign in more than
# the 50 times in 15 minutes one address is allowed): cd backend && LOGIN_LIMIT_PER_IP=500 ./mvnw spring-boot:run
# (added by Dev, 2026-11-21) and with STRIPE_FAKE=true, the stand-in for Stripe that the subscription test pays with (task 68).
cd frontend && npm run e2e          # (added by Dev, 2026-11-16) 72 Playwright tests; locally add PW_CHANNEL=chrome
```

(added by Dev, 2026-11-06, task 79) Scheduled jobs never fire in backend tests: `NoScheduledJobsInTests`, in the test sources, gives every test
context a scheduler that does nothing, as a reminder at ten past the hour once landed inside `PushTest`. Tests call a job directly.
The browser tests change demo data: **restart the API between e2e runs**, or tests fail on duplicates.
The mobile lock file is written with npm 11 (Node 24); npm 10 rejects it, so CI's mobile job uses Node 24.
CI (`.github/workflows/ci.yml`) runs backend, frontend (unit tests and build), web-headers (added by Dev, 2026-11-06: the real nginx image, `frontend/check-headers.sh`), e2e, loadtest and mobile jobs on every
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
  issues a local certificate) and any `SPRING_MAIL_HOST`, plus a throwaway `deploy/country.env` with the legal entity
  (`ProductionChecks` refuses the placeholder; updated by tester, 2026-11-06), then
  `docker compose -p mm-localtest -f deploy/docker-compose.prod.yml --env-file <file> up -d --build`;
  tear down with `down -v`.

## Go-live plan (set 2026-11-05, with the owner)

[PM: Claude, 2026-11-26] **The dates below are provisional** (Sagar, 2026-11-26: the go-live date is still flexible). They stay as
targets; the board marks the launch milestones "(provisional date)", and inputs from Pat are asked for without a deadline.
**Dates.** Dev and production ready in the UK, Ireland and Australia by **Thu 31 Dec 2026**. Go/no-go UK and Ireland Wed
6 Jan 2027; **UK and Ireland open for sign-ups and sales Mon 11 Jan**. Go/no-go Australia Thu 14 Jan; **Australia live Mon
18 Jan**. First training cohorts 25 Jan. **Release 2 by 26 Feb.** Anything that needs Pat, Kay or pilot teachers is due by
Fri 18 Dec (they are away over Christmas); 21-31 Dec is production readiness for the build team only. School calendars:
UK (Wokingham) back 4 Jan, Ireland 6 Jan, Queensland school year starts 27 Jan.

**Release 1 (ready 31 Dec).** Schools: licences signed up by the team, session player, both of Pat's assessments for all
seven colours, head and trust dashboards, induction, resources. Live practitioner and parent courses with card payment.
Motor Movers at Home sold as a 12-month pass (card payment that already works), gated by payment and Pat's parent
induction; `HOME_PROGRAMME` on in production. (added by Dev, 2026-11-21: the owner decided on 2026-11-21 that it is billed monthly from
launch, in place of the pass; built as task 68, see Current state.) Parent app in both stores, asking for the country at first launch. Text
session plans, plus the Green and Orange films if Pat agrees. **Release 2 (26 Feb):** SENCo interventions, inspection
evidence pack PDF, self-service school sign-up, free introductory course, calendar invites, session recordings, filmed
sessions (monthly billing moved into Release 1 on 2026-11-21, task 68). Out of both: movement analysis from video and at-risk prediction (medical-device rules).

**Three deployments, one build.** UK (London, GBP, VAT 20%, Europe/London, school year Sep-Aug), Ireland (EU region, EUR,
VAT 23%, Europe/Dublin, Sep-Jun, Junior/Senior Infants), Australia (Sydney, AUD, GST 10%, Australia/Brisbane for
Queensland, Jan-Dec in four terms, Kindy/Prep). Code work this needs (board tasks in brackets):
- Country settings per deployment: currency, tax rate and label, time zone, school-year months and assessment points,
  year names, legal entity and contact details (task 25). (added by Dev, 2026-11-06) Part A is built (pull request #29):
  the settings, Ireland (`Region.IE`), the clock's zone from them (Australia/Brisbane, not Sydney) and the open
  `GET /api/settings`; parts B (money), C (tax), D (school year and year names) and E (legal entity) follow.
- Money: replace hard-wired pounds (`frontend/src/money.ts` `gbp()`, `PaymentViews`, "£" labels, mobile screens, and
  `currency=gbp` in `StripeApiClient`). Prices shown tax-inclusive with the tax amount on invoices. (added by Dev,
  2026-11-06) Money is done in part B (pull request #30), the tax amount in part C (pull request #32).
- `YearPoint` assumes a September school year; make it follow the deployment (in Australia a school year is one calendar
  year, so year-on-year figures must not split it). (added by Dev, 2026-11-06) Done in part D (pull request #33).
- Parent app: one store app; replace the single `EXPO_PUBLIC_API_URL` with a country chooser at first launch (task 27).
  (added by Dev, 2026-11-06) Built in pull request #36.
- Home programme pass: buy, renewal reminder, access ends, gate before the first session (task 26). (added by Dev,
  2026-11-06) The gate has one place, `HomeService.requireReadyForSessions`: add the payment check there.
- Website per country (task 28): the site still uses the Movement for Learning project's contact email and phone
  (`site.ts`) and leads with "From the Movement for Learning Project"; Pat said Movement for Learning will not be
  reopened, so the credit wording must be agreed with her. `PLATFORM_ENQUIRY_EMAIL` still defaults to the owner.
This supersedes Next steps item 13 (Australia "later").

**Tracking.** monday.com board "Motor Movers Go-Live (Dec 2026)", id 5032300138
(https://ihau-company.monday.com/boards/5032300138): tasks numbered 1 to 77 with Status, Due, Owner, Area and Task #, in groups
"Decide and build: 9 Nov - 11 Dec", "Test, prod-ready and launch: 11 Dec - 26 Feb" and "Release 2 and later". When a task here is done, set it
to Done there. Plan doc: https://claude.ai/artifact/6dYqws3w9cdpzhaARayV3d; requirements review (45 requirements against
the platform, conflicts between the business documents): https://claude.ai/artifact/1WPp7mq67e8dqkCiBCN5Wq; designed PDF:
`F:\IHA\Pat\Motor Movers go-live plan - January 2027.pdf`. A scheduled check (daily at 9:00 Brisbane) reads the
board and the owner's inbox and emails him a digest; it never emails anyone else.

[PM: Claude, 2026-11-06] **Developer tasks and their specs.** Work for the Developer is a go-live board item named
**"DEV: ..."** (Owner "Dev agent (Claude Code)", Area Build). Its **full spec is the item's update** (notes) on monday.com,
not a file in this repository: source, problem, what to build, tests, docs to update, and when it is done. Read it with
the monday.com tools before starting, set the item to Working on it, and reply to the spec with the pull request link;
the owner sets Done when he merges. Specs so far: 14 licence check
(https://ihau-company.monday.com/boards/5032300138/pulses/2899865015), 25 country settings
(https://ihau-company.monday.com/boards/5032300138/pulses/2899847796), 27 country chooser in the parent app
(https://ihau-company.monday.com/boards/5032300138/pulses/2899865399), and 56 and 57 (parent induction gate, films).
[PM: Claude, 2026-11-06] **One board for all open work.** At the owner's request the old team board "Motor Movers
Platform" (5032277063) was merged into the go-live board and **archived** on 2026-11-06 (restorable from monday.com's
archive; its done items stay there as history). Fifteen duplicates were archived first, with their details carried over
as notes on the go-live items; the 14 open items were moved across with their notes as tasks **58 to 71** (Pat's grid
queries, prototype PDFs, refunds and credit notes, the old git stash, the enquiry email, Zoom, Stripe and phone
notifications tried for real, the staging load test, and in the new group **"Release 2 and later (after 26 Feb)"**:
videos, home subscriptions, a shared rate-limit store, Zoom attendance, renewing certificates online). The go-live
board now also has **Details** and **Where in the repository** columns. Add new work only there.

[PM: Claude, 2026-11-12] **Two teams of agents (Sagar's and Rahul's).** Sagar and Rahul each run a Developer, a Tester and a
Project manager on their own computer. They share the go-live board, which is the only channel between the two
computers: its **Team** column (Sagar, Rahul, People only) says whose agents take a task, **Agent stage** (Spec ready,
Dev working, Ready to test, Testing, Bug found, Passed, Merged, Blocked) where it stands, and **Pull request** links
it, with a tagged reply at each step. Each team tests and merges only its own pull requests. Which team a computer's
agents belong to is set in `CLAUDE.local.md` (git ignores it). Rules, tags, migration numbering and setup:
`.claude/team.md`; agent definitions: `.claude/agents/`. The Project manager's private details (contacts, open
decisions, prices, risks) are in `.claude/pm-private.md`, which git ignores and Sagar shares by hand, since this
repository is public.

[PM: Claude, 2026-11-15] **Handoff files: restarting a session without losing context (Sagar, 2026-11-15).** Each role keeps
`.claude/handoff/<pm|dev|tester>.md` on its own computer (git ignores it): what it is doing, waiting on, next steps, promises and
where things are. Read it on start; rewrite it before ending any turn that changed something. Details: `.claude/team.md`,
Starting and ending a session.

**Waiting on Pat and Kay (email of 2026-11-05, decisions due Fri 13 Nov).** Each was sent with a suggested answer:
1 contracting company per country until the joint venture exists (suggested: Neuroway for UK and Ireland, an
Australian company of ours for Australia); 2 brand (Motor Movers Programme or Professor Pat Preedy Programme); 3 prices in
GBP, EUR and AUD (start from the proposal's: early years 450, primary core 900, full 1,750 a year, trusts 15% off;
practitioner 180 then 60 a year; parent course 45; home 35 a year); 4 home billing (12-month pass at launch, monthly from
February); 5 Green and Orange films at launch; 6 stages on sale first (Green, Yellow, Orange); 7 pilot schools (Pat's
four UK schools plus the Berkshire school improvement team; one or two each in Ireland and Australia); 8 scope sign-off.
(added by Dev, 2026-11-05) The owner's reply to Pat's email of 2026-11-03 (**sent 2026-11-05**, Kay and Rahul copied) explains the retired 0 to 3 check, asks her to
resend the revised Motor Spin / Jack and Jill / Twinkle Twinkle file (empty inside her original winmail.dat too), and asks
four grid queries (Blue Thumbs Up twice; Indigo Puppy Fun; Orange See Saw and Violet Motor Lift Four repeating
another's descriptions; a Blue table headed "Final end of Green", read as the end of Blue) and five Motor Movers at
Home questions (Red for under 2s, the parent induction, rhyme order, Cool Down 1, module length; listed in
`docs/assessment-design.md` and `docs/home-programme-design.md`), and asks to use the Green and Orange films. Both
emails went from sagarnikam@ihau.au. The go-live email copied Rahul as rahulshirode@ihau.au, which bounced; it was
forwarded to rahul.shirode@ihau.au. Pat's and Kay's answers go on the boards when they arrive.
[PM: Claude, 2026-11-16] Answered so far: 2 brand (the Professor Pat Preedy Programme), 3 prices (task 4, see Current state), 5 films
(Pat agreed, with Yellow too), and 1 for Australia only (International Healthcare Australia Pty Ltd). Still open: 1 for the UK and
Ireland, 6 stages on sale first, 7 pilot schools, 8 scope sign-off.
[PM: Claude, 2026-11-22] 4 home billing is decided by the owner (2026-11-21): monthly from launch, in place of the 12-month pass (task 68, merged).

[PM: Claude, 2026-11-06] **Contracting company, decision 1 (go-live task 2), part answered.** The owner: for Australia only,
International Healthcare Australia Pty Ltd contracts, with Motor Movers as a brand under that registration, for now. The UK and
Ireland stay as suggested (Neuroway), not yet confirmed by Kay or Neuroway. Its ACN and ABN are still
to confirm before they go in the legal entity settings (`PLATFORM_COUNTRIES_AU_...`, `deploy/country.env`); production
refuses placeholders.

## Current state (2026-11-27)

[PM: Claude, 2026-11-27] **This file was split on 2026-11-27 (Sagar's OK):** it had grown past 160,000 characters, and every session
reads it whole. The dated notes of every task and every Tester report since October are now in **`docs/history.md`**, unchanged and newest
first. Read the note for a task there before changing that area. Keep this section short: what is true now, in a few lines per area, with
the details of new work in its pull request, the board spec, `docs/history.md` (add a dated note there) and `deploy/RUNBOOK.md`.

**Where things stand.** Release 1 is built except the items under Next steps and on the board; nothing is deployed yet (task 9, servers,
checklist in `deploy/SERVER-SETUP.md`). Latest migration **V27**. Tests: backend 327, web 101 unit tests, 72 browser tests. Last merged:
task 88, clean-up (pull request #83, main 5f82e26); task 87, monthly launch price (#81). In progress: **task 89, recorded courses in place
of live Zoom courses** (Developer, branch `task-89`, migration V28; Pat's email of 2026-11-26 and Sagar's decision of 2026-11-27 that the
trainer role is not needed). The go-live dates are provisional (Go-live plan above).

**What the platform does now** (details in `docs/history.md`, by task):
- **Schools:** licences (plans with dated prices on Plans and prices; a school is licensed for a colour on a day when any of its licences
  runs that day and covers it, `LicenceService.licensedFor`; a licence not started shows "Starts <day>"); the teacher's session player with
  Pat's 990 daily session plans for all seven colours, films per rhyme, module or exercise (task 57), the 20-day counter; Pat's two
  assessments (developmental charts and exercise scores 1 to 4, the extra-support flag, never a diagnosis); head and trust or group
  dashboards, governor CSV, record access log, School setup; the staff induction (films only for licensed schools and the team); resources.
- **Training:** courses with quizzes, certificate PDFs and expiry reminders; staff ask for a place and their head approves and pays. Today
  they run live on Zoom in cohorts with trainers; **task 89 turns them into recorded lessons and switches live courses off**.
- **Parents:** a linked child's school assessments in plain words (never scores or the flag); the parent app (Expo) asks for the country at
  first launch. **The Programme at Home** (families whose school is not on the programme; `HOME_PROGRAMME`, off in production): the parent
  induction gate, the starting check, daily home sessions by Pat's rules, billed **monthly** by Stripe subscription at £14.99 / €16.99 /
  A$26.99 (task 87; the old 12-month pass is no longer sold, existing passes still count). The app shows the subscription state only, never a
  price or a payment link (store rules, to confirm with the adviser).
- **Payments:** invoices and payments recorded by the team, card payment by Stripe Checkout, subscriptions, refunds and credit notes, the tax
  in each payment; `STRIPE_FAKE=true` is a stand-in for Stripe in development and the browser tests (production refuses it). Not yet tried
  with a real Stripe account.
- **Three countries, one build:** `PLATFORM_REGION` UK, IE or AU picks the country settings (currency, tax, time zone, school year and
  assessment points, year names, labels, legal entity, launch prices); production refuses placeholder legal entity details.
- **Name and brand:** the Professor Pat Preedy Programme (rules above); certificate numbers and payment references start `PPP-`.
- **Operations:** Docker Compose stack (Caddy, nginx, API, PostgreSQL, nightly and off-site backups, restore test), file storage on
  DigitalOcean Spaces (not yet tried on a real bucket), safe logs with an optional shipper. RUNBOOK has each.

**Launch prices** (task 85, tax included; changed on Plans and prices after the first start):

| | UK | Ireland | Australia |
|---|---|---|---|
| Childcare centre or kindergarten licence, a year | £395 | €465 | A$750 |
| School licence, a year | £635 | €745 | A$1,200 |
| Group of schools, a year (10% below a school) | £571.50 | €670.50 | A$1,080 |
| Practitioner course, then renewal a year | £105 / £30 | €125 / €35 | A$200 / A$60 |
| Parent course | £55 | €60 | A$100 |
| Programme at Home, a month | £14.99 | €16.99 | A$26.99 |

Course prices may change with recorded courses (asked of Kay, 2026-11-27).

**Open with Pat, Kay and the owner** (the board has each): the UK and Irish contracting company (task 2); colours on sale first; pilot
schools; Pat's two introductions (staff and parent), the recorded teacher course, the parent course, renewal and course prices (email of
2026-11-27); the revised developmental assessment; the rhyme book, Green films and the rest of the films; a free first month at home; the
app stores' payment rule; accreditation (task 90, later). Domain: patpreedy.com suggested (2026-11-27), not yet bought.

**Conventions from the history that still apply:**
- A child belongs to a school class or a family: code reading `child.getSchoolClass()` must allow null (`child.isAtHome()`), and
  `HomeSubscription.getFamilyId()` may be null.
- Pages with their own security headers (`/admin/content`, `/live/<id>`) are reached with a full page load (`reloadDocument` or `<a href>`).
- Web: colour stages through `StageBadge`, dates through `day()` (`src/dates.ts`), toasts and confirmations through
  `components/Feedback.tsx` (never `window.confirm`), wording with a count, date or state in `src/labels.ts`; tables of people turn into
  cards under 760px. Browser tests click menu links with `exact: true`.
- A test of `SessionReminderJob.announceOpening` uses `OpeningSession.inFiveMinutes`, never the demo's timing.
- When a pull request changes a screen, flag the guides (`docs/guide-builder`, task 41); when it changes an endpoint, an access rule or a step
  a diagram shows, update that role's file in `docs/flows/`.
- The Tester runs the API from a worktree on `--server.port=8091` with `API_URL=http://localhost:8091` when the owner's ports are taken, and
  before 11am Brisbane time with the browser on UK time (`timezoneId: 'Europe/London'`).

**Merging.** The Developer opens the pull request; the Project manager merges it with the Tester's pass, every check green on the latest
commit and the owner's OK (team rules at the top; never `gh pr merge --auto`). GitHub also has `rahul-work`, a teammate's branch: leave it alone.

Recent commits (newest first, 2026-11-27): [PM: Claude, 2026-11-27] CLAUDE.md split, history to `docs/history.md`, server setup checklist; (added by Dev, 2026-11-26) clean-up before the feature freeze (task 88); (added by Dev, 2026-11-26) the monthly launch price for the Programme at Home (task 87); [PM: Claude, 2026-11-26] go-live dates provisional. Older: `docs/history.md` and `git log`.

[PM: Claude, 2026-11-06] **Boards on monday.com:** all open work is on the go-live board 5032300138 (Go-live plan, Tracking). Two more
boards: "Motor Movers" (5032278115, the flows by role) and "User Journeys" (5032278564).

## Next steps

Not started unless noted. Confirm priorities with the owner.

1. Real go-live inputs from the owner: UK server, domain and DNS, SMTP account with SPF/DKIM for the
   sending domain, then follow `deploy/RUNBOOK.md`.
2. Parent app store builds via EAS with (added by Dev, 2026-11-06) `EXPO_PUBLIC_API_URL_UK`, `_IE` and `_AU` set to the production `https://` addresses (RUNBOOK, Parent app builds). `eas init`
   gives the project id phone notifications need; then set `PUSH_ENABLED=true` (RUNBOOK, Phone notifications).
3. Session videos are timed placeholders until filmed sessions are uploaded. (added by Dev, 2026-11-06) Upload and hosting
   are built (go-live task 29, see Current state); still to do: create the buckets (RUNBOOK, File storage) and upload the
   films (go-live task 57).
4. Rate limits are per API instance (in memory). Move to a shared store (e.g. Redis) before running more
   than one API container.
5. Done 2026-10-21: off-server copies (`offsite` compose profile, rclone to an S3-compatible bucket) and
   `deploy/restore-test.sh`, tested against a demo dump. Still to do on the real server: create the bucket,
   fill in `BACKUP_OFFSITE_*`, and run the restore test after the first night.
6. Done 2026-10-21: Maven wrapper (`backend/mvnw`); CI uses it.
7. Done 2026-10-21: `SEED_STARTER_CONTENT=true` gives an empty deployment the starter content
   (`seed/StarterContent`, shared with the demo) as drafts for the team to approve. (added by Dev, 2026-11-10) Pat's daily
   session plans now load as drafts in every deployment (task 80, Current state), and the team can approve a whole colour or module at once.
   Other kinds of content are still approved one at a time.
8. Done 2026-10-21: bookings, attendance and certificates have a screen (per-session register since 2026-10-22).
   Session reminder emails done 2026-10-26. Not built: emailing the learner when their certificate is issued, and
   reminders before it expires.
9. Online card payment (Stripe Checkout) done 2026-10-26, waiting on the owner's Stripe account. (added by Dev,
   2026-11-06) Refunds and credit notes are built (pull request #37). Not built: VAT (prices are the amounts charged,
   with no net/VAT split; ask the owner whether Motor Movers is VAT-registered), and linking a payment to a
   specific licence or course booking.
10. **Assessment rebuild: Pat replied 2026-10-27; built for Green and Yellow the same day (see Pat's two
   assessments above); all seven colours since 2026-11-04.** Pat answered the questions of 2026-11-03 (see Current
   state): the flag rule agreed. The 0-3 check was retired on 2026-11-04 (the owner decided; see Current state).
   (added by Dev, 2026-11-05) Her revised descriptions and grid answers came on 2026-11-05 and are loaded (pull request
   #25); left: See Saw's and Motor Lift Four's copied descriptions. Pat's model replaced the single 0-3
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
   history fields (suggested: not held), and what parents see (a suggested default is built: plain words, no scores
   or flag); all but the gaps settled 2026-11-03. Full detail, storage, screens and the order of
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
12. **Motor Movers at Home (merged 2026-10-27, switched off in production).** Pat answered the ten questions on
   2026-11-03 and the rules follow her answers (see Current state). Before switching `HOME_PROGRAMME` on in production:
   the parent induction she asked for (content from her; then a gate before the first session), the
   other points under Still to settle in `docs/home-programme-design.md`, a privacy notice for families (Motor Movers is responsible
   for home children's data itself), and a price (card payments exist; a subscription is not built). Not built:
   subscriptions. (added by Dev, 2026-11-21) Monthly subscriptions are built (task 68, Current state); the monthly price is set since 2026-11-26 (task 87). (added by Dev, 2026-11-05) Where Red fits, the rhyme order and the module length were confirmed on
   2026-11-05, and the cool-down ends a session from Green to Indigo (pull request #25).
13. **Australia (planned, not started; the owner said "later", 2026-11-03).** Launch in Australia as well. Suggested:
   its own deployment (`platform.region: AU`, data kept in Australia, as designed) with the currency following the
   deployment (A$ there, £ in the UK), not a currency picker: money is hard-wired to £ today (`frontend/src/money.ts`
   `gbp()`, `PaymentViews` amounts, "£" form labels, Stripe `currency=gbp` in `StripeApiClient`), and one deployment
   mixing currencies would break the spend totals. Each deployment sets its own prices on Plans & prices. Also for
   Australia: the school year runs January to December in four terms (the assessment's start, middle and end of year
   assume September to July), GST at 10% (no tax is handled yet), Foundation/Prep year names, the Privacy Act's
   privacy notices, and Stripe in AUD. To decide with the owner: separate deployment or one platform with two
   countries.
