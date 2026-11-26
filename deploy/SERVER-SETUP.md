# Server setup checklist (go-live task 9)

From `deploy/RUNBOOK.md` and `deploy/.env.example`. Do it once for a **UK staging** server first, then repeat for
production in each country. Never paste keys or passwords into chat or into this file: they go only in
`deploy/.env` on the server, and in your password manager.

## 1. Decide first

- [ ] **Domain names**, e.g. `app.<domain>.co.uk`, `app.<domain>.ie`, `app.<domain>.com.au`, plus a `staging.` name.
- [ ] **Email provider** for outgoing mail (Postmark, Amazon SES, Mailgun or Microsoft 365), sending from your domain.
- [ ] **Owner of the DigitalOcean account**: the company's, not a personal one.
- [ ] **Legal entity details.** Production will not start while any of them reads "To be confirmed". UK and Ireland
      wait on task 2; Australia is International Healthcare Australia Pty Ltd (ACN and ABN to confirm). Staging can use
      made-up details, since only the team sees it.

## 2. DigitalOcean account

- [ ] Turn on two-factor sign-in and add a billing card.
- [ ] Create one project per country: UK, IE, AU.
- [ ] Add your SSH key (Settings, Security).

## 3. A server per country

| | UK | Ireland | Australia |
|---|---|---|---|
| Data centre | LON1 | AMS3 (Amsterdam, EU) | SYD1 |
| Server | Basic, 2 vCPU / 4 GB, Ubuntu 24.04 LTS | same | same |

- [ ] Create the server (Droplet) with your SSH key, and turn on DigitalOcean's weekly backups.
- [ ] Give it a **reserved IP**, so its address never changes.
- [ ] Add a **firewall**: ports 80 and 443 open to everyone, 22 (SSH) only from your own IP address, everything else
      closed.

## 4. DNS (each country's domain)

- [ ] An **A record** for `app.<domain>` (or `staging.<domain>`) pointing at the reserved IP.
- [ ] Wait until it resolves (`nslookup app.<domain>`). The HTTPS certificate is only issued once it does.

## 5. File storage: two Spaces buckets per country, in the same data centre

- [ ] **Files bucket** (films and printables): type **Standard**, private, file listing restricted, CDN **off**.
      Add one CORS rule: origin `https://app.<domain>`, method `PUT`, allowed header `content-type`, max age 5 seconds.
- [ ] **Backups bucket**: private, with a lifecycle rule deleting files after 90 days.
- [ ] **One access key per bucket.** The files key needs read, write **and delete**. Keep them in your password manager.

## 6. Email (task 15)

- [ ] Add your domain at the email provider.
- [ ] Add the **SPF, DKIM and DMARC** records it gives you to DNS, and wait until the provider shows it as verified.
- [ ] Note its SMTP host, port 587, user name and password.

## 7. On the server

- [ ] Install Docker Engine with the compose plugin.
- [ ] Get the code and the settings file:
  ```bash
  git clone https://github.com/ersagarnikam/MoterMoversPlatform.git motor-movers && cd motor-movers
  cp deploy/.env.example deploy/.env && chmod 600 deploy/.env
  ```
- [ ] Fill in `deploy/.env`:
  - `APP_DOMAIN`, `ACME_EMAIL`, `PLATFORM_REGION` (`UK`, `IE` or `AU`)
  - `DATABASE_PASSWORD` (`openssl rand -hex 24`), `PLATFORM_JWT_SECRET` (`openssl rand -hex 32`)
  - `PLATFORM_ADMIN_EMAIL` (the first team account), `PLATFORM_ENQUIRY_EMAIL` (the team's address, task 62)
  - `SPRING_MAIL_*` and `PLATFORM_MAIL_FROM`
  - the six `STORAGE_*` settings (endpoint, region, bucket, the two keys, origin), and the `BACKUP_OFFSITE_*` settings
  - `SEED_STARTER_CONTENT=true` (first start only)
  - leave `HOME_PROGRAMME=false`, and Stripe, Zoom and push blank for now
- [ ] Create `deploy/country.env` with the legal entity (`PLATFORM_COUNTRIES_<REGION>_LEGALENTITY_*`; RUNBOOK,
      Country settings).
- [ ] Start everything:
  ```bash
  docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env --profile offsite up -d --build
  ```

## 8. Check it works

- [ ] `docker compose -f deploy/docker-compose.prod.yml ps`: every service healthy.
- [ ] `https://app.<domain>` opens with a valid padlock.
- [ ] The admin email arrives, not in spam. Set a password and sign in.
- [ ] `docker compose -f deploy/docker-compose.prod.yml logs offsite` says "offsite copy ok".
- [ ] Upload a small file on Content approval, open it, and download a printable (RUNBOOK, File storage, first checks).
- [ ] The day after the first nightly backup: `deploy/restore-test.sh` ends with "Restore test passed."

If the API will not start, its log names the missing setting:
`docker compose -f deploy/docker-compose.prod.yml logs api`.

## 9. Later (not needed for staging)

- Stripe live keys and the four webhook events (tasks 31, 64; RUNBOOK, Card payments)
- Zoom production keys (task 32)
- Phone notifications (task 65)
- Monitoring and alerts (task 43)
- `HOME_PROGRAMME=true` once the parent induction and the families' privacy notice are ready

**Rough cost:** about $24 a month per server, plus about 20% for backups and $5 a month per country for Spaces: about
$90 to $100 a month for three countries, before staging. Check DigitalOcean's current prices when signing up.
