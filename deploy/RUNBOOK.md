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

## Country settings

One build serves three countries, each as its own deployment: `PLATFORM_REGION` is `UK`, `IE` (Ireland) or `AU`. It
picks the country's settings from `platform.countries` in `backend/src/main/resources/application.yml`:

| | UK | Ireland | Australia |
|---|---|---|---|
| Currency, locale | GBP, en-GB | EUR, en-IE | AUD, en-AU |
| Tax | VAT 20% | VAT 23% | GST 10% |
| Time zone (the platform's clock) | Europe/London | Europe/Dublin | Australia/Brisbane (Queensland, no daylight saving) |
| School year | September to August, 3 terms | September to June, 3 terms | January to December, 4 terms |
| Assessment points start | Sep / Jan / May | Sep / Jan / Apr (end Apr to Jun) | Jan / May / Sep |
| Year names | Nursery, Reception, Year 1-3 | Preschool, Junior Infants, Senior Infants, First Class, Second Class | Preschool/Kindergarten, Prep, Year 1-3 |
| Counter on Today (`term-label`) | This half-term | This term | This term |
| Times labelled | UK time | Irish time | Queensland time |
| Data held in | the UK | the EU | Australia |
| Legal entity | To be confirmed | To be confirmed | To be confirmed |

`data-held-in` is what the website says about where data is held (the UK, the EU, Australia): check it against the
server you chose, and blank it (`PLATFORM_COUNTRIES_<REGION>_DATAHELDIN=`) rather than leave something untrue.
Ireland's and Australia's points and year names were confirmed by Pat on 2026-11-10; every legal entity is still to be
confirmed. Anyone can read a
deployment's settings at `GET /api/settings` (no sign-in); the website and the parent app use them.

**The legal entity must be set before a production deployment will start.** While any of its values says "To be
confirmed", the API refuses to start in production and names the settings to fill in. Put them in `deploy/country.env`
(leave a value blank if it does not apply, for example a VAT number before registration):

```bash
PLATFORM_COUNTRIES_UK_LEGALENTITY_NAME=Neuroway Ltd
PLATFORM_COUNTRIES_UK_LEGALENTITY_ADDRESS=1 High Street, Wokingham
PLATFORM_COUNTRIES_UK_LEGALENTITY_COMPANYNUMBER=01234567
PLATFORM_COUNTRIES_UK_LEGALENTITY_TAXNUMBER=GB123456789
PLATFORM_COUNTRIES_UK_LEGALENTITY_EMAIL=hello@example.org
PLATFORM_COUNTRIES_UK_LEGALENTITY_PHONE=0118 000 0000
```

The entity then shows in one line in the website footer, on payment pages, at the foot of the certificate PDF and of
every email. To try the production stack locally, a throwaway `deploy/country.env` with made-up values is enough.

To change a value for one deployment without a new build, add it to `deploy/country.env` (read by the API container
if present) as `PLATFORM_COUNTRIES_<REGION>_<NAME>`, with the name's dashes and dots removed, for example:

```bash
PLATFORM_COUNTRIES_IE_TAXPERCENT=23
PLATFORM_COUNTRIES_AU_LEGALENTITY_NAME=Example Australia Pty Ltd
PLATFORM_COUNTRIES_AU_YEARNAMES=Preschool/Kindergarten,Prep,Year 1,Year 2,Year 3
PLATFORM_COUNTRIES_IE_TERMLABEL=This term
```

then `docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env up -d api`. A value that cannot work
(an unknown currency or time zone, a month outside 1 to 12) stops the API at start-up with a message naming it. Keep a
deployment's region once schools are signed up: schools are stored with it.

### Launch prices

Each country's launch prices are in its `launch-prices` block of `application.yml`, in the currency's main unit and tax
included: `childcare`, `school` and `group` (the three yearly licences; a group pays 10% less than a school),
`practitioner` and `practitioner-renewal`, `parent-course` and `home-pass` (the old 12-month pass, no longer sold).
Override one before the first start with, for example, `PLATFORM_COUNTRIES_AU_LAUNCHPRICES_SCHOOL=1300`.

`home-monthly` is the home programme's monthly price: 14.99 in the UK, 16.99 in Ireland and 26.99 in Australia (the
owner, 2026-11-26; monthly only, with no yearly option). So the subscription is on sale from the first start. It is
applied only when there is no monthly price at all, so a price the team set, even one for a later date, is never
replaced. It is the one price that may be left blank (`PLATFORM_COUNTRIES_UK_LAUNCHPRICES_HOMEMONTHLY=` with nothing
after the equals sign): monthly is then **not on sale** until the team sets a price on Plans and prices. A deployment
that started before these prices were added and has no monthly price gets its country's on the next start.

They are applied on start, once: a licence plan still at its original set-up price gets the launch price from that
day (its history shows "launch prices"), the home pass gets its price if it has none, and the two starter courses are
created at theirs (`SEED_STARTER_CONTENT=true`). **After the first start, change prices on Plans and prices (and a
course's price on its page), not here:** a price the team has set is never overwritten, so editing `launch-prices`
later changes nothing for a plan, pass or course that already has its price.

**Override `group` whenever you override `school`.** Each price is taken as written: `group` is not worked out from
`school`, so `PLATFORM_COUNTRIES_AU_LAUNCHPRICES_SCHOOL=1300` on its own leaves a group school at 1,080. Set
`PLATFORM_COUNTRIES_AU_LAUNCHPRICES_GROUP=1170` (10% less) with it.

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

In the programme's Zoom account (a paid plan, since sessions are longer than 40 minutes), at
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

## Parent app builds

There is one parent app in the stores for all three countries. At first launch it asks "Where are you?", and that
choice picks the server. Set each country's address as a build variable, in `mobile/eas.json` or as EAS environment
variables, all three on every store build:

```bash
EXPO_PUBLIC_API_URL_UK=https://<UK address>
EXPO_PUBLIC_API_URL_IE=https://<Ireland address>
EXPO_PUBLIC_API_URL_AU=https://<Australia address>
```

These are read when the app is built, so changing one needs a new build. A country with no address is left off the
list. `EXPO_PUBLIC_API_URL` adds one "Development" choice: **leave it out of store builds.** The API on each server must
serve that address over https.

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
   `checkout.session.completed`, `checkout.session.async_payment_succeeded`, `refund.created`, `refund.updated`,
   `refund.failed`, and for the home programme's monthly subscriptions `invoice.paid`, `invoice.payment_failed`,
   `customer.subscription.updated` and `customer.subscription.deleted`. Copy its signing secret (`whsec_...`). An
   endpoint made earlier needs the missing events adding: without the four subscription events a family would pay
   and never be let in.
3. Put both in `deploy/.env` as `STRIPE_SECRET_KEY` and `STRIPE_WEBHOOK_SECRET` (the API refuses to start with only
   one), then restart the API. Pay a due payment with Stripe's test card 4242 4242 4242 4242 and check it shows paid.
4. To go live, repeat with the live keys and a live-mode webhook.

**Monthly subscriptions (the Programme at Home).** A family subscribes on Stripe's page and Stripe takes each month.
Each month arrives by the webhook (`invoice.paid`) and becomes a paid payment with its tax, and the days it covers.

1. Settings > Billing > **Customer portal**: switch it on, allow customers to **cancel subscriptions at the end of
   the billing period** (not immediately) and to **update payment methods**, and switch off changing plans or
   quantities. Add the platform's address as the default return link. Without a saved portal configuration, Manage
   fails with "not available just now".
2. Settings > Billing > Subscriptions and emails: choose how often a failed payment is retried (Smart Retries), and
   what happens when the retries run out: **cancel the subscription**. The platform tells the payer when a payment
   fails (once for each invoice); cover ends with the month already paid for, with no extra days.
3. Set the monthly price on Plans and prices (or `home-monthly`, above). A change applies to new subscriptions only.
4. Try it in test mode: subscribe with card 4242 4242 4242 4242 and check that the month shows on Payments with its
   tax and that sessions open; cancel under Manage; and subscribe with 4000 0000 0000 0341, which attaches but fails
   when charged, to see a failed payment. **Not yet tried with a real Stripe account.**

The team can cancel a family's subscription on Payments (under the family's subscription), with a reason. A refund of
a month does not cancel the subscription: cancel it as well if that is meant. In the API log, watch for "paid for a
second home subscription" and "which no longer exists": the platform has cancelled that subscription at Stripe, and
its month is for the team to refund.

For development and demos without Stripe keys, `STRIPE_FAKE=true` swaps Stripe for a stand-in on the API (no card, no
money): Subscribe and Pay by card "pay" at once, and Manage opens a plain page with what Stripe would do next (cancel,
the next month, a refused card). **Production refuses to start with it.**

**Refunds.** The team refunds on Payments (Refund, on a paid payment), in whole or in part, with a reason. A payment
taken by card goes back through Stripe; nothing is saved unless Stripe accepts it. Anything else is recorded, and the
team returns the money by hand. A refund made directly in the Stripe dashboard arrives by the webhook and is recorded
the same way, once. Each refund has a credit note, numbered with no gaps per deployment, emailed to the payer (a school's
heads). When a refund applies is the team's decision, under the programme's refund policy; the platform does not decide it.

Two cases to watch for in the API log: "Stripe could not return refund ..." (`refund.failed`: the refund stops counting,
and the money has to go back another way), and "more than is left to refund" (a dashboard refund on a payment already
refunded here by hand, so the money went back twice, and it is not recorded). Both are for the team to sort out in
Stripe. Refunds of tax are not reported anywhere: prices include tax, and each credit note shows the tax in it.

## File storage

Films, rhyme recordings and printables live in a private bucket, one per deployment. Without one the platform still
works: the team pastes links, as before. **This has not yet been tried on a real bucket**: do the checks below first.

1. In DigitalOcean, create a **Standard** Spaces bucket (not Cold Storage, which has no CORS) near the deployment's
   viewers: LON1 for the UK, AMS3 (Amsterdam) for Ireland, SYD1 for Australia. Keep file listing restricted. Files are private
   by default; do not make them public, or anyone with a link could open paid content.
2. Leave the bucket's CDN off. Signed requests are not cached there, may be charged twice, and an upload through it stops
   at 8100 KiB (Spaces limits); `ProductionChecks` refuses a CDN endpoint. If it is ever switched on, purge its cache after
   any CORS change, or browsers keep failing.
3. Add one CORS rule to the bucket, so browsers can upload to it: origin `https://<APP_DOMAIN>` (the full origin), method
   `PUT`, allowed header `content-type`, max age 5 seconds. Playing a file needs no rule.
4. Create an access key limited to this bucket, with permission to read, write **and delete** (the daily clean-up of
   unsaved uploads needs to delete), then set these in `deploy/.env` and restart `api` and `web`:

| Setting | Example |
|---|---|
| `STORAGE_ENDPOINT` | `https://lon1.digitaloceanspaces.com` |
| `STORAGE_REGION` | `lon1` |
| `STORAGE_BUCKET` | `motor-movers-uk` |
| `STORAGE_ACCESS_KEY`, `STORAGE_SECRET_KEY` | the key from step 4 |
| `STORAGE_ORIGIN` | `https://motor-movers-uk.lon1.digitaloceanspaces.com` |

The API needs all of the first five, or production refuses to start. Without `STORAGE_ORIGIN`, the content page's own
security policy blocks the upload and the form says the file could not be sent.

**First checks on a real bucket:** upload a small file and a film of a few hundred MB on Content approval; save the draft
(the API checks the file is there, with a signed HEAD); open it from the draft; download a printable, which must save
under its title; approve it, then play it as a teacher and drag the video's position bar (seeking); attach a film to a
rhyme (below) and play it in the class player and a home session; try a file that is too
large; open a link after `STORAGE_READ_HOURS`, which must fail; open the film in the parent app; and, for the clean-up, upload a
file without saving the form, set `STORAGE_UNSAVED_DAYS=1`, and check after two nights that the file has gone and the API's
log has no `Could not delete unsaved upload` line.

**Limits (DigitalOcean's docs, checked 2026-11-06).** One PUT can be up to 5 GB, and the platform sends each file as one
PUT, so a film stops at `STORAGE_MAX_UPLOAD_MB` (2048 by default, 5120 at most); a failed upload starts again. A new bucket
takes 800 operations a second (LON1 and SYD1 are not in the table of older limits); every seek is a request, and the docs
suggest the CDN above 400 reads a second, which signed links cannot use. Signed links last `STORAGE_READ_HOURS` (8, up to
168): a link that expires while a page is open stops the file until the page is reloaded. The docs state range requests
are supported on GET.

A signed link works for anyone who has it until it expires, so a shared link can be opened without a licence for those
hours.

**Unsaved uploads.** A file uploaded for a form that is never saved is deleted by a daily job at 02:45, once it is older than
`STORAGE_UNSAVED_DAYS` (7 by default, at least 1). The job only takes keys the API recorded when it handed them out, and never
lists the bucket. If a delete fails, it logs `Could not delete unsaved upload` with the key and tries again the next day. A
form left open past that time is told to upload the file again when it is saved. **Known limits:** a file replaced, or left
behind when a saved item is removed, stays in the bucket, as do files uploaded before this was added.

### Films for sessions

Open **Content approval** from the menu (a full page load, or the upload is blocked), stay on **Class sessions**, and
choose the colour under **Show colour**. There are two kinds of film:

- **A rhyme or module** (Red, Orange and Yellow are filmed a rhyme at a time). Choose the rhyme or module under **Show
  module**. In the card **Film for <colour> rhyme <n>**, upload the MP4 (or paste an `https://` link) and press **Attach
  the film**. Every session of that rhyme or module then plays it.
- **An exercise** (Green to Violet are filmed an exercise at a time). Open **Films for <colour> exercises**, then the
  exercise, and attach its film the same way. Each step of a session then plays the film of its own exercise, the plan
  moves on when a film ends, and the next one starts by itself. A step with no film of its own goes back to the
  module's film, from where it had got to, or to the timed plan.

Wait for the upload's progress bar to finish before attaching. **Open the film** checks it. **Remove the film** takes it
off again; attach another to replace it.

Films play in the class player for a school licensed for the colour, and in the home session for a family with a
running pass (or a parent whose child is linked to a school), on the website and in the parent app. Sessions added later
have them too, so reloading the session plans never drops a film. Where a step has no film of its own, the session's
own film (the session form's video field) plays, then its rhyme's or module's, then the timed plan. The line under a
film is "Film: " and the programme's name unless a credit line is typed for that film; to change it, remove the film and
attach it again. A removed or replaced film's file stays in the bucket.

**Film size.** The player loads only a film's details until someone presses play, then fetches it in ranges, so a film
is never downloaded whole and dragging the position bar works. A range can only play as fast as the connection: the
first recordings received are 1280x720 at 8 Mbps (200 to 440 MB for 3 to 7 minutes), which needs a steady 8 Mbps and
uses that much mobile data, and their index is at the end of the file, which costs one more request before playing.
**Before uploading for real, re-save each film at about 1.5 Mbps (720p, H.264, AAC) with the index at the front ("fast
start" or "web optimised"): about 12 MB a minute, so under 100 MB a film.** The platform does not convert films,
and does not embed or fetch films from video sites.

## Everyday operations

| Task | How |
|---|---|
| Logs | `docker compose -f deploy/docker-compose.prod.yml logs -f api` (JSON, one line per event; see Logs) |
| Health | `https://<APP_DOMAIN>/healthz` (web) and `docker compose ... ps` (all services) |
| Someone locked out | They use "Forgot password". Repeated failed sign-ins lock that email for 15 minutes |
| A member of staff leaves | Their head switches the account off in School setup. It stops working within a minute |
| Sign everyone out | Change `PLATFORM_JWT_SECRET` and restart the api |
| Certificates | Caddy renews automatically. Expiry warnings go to `ACME_EMAIL` |

## Logs

Read them with `docker compose -f deploy/docker-compose.prod.yml logs -f <service>`. The API writes JSON, one line per event,
and Caddy writes one JSON line per request. Each service keeps at most 50 MB (five files of 10 MB): the oldest goes first,
so copy out what you need soon after a problem.

**What is kept out, and how.** No personal data or tokens belong in a log.

| Where | What it does |
|---|---|
| Caddy (`deploy/Caddyfile`) | Drops query strings and all request and response headers, and cuts addresses to their network (/24 for IPv4, /32 for IPv6), in the access and error logs |
| nginx (`frontend/nginx.conf`) | Logs no requests (Caddy does) and only serious errors. A failed call to the API shows as a 502 in Caddy's log |
| PostgreSQL | Terse errors, with no values and no statement |
| API | Logs errors by type and place, never their messages (`SafeErrors`). A refused email logs the mail server's code, such as 550, and Tomcat logs no refused request's address |

To trace a person's problem, go by time and path: the logs will not find an email address. A stack trace from outside the
API's error handler (a scheduled job, say) can still hold an error's message, which is one reason for the shipper's own
redaction below.

**Sending logs to a service (optional, off by default).** The `logs` profile runs Grafana Alloy, which sends the API's and
Caddy's logs to any Loki-compatible endpoint, labelled with the country and service. It drops health checks, and removes
email addresses, tokens and bearer values on the way out as a second safety net. **No service has been chosen, so this has
only been tried against a local Loki.** Without the profile the service is not started and nothing leaves the server.

1. Set `LOG_SHIPPER_URL` in `deploy/.env` (the push address, ending `/loki/api/v1/push`), and `LOG_SHIPPER_USERNAME` and
   `LOG_SHIPPER_PASSWORD` if it needs a sign-in. Keep logs in the deployment's own data region, or check the service's
   terms and the privacy notice first.
2. Start it with `docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env --profile logs up -d`, and add
   `--profile logs` to later updates, or it stops being updated.
3. Check `docker compose ... logs logs` for send errors, then find the lines at the service.

The `logs` service can read Docker's socket, which is how it finds the containers. Read-only mounts do not stop a process
using that socket from controlling Docker, so run only this pinned image with it.

## Limits to know

- Rate limits are counted in each API's memory. Fine for one API container; move them to a shared store
  (e.g. Redis) before running more than one.
- Children's records are deleted 365 days after their leaving date (nightly job at 02:30).
