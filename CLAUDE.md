# Motor Movers Platform: notes for Claude

Read this first. It records where the project stands so a new session can carry on. `README.md` has the
full feature description and demo accounts; `deploy/RUNBOOK.md` has production operations.

**A team works on this project, and this file is how everyone knows its current state.** Update it in every pull
request, not only when a feature lands: the **Current state** (with the date), **Next steps**, test counts, the latest
migration, the recent commits line, and an **In progress** note while work is under way (who, branch, pull request,
what is done and left; remove it once merged). Start from the latest `main`, and keep other people's notes.
(added by Dev, 2026-11-05) Several agents write here; the Developer Agent marks what it adds with "(added by Dev, date)".

## What this is

The platform for the Motor Movers programme (motor-skills programme for primary schools): teachers run a
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
- **Children are identified by initials** unless a school switches full names on. While it is off, the API
  refuses new full names and never sends saved ones (they are kept, hidden, until it is on again). Leavers are deleted
  365 days after leaving (`RetentionJob`).
- **Time comes from the `Clock` bean** (`common/config/ClockConfig`), never `LocalDate.now()` without it.
- **Errors** are problem details with user-safe messages (`common/exception/ApiExceptionHandler`).
- **Schema changes** are new Flyway migrations (`backend/src/main/resources/db/migration`, latest `V20`, added by Dev 2026-11-06);
  never edit an applied one. JPA runs with `ddl-auto: validate`.
- **Email** goes through `common/config/Mailer`; with no SMTP configured it logs the message (dev).
- **Web CSP is strict** (no inline scripts or styles, no third-party hosts). `vite preview` sends the
  same policy as nginx, so the browser tests catch violations. Fonts are self-hosted (`@fontsource`).
- Commit style: short imperative subject, explanatory body, one logical change per commit.

## Build and test

```bash
cd backend  && ./mvnw -B verify      # (added by Dev, 2026-11-06) 189 unit + API integration tests (H2)
cd frontend && npm test             # (added by Dev, 2026-11-06) 59 unit tests (Vitest): money, times, offline queue, API client, assessment points, home ages
cd frontend && npm run build        # typecheck + production build
cd mobile   && npx tsc --noEmit     # parent app typecheck
# Browser tests: start the API with demo data and LOGIN_LIMIT_PER_IP=500 first (the tests sign in more than
# the 50 times in 15 minutes one address is allowed): cd backend && LOGIN_LIMIT_PER_IP=500 ./mvnw spring-boot:run
cd frontend && npm run e2e          # (added by Dev, 2026-11-06) 68 Playwright tests; locally add PW_CHANNEL=chrome
```

The browser tests change demo data: **restart the API between e2e runs**, or tests fail on duplicates.
The mobile lock file is written with npm 11 (Node 24); npm 10 rejects it, so CI's mobile job uses Node 24.
CI (`.github/workflows/ci.yml`) runs backend, frontend (unit tests and build), e2e, loadtest and mobile jobs on every
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
  issues a local certificate) and any `SPRING_MAIL_HOST`, then
  `docker compose -p mm-localtest -f deploy/docker-compose.prod.yml --env-file <file> up -d --build`;
  tear down with `down -v`.

## Go-live plan (set 2026-11-05, with the owner)

**Dates.** Dev and production ready in the UK, Ireland and Australia by **Thu 31 Dec 2026**. Go/no-go UK and Ireland Wed
6 Jan 2027; **UK and Ireland open for sign-ups and sales Mon 11 Jan**. Go/no-go Australia Thu 14 Jan; **Australia live Mon
18 Jan**. First training cohorts 25 Jan. **Release 2 by 26 Feb.** Anything that needs Pat, Kay or pilot teachers is due by
Fri 18 Dec (they are away over Christmas); 21-31 Dec is production readiness for the build team only. School calendars:
UK (Wokingham) back 4 Jan, Ireland 6 Jan, Queensland school year starts 27 Jan.

**Release 1 (ready 31 Dec).** Schools: licences signed up by the team, session player, both of Pat's assessments for all
seven colours, head and trust dashboards, induction, resources. Live practitioner and parent courses with card payment.
Motor Movers at Home sold as a 12-month pass (card payment that already works), gated by payment and Pat's parent
induction; `HOME_PROGRAMME` on in production. Parent app in both stores, asking for the country at first launch. Text
session plans, plus the Green and Orange films if Pat agrees. **Release 2 (26 Feb):** SENCo interventions, inspection
evidence pack PDF, self-service school sign-up, free introductory course, calendar invites, session recordings, monthly
billing, filmed sessions. Out of both: movement analysis from video and at-risk prediction (medical-device rules).

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
(https://ihau-company.monday.com/boards/5032300138): 53 tasks with Status, Due, Owner, Area and Task #, in groups
"Decide and build: 9 Nov - 11 Dec" and "Test, prod-ready and launch: 11 Dec - 26 Feb". When a task here is done, set it
to Done there. Plan doc: https://claude.ai/artifact/6dYqws3w9cdpzhaARayV3d; requirements review (45 requirements against
the platform, conflicts between the business documents): https://claude.ai/artifact/1WPp7mq67e8dqkCiBCN5Wq; designed PDF:
`F:\IHA\Pat\Motor Movers go-live plan - January 2027.pdf`. A scheduled check (weekdays 7:38 and 17:38 Brisbane) reads the
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

## Current state (2026-11-06)

(added by Dev, 2026-11-06) **In progress (Developer Agent, branch `tester-findings`, pull request #39): go-live task 72.** Done:
the Tester's findings 1 to 6 and 8 to 11, with tests. Left: the Project manager merges once CI is green. Findings 7
("This half-term", for Pat) and 12 (what the payer is told about a failed refund) are held. Remove this note once merged.

(added by Dev, 2026-11-06) **The Tester's findings on country and refunds (go-live task 72).** Two more country settings,
both in `GET /api/settings`: `time-label` ("UK time", "Irish time", "Queensland time"; required), used by session reminders,
phone notices and the cohort form, and `data-held-in` ("the UK", "the EU", "Australia"), which the programme page states.
**Check `data-held-in` against the server chosen** (RUNBOOK); left blank, the line is left off. `creditNoteTitle` is in the
settings too, so the refund form and the parent app call it an adjustment note in Australia. The head's assessment card
names a calendar school year as one year (`schoolYearLabel`, `src/country.ts`). The price chart's note no longer says UK;
task 28 decides any VAT wording. Certificate emails write dates in words. Today in the parent app counts what is waiting by
name ("1 session and 2 ratings are saved…"), both queues from the first load. A refund's `clientId` already used on
another payment is refused with a 400; on the same payment it stays a harmless repeat.

(added by Dev, 2026-11-06) **Refunds and credit notes (go-live task 60, migration V20).** A paid payment stays PAID;
`payment_refund` rows say what went back (part refunds allowed, never more than was paid). The team refunds on
`/admin/payments` (`POST /api/admin/payments/{id}/refund`, `RefundService`, `AccessPolicy.requireAdmin`, `RefundForm`): a card
payment with a payment intent goes through Stripe (`StripeClient.createRefund`, with an idempotency key, saving nothing
unless Stripe accepts); anything else is recorded for the money to go back by hand. The webhook also takes
`refund.created`, `refund.updated` and `refund.failed` (checked against Stripe's documentation, 2026-11-06), recording a
dashboard refund once (unique on `stripe_refund_id`); a failed refund stops counting. Every route locks the payment row, so
a webhook cannot overtake the team's own request. Views add `refundedPence`, `refundLabel` and `refunds`; all totals are net,
and in spend by month a refund counts in the month the money went back. Credit notes: gapless numbers from
`credit_note_counter` ("CN-000012"), `CreditNotePdf` with the legal entity, the original payment and the tax in the refund
(added by Dev, 2026-11-06: tax follows the running total of what has been refunded, so parts add up and none is negative), emailed to the payer or the school's heads, and
downloadable on Payments. The title is `credit-note-title` in the country settings ("Credit note", "Adjustment note" in
Australia): **the wording is for the accountant to confirm (task 17).** The parent app shows refunds and emails the credit
note on request, since it cannot save a file. When a refund applies is Kay's policy (task 20), not the platform's. Part C
missed one creator of payments: `TrainingService` now sets the tax rate on the school's invoice for a staff course place.

(added by Dev, 2026-11-06) **The parent app asks for the country (go-live task 27).** One store app, three servers.
`mobile/src/lib/country.tsx` holds the built-in list (United Kingdom, Ireland, Australia; addresses from
`EXPO_PUBLIC_API_URL_UK`, `_IE`, `_AU`, a country with none left off) and the saved choice. In development a missing
address falls back to the local API as before, and `EXPO_PUBLIC_API_URL` adds a "Development" choice. `CountryProvider`
wraps `AuthProvider` and shows `CountryChooser` ("Where are you?") until a country is chosen; the phone's country comes
first (`expo-localization`, new) but the parent is always asked. `API_URL` is now `apiUrl()`, read on every request. The
choice's storage key deliberately does not start with `mm.`, because signing out clears those. Account shows the country
and **Change country**: it sends anything unsent, refuses with "Waiting to send N items. Connect to the internet first."
if some remains, then signs out (which removes the push token from the old server and clears what is on the phone) and
asks again. Each country's currency and tax label show until `GET /api/settings` answers.

(added by Dev, 2026-11-06) **Parent induction gate (go-live task 56, migration V19).** Pat (2026-11-05): a short induction
like the staff one. `induction_lesson` gains `audience` (STAFF or PARENT), and `part` replaces `position` so that each
audience numbers its own parts (the column was replaced because the old unique constraint is named differently on H2
and PostgreSQL). Parents use `GET /api/home/induction` and `POST /api/home/induction/lessons/{id}/complete`, behind the
home programme setting; `InductionService` serves both audiences, and a parent's completion is the same
`app_user.induction_completed_on`, so it is permanent and lessons added later lock nobody out. The team writes and
approves lessons on the Content page's **Parent induction** tab (`/api/admin/parent-induction`), with the wording check.
`POST /api/home/sessions` is refused with a 400 until the parent has finished (`HomeService.requireReadyForSessions`, the
one place for this; **task 26 adds the pass check there**), and each carer does their own. Today gains `inductionDone`:
the website (`/home/induction`, `HomeInductionPage`) and the parent app (`home-induction`) show the induction in place of
Start. **The lessons are two placeholders** ("Placeholder lesson 1" and 2), marked on screen, approved in the demo and
drafts in the starter content; replace them when Pat's arrive. With no approved parent lessons, sessions stay closed and
the page says the induction is being prepared, so `HOME_PROGRAMME` must not be switched on before they are approved.
Demo parents (Priya, Jo) are past the gate; a family signing up in the demo sees it.

(added by Dev, 2026-11-06) **Legal entity (go-live task 25, part E).** `CountrySettings.LegalEntity` gains
`taxNumberLabel` ("VAT number", "ABN" in Australia), `isPlaceholder()` and `line()`, one line such as "Neuroway Ltd, 1 High
Street. Company number 123. VAT number GB456.", null while any value says "To be confirmed", so nothing untrue is
shown. `GET /api/settings` adds `legalLine`. It shows in the website footer (`SiteLayout`), on the My payments and school
Payments pages (`components/LegalLine`), on the parent app's Payments screen, at the foot of the certificate PDF, and at
the foot of every email (`Mailer`). `ProductionChecks` refuses to start with a placeholder; a value that does not apply
can be left blank. A bad country setting now stops start-up with one clear "APPLICATION FAILED TO START" message
(`CountrySettingsException`, `CountrySettingsFailureAnalyzer`, the tester's suggestion). The website's contact details and
credit wording stay with task 28.

(added by Dev, 2026-11-06) **School year by country (go-live task 25, part D).** `assessment/util/SchoolYear` (a bean from
the country settings) replaces `YearPoint.on` and `YearPoint.schoolYear`: `pointOn(date)` and `yearOf(date)`. The UK is as
before; Ireland's end point starts in April; Australia's school year is the calendar year, so the head's and trust
lead's "this school year" figures are never split. `YearPoint` keeps only its labels. `MilestoneAchievement` is given
its school year, and `AssessmentService`, `HomeService`, `HomeRules.checkDue` and the demo seeds use the bean (the
home check's start, middle and end follow the same rule). The school setup class form offers the country's year names
as suggestions (a datalist; free text is still allowed). `SchoolDays` stays weekdays only; term dates are not modelled.
Tests: `SchoolYearTest`, Australian cases in `HomeRulesTest`, and the bean in `RegionIrelandTest` and `RegionAustraliaTest`.

(added by Dev, 2026-11-06) **Tax on payments (go-live task 25, part C, migration V18).** Prices stay tax-inclusive (the
amount charged). `payment.tax_rate_bp` (basis points, nullable) is set from the country settings when a payment is
created (`PaymentService`, `CountrySettings.taxRateBp`) and stays with the payment; older rows stay null and show no
tax line. `Payment.taxPence` is amount x rate / (1 + rate), rounded half up to the minor unit, and `taxPence` is on the
payment views. Web (`taxLine` in `src/money.ts`, on `PaymentTable` and My payments) and parent app (`taxLine` in
`lib/api.ts`, on Payments) show "includes VAT £75" or "includes GST $4.09". No Stripe Tax. Tests: `PaymentTaxTest`.

(added by Dev, 2026-11-06) **Money by country (go-live task 25, part B).** Amounts are held in the currency's minor
unit (pence or cents; every `*Pence` field and column keeps its name) and shown in the deployment's currency from its
country settings. Web: `money(minor)` and `currencySymbol()` in `src/money.ts` replace `gbp()`; `App` calls
`setCurrency` once `useCountry` has the settings (pounds until then), and the money fields on Plans & prices, Schools
& licences, Payments and Courses are labelled with the symbol ("Price (€)"). Parent app: `money()` and `setCurrency` in
`lib/api.ts` (a symbol per currency, no `Intl`), set from `GET /api/settings` in the root layout. Backend:
`common/util/Money` (one formatter from the settings) for the payment-due notification and the certificate renewal
email, in place of `PaymentViews.pounds`; `StripeApiClient` sends the settings' currency (`gbp`, `eur` or `aud`).
Tests: `MoneyTest`, the Stripe request in GBP, EUR and AUD (`OnlinePaymentTest`), and web `money()` in the three
currencies.

(added by Dev, 2026-11-06) **Country settings (go-live task 25, part A; pull request #29).** `PLATFORM_REGION` (`UK`, `IE` new, or `AU`)
picks a block of `platform.countries` in application.yml: country name, currency and locale, tax label and percent,
time zone, school year (first and last month, terms, the months the start, middle and end points begin) and year names,
and the legal entity (placeholders until Kay's decision 1). Ireland's and Australia's points and year names are to be
confirmed by Pat. `CountrySettings` (common/config, checked at start-up: a bad currency, zone or month stops the API),
`CountryProperties`, `CountryConfig`. Every value can be overridden by an environment variable,
`PLATFORM_COUNTRIES_<REGION>_<NAME>` (keys are lower case in the yml so the overrides match; in production through the
optional `deploy/country.env`, RUNBOOK, Country settings). `ClockConfig` takes the zone from it: Australia is now
Australia/Brisbane. `GET /api/settings` is open (like `/api/home/available`); the web has `useCountry` (`src/country.ts`),
not yet used by a screen. `school.region` is VARCHAR(10) with no check constraint, so IE needed no migration
(`RegionIrelandTest` saves an Irish school). Tests: `CountrySettingsTest` (per region, environment overrides, bad values),
`RegionIrelandTest` and `RegionAustraliaTest` (own contexts started with `PLATFORM_REGION`). Not yet: money, tax lines,
the school year by country and the legal entity on screens (parts B to E).

(added by Dev, 2026-11-06) **Licence rule (Go-Live task 14; merged 2026-11-06, pull request #27).** A school is licensed for a colour on a day when **any** of
its licences runs that day (`Licence.isValidOn`: from `startsOn` to `endsOn`, both included) and covers the colour:
`LicenceService.licensedFor` / `licensedStages`, the one place for it. Today (`ClassroomService.today`) hides the
session without it, recording (`ClassroomService.complete`) refuses it with a 400 ("This class's colour is not covered
by your school's licence today. Please ask your head teacher."), and the resource library uses the same rule. Before,
only the licence with the latest end date was looked at and its start date was ignored, and recording did not check at
all. Screens that show a school's licence status (trust dashboard, Schools & licences, team overview) still show the
latest licence and `statusOn`, which ignores the start date (a licence starting next month shows Active): left as it
is, for the owner to decide. The web offline queue (`outbox.ts`, assessment scores and movements) now drops a record
the server refuses (400) with a toast saying why, sending the rest of the batch one at a time, instead of retrying it
for ever; daily sessions are not queued (the player shows the refusal as a toast).

**Testing (updated by tester, 2026-11-06).** A tester session checks the platform and records its results here,
each update marked "updated by tester". Latest: **pull request #29 (country settings, go-live task 25 part A) passes**
on `main` at 20db19c, tested at the Developer's request (the owner asked for it). Backend 159/159, web 54 unit tests
and build, 67/67 browser tests (UK). `GET /api/settings` (open) is right for UK, IE and AU; demo data, teacher
sign-in and today's session work in all three; overrides (`PLATFORM_COUNTRIES_IE_TAXPERCENT=13.5`, an AU legal
entity name) show; bad values (currency, time zone, locale, tax 100 or below 0, month 13, a start month that is not
the first month, region FR) stop start-up naming the setting. No bugs. Minor, sent to the Developer: a bad country
value is named only inside a long stack trace (no "APPLICATION FAILED TO START" summary); the Developer will add a
clean start-up message with part E. Start-up checks only the deployment's own country block, but `CountrySettingsTest`
validates all three, so a typo in any block fails CI. For Pat: Ireland's end point starts in April and runs past June,
the school year's last month.
How the tester runs things while the owner's platform uses 8080, 5173 and 8082: a worktree, the built jar on
`--server.port=8091` (wait for "Demo data loaded", which comes after "Started"), `API_URL=http://localhost:8091` for
the browser tests, and before 11am Brisbane time a local Playwright config with `timezoneId: 'Europe/London'`.
Earlier (2026-11-05, `main` at 077a5d5): the first baseline, all suites green. Not run yet: load tests, the
production compose stack, a real phone, and the exploratory pass role by role.

[PM: Claude, 2026-11-06] **Boards on monday.com** (account ihau-company, Main workspace). All open work is on the
go-live board "Motor Movers Go-Live (Dec 2026)", https://ihau-company.monday.com/boards/5032300138 (see Go-live plan,
Tracking). When an item here changes (done, answered, new), change it on that board too. The old team board "Motor
Movers Platform" (5032277063, 2026-11-04) was merged into it and archived on 2026-11-06. Two more boards: "Motor Movers" (5032278115, the 30 flows of `docs/flows/` by role) and "User Journeys"
(5032278564, one item per customer from Interested to Active, with a draft workflow "User journey" that moves the
item, sets the next step and notifies its owner; to review and publish in monday.com).

(added by Dev, 2026-11-05) **Pat's answers of 2026-11-05 (her reply to the go-live plan, file "Revised 2"; merged 2026-11-05,
pull request #25).** Go-Live
board tasks 54 and 55:
- **54, assessment content.** Green 4 Motor Spin, Yellow 11 Jack and Jill and 13 Twinkle Twinkle, and Orange 1 Rock a
  Bye Baby have her own descriptions; Indigo 14 Puppy Fun has instructions and descriptions; Blue 5 Thumbs Up uses
  Green's version with Blue's points; "Final end of Green" in Blue is the end of Blue. `AssessmentContent` now brings
  an exercise's title, instructions and descriptions up to date from the files on every start (never its points), so
  an existing deployment takes revised wording. Still open: See Saw Marjory Daw and Motor Lift Four keep copied
  descriptions (`docs/assessment-design.md`).
- **55, home rules.** Confirmed as built: Red under 2, rhyme order = the grid, one rhyme a week, five weeks a module for
  Green to Indigo (Violet assumed the same). New: from Green to Indigo a home session ends with the module's cool-down
  (`assessment/cool-downs.json`, `home/service/CoolDowns`, `coolDown` on Today, the last step on web and app); a
  rhyme ends with its own, in its words. Only Green's Cool Down 1 has text so far, so it ends every Green session.
- **56, parent induction:** a short induction like the staff one; her lessons to follow. **57, films:** she agreed
  they can be used for launch; blocked until the files arrive (Stuck on the board).

**The 0 to 3 readiness check is retired (merged 2026-11-05, pull request #19; the owner's decision, migration V17).**
Pat's two assessments replace it, so it was not put to Pat again (her question about it is answered in the reply drafted for her). Gone: the
`screening` package and its endpoints (`/api/screening/*`, `/api/children/{id}/profile`), the teacher's Readiness check
page and its offline queue, the readiness card on the profile (now Pat's assessments and the parent code only), and
the six areas in the parent app. V17 drops `screening_result`, `screening_item_level` and `screening_item`.
**Placement:** a school child now works at the colour they are assessed at: the first exercise score places them,
a later colour moves them up, never down (`AssessmentService.submitScores`; home children still follow `HomeRules`).
"Children assessed" (head), "Assessed" (trust or group) and `children_assessed` (governor CSV) count children with an
exercise score or a secure movement. The starter induction lesson is now "Assessing the children" and the practitioner
course's second quiz asks about the two assessments. The public website describes the two assessments.
`deploy/loadtest/school-day.js` opens assessment profiles and saves exercise scores. Details:
`docs/assessment-design.md`, *Retiring the 0 to 3 check*. The guides (normal and prototype) were
rebuilt without it on 2026-11-05 (merged, pull request #21).

**Flow sequence diagrams (merged 2026-11-04, pull request #17, at the owner's request).** `docs/flows/`, 30 Mermaid sequence diagrams in one Markdown file
per role (Everyone, Class teacher, Head teacher and SENCo, Trust or group lead, Practitioner, Parent with a school
child, Parent on Motor Movers at Home, Trainer, Motor Movers team, Visitor and scheduled jobs) plus a `README.md`
index. Each flow names its endpoints, the services and outside systems involved (PostgreSQL, Zoom, Stripe, Mailer,
Expo push) and the rules the code enforces, traced from the controllers and services at commit 93da753. No code
changed. **Keep them current:** when a pull request changes an endpoint, an access rule or a step a diagram shows,
update that role's file in the same pull request. A browsable copy of the same diagrams is a private Claude artifact
the owner can share.

**Pat's answers (merged 2026-11-04, pull request #16, at the owner's request; email of 2026-11-03, forwarded to Rahul; saved as `F:\IHA\Pat\Fw_ Pat Preedy Prototype Response .eml`
and `.msg`, attachments in `F:\IHA\Pat\RE__Pat_Preedy_Prototype_Response_`).** She answered the sixteen questions:
- **Grids for all seven colours.** Red and Orange (18 rhymes each, start and end), Blue (40), Indigo (34) and Violet
  (24), modules 1-6, are now `exercises-<colour>.json` and `STAGES_WITH_EXERCISES` is every colour; an existing
  deployment loads them on its next start. Converted by reading the Word tables (checked against Green and Yellow
  first); the doubtful bits (Blue's Thumbs Up twice, Indigo's Puppy Fun without instructions, copied descriptions) are
  listed for Pat in `docs/assessment-design.md`. Her revised Motor Spin, Jack and Jill and Twinkle Twinkle file
  arrived **empty (0 bytes)** in every copy: ask her to send it again.
- **Agreed:** the flag rule, no date of birth or birth history, what parents see, parents rating, her descriptions as
  they are. She asked what "the old 0 to 3 readiness check" is: the owner retired it on 2026-11-04 without asking again.
- **Motor Movers at Home, changed to her answers** (`HomeRules`, `HomeViews`, web and app): the colour no longer
  follows the age; children start at Orange when young and at Green from school age (Red for under 2s, to confirm with
  her), and the check only records movements. Rhyme colours: one rhyme a week, five times. Green to Violet: each
  module builds up over five weeks (the exercises carried on from the module before and two new ones in week 1, then
  one or two more a week; `HomeRules.buildUp`). The check is suggested again at the start, middle and end of each
  school year (it was every 91 days). Her gentle prompt (nursery or health visitor) and her safety note (shown before
  the first session) word for word. Today gains `rhymes`, `week`, `weeks`, `progressLabel`, `sessionTitle`,
  `newThisWeek` and `safetyNote`; the check result loses `forAge` and `earlier`.
- **Not built:** (added by Dev, 2026-11-06: the induction's mechanism is built, see Current state, its lessons are still to come) the induction training she wants home parents to do first (no content yet; ask her what it covers
  and whether the parent course counts), Cool Down 1 at the end of each session, and videos (Green and Orange are
  filmed on neuroway.co.uk). Open questions in `docs/home-programme-design.md` (Still to settle).
- **Browser tests and time zones:** the API's clock is UK time and the web sends the browser's date, so between
  midnight and about 11am in Brisbane a session saved from the website lands on "tomorrow" and the home browser test
  fails on "Done today". Run the browser tests later in the day, or with the browser on UK time (Playwright
  `timezoneId: 'Europe/London'`). Not a fault in the code for UK families.

**Shared with Pat on Google Drive (2026-11-03).** A **PAT** folder in the connected Drive (ihaustralia.info@gmail.com,
https://drive.google.com/drive/folders/1CUAMy7DXaweoWH8KsqjIJUhp5y2Mikdn) holds: **Logo**, four logo directions for a
possible new name, "Professor Pat Preedy Programme" (SVG; the designs are a Claude design canvas, not in the
repository; nothing in the platform is renamed); and two Google Docs rewritten for Pat from `docs/assessment-design.md`
and `docs/home-programme-design.md` (prototype wording, no prices). The prototype guide PDFs go in by hand (too large
for the Drive connection). Kay's media one-pager (Kay Cogan, kaycogan@ihau.au) is a Claude design canvas too.

**Price chart and enquiry form on the website (merged 2026-11-03, pull request #13, at the owner's request).** "Ask for the price guide" on `/for-schools` was a `mailto:` link, which does
nothing on a computer with no email program (the owner's). It now opens, in place, the **price chart** (the active
licence plans at today's price from Plans & prices, `GET /api/plans`, open without signing in; the free trial shown as
Free, a zero-price plan that is not the trial, such as "Free", left out) and an **enquiry form** (name, email, setting,
phone, classes, stages, the plan chosen on the chart, message). "Apply online" opens the same form for applying; the
email links stay as a second way. The API (`enquiry` package, `POST /api/enquiries`, open, 5 an hour per address, a
hidden field that bots fill in) emails the enquiry to `platform.enquiry-email` (`PLATFORM_ENQUIRY_EMAIL`). Nothing is
stored and the sender is not emailed. **For now the default is the owner's address (sagarnikamforu@gmail.com), so no
test enquiry reaches Pat** (asked 2026-11-03); set it to the team's address for go-live. Tests: `EnquiryTest`, and the
price guide browser test in `e2e/site.spec.ts`. Before go-live: the owner confirms the prices, and whether they can be
public, with **Kay** (prices go to Kay, not Pat), and sets `PLATFORM_ENQUIRY_EMAIL`.

**Questions sent to Pat (2026-11-03, by the owner, by email).** Sixteen numbered questions, each with the approach
considered in the design (the platform is a prototype, so the email speaks of the design, not of what is built):
A. assessments in school: 1 the other five colours' grids; 2 the gaps in the grids (Motor Spin's descriptions; Jack
and Jill and Twinkle Twinkle repeat another rhyme's); 3 the flag rule ("a pattern of low scores"); 4 no date of birth
or birth history held; 5 what parents see; 6 retiring the 0-3 check and updating the induction lesson. B. Motor
Movers at Home: 7 her materials at home; 8 colour by age; 9 the starting check's rule; 10 parent words for the levels;
11 parents rating; 12 the gentle prompt; 13 session shape; 14 recheck every three months; 15 a safety note; 16 videos.
(The "X/108" totals question was dropped.) She replied the same day (above).

**Merged 2026-10-28 (pull requests #8 to #11, stacked):** emailing a family code (#8), school assessments in the
parent app (#9), Motor Movers at Home on the website (#10) and the rebuilt guides (#11). #10 and #11 were merged into
their stacked base branches rather than `main`, so they reached `main` through a follow-up pull request
(`finish-home-web`, 2026-11-02). With stacked pull requests, merge one, wait for GitHub to move the next one's base
to `main`, then merge it; or open each from `main`.

**Motor Movers at Home (merged 2026-10-27, pull request #7; switched off in production).** The parent app
for families whose child's school is not on Motor Movers. Behind `HOME_PROGRAMME` (on in development and
the demo, off in production until the owner and Pat agree; it needs its own privacy notice first). Design note with
ten questions for Pat: `docs/home-programme-design.md` (also shared with the owner as a Claude doc).
- Data (migration V16): a child belongs to a school class **or** a family (`child.family_id`, `school_class_id` now
  nullable, a check constraint), with `age_months` on `age_recorded_on` instead of a date of birth and
  `home_check_on`. `family`, `family_member` (OWNER or CARER; one family per account), `family_invite` (one-time
  codes, 14 days), `home_programme_session`. Code that reads `child.getSchoolClass()` must allow null: use
  `child.isAtHome()`.
- Access: `AccessPolicy.requireFamilyOf` (the family's parents and carers only); `requireRecordAccess` picks family
  or class access, and the assessment endpoints use it, so a teacher, head, trust lead or the team gets 403 on a home
  child. `requireClassAccess(null)` refuses. Home children are not in a school's record access log.
- API `/api/home` (PARENT; `HomeService`, `HomeController`): the family, children (add, edit, delete with everything
  recorded), the starting check (movements in the year up to the child's age; each Yes kept as a
  `milestone_achievement` with the age), the colour, today's session, sessions (offline-safe), invites, joining,
  removing or leaving (the last member leaving deletes the family), and `join-school` (the school's parent code; the
  home record moves onto the school's record, the school's own rows winning). Ratings use
  `/api/assessment/exercise-scores`. An invite can also email the code (`POST /api/home/invites` with `email`; the
  email names the sender, not the children; 20 a day per account). Rules from Pat's answers of 2026-11-03 in `home/util/HomeRules.java` (the
  starting colour, the check, one rhyme a week, modules built up over five weeks of five sessions, the check again
  each part of the school year); parent words, the gentle prompt and the safety note in `home/dto/HomeViews.java`.
- Parent app: Today (a home child's session, or the starting check; a colour without Pat's grid would fall back to the
  home activities, but since 2026-11-04 every colour has one), `add-child`, `check/[id]`, `home-session/[id]` (then first-time ratings), `review/[id]` (end of module),
  `home-child/[id]` (progress, check again, name and age, link a school, delete), `join-school/[id]`, `family`
  (invite by code or email, join, remove). Sessions and ratings queue offline (`lib/homeOutbox.ts`).
- Website (2026-10-28, `frontend/src/pages/home`): the same for families without the app. `/join` creates a parent
  account (`RegisterPage`; signed in, `/join` goes on to `/home`, which falls back to the parent course while the
  programme is off); **At home** (`/home`, the menu item shown only while it is on) lists the children and adds one;
  `/home/child/:id` has today's session (or the colour's home activities), progress and settings (name and age,
  colour, link a school, delete); `/check`, `/session` (then first-time ratings) and `/review` under it;
  `/home/family` for sharing. `GET /api/home/available` is the one home endpoint open without signing in (the
  website asks it to decide what to show; `useHomeAvailable` in `src/homeProgramme.ts`). No offline queue on the web.
- Demo: home@example.test (Priya Shah): Ava (50 months, Green, week 1 of module 2, module 1 review due) and Leo (20
  months, no check yet; he starts at Red).
- Tests: `HomeProgrammeTest` (own context with a raised sign-in limit, so it does not use up the shared one),
  `HomeRulesTest`, `HomeProgrammeOffTest`. Tried in the app's web preview: session, ratings, check, fallback, family.

**Pat's two assessments (merged 2026-10-27, pull request #4, migration V15).** Built from Pat's documents of 2026-10-27 (`docs/assessment-design.md`). Package `assessment`:
- Content: `backend/src/main/resources/assessment/` holds the booklet's fine (59) and gross (74) motor charts and the
  grids of all seven colours as JSON: Green (27 exercises), Blue (40), Indigo (34) and Violet (24) with modules 1-6,
  and Red, Orange and Yellow (18 rhymes each, start and end), made from Pat's Word files (obvious typos corrected,
  repeated chart rows dropped; the other five added 2026-11-04). `AssessmentContent` loads them on start-up in every
  deployment, adding only what is missing, so another colour's grid is a new `exercises-<colour>.json` plus its
  colour in `STAGES_WITH_EXERCISES`. A test checks all of it against the wording guard.
- Exercises: the teacher's **Assess** page (`/class/:id/assess`, from "Assess children") scores the whole class on one
  exercise at one point (Start, End of module n, End of the colour), 1 to 4 with Pat's four descriptions, through the
  offline queue (`outbox.ts`, now generic). A later score at the same point replaces the earlier one. The suggested
  point (`suggestedPoint` in `src/assessment.ts`) stays on a point until every child has a score there, unless half
  the class or more already has a score at a later point (so one absent child does not hold the class back).
- Developmental: `/child/:id/development`. The teacher enters the child's age in months (no date of birth is held)
  and the point in the school year (Sept-Dec start, Jan-Apr middle, May-Aug end, suggested from the date), taps each
  movement that is secure, and writes each chart's comment. Once secure a movement stays secure; one recorded by
  mistake can be taken off.
- Profile (`/child/:id`): both assessments first (movements secure, Pat's table of scores per point, start and end
  totals, the summary comments on personal, social and emotional development and on academic achievement), then the
  parent code. (The old 0-3 readiness check was retired on 2026-11-04.)
- Flag: `ExtraSupportRule` (agreed by Pat 2026-11-03): 1 or 2 on the same exercise at two
  points in a row, or 1 or 2 on half or more of the exercises (at least two) at a module end or the colour end. It
  shows as "May benefit from extra support" with its reasons, never a cause. Up to three exercises low at two points
  in a row are named one by one; more read as one line ("1 or 2 at two points in a row on 21 exercises, including
  …"); the profile's table has every score.
- Head: an Assessments section on the Progress page. Per class: children assessed at each point this school year,
  the average movements secured this year per child assessed, and how many **children** are at each exercise level
  (each child counted once, at the middle of their latest scores; `ClassOverview.childrenAtLevel`), then the list of
  flagged children (first reason, and how many more are on the profile). Each class shows its colour stage as a pill
  washed in the stage's own colour, with the row edged in it (`StageBadge`, `.stage-tint-*` in styles.css). Trust or
  group leads see per school only the share developmentally assessed this year and how many are flagged.
- Demo: Ms Hardy's Year 2 R (Yellow) has start scores and start-of-year charts; Year 3 K (Green, no teacher) is four
  modules in, with two children flagged. The last three children of each class are left to assess.
- Not built: editing the charts or grids on screen (change the JSON).

`main` is GitHub's default branch (since 2026-10-19). New work goes on short branches. Since 2026-10-26 `main` only accepts changes
through a **pull request** (a GitHub rule): push the branch and open a pull request with a detailed description. **Never merge it: the owner
merges** (asked 2026-10-26), unless the owner asks for a particular one to be merged (as with pull requests #4 to #7,
2026-10-27). The
GitHub CLI is installed and signed in (`C:\Program Files\GitHub CLI\gh.exe`, account ersagarnikam): open pull
requests with `gh pr create` and check CI with `gh pr checks`. GitHub also has `rahul-work`, a teammate's branch: leave it alone. One old stash remains
("zoom video size tweak (unverified)"), superseded by the merged fix; drop it if the owner agrees.
GitHub CI is green on every job (backend, frontend, e2e, loadtest, mobile). Everything below is built and passing: backend tests, web build, mobile typecheck,
67/67 browser tests, and the production compose stack run locally (HTTPS, headers, migrations,
rate limits, backup dump all checked).

**Training team (2026-10-24).** Pat has a team of trainers. `Role.TRAINER` ("Trainer") sees only Live sessions, and
runs and keeps the register of the cohorts assigned to them (`cohort.trainer_id`, migration V10); the team (ADMIN)
can run any session and reassign cohorts. Each trainer or team member can have their own Zoom login
(`app_user.zoom_email`), so sessions can run at the same time; without one, `ZOOM_HOST_EMAIL` hosts. The team adds
trainers (emailed an invite) and sets Zoom logins in the Trainers section of `/admin/courses`
(`AdminTrainerController`, which also switches a trainer's account off), and the cohort form picks a trainer. The
Courses page warns when a cohort's sessions overlap another cohort's on the same Zoom login (`AdminCohortView.clashes`;
a warning, not a refusal). Trainers also have **My cohorts** (`/trainer/cohorts`, `GET /api/trainer/cohorts`): the
bookings, attendance and the end-of-course assessment of their own cohorts (`LiveSessionService.canHost` decides).
Demo trainer: trainer@motormovers.test (Amy Clarke, Cohort B · UK morning). Each Zoom login used as a host needs its
own licensed Zoom user on the owner's account.

**Web app usability (2026-10-26).** Colour stages always show through `StageBadge` (web: a pill washed in the stage's
colour, `.stage-tint-*` classes; parent app: `StageBadge` in `components/ui.tsx`, the same look), never as plain text
(2026-10-27). Dates through `day()` in `src/dates.ts` ("Thu 5 Nov 2026"); after an action, a
toast via `useToast()` and confirmations via `useConfirm()` (`components/Feedback.tsx`; never `window.confirm`);
`components/Skeleton` while loading; `components/ListFilter` for searchable lists. The team's home is `/admin`
(`TeamOverviewService`, `GET /api/admin/overview`); courses list at `/admin/courses` with a page per course
(`/admin/courses/:id`) and trainers at `/admin/trainers`. Tables of people turn into cards under 760px
(`table.cards` with `data-label` cells). Browser tests click menu links with `exact: true` (Home cards repeat their
names).

**Card payments (2026-10-26, migration V14).** With `STRIPE_SECRET_KEY` and `STRIPE_WEBHOOK_SECRET` set (both or neither;
`ProductionChecks` refuses one), payers see **Pay by card** on what is due: parents and practitioners on `/payments`
and in the app, heads on the school's Payments page. It opens a Stripe Checkout page (`payment/stripe`,
`OnlinePaymentService.checkout`); the card never touches the platform. Stripe's signed webhook
(`POST /api/stripe/webhook`, open to Stripe only by its HMAC signature, 5-minute tolerance) marks the payment paid
with `payment.paid_online_ref`; returning to the platform proves nothing on its own. Not yet tried with a real Stripe
account (RUNBOOK, Card payments). (added by Dev, 2026-11-06) Refunds are on the platform: see Refunds and credit notes in Current state.

**Phone notifications (2026-10-26, migration V13).** The parent app registers the phone after sign-in (`src/lib/push.ts`,
`expo-notifications`; `POST /api/me/push-tokens`, removed on sign-out) and opens the screen a notification names
(`data.url`). The API sends through Expo's push service (`notification` package: `PushService`, `ExpoPushSender`)
when `PUSH_ENABLED=true`; otherwise it only logs. Sent: a live session open to join (`SessionReminderJob.announceOpening`,
every 2 minutes, once per session, `cohort_session.opening_push_sent_at`), the day-before reminder, and a payment
recorded as due for a person. Tokens Expo reports as gone are deleted. On the web the app skips notifications (the calls throw there). Needs an EAS project id in `app.json` and a
store or development build to work (RUNBOOK, Phone notifications); not yet tried on a phone.

**Quizzes and certificates (2026-10-26, migration V12).** Short quizzes between a course's live sessions
(`course_quiz`, `quiz_question`, `quiz_attempt`; `QuizService`): the team writes them on the Courses page (Quizzes on
each course; the wording check runs on save; a quiz learners have taken cannot be deleted). A quiz opens once the
session it follows has started or the learner is marked present at it; learners take it at
`/training/quiz/:enrolmentId/:quizId`, as often as they like; the server marks it and names the questions to look at
again, never sending the answers. With quizzes on a course, all must be passed for the certificate
(`CertificateService.issueIfComplete` is the one place certificates are issued). On issue the learner is emailed with
the PDF attached (`CertificatePdf`, Apache PDFBox; also `GET /api/training/enrolments/{id}/certificate.pdf` and
Download PDF on the certificate page), and a month before expiry they are reminded once (daily at 08:00,
`enrolment.expiry_reminder_sent_on`). The starter practitioner course has three starter quizzes (80% to pass) for
Pat to check and replace. Renewing a certificate online is not built: the reminder asks them to contact the team.

**Staff course places (2026-10-26, migration V11).** A school pays for its staff's places, so staff other than heads
**ask for a place** (`course_request`); the school's heads are emailed and approve or decline on the school's Payments
page. Approving books the place and adds the school's payment, linked to the place (`enrolment.payment_id`); a head
books their own place directly (and is invoiced). Places can be **cancelled** (`POST /api/training/enrolments/{id}/cancel`):
by the learner or their head until the first session starts, by the team any time before a certificate; a linked
invoice still due is cancelled, a paid one is left for the team to refund by hand. **Reminder emails**
(`SessionReminderJob`, every 10 minutes) go to everyone booked, and the trainer, once per session a day ahead
(`cohort_session.reminder_sent_at`; moving a session sends a new one).

DigitalOcean deployment was discussed (2026-10-21) but not started: the owner stopped before choosing how to
connect, the server size, the web address and email. Nothing was created.

Built so far:
- Public website (`frontend/src/pages/site`, `SiteLayout`, shared facts in `src/site.ts`): home, about and
  research, the programme, for schools (licence, price chart, how to join, an enquiry form for the price guide and applying), for families, and a
  role chooser at `/sign-in` that opens `/login?as=<role>`. Content is adapted from
  movementforlearningproject.co.uk; the contact email and phone are theirs, so confirm them with the owner.
- Teacher: today's session player with nursery rhymes, 20-day participation counter, Pat's two assessments and
  each child's profile, one-hour staff induction gate, resource library.
- Head: progress dashboard, flagged classes, governor summary CSV, record access log, **School setup**
  (invite staff, switch accounts off, classes, add children, leavers, move children, privacy settings).
- Trust or group lead: schools, licences, summary results. Practitioner: courses, bookings and a printable
  certificate (`/training/certificate/:id`).
- Live course sessions on Zoom inside the platform (merged 2026-10-22; `training/zoom`, `LiveSessionService`,
  migration V7). Each cohort has a start time and weekly dated sessions (`cohort_session`); the register is per
  session (`session_attendance`) and feeds `enrolment.sessions_attended` and the certificate. The team (demo: Pat,
  pat@motormovers.test) starts a session at `/live` and the room `/live/:id` shows the Zoom meeting (Meeting SDK
  component view), the rules and the register; learners booked on the cohort join from their Training page from
  15 minutes before the start, without a Zoom account. The API creates each Zoom meeting on first use
  (Server-to-Server OAuth). Two modes (`ZoomProperties`): with only the Server-to-Server app (the owner's choice,
  2026-10-22) the meeting **opens in Zoom** in a new tab: the host gets `https://zoom.us/s/<id>?zak=<ZAK>` (signed in
  as host), learners the meeting's `join_url` (stored, migration V8). With a separate Meeting SDK app's keys (a General
  app; a Server-to-Server app's keys cannot sign the SDK) it shows inside the page instead. Keys are `ZOOM_*` (RUNBOOK,
  Live sessions on Zoom); without them sessions run without video. Tested for real 2026-10-22 with the owner's
  Zoom account in both modes: in the portal (host start) and opening in Zoom (parent and teacher join links).
  Who has joined (2026-10-24, migration V9 `session_join`): when a booked learner opens a session it is recorded;
  the host's register shows "Joined: X of Y", each learner's join time, refreshes every 15 seconds, and "Tick everyone
  who joined" (`POST /api/admin/live/{id}/register/mark-joined`). It records opening the session, not time in the
  call, so ticking stays the host's decision. Zoom's own attendance (report API after the meeting, or webhooks)
  is not built: next step if wanted. Load test `deploy/loadtest/live-session.js` (2026-10-24): 50, 99 and 500
  learners joining within a minute, and 500 within 5 s: no failures, Join under 5 ms (95%), every join on the
  register. Load-test-only settings `LOAD_TEST_LEARNERS`, `LOGIN_LIMIT_PER_IP` (production refuses a raised limit).
  The owner's Zoom plan allows 100 participants: up to 99 learners per session. The SDK and its six global libraries load from Zoom's CDN,
  pinned to 6.5.0 with SRI hashes (`src/live.ts`); only `/live/<id>` has the looser CSP and camera/microphone
  permission (`vite.config.ts` and `frontend/nginx.conf`, kept the same). Reach the room with a full page load
  (`<a href>`), never client-side routing, or the strict CSP stays in force and Zoom is blocked. The parent app
  (2026-10-26) lists live sessions on its Course tab and joins in the **Zoom app** (Join in Zoom opens the meeting's
  join link, which the API now returns to learners in both modes; Today shows a banner while a session is open). Demo: a parent cohort starts ten minutes after start-up
  with Jo booked.
- Team (admin): writing new content (saved as drafts) and content approval with wording checks, courses
  and cohorts (`/admin/courses`; the wording check runs on save; each cohort has a bookings panel to mark
  live sessions attended, with undo, and the assessment, and the certificate is then issued and locked), schools and licences, trusts or school groups and their leads.
- Payments (`payment` package, `/admin/payments`): the team records invoices and payments (a school or a
  person pays; due, paid or cancelled) and sees spend by month, payer type, purpose, trust, school and person.
  Heads see their school's payments, trust leads totals per school, parents and practitioners their own
  on a Payments page (`/payments`: due now with references and how to pay, then the history; a reminder on
  the Training page while anything is due), and parents the same in the parent app (`app/payments.tsx`, linked
  from Account and a reminder on Today). Staff see their own on the Training page. Online payment is not connected.
- Plans and prices (`/admin/plans`, `PlanService`, migration V6): licence plans live in the database with dated
  prices (`licence_plan_price`); a change applies from today or later and never rewrites a price in force, and
  each change records who made it. Plans can be added or retired. Each licence has an agreed price (default: the
  plan price on its start date; editable on Schools & licences), and the payment form fills in licence and
  course prices.
- Load test (`deploy/loadtest/`, k6): a school-day mix against the production stack with demo data. On one
  laptop (2026-10-19): 500 people with pauses at 6 ms median and no errors; ceiling about 800-900 requests a
  second with the machine's CPU saturated, never failing. Repeat from another machine against staging.
- Flow sequence diagrams (2026-11-04, `docs/flows/`): one Markdown file per role with Mermaid sequence diagrams of
  each flow, its endpoints and rules. Update the role's file when a flow it shows changes.
- Guides, rebuilt 2026-10-26 for all seven roles (Trainer included): the **user guide** (`docs/user-guide/`, for the
  people who use the platform: getting started, "how do I…?" tasks with numbered steps, answers) and the **flow
  guides** (`docs/flow-guides/`, for partners, investors and school leaders, plus the platform overview with safety,
  income and status). Both are built from screenshots of the demo by `docs/guide-builder/` (`capture.mjs`, then
  `build.mjs`; how-to in its README). **Rebuild them when screens or features change**, and update the status page
  in `build.mjs` (`overviewBody`). The words live in `flow-content.mjs` and `user-guide-content.mjs`. Rebuilt again
  2026-10-28 for Pat's assessments (teacher, head, trust lead), school assessments in the parent app, and Motor Movers
  at Home (the capture walks Priya on the website and in the app, saving nothing, then Jo's child page). Rebuilt again 2026-11-05 after the 0 to 3 check was retired, with the status pages
  saying all seven colours have grids.
  **Prototype copies** (merged 2026-11-03, pull request #15, `build.mjs all prototype`, into `docs/flow-guides/prototype` and
  `docs/user-guide/prototype`; they replace the old `prototype-labelled` set of 2026-10-19): for Pat and anyone outside
  the build team. Labelled Prototype, "considered in the design" instead of "built" (`PROTO_WORDING`), no prices, and
  a status page of what is still to settle. Rebuild them with the normal set.
- Parent: parent course on the web; parent app (link a child with a school code, home activities, guidance).
  Since 2026-10-28 a linked child's Progress screen shows the school's assessments in plain words
  (`assessment/service/ParentProgress`, shared with Motor Movers at Home; mobile `components/AssessmentSummary`):
  movements secure on each chart (and those new in the last 91 days, after the first record), and each exercise's
  first and latest rating as Not yet / Getting there / Nearly / Got it, for the child's colour if rated there, else
  the class's. Never the 1-4 scores, totals or the extra-support flag (the school talks to the family about that).
  The suggested default for Pat's open question on what parents see.
- Accounts: invite and reset links (hashed one-time tokens), 10-character minimum passwords, accounts
  switched off stop working within a minute (`ActiveUserFilter`), rate limits on sign-in, registration,
  resets and parent codes (`RateLimiter`, in memory).
- Go-live: `prod` profile, `ProductionChecks` refuses to start with dev settings, `AdminBootstrap`
  creates the first admin from `PLATFORM_ADMIN_EMAIL`, Docker images, Caddy + nginx, nightly backups,
  `deploy/.env.example`, `deploy/RUNBOOK.md`.

Recent commits (newest first, 2026-11-06): (added by Dev, 2026-11-06) the Tester's findings on country labels and refunds; (added by Dev, 2026-11-06) refunds and credit notes; (added by Dev, 2026-11-06) the parent app asks for the country; (added by Dev, 2026-11-06) parent induction before the first home session; (added by Dev, 2026-11-06) the legal entity on screens, emails and certificates; (added by Dev, 2026-11-06) the school year and year names by country; (added by Dev, 2026-11-06) the tax included in each payment; (added by Dev, 2026-11-06) amounts in the deployment's currency; [PM: Claude, 2026-11-06] one monday.com board for all open work; (added by Dev, 2026-11-06) country settings, Ireland and the open settings endpoint; (added by Dev, 2026-11-06) check the licence when a session is recorded; [PM: Claude, 2026-11-06] where Developer specs live (go-live board); (added by Dev) Pat's revised assessment content and the home cool-down; guides rebuilt without the 0 to 3 check; retire the 0 to 3 readiness check; flow sequence diagrams for every role (`docs/flows/`); Pat's answers (the other five colours' grids, Motor Movers at Home
rules); prototype guides for Pat; price chart and enquiry form on the website; guides rebuilt; Motor Movers at Home on the website; school assessments in the parent app; email a family code; Motor Movers at Home; clearer head assessment figures,
suggested point and flag reasons;
colour stage pills on every screen (web and parent app); Pat's two assessments (developmental charts and exercise scores);
Pat's answers on the new assessments; team home page, a page per course, searchable lists; dates, toasts,
in-page confirmations and live session labels; user guide and refreshed platform guides with their builder; parent
app no longer crashes on the web; card payments (Stripe); phone notifications; quizzes and certificate PDFs; heads
approve staff course places and trainers run their cohorts; training team.

## Next steps

Not started unless noted. Confirm priorities with the owner.

1. Real go-live inputs from the owner: UK server, domain and DNS, SMTP account with SPF/DKIM for the
   sending domain, then follow `deploy/RUNBOOK.md`.
2. Parent app store builds via EAS with (added by Dev, 2026-11-06) `EXPO_PUBLIC_API_URL_UK`, `_IE` and `_AU` set to the production `https://` addresses (RUNBOOK, Parent app builds). `eas init`
   gives the project id phone notifications need; then set `PUSH_ENABLED=true` (RUNBOOK, Phone notifications).
3. Session videos are timed placeholders until filmed sessions are uploaded (`programme_session.video_url`);
   content upload and hosting for videos/audio is still to be designed.
4. Rate limits are per API instance (in memory). Move to a shared store (e.g. Redis) before running more
   than one API container.
5. Done 2026-10-21: off-server copies (`offsite` compose profile, rclone to an S3-compatible bucket) and
   `deploy/restore-test.sh`, tested against a demo dump. Still to do on the real server: create the bucket,
   fill in `BACKUP_OFFSITE_*`, and run the restore test after the first night.
6. Done 2026-10-21: Maven wrapper (`backend/mvnw`); CI uses it.
7. Done 2026-10-21: `SEED_STARTER_CONTENT=true` gives an empty deployment the starter content
   (`seed/StarterContent`, shared with the demo) as drafts for the team to approve. Still to do: the daily
   session plans (the demo ones are generated filler and are not in the starter set), and replacing the
   placeholder assessment items once Pat sends the real ones (step 10). Approving drafts is one at a time.
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
   subscriptions. (added by Dev, 2026-11-05) Where Red fits, the rhyme order and the module length were confirmed on
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
