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
14 days. Those copies are on the same server, so also copy them somewhere else every day, e.g.:

```bash
# crontab on the server, 03:00
0 3 * * * rclone copy /path/to/motor-movers/deploy/backups remote:motor-movers-backups
```

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

Test a restore on a spare machine at least once a term.

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
