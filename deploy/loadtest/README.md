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

## Results so far (2026-10-19)

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
