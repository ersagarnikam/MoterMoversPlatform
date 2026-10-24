# Load testing

`school-day.js` is a [k6](https://grafana.com/docs/k6/latest/) script that simulates a busy school day:
teachers opening today's session and children's readiness profiles (each view is logged), screening saves,
head and trust dashboards and payments, and parents using the app. Every simulated user signs in once at the
start, because sign-in is rate limited, and then repeats their journey.

**Never run it against production with real data.** It needs the demo accounts, and it writes screening
results and access-log entries. Use a local copy of the production stack, or a staging server, with demo
data (below).

## Run it on this machine

1. Write a throwaway settings file outside the repository, for example `loadtest.env`:

   ```
   APP_DOMAIN=localhost
   ACME_EMAIL=loadtest@example.test
   DATABASE_USERNAME=motormovers
   DATABASE_PASSWORD=<any long random string>
   PLATFORM_JWT_SECRET=<any random string of 32 characters or more>
   PLATFORM_MAIL_FROM=Motor Movers <no-reply@localhost>
   SPRING_MAIL_HOST=localhost
   ```

2. Start the production stack with demo data (Caddy issues a local certificate for `localhost`):

   ```
   docker compose -p mm-loadtest -f deploy/docker-compose.prod.yml -f deploy/loadtest/demo-data.override.yml --env-file loadtest.env up -d --build
   ```

   Wait until `docker inspect -f '{{.State.Health.Status}}' mm-loadtest-api-1` says `healthy`.

3. Run a test from `deploy/loadtest`:

   ```
   k6 run school-day.js                          # normal day: up to 500 people, pausing 1-3 s (about 7 min)
   k6 run -e PEAK=1000 school-day.js             # the same with more people
   k6 run -e TEST=stress school-day.js           # no pauses, rising to 1,000 journeys a second (about 6 min)
   k6 run -e TEST=fixed -e RATE=400 school-day.js  # no pauses, 400 journeys a second for 90 s
   ```

   Exit code 99 means a pass mark was missed (fewer than 1% failed requests, 95% of requests within 1 s);
   the summary shows which, page by page.

4. Remove the stack and its data: `docker compose -p mm-loadtest -f deploy/docker-compose.prod.yml -f deploy/loadtest/demo-data.override.yml --env-file loadtest.env down -v`.

To test a staging server, start it the same way with the override, then add `-e BASE=https://<its address>`.
Run k6 from a different machine, or the load tool competes with the server for the processor.

## Live session rush (`live-session.js`)

The busiest moment for a live course session: every booked learner opens their Training page and presses Join
within a minute, while the host's register refreshes every 15 seconds. It tests the platform only, so run the API
**without Zoom keys** (no real meetings are created). Zoom's own limit is the host's plan: 100 participants on
the owner's plan, so up to 99 learners per session.

```
# API on this machine with demo data, 500 extra learners booked, and a sign-in limit the test will not hit
LOAD_TEST_LEARNERS=500 LOGIN_LIMIT_PER_IP=2000 ./mvnw spring-boot:run     # from backend/, within the hour
k6 run -e LEARNERS=99 live-session.js              # 99 learners arriving over 60 s
k6 run -e LEARNERS=500 -e RUSH=5 live-session.js   # 500 within 5 s
```

It checks that every join succeeds and that the register shows every learner as joined at the end. Production
refuses to start with `LOGIN_LIMIT_PER_IP` raised, and `LOAD_TEST_LEARNERS` only acts with demo data.

Results on the same laptop (2026-10-24, API on H2 in development mode, k6 on the same machine):

| Learners | Arriving over | Requests | Failed | Join, 95% within | Register, 95% within | All shown as joined |
|---|---|---|---|---|---|---|
| 50 | 60 s | 309 | 0 | 5 ms | 59 ms | 50 of 50 |
| 99 (a full session) | 60 s | 603 | 0 | 4 ms | 18 ms | 99 of 99 |
| 500 | 60 s | 3,009 | 0 | 3 ms | 26 ms | 500 of 500 |
| 500 | 5 s | 3,006 | 0 | 3 ms | 67 ms | 500 of 500 |

The platform side of joining is light: far below the ceiling found in the school-day test. Repeat against
staging (PostgreSQL, a separate machine for k6) before go-live, and run a pilot with real people for the Zoom side.

## School day results (2026-10-19)

All on one 16-core laptop running both the stack and k6, with the demo data (4 schools, 261 children).

| Test | Requests | Failed | Median | 95% within |
|---|---|---|---|---|
| Normal day, up to 500 people with pauses (128 requests a second) | 49,602 | 0 | 6 ms | 12 ms |
| Stress, rising to 1,000 journeys a second | 242,499 | 0 | 2.9 s | 3.7 s |
| Fixed, 400 journeys a second for 90 s | 84,857 | 0 | 2.1 s | 4.0 s |

The ceiling was about 800-900 requests a second, past which requests queued but none failed. At that point
the whole laptop was at 97-99% CPU (the API used about 5 cores, PostgreSQL about 4, the API under 600 MB of
memory), and raising the database pool from 10 to 30 made no difference: the limit was the shared machine,
not the platform. Repeat from a separate machine against staging before go-live.
