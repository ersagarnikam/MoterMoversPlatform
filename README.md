# Motor Movers Platform

The digital platform for the Motor Movers programme: one website and app that runs the programme for a
school, from assessing each child to showing the head teacher the results, plus live training for
practitioners and parents.

Built from the *How the platform works* overview and the *Motor Movers, online* partnership proposal
(15 October 2026).

## Tech stack

| Part | Tech |
|---|---|
| API (`backend/`) | Java 21, Spring Boot 4, Spring Security (JWT), Spring Data JPA (Hibernate), Flyway migrations |
| Database | H2 in memory for development, PostgreSQL 17 for real environments |
| Web app (`frontend/`) | React 19, TypeScript, Vite 8, React Router 7; self-hosted fonts (`@fontsource`) and a strict content security policy |
| Parent app (`mobile/`) | Expo SDK 58, React Native, TypeScript, Expo Router (iOS, Android and web via react-native-web) |
| Production (`deploy/`) | Docker Compose: Caddy (HTTPS) -> nginx (web app) -> API -> PostgreSQL, plus a nightly backup; email over SMTP |
| Tests | JUnit and Spring Boot integration tests (API), Playwright with accessibility checks (web), k6 load test (`deploy/loadtest/`) |
| CI | GitHub Actions: backend, frontend, browser and parent app jobs on every push |

## Running it locally

You need Java 21 and Node 22+. Maven is not needed: `backend/mvnw` (`mvnw.cmd` on Windows) downloads
Maven 3.9.16 on first use.

```bash
# 1. API on http://localhost:8080, with demo data in an in-memory database
cd backend
./mvnw spring-boot:run

# 2. Web app on http://localhost:5173 (proxies /api to the API)
cd frontend
npm install
npm run dev
```

Open http://localhost:5173 for the public website (home, about, programme, for schools, for families).
**Sign in** asks for your role, then opens the sign-in page, where you can pick a demo account. Every account uses the
password `password`:

| Account | Who | Lands on |
|---|---|---|
| `teacher@oakfield.test` | Class teacher (Ms Hardy, Year 2 R) | Today's session |
| `newteacher@oakfield.test` | Class teacher who has not done the induction yet (Mr Lewis, Year 2 T) | Today's session, locked until the induction is done |
| `head@oakfield.test` | Head teacher / SENCo, Oakfield Primary | Progress dashboard |
| `trust@northbridge.test` | Trust or group lead, Northbridge Academy Trust | Our schools |
| `practitioner@example.test` | Practitioner | Practitioner course |
| `parent@example.test` | Parent or carer (Jo Bennett, linked to a child in Year 2 R) | Parent course; use the parent app |
| `admin@motormovers.test` | Motor Movers team | Content approval |
| `pat@motormovers.test` | Motor Movers team (Pat, trainer) | Content approval; Live sessions to start a course session |
| `trainer@motormovers.test` | Trainer in Pat's team (Amy Clarke) | Live sessions for her own cohorts only |

Names, schools and figures in the demo data are examples only.

Use the **Light / Dark / System** switch in the top bar (or on the sign-in page) to change the theme.
The choice is remembered on that device; *System* follows the device setting.

If the API runs somewhere other than port 8080, start the web app with `API_URL=http://localhost:9090 npm run dev`.

### Parent app

```bash
cd mobile
npm install
npx expo start          # scan the QR code with Expo Go on a phone on the same Wi-Fi
npx expo start --web    # or open it in a browser at http://localhost:8081
```

In development the app finds the API on the machine running Expo, port 8080. Set
`EXPO_PUBLIC_API_URL` to use another address (for example `EXPO_PUBLIC_API_URL=https://api.example.org`).
Sign in as `parent@example.test` / `password`, or create a new account and link a child with the demo
code **HOME-TIME** (works once per database; restart the API to reset it).

Store builds go through EAS (`npx eas-cli@latest build`). Real builds must use an `https://` API address:
Android blocks plain `http://` outside development.

### With PostgreSQL

```bash
docker compose up -d db
cd backend
SPRING_PROFILES_ACTIVE=postgres PLATFORM_JWT_SECRET=$(openssl rand -hex 32) ./mvnw spring-boot:run
```

Set `SEED_DEMO_DATA=true` to load the demo data into PostgreSQL. Flyway creates the schema
(`backend/src/main/resources/db/migration`). For a real deployment, `SEED_STARTER_CONTENT=true` instead loads
only the programme content (no demo people or schools) as drafts for the team to approve; see
`deploy/RUNBOOK.md`.

### Tests

```bash
cd backend && ./mvnw verify     # unit and API integration tests
cd frontend && npm test        # web app unit tests (Vitest)
cd frontend && npm run build   # type-check and production build
cd mobile && npm run typecheck # parent app type-check
cd frontend && npm run e2e     # browser tests; needs the API running with demo data (restart it between runs)
```

GitHub Actions runs all of these on every push (`.github/workflows/ci.yml`).

### Production

`deploy/docker-compose.prod.yml` runs the whole platform on one server with HTTPS, PostgreSQL and nightly
backups. Settings go in `deploy/.env` (start from `deploy/.env.example`). See `deploy/RUNBOOK.md` for the
first deployment, updates, backups and restores.

## What is built

### Six kinds of user, one platform

| Role | What they can do |
|---|---|
| Class teacher | See their own class only: today's session, the half-term counter, assessing children, readiness profiles. Take the one-hour induction and use the resource library |
| Head teacher / SENCo | Progress and participation for their school, flagged classes, exports, the record access log, their school's payments. Take the induction and use the resource library |
| Trust or school group lead | Each school's licence, renewal date and summary results, spending per school. Never individual children, unless a school grants it |
| Practitioner | Live course catalogue, booking, attendance, certificate status and a printable certificate, a Payments page (what is due, how to pay, history). Can also take the induction |
| Parent | The parent course, a Payments page (what is due, how to pay, history; a reminder while anything is due), and the parent app, which has the same payments screen |
| Motor Movers team (admin) | Writing and approving content (sessions, home activities, guidance, resources, induction lessons), setting up courses and cohorts, licence plans and prices, recording payments and spending summaries, schools and licences (with each school's agreed price), every class |

Access rules are enforced in the API (`security/service/AccessPolicy.java`), not just hidden in the UI.

### Teacher pages
- **Today**: one button to start the daily 10-minute session, the next session in sequence for
  the class's colour stage, and a 20-day counter that shows when a class is quietly dropping off.
- **Class player**: the session plan ticks along as the session runs. Tap a child to mark them absent,
  then **Complete session**. The video is a timed placeholder until filmed sessions are uploaded
  (`programme_session.video_url`). Red, Orange and Yellow sessions also show the session's nursery rhyme:
  the recording (once uploaded) and the words.
- **Induction gate**: school staff complete the one-hour induction (six 10-minute parts, under
  *Training*) before they can run sessions. Until then *Start session* is replaced by a link to the
  induction, and the API refuses to record a session. Finishing it is permanent: parts added later do not
  lock anyone out (`training/service/InductionService.java`).
- **Resources**: rhyme recordings and words, the rhyme book and printables, for the stages on the
  school's licence. Files are URLs (`library_resource.file_url`) until uploads are built.
- **Assessment** (the `screening` package in the code): one movement at a time with what to watch for and a 0 to 3 scale. Scores are queued on the
  device and sent when online. Each score carries a client id, so resending is safe.
- **Readiness profile**: six areas (balance, gross motor, manual dexterity, aiming and catching, postural
  control, bilateral coordination) in programme terms (Emerging, Developing, Secure), the suggested stage
  and the change since the first assessment. The *Not a diagnosis* banner is permanent.

### Dashboards
- **Head teacher**: children assessed, moved up a stage, daily participation, classes active, and
  participation by class with classes below 70% flagged. CSV exports: governor summary and anonymised
  research data.
- **Trust or school group**: one row per school with licence status (Active, Renewal due, Free trial, Expired), stages,
  assessed rate and participation.

### Training
The one-hour staff induction comes first (see *Induction gate* above), done online at the teacher's own
pace. Then the live courses: professional track (4 × 90 min, certificate renewed yearly) and parent
track (3 × 60 min, no certificate). Cohorts have UK-evening and UK-morning / AU-evening slots. Each cohort has a start time and dated
weekly sessions. **Live sessions run on Zoom inside the platform**: the team starts one from Live sessions, with
the register beside the meeting, and learners booked on the cohort press Join on their Training page from 15
minutes before the start, with no Zoom account (setup: `deploy/RUNBOOK.md`, Live sessions on Zoom). The register shows who has joined so far and when
("Joined: 7 of 9"), with one click to tick them all. Ticks in the register, or on the Courses page, count as
attendance; with the assessment passed the certificate is issued
automatically, and the learner can print it or save it as a PDF from the Training page.

### Safety built in
| Rule | Where |
|---|---|
| Initials and year group by default; full names only if the school switches them on; no dates of birth | `child/model/Child.java`, `school/model/School.java` |
| Every view of a child's record is logged; heads can read the log | `child/model/ChildAccessLog.java` |
| Records deleted automatically a set time after a child leaves (`platform.retention-days`) | `child/service/RetentionJob.java` |
| Video of children off unless the school opts in, with consent per child | `School.videoEnabled`, `Child.videoConsent` |
| Wording check: content with diagnostic terms (ADHD, autism, diagnosis, centile…) cannot be approved | `content/util/WordingGuard.java` |
| Nothing reaches teachers until Pat or Laura approve it: sessions, rhymes and resources, induction lessons | `content/model/ContentStatus.java` |
| No one runs a session before finishing the induction | `training/service/InductionService.java` |
| Trust or group leads see summaries only | `security/service/AccessPolicy.java` |
| One data region per deployment (UK or AU) | `platform.region` |

## Project layout

```
backend/
  src/main/java/uk/co/motormovers/platform/
    security/     sign-in, JWT, who can see what
    school/       trusts, schools, classes, licences and plans
    child/        children, access log, retention
    content/      programme sessions, resource library (rhymes, books, printables), approval, wording check
    classroom/    today's session, the induction gate, completing a session
    screening/    screening items, results, readiness profile
    dashboard/    head teacher and trust dashboards, exports
    training/     staff induction, courses, cohorts, enrolments, certificates
    family/       parent app: invite codes, parent links, progress, home activities, guidance
    seed/         demo data (dev profile only)
    common/       shared config, exceptions, enums and helpers
                  each feature splits into controller/, service/, model/ (entities, enums),
                  repository/, dto/ (API views), util/ and config/ as needed
  src/main/resources/db/migration/   Flyway SQL
frontend/
  src/pages/      one file per screen
  src/api.ts      typed API client
  src/outbox.ts   offline queue for screening scores
mobile/
  src/app/        one file per screen (Expo Router); (tabs)/ holds the five tabs
  src/components/ shared UI
  src/lib/        API client, sign-in, offline queue for home sessions, theme
```

## Parent app

A phone app for parents and carers (`mobile/`), with five tabs:

- **Today**: one short home activity a day for the child's colour stage, played step by step with a
  gentle timer, and a 14-day counter. Sessions are saved on the phone first and sent when online.
  Parents without a linked child pick a stage.
- **Progress**: the child's colour stage, the six areas as *Emerging / Developing / Secure* (never raw
  scores), the dates of movement checks, and how often the class did its daily session. The *Not a
  diagnosis* banner is always shown. Parents can give the child a name that is kept on the phone only.
- **Guidance**: short articles approved by Pat and Laura.
- **Course**: the parent course: book a place and see attendance.
- **Account**: what the school can see, and signing out.

How a parent gets linked to a child:

1. The platform team switches **Parent app** on for the school (*Schools & licences*). It is off by default.
2. A teacher or head opens a child's readiness profile and creates a parent code, for example `K7QX-3MPA`.
   Codes work once and expire after 14 days.
3. The parent creates an account in the app and enters the code.
4. The head sees every link under *Parent app links* on the progress dashboard and can remove any of them.
   The parent can also remove the child from their app.

| Rule | Where |
|---|---|
| A parent sees a child only through a school-issued code, only while the school keeps parent links on, and only while the child is on roll | `security/service/AccessPolicy.java` (`requireParentOf`) |
| Every time a parent opens progress is in the school's record access log | `family/service/FamilyService.java` |
| Parents see programme bands, not scores or fractions | `family/dto/FamilyViews.java` (`AreaBand`) |
| Home activities and guidance go through the same sign-off and wording check as class sessions | `content/controller/HomeContentController.java` |
| A parent's home activity log is theirs: it is not shown to the school | `family/model/HomeSessionRecord.java` |
| Bad invite codes all get the same message, so codes cannot be probed | `family/service/FamilyService.java` |
| The sign-in token is kept in the phone's secure store; parent sign-ins last 30 days | `mobile/src/lib/storage.ts`, `platform.jwt.parent-ttl-minutes` |

## Not built yet

These come later in the roadmap or need decisions first:

- Filmed session videos and offline caching of videos on school devices
- Online payment for licences and courses (Phase 0 uses an off-the-shelf course platform)
- Video calls for live training sessions, email reminders and calendar invites
- Inspection evidence PDF export
- Parent app: filmed home activity videos, push reminders, email verification, rate limiting on sign-in
  and code entry, and online payment for the parent course (booking a place does not take payment yet)
- Nursery mode
- Self-service school sign-up, staff account management and a password reset flow (parents included)
- The real screening items and stage mapping. `screening/util/ReadinessRules.java` holds placeholder
  thresholds until Pat and Laura confirm them.
- Uploading files to the resource library (rhyme recordings, the rhyme book, printables), and rhymes in
  the parent app. The induction's real wording and videos replace the demo text.
- Adapting the programme for older children and for children with special educational needs: today a
  class works at one stage, with no adapted sessions or stage per group.

Open questions from the *Motor Movers Overview* (Neuroway) for Pat and Laura:

- Age range: the overview says 6 months to 7 years; `common/model/ColourStage.java` says 6 months to 8.
- Session length: the Loughborough research ran 20 minutes a day; the platform's daily session is 10.
- The overview says staff can "distinguish between developmental gaps and potential SEND needs". The
  platform deliberately makes no such call. A non-diagnostic prompt (for example, "not moving on after a
  good run of daily sessions: talk to your SENCo") would support the claim without crossing that line.

Deliberately out of scope: automatic movement analysis from video and predicting which children are at
risk. Both would push the platform toward medical-device rules.
