# Motor Movers Platform

The digital platform for the Motor Movers programme: one website and app that runs the programme for a
school, from screening each child to showing the head teacher the results, plus live training for
practitioners and parents.

Built from the *How the platform works* overview and the *Motor Movers, online* partnership proposal
(15 October 2026).

| Part | Tech |
|---|---|
| API (`backend/`) | Java 21, Spring Boot 4, Spring Security (JWT), Spring Data JPA, Flyway |
| Database | H2 in memory for development, PostgreSQL for real environments |
| Web app (`frontend/`) | React 19, TypeScript, Vite, React Router |
| Parent app (`mobile/`) | Expo SDK 58, React Native, TypeScript, Expo Router (iOS, Android and web) |

## Running it locally

You need Java 21, Maven 3.9+ and Node 22+.

```bash
# 1. API on http://localhost:8080, with demo data in an in-memory database
cd backend
mvn spring-boot:run

# 2. Web app on http://localhost:5173 (proxies /api to the API)
cd frontend
npm install
npm run dev
```

Open http://localhost:5173 and pick a demo account on the sign-in page. Every account uses the
password `password`:

| Account | Who | Lands on |
|---|---|---|
| `teacher@oakfield.test` | Class teacher (Ms Hardy, Year 2 R) | Today's session |
| `head@oakfield.test` | Head teacher / SENCo, Oakfield Primary | Progress dashboard |
| `trust@northbridge.test` | Trust lead, Northbridge Academy Trust | Trust schools |
| `practitioner@example.test` | Practitioner | Practitioner course |
| `parent@example.test` | Parent or carer (Jo Bennett, linked to a child in Year 2 R) | Parent course; use the parent app |
| `admin@motormovers.test` | Motor Movers team | Content approval |

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
SPRING_PROFILES_ACTIVE=postgres PLATFORM_JWT_SECRET=$(openssl rand -hex 32) mvn spring-boot:run
```

Set `SEED_DEMO_DATA=true` to load the demo data into PostgreSQL. Flyway creates the schema
(`backend/src/main/resources/db/migration`).

### Tests

```bash
cd backend && mvn verify       # unit and API integration tests
cd frontend && npm run build   # type-check and production build
cd mobile && npm run typecheck # parent app type-check
```

GitHub Actions runs both on every push (`.github/workflows/ci.yml`).

## What is built

### Six kinds of user, one platform

| Role | What they can do |
|---|---|
| Class teacher | See their own class only: today's session, the half-term counter, screening, readiness profiles |
| Head teacher / SENCo | Progress and participation for their school, flagged classes, exports, the record access log |
| Trust lead | Each school's licence, renewal date and summary results. Never individual children, unless a school grants it |
| Practitioner / parent | Live course catalogue, booking, attendance and certificate status |
| Motor Movers team (admin) | Content approval, schools and licences, every class |

Access rules are enforced in the API (`security/service/AccessPolicy.java`), not just hidden in the UI.

### Teacher screens
- **Today**: one button to start the daily 10-minute session, the next session in sequence for
  the class's colour stage, and a 20-day counter that shows when a class is quietly dropping off.
- **Class player**: the session plan ticks along as the session runs. Tap a child to mark them absent,
  then **Complete session**. The video is a timed placeholder until filmed sessions are uploaded
  (`programme_session.video_url`).
- **Screening**: one item at a time with what to watch for and a 0 to 3 scale. Scores are queued on the
  device and sent when online. Each score carries a client id, so resending is safe.
- **Readiness profile**: six areas (balance, gross motor, manual dexterity, aiming and catching, postural
  control, bilateral coordination) in programme terms (Emerging, Developing, Secure), the suggested stage
  and the change since the first screening. The *Not a diagnosis* banner is permanent.

### Dashboards
- **Head teacher**: children screened, moved up a stage, daily participation, classes active, and
  participation by class with classes below 70% flagged. CSV exports: governor summary and anonymised
  research data.
- **Trust**: one row per school with licence status (Active, Renewal due, Free trial, Expired), stages,
  screened rate and participation.

### Training
Professional track (4 × 90 min, certificate renewed yearly) and parent track (3 × 60 min, no
certificate). Cohorts have UK-evening and UK-morning / AU-evening slots. The admin API records attendance
and the assessment, and the certificate is issued automatically when both are complete.

### Safety built in
| Rule | Where |
|---|---|
| Initials and year group by default; full names only if the school switches them on; no dates of birth | `child/model/Child.java`, `school/model/School.java` |
| Every view of a child's record is logged; heads can read the log | `child/model/ChildAccessLog.java` |
| Records deleted automatically a set time after a child leaves (`platform.retention-days`) | `child/service/RetentionJob.java` |
| Video of children off unless the school opts in, with consent per child | `School.videoEnabled`, `Child.videoConsent` |
| Wording check: content with diagnostic terms (ADHD, autism, diagnosis, centile…) cannot be approved | `content/util/WordingGuard.java` |
| Nothing reaches teachers until Pat or Laura approve it | `content/model/ContentStatus.java` |
| Trust leads see summaries only | `security/service/AccessPolicy.java` |
| One data region per deployment (UK or AU) | `platform.region` |

## Project layout

```
backend/
  src/main/java/uk/co/motormovers/platform/
    security/     sign-in, JWT, who can see what
    school/       trusts, schools, classes, licences and plans
    child/        children, access log, retention
    content/      programme sessions, approval, wording check
    classroom/    today's session, completing a session
    screening/    screening items, results, readiness profile
    dashboard/    head teacher and trust dashboards, exports
    training/     courses, cohorts, enrolments, certificates
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

Deliberately out of scope: automatic movement analysis from video and predicting which children are at
risk. Both would push the platform toward medical-device rules.
