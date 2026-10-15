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
| `parent@example.test` | Parent or carer | Parent course |
| `admin@motormovers.test` | Motor Movers team | Content approval |

Names, schools and figures in the demo data are examples only.

Use the **Light / Dark / System** switch in the top bar (or on the sign-in page) to change the theme.
The choice is remembered on that device; *System* follows the device setting.

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

Access rules are enforced in the API (`security/AccessPolicy.java`), not just hidden in the UI.

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
| Initials and year group by default; full names only if the school switches them on; no dates of birth | `child/Child.java`, `school/School.java` |
| Every view of a child's record is logged; heads can read the log | `child/ChildAccessLog.java` |
| Records deleted automatically a set time after a child leaves (`platform.retention-days`) | `child/RetentionJob.java` |
| Video of children off unless the school opts in, with consent per child | `School.videoEnabled`, `Child.videoConsent` |
| Wording check: content with diagnostic terms (ADHD, autism, diagnosis, centile…) cannot be approved | `content/WordingGuard.java` |
| Nothing reaches teachers until Pat or Laura approve it | `content/ContentStatus.java` |
| Trust leads see summaries only | `security/AccessPolicy.java` |
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
    seed/         demo data (dev profile only)
  src/main/resources/db/migration/   Flyway SQL
frontend/
  src/pages/      one file per screen
  src/api.ts      typed API client
  src/outbox.ts   offline queue for screening scores
```

## Not built yet

These come later in the roadmap or need decisions first:

- Filmed session videos and offline caching of videos on school devices
- Online payment for licences and courses (Phase 0 uses an off-the-shelf course platform)
- Video calls for live training sessions, email reminders and calendar invites
- Inspection evidence PDF export
- Parent home app (month 13+), nursery mode, school-linked parent invites
- Self-service school sign-up, staff account management and a password reset flow
- The real screening items and stage mapping. `screening/ReadinessRules.java` holds placeholder
  thresholds until Pat and Laura confirm them.

Deliberately out of scope: automatic movement analysis from video and predicting which children are at
risk. Both would push the platform toward medical-device rules.
