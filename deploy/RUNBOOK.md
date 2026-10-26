# Production runbook

One Linux server with Docker runs everything (`deploy/docker-compose.prod.yml`):

```
internet -> caddy (HTTPS, :80/:443) -> web (nginx: app + /api proxy) -> api (Spring Boot) -> db (PostgreSQL)
                                                                         backup (nightly pg_dump)
```

Only Caddy is published. The database is not reachable from outside the server.

## First deployment

1. Server: 2 vCPU / 4 GB RAM is plenty to start. Install Docker Engine with the compose plugin. Open
   ports 80 and 443 only. Host it in the data region you serve (UK schools on UK hosting).
2. Point DNS for your domain (an A record) at the server, and wait until it resolves.
3. Get an SMTP account (Postmark, SES, Mailgun, Microsoft 365…) and verify the sending domain (SPF/DKIM),
   or invite and reset emails will land in spam.
4. On the server:
   ```bash
   git clone <repo> motor-movers && cd motor-movers
   cp deploy/.env.example deploy/.env
   chmod 600 deploy/.env
   # fill in deploy/.env: openssl rand -hex 32 for PLATFORM_JWT_SECRET, openssl rand -hex 24 for DATABASE_PASSWORD
   docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env up -d --build
   ```
5. Check: `docker compose -f deploy/docker-compose.prod.yml ps` shows everything healthy, and
   `https://<APP_DOMAIN>` loads with a valid certificate.
6. The address in `PLATFORM_ADMIN_EMAIL` receives a sign-in link. Choose a password, sign in, then add
   trusts, schools and heads from the admin screens. Heads invite their own teachers.
7. Programme content. With `SEED_STARTER_CONTENT=true` on the first start, the platform begins with the
   starter content as drafts: approve each item in **Content approval** (or edit it first). Then write the
   daily session plans for each licensed colour stage in Content approval, and open course cohorts in
   **Courses**. Teachers see nothing until it is approved. Without the setting, the team enters everything
   from scratch.

If the API will not start, read its log first: `docker compose -f deploy/docker-compose.prod.yml logs api`.
"Not safe to start in production" lists exactly which settings are missing.

## Updating

```bash
git pull
APP_VERSION=$(git rev-parse --short HEAD) docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env up -d --build
```

Database changes (Flyway migrations) run automatically when the new API starts. Take a manual backup first
(below) for any release that adds a migration.

Rollback: start the previous version's images with `APP_VERSION=<previous>` and the same command without
`--build`. A release that added a migration cannot simply be rolled back; restore the backup taken before it.

## Backups

The `backup` service writes `deploy/backups/motormovers-YYYYMMDD.sql.gz` at 02:00 UTC every night and keeps
14 days. Those copies are on the same server, so also copy them off it.

**Off-server copies.** Create a bucket with any S3-compatible storage in the UK, fill in the `BACKUP_OFFSITE_*`
settings in `deploy/.env`, and start the `offsite` service:

```bash
docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env --profile offsite up -d
docker compose -f deploy/docker-compose.prod.yml logs offsite   # expect "offsite copy ok"
```

It copies once when it starts, so a wrong setting shows straight away ("offsite copy FAILED" with the reason
above it), then every night at 03:00 UTC. It only adds files, so the bucket keeps dumps the server has already
deleted: set a lifecycle rule on the bucket (e.g. delete after 90 days) to keep storage in check. Always add
`--profile offsite` when running `up` so the service keeps running after updates.

Manual backup now:

```bash
docker compose -f deploy/docker-compose.prod.yml exec db pg_dump -U "$DATABASE_USERNAME" --no-owner motormovers | gzip > manual-$(date +%F).sql.gz
```

Restore (stops the API while restoring):

```bash
docker compose -f deploy/docker-compose.prod.yml stop api
gunzip -c motormovers-YYYYMMDD.sql.gz | docker compose -f deploy/docker-compose.prod.yml exec -T db \
  sh -c 'dropdb -U "$POSTGRES_USER" motormovers && createdb -U "$POSTGRES_USER" motormovers && psql -U "$POSTGRES_USER" motormovers'
docker compose -f deploy/docker-compose.prod.yml start api
```

**Test a restore** after the first night in production and then at least once a term. The script loads a backup
into a throwaway database (never the live one), checks the schema version and row counts, and removes it:

```bash
deploy/restore-test.sh                                  # the newest dump in deploy/backups
deploy/restore-test.sh /path/to/motormovers-YYYYMMDD.sql.gz   # e.g. one downloaded from the bucket
```

It ends with "Restore test passed." and exits non-zero if the dump will not load. Tested 2026-10-21 against a
dump of the demo data on PostgreSQL 17: schema V6, every table restored.

## Live sessions on Zoom

Live course sessions run as Zoom meetings shown inside the platform (Zoom's Meeting SDK). The team starts a
session from **Live sessions**; learners booked on the cohort join from their Training page, from 15 minutes before
the start, without a Zoom account. Without the keys below, sessions still run (register, rules) without video.

In the Motor Movers Zoom account (a paid plan, since sessions are longer than 40 minutes), at
[marketplace.zoom.us](https://marketplace.zoom.us) > Develop > Build App:

1. **Server-to-Server OAuth app.** Note the Account ID, Client ID and Client Secret into `ZOOM_ACCOUNT_ID`,
   `ZOOM_CLIENT_ID` and `ZOOM_CLIENT_SECRET`. Add the scopes for creating meetings (`meeting:write:meeting:admin`)
   and reading a user's token (`user:read:token:admin`), then activate the app.
2. Optional: **General app with the Meeting SDK turned on** (Features > Embed > Meeting SDK), to show the meeting
   inside the platform. Its own Client ID and Client Secret go in `ZOOM_SDK_KEY` and `ZOOM_SDK_SECRET` (not the
   Server-to-Server app's: Zoom rejects those for the SDK). Without it, the session page opens the meeting in Zoom
   in a new tab: the host signed in as host, learners with the meeting's join link. The app need not be published.
3. `ZOOM_HOST_EMAIL`: the licensed Zoom user who hosts every session (e.g. Pat's Zoom login).

Restart the API (`up -d`). A session's Zoom meeting is created the first time someone starts or joins it, and
recorded to Zoom's cloud when the cohort says sessions are recorded. Test with a session a few days away: the
team can open it at any time. The live page alone allows Zoom's scripts and the camera and microphone
(`frontend/nginx.conf`); if the browser console reports a blocked Zoom address there, add it to that policy and
to `LIVE_CSP` in `frontend/vite.config.ts`.

## Phone notifications

The parent app tells parents on their phone when a live session is open to join (15 minutes before), the day
before a session, and when a payment is due; a tap opens the right screen. Emails carry the same news, so this is
optional.

1. The app needs an EAS project and a store or development build (push does not work in Expo Go on Android):
   `npx eas-cli@latest init` in `mobile/` adds the project id to `app.json`, then build with EAS. For Android push,
   upload the Firebase (FCM) credentials to EAS; for iOS, let EAS create the push key when it asks.
2. In `deploy/.env` set `PUSH_ENABLED=true` (and `EXPO_ACCESS_TOKEN` only if the Expo account has enhanced push
   security on), then restart the API. With it off, the API only logs the notifications it would send.

Phones register when a parent signs in to the app and allows notifications, and are removed on signing out or when
Expo reports the app was deleted.

## Card payments

Parents, practitioners and heads can pay what is due by card on Stripe's own page (Stripe Checkout); the card never
touches the platform. Stripe then calls the platform, which marks the payment paid ("by card"). Without the keys,
Pay by card is hidden and the team marks payments paid by hand, as before.

1. In the Stripe dashboard (start in test mode): Developers > API keys, copy the secret key (`sk_test_...`).
2. Developers > Webhooks > Add endpoint: `https://<APP_DOMAIN>/api/stripe/webhook`, events
   `checkout.session.completed` and `checkout.session.async_payment_succeeded`. Copy its signing secret (`whsec_...`).
3. Put both in `deploy/.env` as `STRIPE_SECRET_KEY` and `STRIPE_WEBHOOK_SECRET` (the API refuses to start with only
   one), then restart the API. Pay a due payment with Stripe's test card 4242 4242 4242 4242 and check it shows paid.
4. To go live, repeat with the live keys and a live-mode webhook.

Refunds are made in the Stripe dashboard (find the payment by the payer's email). The platform does not record
refunds yet, so the payment still shows as paid there. Prices are charged as they are, with no VAT split.

## Everyday operations

| Task | How |
|---|---|
| Logs | `docker compose -f deploy/docker-compose.prod.yml logs -f api` (JSON, one line per event) |
| Health | `https://<APP_DOMAIN>/healthz` (web) and `docker compose ... ps` (all services) |
| Someone locked out | They use "Forgot password". Repeated failed sign-ins lock that email for 15 minutes |
| A member of staff leaves | Their head switches the account off in School setup. It stops working within a minute |
| Sign everyone out | Change `PLATFORM_JWT_SECRET` and restart the api |
| Certificates | Caddy renews automatically. Expiry warnings go to `ACME_EMAIL` |

## Limits to know

- Rate limits are counted in each API's memory. Fine for one API container; move them to a shared store
  (e.g. Redis) before running more than one.
- Children's records are deleted 365 days after their leaving date (nightly job at 02:30).
