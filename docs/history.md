# History of the platform (notes moved from CLAUDE.md)

[PM: Claude, 2026-11-27] Until 2026-11-27 these notes were the **Current state** section of `CLAUDE.md`. They were moved here unchanged
because `CLAUDE.md` had grown past 160,000 characters and every session reads it whole. Newest first, as written at the time: a later
note can supersede an earlier one. Add a dated note at the top for new work, and keep `CLAUDE.md` short.

## Notes by date (to 2026-11-27)

(added by Dev, 2026-11-27) **Recorded courses in place of live Zoom courses (go-live task 89; migration V28).** Pat (email of 2026-11-26): all
training is recorded, since live sessions cannot cope with the volume and three time zones. Sagar (2026-11-27): the trainer role is not needed.
- **A course is ordered lessons.** `course_lesson` (title, text such as the script, a film and slides each by upload or `https` link, DRAFT or
  APPROVED with the wording check; `training/model/CourseLesson`). The team writes and approves them on **Content approval, Course lessons**
  (the one page that may send a file to the store; the course's own page lists them and links there) through `CourseLessonService`:
  `/api/admin/courses/{id}/lessons`, `/api/admin/lessons/{id}` with `/approve`, `/reject` and DELETE. A changed lesson goes back to a draft;
  one learners have done cannot be deleted. `UnsavedUploadRepository.uses` counts both key columns.
- **A place is on the course itself.** `enrolment.course_id` is always set and `cohort_id` is optional (`Enrolment.isRecorded()`); the same for
  `course_request`. **Code that reads `getCohort()` on an enrolment or a request must allow null: use `getCourse()`.** A learner books with
  `POST /api/training/courses/{id}/enrol` and starts at once; staff whose school pays ask with `/request` and their head approves on the
  school's Payments page, which books the place and invoices the school, as before. A course with no approved lesson cannot be booked.
- **Taking it.** `GET /api/training/enrolments/{id}/lessons` gives the approved lessons in order, each done or open, with signed film and
  slides links for an open lesson only; `POST .../lessons/{lessonId}/complete` marks one done and is refused out of order
  (`course_lesson_progress`). The website (`/training/course/:enrolmentId`, `CourseLessonsPage`, rules in `courseLessons.ts`) offers Mark as
  done once the lesson's film has played to its end, or at any time for a lesson with no film. The server cannot see playback: it keeps the
  order, the browser keeps the film rule.
- **The test and the certificate.** A recorded place's quizzes open when every approved lesson is done (`QuizService.isOpen`).
  `CertificateService.issueIfComplete` is still the one place: every lesson done and every quiz passed (`lessonsDone`, `lessonProgress`).
  The PDF says "has completed every lesson and passed the test"; the email, expiry and the reminder are unchanged. Cancelling: until the first
  lesson is done (the team any time before a certificate).
- **Access:** `AccessPolicy.requireCoursePlace` (the learner whose place it is, or the team) for the lessons and links, and
  `requireOwnCoursePlace` for marking a lesson done. Drafts and the lesson endpoints are the team's (`requireAdmin`).
- **Live courses are switched off, not deleted.** `platform.live-courses` (`LIVE_COURSES`, default false; `training/config/LiveCourses`). Off:
  an interceptor answers 404 on every live and trainer endpoint (`/api/training/live/**`, `/api/admin/live/**`, cohorts, trainers,
  `/api/trainer/**`, the assessment), `SessionReminderJob` sends nothing, cohort places and requests are not listed, and the web and the app
  hide Live sessions, Trainers, My cohorts, cohorts and the Zoom rules (`liveCourses` in `GET /api/settings`). A trainer account signs in to
  "Courses are recorded" with nothing to do. Zoom keys are not needed. **The backend tests run with it on**
  (`backend/src/test/resources/application.properties`), so the live tests are unchanged; `RecordedCourseTest` (own database) runs with it off.
  CI's loadtest job sets `LIVE_COURSES=true`; the browser tests run with it off.
- **Starter and demo content.** With live courses off: "Practitioner course" with three placeholder lessons (20 minutes each, marked as
  placeholders) and one final test of ten placeholder questions at 80% (8 of 10; the first answer is the right one), and "Parent course" with
  two placeholder lessons; launch prices unchanged. Demo places (`seed/DemoCourses`): Sam Rivers and Jo Bennett have each done their first
  lesson, and Mr Patel holds the certificate. With live courses on, the starter and demo are as they were (cohorts, three quizzes).
- **Parent app:** the Course tab lists a place's lessons and opens each open lesson's film (Watch the film) and slides; booking is there too.
  Marking a lesson done and the test are on the website, because the app has no player to tell when a film has ended.
- **Kept the title "Practitioner course"** (the spec offered "Teacher course"; the team can rename it on screen).
- **Known limits:** Pat's recordings, scripts, slides and questions are placeholders until the team enters them. Marking a lesson done trusts
  the browser about the film. A course cannot be both sold live and recorded sensibly: with live courses on, a course shows both. No renewal
  of a certificate online (as before). Not tried on PostgreSQL itself or with a real bucket. **The guides need rebuilding** (task 41):
  Training, Courses, Content approval, the team's home page and the parent app's Course tab changed, and the trainer's guide no longer applies.
- **Tests:** `RecordedCourseTest` (5: a practitioner to the certificate, a parent booking and cancelling, a head's approval, access, live off),
  `courseLessons.test.ts`, and browser tests: the team sets up a course with a film, a learner plays it to the end and does the lessons; a
  teacher's request and the head's approval; a practitioner passes the final test and downloads the certificate; a trainer signs in to nothing.

(added by Dev, 2026-11-26) **Clean-up before the feature freeze (go-live task 88; migration V27).** Eight small fixes from the Tester's low
notes on tasks 57, 68, 80, 82 and 85, and the licence status left open since task 14:
- **Last-day email once.** A family is told once for each last day (`home_subscription.end_notice_day`, `HomeSubscription.tellOfEnd(day)`):
  cancel, carry on and cancel again in the same month sends one email; a cancellation in a later month, with a later last day, sends another.
- **A subscription's row outlives its family.** When the last member leaves, the family is deleted and its subscription rows stay, ENDED and
  with no family (`family_id` is now optional, ON DELETE SET NULL; `paid_to` is kept on the row because the days themselves go with the
  family). The team's family search, by the payer's email, then shows "This family has been deleted" with the subscription, its price, the
  day it was paid to and "Cancelled by the family was deleted"; Extend is not offered. A month Stripe takes for such a row is stopped, not
  recorded. Code that reads `HomeSubscription.getFamilyId()` must allow null.
- **A refunded part says so.** The team's history (`HomePassViews.Entry.refundedOn`, `coverEntryLine` in `frontend/src/labels.ts`) reads
  "... · Monthly payment · Refunded on <day>" (a pass reads "Pass", never "Paid"); a part refunded before it started has no end date.
- **A licence that has not started shows "Starts <day>"** (`Licence.Status.UPCOMING`, the same rule as `isValidOn`, whatever the plan), on
  the trust or group dashboard and Schools and licences, which gains a "Not started yet" filter. Where a school has several licences the
  row shows the one running today, else the next to start, else the last that ended (`Licence.shownOn`); the agreed price is changed on that
  one. `LicenceService.licensedFor` is unchanged. The team overview's "licences to renew" needed no change. **The owner can still say no to
  this item.** `LicenceRepository.findFirstBySchoolIdOrderByEndsOnDesc` is gone.
- **The film stops at the session's end** (`Film`'s `stopped`, set by the class player once the plan is finished, as after Skip to end).
- **Approve all** reads "Approve all 180 drafts in Green" or "Approve the 1 draft in Yellow module 19": the scope it approves, not "shown".
- **The check is offered only when the family can do it** (covered today and the induction done): "do the check again" on the web child
  page, "Time for the check again" on At home, and the parent app's child screen button.
- **Launch prices:** the RUNBOOK says to override `group` with `school`; a retired plan reads "No longer on sale" in place of its old price.
Wording rules with a count, date or state are in `frontend/src/labels.ts` (`labels.test.ts`). Tests: `HomeSubscriptionTest` (the email, the
kept row, the refunded months), `LicenceTest`. **For the guides (task 41):** Schools and licences, the trust dashboard, Plans and prices and
Content approval changed slightly. Not tried on PostgreSQL itself (V27 replaces the `family_id` column, as V19 did for another table).

(added by Dev, 2026-11-26) **The Programme at Home is on sale monthly from the first start (go-live task 87; no migration).** The owner
decided on 2026-11-26: monthly only, with no yearly option, at £14.99 (UK), €16.99 (Ireland) and A$26.99 (Australia) a month, tax
included (£14.99 includes VAT £2.50; €16.99 includes VAT €3.18; A$26.99 includes GST A$2.45).
- **Where:** `home-monthly` in each country's `launch-prices` in application.yml. `seed/LaunchPrices` (unchanged) sets it from today when
  there is no monthly price at all, so a price the team set, even one for a later date, is never replaced, and a second start changes
  nothing. A deployment that started with none gets its country's on the next start.
- **It can still be left blank** (`PLATFORM_COUNTRIES_<REGION>_LAUNCHPRICES_HOMEMONTHLY=` with nothing after it): monthly is then not on
  sale until the team sets a price. `HomeSubscriptionTest` starts that way, because it sets its own prices and dates.
- **Demo:** monthly is on sale at once. Priya (pass running) is told the day she can subscribe from; Tom (pass ended) sees "Subscribe
  for £14.99 a month". This supersedes the lines in the task 68 note below that say no monthly price is set.
- **Tests:** `CountrySettingsTest` (the three prices, and blank), `LaunchPricesTest` (applied once, the team's price left alone, a month
  with UK VAT), `RegionIrelandTest` and `RegionAustraliaTest` (the price, and a month with its tax). The browser test no longer sets the
  price first: it reads the launch price on Plans and prices, sets a later price, and subscribes at £14.99.
- **Guides:** the income table and status line in `docs/guide-builder/build.mjs` say monthly; **the PDFs are not rebuilt** (task 41).
- **Not in this task:** a free first week or month (Kay and the adviser), a yearly option, moving families that already subscribe.

[PM: Claude, 2026-11-22] **Monthly billing merged (go-live task 68, pull request #79, main 1f0f03a).** Sagar accepted the Developer's five decisions (the note
below). Before staging: the four Stripe webhook events and the Customer Portal (RUNBOOK, Card payments); Kay's monthly prices are still to come.

(added by Dev, 2026-11-21) **The Programme at Home is billed monthly (go-live task 68; migration V26; the owner's decision of 2026-11-21).** A
family subscribes on Stripe's own page and Stripe takes each month; the 12-month pass (task 26) is no longer sold.
- **The gate is unchanged.** Each month Stripe takes arrives by the signed webhook (`invoice.paid`) and becomes a payment (the payer's,
  PAID, purpose `HOME_PASS`, with the country's tax, `payment.stripe_invoice_id` unique so an invoice is recorded once) and a `home_pass` row
  (`subscription_id`) for the invoice's period. So `HomePassService.requireReady`, Today, offline dates and refunds work as before.
  **A month's cover runs from the day Stripe's period starts to the day it ends, both included**, not to the day before as the spec said:
  Stripe takes the next month about an hour after the period ends, so without that day every family would be locked out on the morning of
  each renewal. A failed payment gives no days beyond that.
- **Where it lives:** `home/service/HomeSubscriptionService` (subscribe, manage, Stripe's messages, the team's cancel, the monthly price);
  `home_subscription` (family, payer, the price subscribed at, PENDING, ACTIVE, PAST_DUE or ENDED, Stripe's ids, cancelling) and
  `home_monthly_price`. `OnlinePaymentService` checks the signature and publishes `StripeEvents.Received`; the home code listens, as with
  `PaymentEvents`. `HomePassService` keeps the gate, extensions, refunds and reminders.
- **API:** `GET /api/home/pass` now carries `subscription` (NONE, ACTIVE, CANCELLING, PAST_DUE), `renewsOn`, `canSubscribe`, `canManage`,
  and `onSale` and `pricePence` are the monthly price. `POST /api/home/subscription/checkout` (Stripe Checkout in subscription mode, an
  inline monthly price, the row's id in the subscription's metadata) and `/portal` (Stripe's customer portal). `/api/home/pass/checkout`
  is gone. The team: `/api/admin/home-pass/monthly-prices` and `/cancel-subscription`, through `AccessPolicy.requireAdmin`.
- **Rules:** one subscription per family (the family's row is locked; Subscribe twice opens the same page; a second carer is refused).
  A family still covered by a pass subscribes from the pass's last day. Only the payer manages it. A change of the monthly price applies to
  new subscriptions only. A payer who leaves a family has it cancelled at the end of the paid month; a family that is deleted has it ended
  at once (`HomeService.leave`). A month refunded in full ends that cover on the refund day and does not cancel the subscription (the
  refund form says so). The team's cancel is at the end of the paid month, or at once if a payment has failed, with who and why.
- **Emails and phone notices:** a failed payment (the payer, once for each invoice); a cancellation (the family, once, with the last day).
  The 30, 7 and 0 day reminders go only to a family whose last cover is a pass or an extension.
- **Stripe's two shapes.** Checked against Stripe's documentation on 2026-11-21: since its 2025-03-31 version an invoice names its
  subscription under `parent.subscription_details` and no payment intent (asked for with `GET /v1/invoice_payments`, so a month can be
  refunded through Stripe), and a cancellation may come as `cancel_at`. The older shape is read too. The month is the invoice line's
  `period`, never the invoice's own `period_start` and `period_end`.
- **Price:** `home-monthly` in a country's `launch-prices` is the one price that may be left out. (added by Dev, 2026-11-26: set in all three
  countries since task 87.) With no monthly price, monthly is not on sale. The team sets it on Plans and prices (the pass price card is gone from there).
- **Web:** the At home card (`HomePassCard`, `homePass.ts`): the monthly price and Subscribe, then the state, with Manage for the payer.
  Payments: the family's subscription, Cancel the subscription and Extend. **Parent app:** the state only, with no price, no button and no
  link to a payment page (the app stores' rules; the owner and the adviser are to confirm this for each store).
- **A stand-in for Stripe, `STRIPE_FAKE=true`** (`payment/stripe/FakeStripe`, `FakeStripeController` under `/api/dev/stripe`; development,
  demos and the browser tests only, and `ProductionChecks` refuses it). No card and no money: Subscribe and Pay by card "pay" at once, and
  Manage opens a plain page with what Stripe would do next (cancel, the next month, a refused card, end it). Its messages go through the
  same code as the webhook. **The browser tests need it**, and CI's e2e job sets it.
- **Demo:** Priya's and Tom's 12-month passes are as before (Priya covered, Tom's ended); (added by Dev, 2026-11-26) monthly is on sale at the
  launch price since task 87, so Tom can subscribe.
- **Known limits:** not tried with a real Stripe account (RUNBOOK, Card payments, lists the webhook events and the Customer Portal settings
  to set first). A family that already subscribes is not moved to a new price. A family whose children have all moved to a school keeps
  paying until it cancels. A Stripe page left open and paid after the family subscribed another way makes a second subscription, which the
  platform cancels at once and logs, for the team to refund. No free first month, minimum term or cooling-off rule is built (Kay and the
  adviser, open). **The guides need rebuilding** (task 41): the At home card, Plans and prices and Payments changed.
- **Tests:** `HomeSubscriptionTest` (12, own database), `FakeStripeTest`, a month in euros and in dollars with its tax in
  `RegionIrelandTest` and `RegionAustraliaTest`, `homePass.test.ts`, and the browser test, which subscribes and cancels with the stand-in.

[PM: Claude, 2026-11-17] **Films merged (go-live task 57, part 1, pull request #76, main 60c88dc).** Still open on task 57: the Green files from
Neuroway (and films for Quick March and Roundabout), Orange rhyme 6, the rest of Pat's Yellow films, the buckets (task 29) for the real
upload, and whether to disguise the people in the films (Pat's idea, to decide later). The films keep their original size (Sagar, 2026-11-16).

(added by Dev, 2026-11-17) **The session-notice tests no longer depend on when the demo was loaded (go-live task 86; tests only).**
`RegionAustraliaTest.aSessionNoticeSaysQueenslandTimeNotUkTime` failed on CI at 23:59 Brisbane time: `SessionReminderJob.announceOpening`
only looks at sessions starting in the next 15 minutes, and a demo loaded in the last ten minutes of a day starts its "starting now"
session at once (task 83), so it was already in the past. `RegionIrelandTest` and `PushTest` leaned on the same session (near midnight
Irish and UK time). All three now call `OpeningSession.inFiveMinutes` (test sources), which moves that cohort's first session to five
minutes from now with its notice unsent; the assertions are unchanged. **A new test of `announceOpening` should do the same**, never
rely on the demo's timing. Shown with a clock fixed at 23:55:30 Brisbane: the old test fails, the new one passes. No product code changed.

(added by Dev, 2026-11-16) **The programme's films (go-live task 57, part 1; migration V25).** The team attaches films on Content approval,
Class sessions, once a colour is chosen, and can remove them. Each is an uploaded MP4 or WebM (the existing upload flow) or a pasted
`https://` link, with an optional credit line. **Two kinds, by colour** (the PM, 2026-11-16: Orange is filmed a rhyme at a time, Green an
exercise at a time):
- **A rhyme or module** (choose the module too; card "Film for Orange rhyme 4"): every session of it plays the film. This is the kind for
  Red, Orange and Yellow.
- **An exercise** of a colour's grid, from Green to Violet ("Films for Green exercises", one row each): each step of a session plays its own
  exercise's film. A rhyme colour's exercise is refused ("Attach it to the rhyme").

**Where they live:** table `programme_film`, a row per colour and unit or per exercise (`content/model/ProgrammeFilm`,
`content/service/ProgrammeFilms`, `ProgrammeFilmController` at `/api/admin/content/films`: GET, GET `/exercises?stage=`, PUT and DELETE, all
through `AccessPolicy.requireAdmin`). Nothing is copied onto the sessions, so sessions added later (`SessionPlans`) have the films too and a
reload never drops one. **Which film a screen plays:** a step's own exercise film; else the session's own film
(`programme_session.video_key` or `video_url`); else its rhyme's or module's; else the timed plan. `UnsavedUploadRepository.uses` counts
`programme_film.video_key`, so the clean-up never deletes an attached film. **Who gets a link (unchanged rules):** the class's session card
(`SessionCard.videoUrl`, `filmCredit`, and the same two on each of `steps`), only for a school licensed for the colour today; the home
session's Today (`videoUrl`, `filmCredit`, `exerciseFilms`), only for a family with a running pass and the induction done, or a parent whose
child is linked to a school; signed links through `FileLinks`, as before. **Screens:** `components/Film.tsx` (web: `preload="metadata"`, so
only a film's details load until play, with the credit line under it). In the class player a step with its own film plays it, the plan moves
on when the film ends and the next step's film starts by itself; the clock waits meanwhile, so a film is never cut. The home session shows
the film of the exercise on screen. The parent app has **Watch the film**, which opens the link as home activities do (no player inside the
app). **Credit line:** `ProgrammeFilms.DEFAULT_CREDIT`, "Film: " and `Brand.NAME` (the owner, 2026-11-16), unless the team types another for
a film. **Tried with local storage only**, with Pat's Orange films from `F:\IHA\Pat\Films` (never committed or uploaded anywhere): they are
1280x720 at 8 Mbps, 200 to 440 MB for 3 to 7 minutes, with the index (moov) at the end of the file. They stream in ranges and seek without
loading the whole file, but need a steady 8 Mbps: suggested target before the real upload, about 1.5 Mbps 720p with fast start, under 100 MB
a film (RUNBOOK, Films for sessions). No transcoding is built. **Not in this part:** uploading the real films (the buckets, task 29, and the
owner's consent and ownership question on the Orange recordings come first); the Green files themselves (not received; no YouTube embedding
or downloading). **Known limits:** a removed or replaced film's file stays in the store; to change only a credit line, remove the film and
attach it again; a cool-down has no film of its own. Tests: `ProgrammeFilmTest` (own database), and a browser test that attaches a film
recorded in the test and plays it in the class player. **The guides need rebuilding** when films are attached for real (the player and
Content approval changed).
(added by Dev, 2026-11-16, the Tester's bug on pull request #76) **The class player's clock is now one module, `frontend/src/playerClock.ts`**,
with its rules tested in `playerClock.test.ts` (a session mixing steps with their own film, steps on the module's film and a step with
none). The plan and the picture had drifted apart in such a session. Now: a step with its own film waits for the film's end, never the
clock; on the session's film the clock counts only while that film is really playing; with no film it counts while the session is under
way. Being under way carries from step to step, so whatever the next step shows starts by itself, and a pause stays a pause. The session's
film carries on from where it had got to when it comes back after a step's own film. The film cards' upload hint names video only.

(added by Dev, 2026-11-16) **Launch prices per country (go-live task 85; migration V24).** Kay's Australian prices and the owner's UK and
Irish ones (task 4), tax included:

| | UK | Ireland | Australia |
|---|---|---|---|
| Childcare centre or kindergarten licence, a year (`EARLY_YEARS`) | £395 | €465 | A$750 |
| School licence, a year (`PRIMARY_CORE`) | £635 | €745 | A$1,200 |
| Group of schools, a year (`TRUST`, 10% below a school) | £571.50 | €670.50 | A$1,080 |
| Practitioner course, then renewal a year | £105 / £30 | €125 / €35 | A$200 / A$60 |
| Parent course | £55 | €60 | A$100 |
| Programme at Home, 12-month pass (no longer sold, task 68) | £530 | €620 | A$1,000 |
| Programme at Home, a month (added by Dev, 2026-11-26, task 87) | £14.99 | €16.99 | A$26.99 |

**Plans (V24):** the codes stay; `EARLY_YEARS` is labelled "Childcare centre or kindergarten", `PRIMARY_CORE` "School", `TRUST` "Group of
schools", and `FULL_PROGRAMME` is retired (not on sale or on the price chart; a licence that holds it keeps working). **Where the prices
live:** `launch-prices` in each country's block of application.yml (`CountrySettings.LaunchPrices`, main unit, overridable like
`PLATFORM_COUNTRIES_AU_LAUNCHPRICES_SCHOOL`; a missing, negative or three-decimal price stops start-up). **Applying them:**
`seed/LaunchPrices` runs on every start, before the demo and starter content: a plan whose price in force today is still V6's "platform
setup" one gets the launch price from today, changed by "launch prices"; with no home pass price at all, the pass gets its price.
It never touches a price the team set, and a second start changes nothing. `StarterContent` creates the two courses at their launch
prices; a course that already exists keeps its price. After the first start, prices are changed on Plans and prices, not in the
settings (RUNBOOK, Launch prices). **Groups and training:** there is no automatic 10% off courses for a group of schools; the team
records the agreed price on the invoice, as now. **Demo:** licences, invoices, course fees and the home pass use the deployment's launch
prices (the placeholder pass price of 35 is gone); the group's invoices read "Group licence". On Plans and prices the V6 row reads
"Before launch", and a retired plan's card has a dashed border instead of being faded, which failed the contrast check once a plan was
retired. An existing licence keeps its agreed price. Tests: `LaunchPricesTest` (UK, own database), the Irish and Australian prices in
`RegionIrelandTest` and `RegionAustraliaTest`, `CountrySettingsTest`. **The guides need rebuilding** (task 41): the income table in
`build.mjs` has the new plans and UK prices.

(added by Dev, 2026-11-16) **Renamed to the Professor Pat Preedy Programme (go-live task 84; no migration).** Every screen, email, PDF and
notification that said "Motor Movers" now uses the new names (the rule is under Rules the code relies on). **Logo:** the running child in
the seven stage colours with the name as live text, `components/Logo.tsx` on the web (an inline SVG whose head takes the text colour;
side by side in the website and app headers and the website footer, stacked on the login, register, forgot and reset pages; at phone
width the headers drop "Professor"). `frontend/public/favicon.svg` is the child on a cream tile. **Parent app:** the store name in
`app.json`, the icon, Android adaptive icon (foreground, background, monochrome), `splash-icon.png`, `favicon.png` and `logo-mark.png`
in `mobile/assets`, made from the child by `F:\IHA\Pat\_work\task-84\icons.cjs` (Playwright; outside the repository), and `Logo` in
`components/ui.tsx` on the sign-in and country screens. **API:** `Brand.NAME`, `Brand.HOME` and `Brand.TEAM` in email subjects, bodies and
sign-offs, the certificate and credit note PDFs' properties, messages, and the default sender (`PLATFORM_MAIL_FROM`, also in
`deploy/.env.example`). **Content names** lost the prefix in the starter and demo content: "Practitioner course", "Parent course", "Rhyme
book", "Induction"; enquiry subjects are "Price guide" and "Apply for the programme"; the certificate downloads as `certificate.pdf`.
A deployment that already holds the starter content keeps its old titles (the demo reloads on a fresh database; no real deployment exists).
**Splash screen:** `expo-splash-screen` (new package, set up as a plugin in `app.json`) shows the child on the cream background
(`#f4f1ea`, the same in light and dark); it only appears in a store or development build, not in Expo Go or the web preview.
**Numbers:** certificate numbers and payment references start "PPP-" in place of "MM-" (the owner, 2026-11-16, through the PM;
`Brand.REFERENCE_PREFIX`): the certificate PDF prints "Certificate PPP-12", and the demo's payments are PPP-1001 to PPP-1033. A payment's
reference is free text the team types, so nothing stored needed changing and there is no migration; credit notes keep "CN-".
**Not changed:** code names; the website's contact details and Movement
for Learning credit (task 28, Rahul's team, whose branch touches the same website files); `docs/assessment-design.md` and
`docs/home-programme-design.md`, which are dated notes. (added by Dev, 2026-11-16, the Tester's bug on pull request #71) The website home page still said "Motor Movers builds on that work", with the
name split over two source lines, which the tests read line by line and missed. Both tests now also read each file whole, so a name
split over two lines of text or of a comment fails. At phone width "Professor" is hidden from sight only, so a screen reader still reads
the name in full, and the parent app says "the Programme at Home" in the three places that had it in lower case.
**The guides need rebuilding** (task 41): their wording files are renamed, the
built PDFs are not, and their file names change to "Professor Pat Preedy Programme ...". [PM: Claude, 2026-11-16] Rebuilt the same day
(pull requests #73 and #74).

[PM: Claude, 2026-11-16] **Renamed, priced and the guides rebuilt (the owner's decisions, 2026-11-16).**
- **Name:** the platform, website and parent app are the **Professor Pat Preedy Programme** (task 84, pull request #71; moved from Rahul's
  team to Sagar's, and Rahul was emailed about it). Certificate numbers and payment references start `PPP-` (the owner). Code names stay.
  The note below of 2026-11-15 is superseded where it says the rename is not decided.
- **Prices:** task 4 is Done. Kay set the Australian prices; the owner set the UK and Irish ones to match, and the parent course and the
  12-month pass; Kay was emailed the table on 2026-11-16. They are in the platform through task 85 (pull request #72, its note below).
  Whether VAT and GST are included is for the accountant (task 17).
- **Guides:** rebuilt twice with the new name, plans and prices (task 41, pull requests #73 and #74).
- **Films (task 57):** downloaded on the owner's computer: Orange, 18 weekly films, with week 6 missing (the teacher's week 6 link is
  week 4's film); Yellow, Pat's one test film; Green needs a Dropbox link anyone can open. Uploading still needs the buckets (task 29).
- **Board:** Done on 2026-11-16: tasks 3, 4, 21, 26, 41, 61 (the old git stash, dropped), 80, 81, 82, 83, 84 and 85.
- **Rahul's team:** its unmerged `task-28` branch (website per country) touches the renamed website files and needs updating from `main`.

[PM: Claude, 2026-11-15] **Brand and films.** (1) Brand: Kay (email of 2026-11-13) settled it as the **Professor Pat Preedy
Programme** on its own, for an education business; Motor Movers is not kept as a sub-brand (a search finds removal firms by that name).
Renaming the platform and apps is not decided yet (the owner, 2026-11-13): screens, apps and code stay "Motor Movers" for now, and go-live
task 84 (new logo and brand on the website and apps, Team Rahul) is on hold. (2) Films: Pat (email of 2026-11-14) has recordings of the
**Yellow** sessions as well as Orange; she sent one test file through Google Drive (Yellow 27, Jelly on the Plate, MP4) and will send the
rest, which are large. She thinks the Orange and Yellow recordings are fine to use, and that **Green to Violet** should be filmed from now
on. The Drive file is shared to the owner's work address, not to the Drive the agents can read. This widens go-live task 57 (films) from
Green and Orange to Orange and Yellow, with Green to Violet to be filmed; uploading still needs the buckets (task 29, RUNBOOK, File storage).

(added by Dev, 2026-11-12) **Home pass follow-ups (go-live task 82, the Tester's notes on pull request #64).** (a) A full refund
with a renewal queued no longer leaves a gap: `HomePassService.closeGaps` moves the parts queued after the refunded one forward to follow on the next day,
each keeping its length, and buying or extending follows on from the end of the unbroken run of pass days (`runsUntil`), or starts today when none
runs; with no gaps, `ended` is true whenever a family that has had a pass has none today. (b) `requireReady(parent, child, doneOn)`: a session
(`doneOn`) or a rating or movement (`assessedOn`, `achievedOn`) saved offline is accepted if a pass covered that day, even after it ended; today, tomorrow
and days outside any pass still need a pass running today; the induction always. (c) `GET /api/home/pass` carries `inductionDone`; the web child page and
the app's Today offer "Start the check" (and the check-due banner) only with a running pass and the induction, and show the pass card or the induction
otherwise. (d) "Please contact us" names the country's contact email and phone (`LegalEntity.contactEmail`/`contactPhone`, in `GET /api/settings` and
the pass), each left out while blank or the placeholder. The app's child screen (`home-child/[id]`) still offers "Do the check again", which explains a
refusal. Tests: `HomePassTest` (12), `CountrySettingsTest`, `homePass.test.ts`, the browser test with **lapsed@example.test**.

(added by Dev, 2026-11-12) **Intermittent live-session test failures fixed (go-live task 83).** `TeamOverviewTest`, `BookingRequestTest` and
`LiveSessionTest` failed on some CI runs because `LicenceCheckTest` (a clock fixed on 4 Nov 2026) and `RegionAustraliaTest` (Brisbane time)
shared the default H2 database: when one started first, the demo (loaded once per database) was dated on its clock, so "Cohort A · starting
now" was not today's for the others. Linux CI runs test classes in no fixed order; Windows ran them alphabetically, so it passed locally.
Those two and `RegionIrelandTest` now have their own databases. The demo's "starting now" session, loaded in the last ten minutes of a day,
now starts at once rather than after midnight (`DemoDataLoader`; `DemoNearMidnightTest`, clock fixed at 11:55 pm UK). No assertion was weakened.

(added by Dev, 2026-11-12) **The Motor Movers at Home pass (go-live task 26; migration V23).** A family buys a 12-month pass, for all its children and carers, by card through the
existing Stripe Checkout (`POST /api/home/pass/checkout`; a pass started but not paid is reused). It is a person's payment with the new purpose `HOME_PASS` ("Home
programme"), so it shows on Payments and in the team's figures. Only the signed webhook marks it paid: `OnlinePaymentService` and the team's mark-paid now publish
`PaymentEvents.Paid`, and refunds `PaymentEvents.Refunded`, which `HomePassService` listens to. The pass runs from the day it is paid, or from the end of the pass running
then (`home_pass` rows; a row bought by card has no dates until paid). Price: `home_pass_price`, dated like licence prices, set on Plans and prices; with none the pass
is not on sale (`GET /api/home/pass` `onSale`). **Gate, in the API** (`HomePassService.requireReady`, the one rule): the parent induction first, then for a child at home a
running pass, for sessions (`POST /api/home/sessions`), the starting check (GET and POST) and ratings (`AssessmentService.requireRecorder`); Today carries `passRunning` and
`passEndsOn` and sends no session while locked. Adding, editing and deleting children, sharing, progress and linking a school stay open. Uploaded home films need a
running pass (`FamilyService.hasChild`). Reminders (`HomePassService.remindEnding`, daily 08:15): by email and push 30 and 7 days before the end and on the day, once each per
end date (`home_pass_reminder`). A pass refunded in full ends on the refund day; a partial refund leaves it. The team finds a family by any member's email and extends its pass
on Payments (`/api/admin/home-pass/family`, `/extend`, recorded with who and why). A child linked to a school needs no pass. Web: `HomePassCard` on At home, the child page
and session page say what is missing, the check page explains a refusal; app: a pass card on Today. Demo: a placeholder price of 35 from two years ago; Priya's pass runs;
**lapsed@example.test** (Tom Reid, Noah) has one that ended. Not in this task: monthly billing (Release 2, task 68), and the price in each country (Kay, decision 3).

(added by Dev, 2026-11-12) **Ireland and Australia details, and six-week modules at home (go-live task 81; no migration).** From Pat's answers of 2026-11-10:
Ireland's and Australia's assessment points and year names are confirmed (Ireland's end point April to June; a July or August record still counts as the end of the year
just finished). Australia's first year is "Preschool/Kindergarten" in place of "Kindy"; a class's year group is free text with the names only suggested, so classes saved
as "Kindy" keep that name and no migration renames them. A new country setting `term-label` names the teacher's counter on Today: "This half-term" in the UK, "This term"
in Ireland and Australia (`GET /api/settings` `termLabel`; `counterLabel` in `src/country.ts`, blank until the settings load). The starter induction lesson now says "the
counter". **Motor Movers at Home modules are six weeks** from Green to Violet (the owner's decision of 2026-11-12): built up over five weeks as before, week 6 repeats week 5,
then the module's review (`HomeRules.WEEKS_PER_MODULE` 6, `BUILD_UP_WEEKS` 5). Where a child is comes from the sessions done, so a family in week 5 goes on to week 6; one
that had finished a module in five weeks would find itself back in that module's week 6 (no real families exist: `HOME_PROGRAMME` is off in production). The demo's Ava did
her check seven weeks ago.

(added by Dev, 2026-11-10) **Pat's daily session plans (go-live task 80; no migration).** `assessment/session-plan-<colour>.json`, made from her "Daily Session Plans"
(email of 2026-11-07) and her answers of 2026-11-10, load on every start through `SessionPlans` as drafts, adding only sessions that are missing: 990 in all (18 rhymes of 5 days
for Red, Orange and Yellow; 6 modules of 6 weeks of 5 days for Green to Violet). The weeks come from the file. `unit` is the module or the rhyme number and `day` runs through it;
titles read "Green Module 1, Week 2, Day 3" and "Red Rhyme 1, Day 3". A session stores the grid's exercise names; the teacher's card (`SessionCard.steps`) takes the instructions from
the grid when it is shown, so a revised grid reaches sessions at once, and adds the module's cool-down (`coolDown`, the last step; `cool-downs.json` now has all 24, Violet included,
so home sessions show them too). A name not in that colour's grid stops the start-up. A rhyme session links to the library rhyme of that name on every start. `POST
/api/admin/content/approve-all` (`stage`, optional `unit`; `AccessPolicy.requireAdmin`) approves a colour or module; a draft failing the wording check stays a draft and is named. Content
approval has colour and module filters and shows 100 cards at a time. The demo approves these in place of the filler; its two drafts are Yellow unit 19. Motor Movers at Home stayed
at five weeks then; (added by Dev, 2026-11-12) six since task 81. **Known limit:** a session already loaded keeps its list of exercises if a plan file is later changed (only
missing sessions are added, as the spec asks). **The guides need rebuilding** (`docs/guide-builder`): the player and Content approval changed. Names mapped and points for Pat:
`docs/assessment-design.md`, Daily session plans.

(added by Dev, 2026-11-10) **Unsaved uploads are cleaned up (go-live task 74; migration V22).** `POST /api/admin/files` records each key it hands
out in `unsaved_upload`, and `FileLinks.requireKey` removes the row in the item's own transaction. `UnsavedUploadJob` (daily at 02:45) takes rows
older than `STORAGE_UNSAVED_DAYS` (7; production refuses under 1). A key on none of the four key columns has its file and row deleted; a key
still used loses only its row; a failed delete keeps the row for the next day. It only ever takes keys from that table and never lists the
bucket, so it cannot remove a file it did not hand out. Each key has its own transaction with the row locked, so a save and the job cannot
cross. `FileStorage.delete` is a signed DELETE on a bucket and needs the Spaces key to allow deleting. Not tried on a real bucket. Known limits:
a file replaced or left behind by deleting a saved item stays, and keys handed out before V22 have no row.

[PM: Claude, 2026-11-06] **Film access decided by the owner (settles the three questions in the notes below).** Staff
induction films go only to staff at a school with a licence running today, plus the team; everyone else keeps the lesson
text and can complete the induction, but gets no film. Built as go-live task 77 (pull request #51, the Dev note below).
Parent induction films (a parent with
no child can open them) and session films (a new teacher can watch before the induction, but cannot record a session) stay
as built. Limiting home films to paying families is part of task 26.

(added by Dev, 2026-11-06) **The Tester's file hosting and log findings (go-live task 75).** `/admin/content` and `/live/<id>` now really
send their own headers: both had the strict policy in production, so uploads to a bucket and Zoom in the page could not have
worked, since 2026-10-22 for Zoom. Tomcat's `Http11Processor` is at WARN, as at INFO it logs a refused request's raw
address. An enquiry whose email fails answers 503 and the form gives the other ways to reach us; enquiries are still not
stored. `Mailer.send` returns whether it sent, and a failure logs the SMTP code (`SafeErrors.smtpCode`; `angus-mail` is now a
direct dependency). A key is checked with the store on save (`FileStorage.exists`, a signed HEAD), resources other than rhymes
download under their title (`downloadLink`, a signed `response-content-disposition`), upload messages name the formats and
MOV is not accepted (`FileTypes.FORMATS`). `ActiveUserFilter` answers a failure as a problem detail. The parent app's induction
shows Watch the video. The shipper also catches a bare JWT and JSON like `"token":"x"`.
(added by Dev, 2026-11-06, task 77) Staff induction films go only to the team and to teachers and heads whose school has a licence
running today, any colour (`LicenceService.hasLicence`, `InductionService`; the owner's decision). Everyone else keeps the lesson,
its progress and completion, with `videoUrl` null and `filmHeld` true, and the web says who the film is for. Parent induction
films and a session film before the induction stay as built. (added by Dev, 2026-11-06, task 76) Download names keep Irish letters (`filename*` in
`FileTypes.contentDisposition`), a failed enquiry does not count against its limit (`RateLimiter.release`), and the shipper also
catches `access_token`, `client_secret`, Basic credentials and cookies; `ShipperRedactionTest` tests `deploy/config.alloy` itself.
(added by Dev, 2026-11-06, task 78) Download names keep apostrophes, straight or curly. The shipper removes every value of a JSON
array such as `"Cookie":[...]`, the whole of a JSON value holding a quote, and also lower-case basic credentials, unquoted
`access_token: x` and `X-Api-Key`. A plain `password: x` or `token: x` stays, as ordinary messages read like that.

(added by Dev, 2026-11-06) **Logs (go-live task 73).** Every service keeps at most 50 MB (`x-logging` in the compose file). Caddy
drops query strings and request headers (a page at a password link sends the link as its Referer) and cuts addresses to
their network, in the access log and the error log, which logs the request too. nginx logs no requests, and only serious
errors, because its error lines repeat the Referer. PostgreSQL runs with terse errors, without values or statements. In the
API, errors are logged by type and place (`common/exception/SafeErrors`), and `org.hibernate.orm.jdbc.error` is off because it
logs the database's own message (the logger's name changed in Hibernate 7: `LogSafetyTest` caught it). Still possible:
a stack trace from outside `ApiExceptionHandler` (a scheduled job, a servlet filter) keeps its messages. The optional
shipper is the `logs` compose profile (Grafana Alloy, `deploy/config.alloy`), which redacts as a second safety net; with no
profile nothing leaves the server. RUNBOOK, Logs.

(added by Dev, 2026-11-06) **File storage (go-live task 29, the owner chose DigitalOcean Spaces; migration V21).** `common/storage`:
`FileStorage` with `S3FileStorage` (any S3-compatible bucket; Signature Version 4 written by hand, no AWS SDK, pinned to AWS's
published example in `S3FileStorageTest`) and `LocalFileStorage` (dev and tests, on by default in the `dev` profile, refused
in production). With no store, pasted links work as before. Items keep their pasted link and gain a key beside it
(`video_key`, `file_key`); a key wins. The team (`/api/admin/files`, ADMIN) gets a one-hour upload link bound to the file's
type and size, and the browser sends the file straight to the store (`src/upload.ts`), so films skip nginx's 2 MB limit.
`FileLinks.link` signs a read link for 8 hours (`STORAGE_READ_HOURS`): after the licence check for sessions, rhymes and
resources (a resource for all stages needs some licence), for a parent with a linked child or a family for home
activities, and for anyone who can already see an induction lesson. **Reads and uploads go to the bucket, never the CDN.**
**Only `/admin/content` allows the bucket in connect-src** (`STORAGE_ORIGIN`, nginx.conf and vite.config.ts), and a CSP is set
when a page loads, so links to that page must be full page loads (`reloadDocument`), as with `/live`. Drafts show "Open the
file" so a reviewer can check it. (added by Dev, 2026-11-10) Files uploaded for a form never saved are now cleaned up (task 74, above); a file left
behind by a saved item is still not deleted. Details: RUNBOOK, File storage.

(added by Dev, 2026-11-06) **The Tester's findings on country and refunds (go-live task 72).** Two more country settings,
both in `GET /api/settings`: `time-label` ("UK time", "Irish time", "Queensland time"; required), used by session reminders,
phone notices and the cohort form, and `data-held-in` ("the UK", "the EU", "Australia"), which the programme page states.
**Check `data-held-in` against the server chosen** (RUNBOOK); left blank, the line is left off. `creditNoteTitle` is in the
settings too, so the refund form and the parent app call it an adjustment note in Australia. The head's assessment card
names a calendar school year as one year (`schoolYearLabel`, `src/country.ts`). The price chart's note no longer says UK;
task 28 decides any VAT wording. Certificate emails write dates in words. Today in the parent app counts what is waiting by
name ("1 session and 2 ratings are saved…"), both queues from the first load. A refund's `clientId` already used on
another payment is refused with a 400; on the same payment it stays a harmless repeat. Findings 7 ("This half-term", for Pat) and 12 (what the payer is told about a failed refund) are held.

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

**Task tested on its branch (updated by tester, 2026-11-27, `task-88` at 6e9b8ae, before merge, the Project manager's brief; Australia only, Sagar's choice): pull request #83
(task 88, the clean-up before the feature freeze) passes, no bugs.** Backend 327, web 101 unit tests, the build and the parent app typecheck pass here. The browser tests were not rerun (CI passed them on 6e9b8ae).
- **1, the last-day email (API, the stand-in, the development mail log):** cancel, carry on, cancel again sends one email, and again one after a second round. After the next month is paid, a cancellation with the later last day sends a second email.
  The team's cancel on the same day sends none.
- **2, the last member leaves:** the subscription stays ENDED with no family ("Cancelled by the family was deleted"), the team finds it by the payer's email with the day it was paid to, its two payments are unchanged, and no email goes out.
  This also works when the payer leaves first and the carer last. A carer's email finds nothing ("no family").
- **3, refunds:** a month refunded before it started has no end date; one refunded after it started ends on the refund day. Both read "Monthly payment", refunded on the day. A part refund is not marked refunded. Tom's ended pass, refunded in full,
  reads "Pass" refunded, never "Paid", and gives no access back.
- **4, licence status (API and screens):** a licence starting later reads "Starts Sun 6 Dec 2026" on Schools and licences and the trust or group dashboard, a free trial not started too, and one starting today does not. "Not started yet" filters them.
  With several licences: running beats later; two running shows the later to end; ended plus two later shows the next to start (and the agreed price changes that one); only ended shows the last. With Oakfield's licence moved to tomorrow,
  Today has no session and recording is refused with the licence message; moved back, the session returns.
- **5, the film (Chrome, a 25-second film of the tester's own on Yellow rhyme 4):** playing at 3 s, paused after Skip to end, and still paused 3 s later. Pressing play afterwards still plays it.
- **6, Approve all:** with Green's 180 sessions put back to draft, 100 shown reads "Approve all 180 drafts in Green"; module 1 reads "Approve all 30 drafts in Green module 1", and the confirmation reads the same. One Yellow draft reads "Approve the 1 draft in Yellow".
- **7, the check (web and the app's web preview):** Tom, whose check is due and whose cover has ended, is not offered it on At home, the child page or the app's child screen. Priya still is, for Ava on the web and for both children in the app.
- **8:** the RUNBOOK says to override `group` with `school`. On Plans and prices the retired Full programme reads "Retired · No longer on sale", with no price.
- **V27, upgrading from main (9dee7e9) in Australia (H2 in PostgreSQL mode):** four subscriptions (active with two months, cancelling and told, ended, pending) keep their family, payer and status, and `paid_to` is each one's last covered day (the pending one has none).
  A second start changes nothing. A family upgraded this way still keeps its row when the last member leaves.
- **Low, for the owner or the Developer:** (1) A subscription whose last day was told before V27 (`end_notice_sent` with no `end_notice_day`) is never told a later last day. Seen with signed events: carry on, the next month paid, cancel again, no email.
  No deployment has such rows yet. (2) The team overview lists a free trial that has not started under "Licences to renew", by its end date, with no "Starts" label. (3) When a deleted family's payer starts a new family, the team's search shows only the new
  one, so the ended subscription is no longer found. (4) Items 5 and 7 have no automated test, though the spec asks for one per item.
- **Not tested:** PostgreSQL (Docker's engine would not start), the UK and Ireland (Sagar: Australia only for now), a real Stripe account, and a real phone.

**Task tested on its branch (updated by tester, 2026-11-26, `task-87` at 47e8c48, before merge, the Project manager's brief): pull request #81 (task 87, the monthly price
for the Programme at Home) passes, no bugs.** Backend 324, web 92 unit tests, the build and the parent app typecheck pass here. The browser tests were not rerun (Sagar chose to skip them; CI passed them on 47e8c48).
- **Each country started fresh (UK, Ireland, Australia, demo with `STRIPE_FAKE=true`, 13 API checks each):** `GET /api/home/pass` is on sale at 1499, 1699 and 2699, from today, changed by "launch prices". Tom (pass ended)
  can subscribe; Priya (pass to Oct 2027) sees the price but cannot yet. Tom subscribes on the stand-in and his month is PAID at the price, with tax 250 (VAT £2.50), 318 (VAT €3.18) and 245 (GST A$2.45). The team sets a later price
  (accepted) and a second price for today is refused; today's stays the launch price.
- **A second start changes nothing:** every row of the monthly, pass and licence prices is identical across three starts in the UK and two in Ireland and Australia, the team's later price included.
- **Upgrade from main's database (de59b3e, UK):** a team price from today stays and no launch row is added. A team price for a later date only stays, and monthly stays off sale until then. With no price, the launch price is added from today.
  (A first run lost the later price, because the tester force-killed H2 just after saving it; rerun with a clean stop, it is kept. `LaunchPrices` only adds a price when there is none.)
- **Overrides (Australia, no demo):** unset gives 2699, `29.95` gives 2995, blank leaves monthly off sale, and `abc`, `-1` and `26.999` stop start-up with the setting named ("must be amounts of 0 or more, with at most two decimal places").
- **Docs:** RUNBOOK, README, the guides' income table and the Developer's note give the monthly price and no longer say it is not on sale.
- **Low, for the owner or the Developer:** (1) `HOMEMONTHLY=0` is accepted and saves a monthly price of A$0 from today, while the team's form refuses 0 (what a family is then offered was not tried). (2) The Project manager's note of 2026-11-22 still says "Kay's monthly prices are still to come".
- **Not tested (Sagar, 2026-11-26: Australia only for the rest until everything is ready):** the screens (At home, Plans and prices) and the app on screen, which the browser test covers for the UK, and the browser tests locally. Also not tested: PostgreSQL and a real Stripe account.

**Task tested on its branch (updated by tester, 2026-11-21, `task-68` at c0973bc, before merge, the Project manager's brief): pull request #79 (task 68, monthly billing for
the Programme at Home) passes, no bugs.** Backend 322, web 92 unit tests, the build, the parent app typecheck and all 72 browser tests pass (with `STRIPE_FAKE=true`; H2 with demo data, installed Chrome).
- **Through the API, 76 checks in each of the UK, Ireland and Australia** (the stand-in for Stripe, plus webhook events signed by the tester with a local test secret): not on sale and checkout refused with no monthly price; a price in the past, of 0,
  or a second one on the same day refused. Subscribing twice gives the same page; nothing opens until it is paid; then sessions, the check and a rating open, and the team sees ACTIVE with the paid-to day. Each month is a PAID payment
  with the tax (£25 includes VAT £4.17; €29 with 23% VAT; A$45 includes GST $4.09). A repeated `invoice.paid` records nothing; an unsigned or wrongly signed one is refused. The next month follows on (each month's row shares its last day with
  the next one's first, decision 1). A refused card: PAST_DUE, the payer emailed once per invoice, cover ends with the month already paid. The team's cancel needs a reason; after a failed payment it ends at once, otherwise CANCELLING,
  with who and why; access stays to the paid day. The payer's cancel, change of mind and end on the stand-in page behave the same. A month refunded in full ends only that month's cover and leaves the subscription running.
  One subscription per family: a carer is told while the payer's page is open and once it runs, and only the payer opens Manage. Priya (pass to 2 Oct 2027) cannot subscribe and is told the day; a pass ending tomorrow cannot, one ending today can (decision 2).
  The payer leaving cancels at the end of the paid month and the carer keeps it; the last member leaving ends it at once at the stand-in (decision 4). Signed events of the tester's own: a month that ended yesterday gives no access today
  (yesterday's session is accepted), a month ending today covers today (decision 1), another currency is not recorded, and a failure gives no extra days. The old pass checkout is gone (404); Priya's pass runs and the team's Extend works.
- **Access:** teacher, head, trust or group lead, trainer, practitioner and both parents get 403 on the four team endpoints; the staff roles get 403 on the parent ones; signed out 401; nothing changed. A parent with no family is told there is nothing to manage.
- **Screens (Australia, Chrome, 1280 and 390px, no overflow):** Plans and prices has the monthly price card (with the new-subscriptions-only note) and no pass price card. At home: "Subscribe for $45 a month", then the state with the next payment day and Manage,
  which opens the stand-in page. My payments and the team's Payments show each month with "includes GST $4.09"; the team's family card shows the subscription. Priya is told when she can subscribe. **Parent app (web preview):** Today shows the state only
  ("Your family's subscription is running", "Subscriptions are managed on the website, not in this app"), with no price, no button and no payment link (decision 5); the pass checkout link is gone.
- **Logs:** no email, name or Stripe id outside the development Mailer's whole-email lines; the service logs ids and dates only.
- **Low, for the owner or the Developer:** (1) Cancel, change of mind and cancel again sends the last-day email twice (the spec says one email). (2) When the last member leaves, the family's subscription row goes with it, so the team's
  family search no longer shows it (its payments stay). (3) A refunded month reads "21 Dec to 20 Dec" in the team's history, as for passes (task 82). (4) The stand-in's pages need no sign-in: anyone who knows a customer id can act on a development server (production refuses `STRIPE_FAKE`).
- **Not tested:** a real Stripe account (no keys; the tester does not read `backend/.env`), phone notifications (push off here, no phone), PostgreSQL.

**Fix verified (updated by tester, 2026-11-16, `task-57` at 4157161, before merge): pull request #76 (task 57 part 1) now passes, no bugs.** Web 87 unit tests (the new `playerClock.test.ts` included), the build and all 72 browser
tests pass; the backend was not rerun (only web files changed; 305 passed at 0e6bc0f). In Chrome on Year 3 K with real Orange films on local storage, standing in for Green: Yes and No, Flying Machines and Motor Spin with their own films.
- **(a)** When Flying Machines' film ends, the module film starts by itself on Starfish and the bar counts. With no module film, the timed plan runs on by itself there instead.
- **(b)** During a step film the bar holds still (Yes and No, Flying Machines, Motor Spin, reached by the clock). After Motor Spin's film the module film carries on from where it was (88 s) and the bar counts.
- **(c)** Paused, nothing counts: a step film, the module film (6 s) and the timed plan (5 s).
- **(d)** A step with no film (the timed plan) and the cool-down count while running; Skip to end fills the bar. **(e)** Orange rhyme 4 (one film, at 390px): the bar counts only while the film plays, holds while paused, resumes with it.
- **Low note 1 fixed:** the film card's upload hint names video only. **New, low:** after Skip to end the module film keeps playing; it only stops when paused by hand. No console or content policy errors.

**Task tested on its branch (updated by tester, 2026-11-16, `task-57` at 0e6bc0f, before merge, the Project manager's brief): pull request #76 (task 57 part 1,
films in sessions) has one bug, handed back to the Developer.** Backend 305, web 79 unit tests, the build, the parent app typecheck and all 72 browser tests pass (H2 with demo data, installed Chrome).
- **Bug (minor): the class player's clock and the film get out of step when a session mixes steps with their own film and steps without one** (`PlayerPage.tsx`). Tried on Year 3 K with Orange films standing in for
  Green: Yes and No and Flying Machines with their own films, Motor Spin too, and a module film. (a) When the second step's film ends, the module film shows for Starfish paused at 0:00 and the plan stops there until
  someone presses play; it does not start by itself as the step films do. (b) With the module film playing, moving on to Motor Spin (its own film) stops the module film without its pause being noticed, so when Motor
  Spin's film ends the module film shows paused at 0:00 while the plan's clock runs on: in 60 seconds the bar went from 57% to 67% with nothing playing, and it would reach the end of the session that way. The step
  films' own play and pause do not reach `playing`, and the session film is unmounted without `onPause`. Real Green sessions will mix them (Quick March and Roundabout have no film; the cool-down never has one).
- **Attach, replace, remove (64 API checks):** a rhyme film (Orange, Yellow) and an exercise film (Green), by upload and by https link, with the default credit "Film: Professor Pat Preedy Programme" or one typed
  (trimmed). Replacing keeps one row; the same key with a new credit is accepted; removing clears the film and its credit, and removing again answers false. Refused with clear messages: key and link together or
  neither, http or javascript links, a link over 500 characters, a module or rhyme with no sessions, unit 0, a made-up key, a PDF key, a credit over 200 characters or with a diagnostic word, both a unit and an
  exercise, an exercise film for Orange, an unknown exercise (404). On Content approval in Chrome: a 226 MB film uploaded on the rhyme card, attached with a credit, shown, and removed; Green lists its 27 exercises.
- **Restarts:** after a restart every film and the player's films are the same, and new signed links work. A session deleted from the database and added back by the session plans (Orange rhyme 4 day 5, Green
  module 1 week 6 day 5, as drafts) belongs to the same rhyme or module, whose film stays.
- **Access:** teachers, heads, trust or group lead, practitioner, trainer and parents get 403 on all four team endpoints, signed out 401, and nothing changes. Ms Hardy, the trust lead, practitioner, trainer and
  parents cannot open another class's session. An Indigo class at Oakfield (licensed Red to Blue) gets no session and no file link, with films on Indigo module 1 and all its exercises. Tom (pass ended) gets no
  film and no exercise films; a new parent and Jo cannot open Ava's session. Links are signed for 8 hours; a changed signature, a changed expiry, another key or no signature is refused.
- **Real films on local storage:** Orange 04 (425 MB) on rhyme 4 in Year 1 M's player streams in ranges: 3.4 MB before play, 17 MB after 4 seconds, 24 MB after seeking to 70% (a new range request), never the whole file.
  Step films (Orange 02 and 03 as Green exercises) play one after another and the plan follows; the credit shows under each. Desktop and 390px, no overflow, no console or content policy errors.
- **Home:** Ava (web) shows her first exercise's own film and the module film for the others, each with its credit. The parent app's web preview shows Watch the film with the credit on each exercise, opening the signed film.
- **Low, for the owner or the Developer:** (1) The film card's upload hint lists "MP3, M4A or WAV audio, PDF, PNG or JPEG", though only video can be chosen and attached. (2) A credit can only be changed by removing and
  attaching the film again (as the card says). (3) A removed or replaced film's file stays in the store (as the Developer says).
- **Not tested:** a real bucket, a real phone on 4G, Green films (none yet), PostgreSQL itself.

**Task tested on its branch (updated by tester, 2026-11-16, `task-85` at d00b37b, before merge, the Project manager's brief): pull request #72 (task 85,
launch prices per country) passes, no bugs.** Backend 299, web 79 unit tests, the build, the parent app typecheck and all 71 browser tests pass (H2 with demo data, installed Chrome).
- **Each country, started fresh (`PLATFORM_REGION` UK, IE and AU, 21 checks each):** `GET /api/settings` has the currency; the price chart (API and `/for-schools` on screen) lists Free trial, "Childcare centre or kindergarten", "School" and "Group of schools"
  at £395/£635/£571.50, €465/€745/€670.50 and A$750/A$1,200/A$1,080, with no Full programme and no other currency. Plans and prices shows the new labels, Full programme marked Retired, and the pass price. The practitioner course (£105 + £30,
  €125 + €35, A$200 + A$60), the parent course (£55, €60, A$100) and the home pass (£530, €620, A$1,000) on the API and the Training and payments pages. A new school on Full programme is refused ("no longer on sale"); one on School takes the launch price.
- **Tax at these prices:** a licence invoice, a head's course booking (the school's invoice) and a home pass payment: VAT 20% in the UK (£635 includes £105.83), VAT 23% in Ireland, GST 10% in Australia (A$1,000 includes A$90.91).
- **Upgrade and second start (a file H2 database; Docker's engine would not start here, so not PostgreSQL):** started on `main` (V23) with demo data in Australia, the team set Childcare to 51000 from today and School to 99000 from 26 Dec, and signed up a school on Full programme and one on School at an agreed 88000.
  Then task 85 (V24): labels changed and Full programme retired; Childcare kept the team's price; School and Group got the launch prices from today (the team's later School price stays queued); every licence kept its plan and agreed price, the Full programme one included;
  the existing courses (18000/6000, 4500) and pass price (3500) were left alone. A second start changed nothing (every row identical). Only sign-up checks that a plan is on sale; licences on a retired plan keep working.
- **Overrides:** `PLATFORM_COUNTRIES_AU_LAUNCHPRICES_SCHOOL=1300` gives School A$1,300 on a first start; -5 and 1300.505 stop start-up with "Invalid country setting: platform.countries.au.launch-prices must be amounts of 0 or more, with at most two decimal places", and "abc" with a binding error naming the setting.
- **Low, for the owner or the Developer:** (1) Overriding `school` does not move `group` (A$1,080 beside a A$1,300 School); the RUNBOOK could say to override both. (2) The retired Full programme still shows its old proposal price in the deployment's currency (A$1,750, €1,750) on Plans and prices.
  (3) In the demo the pass price history lists the same price twice (from today, "launch prices", and from two years ago, the demo's). (4) An existing deployment keeps its courses' and pass's old prices on upgrade, as designed; there are none in production yet.
- **Not tested:** PostgreSQL itself (the same SQL ran on H2 in PostgreSQL mode).

**Fix verified (updated by tester, 2026-11-16, `task-84` at ebdb734, before merge): pull request #71 (task 84) now passes, no bugs.** Backend 290, web 79 unit tests, the build, the parent app typecheck and all 71 browser tests pass.
- **The bug is fixed:** the website home page reads "The programme builds on that work." No "Motor Movers" on any website page, at 1280 and 390px; both brand tests (`BrandTest`, `brand.test.ts`) now also catch the name split over two lines, and fail on the old sentence.
- **Low notes 1 and 2 fixed:** under 520px "Professor" is hidden from sight only, so the logo is still read as "Professor Pat Preedy Programme" (checked in the accessibility tree, website and teacher header). The app says "the Programme at Home" in all three places, and its tabs for Priya, Jo and Tom have no old name.
- **Left as it is:** low note 3, the splash has no dark-mode setting. Not tested: a store or development build (the splash and icons on a phone).

**Task tested on its branch (updated by tester, 2026-11-16, `task-84` at 3feec55 and again at d228d9f, before merge, the Project manager's brief): pull request #71 (task 84,
the Professor Pat Preedy Programme) has one bug, handed back to the Developer.** Backend 288 (289 at d228d9f), web 78 unit tests, the build, the parent app typecheck (after `npm ci`) and all 71 browser tests pass (at both commits) (H2 with demo data, installed Chrome).
- **Bug (minor, but the brief's first rule): the website's home page still says "Motor Movers builds on that work."** (`frontend/src/pages/site/HomePage.tsx` lines 88 and 89), in the UK, Ireland and Australia. The name is split over two source lines, so neither `brand.test.ts` nor
  a plain search finds it; a test that joins lines would. `LiveSessionService.java` line 34 has the same split in a code comment (not shown to anyone).
- **Everything else has the new name.** A crawl of the web app as twelve accounts (visitor, both teachers, head, trust or group lead, practitioner, three parents, trainer, the team and Pat; about 300 pages, with page titles, alt text, labels and meta tags):
  no "Motor Movers" apart from demo email addresses. The parent app's web preview (country chooser, sign-in, every tab and Payments for Priya, Jo and Tom): none. Eleven emails sent and read in the log (reset, two account invites, enquiry, family code, course place
  to approve and booked, credit note, certificate, both live session reminders): all use the new name; the pass reminder and push texts read from `Brand` in the code. Error messages: none.
- **PDFs, rendered with PDFBox:** the certificate and the credit note both carry "PROFESSOR PAT PREEDY PROGRAMME" on one line with room to spare; the certificate downloads as `certificate.pdf` and its PDF author is the new name.
- **Look:** the header at 1280 and 390px, light and dark, on the website, the teacher's and parent's pages and `/live/<id>` (seen, with the register): no overflow, no console or content policy errors, favicon `/favicon.svg`. Under 520px the logo reads
  "Pat Preedy / PROGRAMME" (`.brand .logo-title` is hidden on purpose). Nothing diagnostic: the matches are "Not a diagnosis" notes, "screen" as in the device, and the demo's ADHD draft the wording check blocks.
- **Parent app:** the Expo config resolves with the store name "Professor Pat Preedy Programme", the icon and Android icons, and the `expo-splash-screen` plugin; the splash image is transparent, so no white square on the cream. Not seen: the splash itself (no build).
- **Low, for the owner or the Developer:** (1) Under 520px "Professor" is hidden with `display: none`, so a screen reader also reads "Pat Preedy Programme" there; `Logo.tsx` says the name is read in full. (2) The app writes "the programme at home" in lower case in three places
  (`account.tsx` 76, `(tabs)/index.tsx` 195, `add-child.tsx` 60). (3) The splash has no dark-mode setting, so phones in dark mode see the cream background.
- **The PPP- prefix (d228d9f, the owner's change):** the certificate PDF prints "Certificate PPP-2"; the demo's references are PPP-1001 to PPP-1033 with no MM- left (team API), and PPP- shows on the team's, the school's and the parents' Payments pages and the app's Payments screen, including a reference the team typed. The web certificate page shows no number, as before, and no email carries a payment reference (a payment due is a push with the amount only). Credit notes keep CN-.
- **Not tested here:** a store or development build, so the splash and app icons on a phone.

**Task tested on its branch (updated by tester, 2026-11-12, `task-82` at abefc0d, before merge, the Project manager's brief): pull request #65 (task 82,
the home pass follow-ups to the tester's four notes on #64) passes, no bugs.** Backend 283 (the live-session tests pass here), web 72 unit tests, the build, the parent app typecheck and all 71 browser tests pass (H2 with demo data, installed Chrome).
- **Refund with something queued:** refunding the current pass in full moves a queued renewal, or a team extension, to start the next day, so no day is left without a pass. Refunding the middle of three moves the third up behind the first.
  A partial refund changes nothing. Buying (Tom) or extending with no pass running starts today; buying again after a refund follows on the next day.
- **Offline dates (sessions and ratings, Tom, whose pass ended 1 Nov):** a day the pass covered is accepted after it lapsed. Refused: the day after it ended, today, tomorrow, beyond 30 days, and no induction (even with a pass). A batch with one refused date is refused whole. The check stays refused.
- **The check:** Tom's child page on the web and the app's Today no longer offer "Start the check". Priya is still offered it, for Leo, on both.
- **Contact:** with the placeholders, "Please contact us to buy the pass" (web and app). With test values through `PLATFORM_COUNTRIES_UK_LEGALENTITY_EMAIL` and `_PHONE`: "Please contact us at hello@motormovers.test or 0118 000 0000 to buy the pass"; phone only gives just the phone in `/api/settings`.
- **Low, for the owner or the Developer to decide:** (1) While a pass runs today, a past day no pass covered (between an old pass and a new one) is still accepted; the brief says dates outside any pass are refused. Harmless: it only lets a family backfill up to 30 days.
  (2) A moved part keeps its length in days, so across 29 Feb 2028 it ends a day later or earlier than 12 calendar months. (3) The team's history lists a refunded part as "Paid", and a refunded part that had not started as ending the day before it starts.
  (4) With the pass ended, the web child page still links "do the check again" under Colour, and At home still says "Time for the check again" (like the app's known "Do the check again").
- **Not tested:** Stripe test checkout and the webhook (no test keys in this run); a real phone.

**Task tested on its branch (updated by tester, 2026-11-12, `task-83` at 7fd0a68, before merge, the Project manager's brief): pull request #66 (task 83,
the intermittent live-session test failures) passes, no bugs.**
- **Reproduced and fixed:** `-Dtest=LicenceCheckTest,TeamOverviewTest -Dsurefire.runOrder=alphabetical` fails on `main` (adbf57f, `TeamOverviewTest.theTeamSeesWhatNeedsAttention`) and passes on `task-83`.
- **Random order, three runs:** 279 tests, no failures each time (seeds 32597616537100, 32738544288700, 32840360326000; rerun one with `-Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed=<seed>`).
- **No assertion weakened:** the diff changes only the three tests' database URLs, adds `DemoNearMidnightTest` and the loader's last-ten-minutes case. The tests that fix a clock as a bean (`LicenceCheckTest`, `DemoNearMidnightTest`) or set a region now all have their own database;
  `UnsavedUploadTest` only makes a fixed clock locally, so it can share the default one.
- **Demo:** all 70 browser tests pass. On a fresh start "Cohort A · starting now" begins ten minutes after start-up, is in the team's sessions today and is open for Jo to join, with sessions 2 and 3 a week apart.
- **Not tested:** loading the demo between 11:50 pm and midnight UK time by hand (`DemoNearMidnightTest` covers it).

**Task tested on its branch (updated by tester, 2026-11-12, `task-26` at a928542, before merge, the Project manager's brief): pull request #64 (task 26,
the Motor Movers at Home pass) passes, no bugs.** Backend 278, web 69 unit tests, the build, the parent app typecheck and all 70 browser tests pass here (H2 with demo data, installed Chrome).
- **Through the API, 78 checks:** Tom (lapsed@example.test) is refused a session, the check (GET and POST) and a rating with "Your family's pass ended on ..."; Today sends no session, review or cool-down.
  Adding, editing and deleting a child, progress, the invite code and the induction stay open. Priya's session and check work. A new parent is refused for the induction first, even with a pass.
  Each family is refused on the other's child, and teachers, heads, trainers and parents get 403 on the team's pass endpoints.
- **Team:** finding a family by any member's email, in any case (unknown 404, an account with no family 400). Extending is refused for a past date, a missing date or one before the current end; once extended, Tom's sessions work.
  The price is refused in the past and at zero or below, can be revised for a future date, and today's price is unchanged. A pass payment recorded as paid, or marked paid later, starts a 12-month pass, and a second one carries on from the end.
- **Refunds:** a partial refund leaves the pass, and the rest refunded ends it on the refund day (it still runs that day). A full refund at once ends it the same way.
- **Reminders, on a moving clock (a test of the tester's, not committed), day by day for 46 days:** one each at 30, 7 and 0 days. Missed days catch up (sent at 28 if 30 and 29 were missed; the 7-day one is skipped when days 7 to 1 are missed). A renewal before the end stops them.
- **Screens:** web (At home, the child page, the session and check pages, a phone width) and the app's web preview show "Your pass has ended" with no session, and Priya runs hers. Payments has Find the family and Extend the pass, with history and who did it; Plans and prices has the pass price.
  Without Stripe keys the card says "Paying by card is not switched on yet. Please contact us to buy the pass", checkout answers 400, and no payment is left behind.
- **Low, for the owner or the Developer to decide:** (1) Refunding a pass in full while a renewal is queued leaves a gap: the family has no pass until the renewal starts, the card does not say "ended", and buying again would start after the renewal.
  The team cannot extend into the gap either, since an extension must end after the latest end. (2) Sessions saved offline in the app while the pass ran are refused once it has lapsed: the gate looks at today, not the session's date. They stay queued until the pass is renewed.
  (3) With the pass ended, the web child page and the app's Today still offer "Start the check", which then explains the refusal. (4) "Please contact us" gives no email or phone.
- **Not tested:** Stripe test checkout, the webhook and `/home?paid=1` (no Stripe test keys in this run; the tester does not read `backend/.env`); a real phone; push reminders on a phone.

**Task tested on its branch (updated by tester, 2026-11-12, `task-81` at 3758d3d, before merge, the Project manager's brief): pull request #63 (task 81,
Ireland and Australia details and six-week modules at home) passes, no bugs.** Backend 270, web 64 unit tests, the build and the parent app typecheck run here. The browser tests
were not rerun; CI passed them on 3758d3d. Installed Chrome, the API with demo data on H2.
- **Each country (`PLATFORM_REGION` UK, IE and AU):** `GET /api/settings` gives the right `termLabel`, year names and school-year months (Ireland end point April, last month June;
  Australia January, May, September). The teacher's Today counter reads "This half-term" in the UK and "This term" in Ireland and Australia, and no other label ever appeared while the
  page loaded. The class form suggests each country's year names.
- **Australia:** a class made in the form with "Preschool/Kindergarten" saves and lists. A class saved as "Kindy" can still be created, edited, given children and exported; the research CSV
  carries both names unquoted.
- **Motor Movers at Home, on the API, the website and the app's web preview:** a new Green child reads "week n of 6" through module 1. Week 6 has the same eight exercises as week 5,
  with nothing new and no review. After 30 sessions the child is in module 2 week 1 with the module 1 review due, and the review page lists its exercises. A child part-way through shows
  "week 6 of 6" and its session in both. An Orange child stays at one rhyme a week (session 5 of 5, then rhyme 2). Ava: module 2 week 1 of 6, review due. No page errors.
- **UK otherwise unchanged:** by the diff, the only other UK change is the starter induction lesson saying "the counter".
- **Not tested:** a real phone; the guides still show "This half-term" and "week n of 5" until they are rebuilt (task 41).

**Task tested on its branch (updated by tester, 2026-11-10, `task-80` at ecc03a8, before merge, the Project manager's brief): pull request #62 (task 80,
Pat's daily session plans) passes, no bugs.** Backend 267, web 62 unit tests and the build run here. The 70 browser tests and the mobile job were not rerun: CI passed them on
ecc03a8. Run on PostgreSQL 17 with installed Chrome.
- **Against Pat's Word file, compared by script:** all 990 sessions, every exercise in order, and all 24 cool-downs word for word. The only differences are the spelling matches listed in
  `docs/assessment-design.md` and her three corrections: Blue cool-downs 1 to 6, Hot Air Balloon in Blue Module 3 from week 5 (not before), and Indigo Module 1 Cool Down 1 ending "5, 4, 3, 2, 1"
  with the repeated step gone.
- **Through the API, 10,727 checks:** title, order, 10 minutes and a clean wording check on every session; each of the 24 modules' first session with its steps, the grid's instructions and its cool-down;
  a class moving across modules and wrapping round after the last session; no cool-down on a rhyme colour. A colour off the licence shows no session and recording is refused with a 400.
- **Session player by eye in Chrome (Blue, Green, Indigo, Violet, Yellow; phone width too):** the step list, each step's own instructions when clicked, the cool-down last, the timer, Skip to end and Complete session. No page or
  content policy errors. **Rhyme card:** shown with the words and "The recording is on its way" on a rhyme with a library match; left out, with the instructions still shown, on one without.
- **A fresh deployment, all 990 drafts:** content approval opens in about 0.3 s with 100 cards and "Showing the first 100 of 990", and All lists drafts first. A module of 30 takes about 0.3 s and a colour of 180 about 0.4 s,
  button to message. A failing draft stays and is named on the page, a rejected one stays rejected, Keep it changes nothing, and approving again approves none.
- **Refused:** teacher, head, trust or group lead, trainer, practitioner and parents get 403 and change nothing; signed out 401; a bad colour or module 400.
- **Starting up again adds nothing:** three starts of the fresh deployment and restarts after approving, every row identical (ids, status, reviewer, rhyme).
- **Motor Movers at Home:** `HomeRules` is untouched. Ava is at week 1 of 5 and now ends with Cool Down 2.
- **Low, not blocking, for task 22:** the rhyme "Hickory Tickory Tock" (Pat's spelling, Red 10 and Yellow 14) does not link to the starter library's "Hickory dickory dock", so those weeks have no rhyme card. On Jack and Jill the
  starter library's traditional words sit directly under Pat's own different words. A rhyme added in the library links only at the next API start. **Cosmetic:** the button reads "Approve all 1 drafts shown", and "Approve all 180 drafts
  shown" while 100 are on the page (the confirmation gives the right scope).
- **Not tested:** a real phone; Ireland and Australia; the Violet home session on screen (`CoolDownsTest` covers its cool-down).

**Fix verified (updated by tester, 2026-11-10, `main` at 34b9716, the Project manager's brief): pull request #59 (task 74) passes, no
bugs.** Backend 254, web build. Web unit, browser and mobile suites not rerun (no web or app code changed).
- **The real job, as scheduled, on PostgreSQL 17 and the local store** (the API's time zone set so that 02:45 came within minutes): an upload past
  `STORAGE_UNSAVED_DAYS` goes, file and row, at 8 days and at 7 days 5 minutes; one at 6 days 23 hours and a fresh one stay. A file never
  sent clears its row. A key on each of the four columns keeps its file and column and loses its row. A file in the store that was never
  handed out stays, and a row with a path in it removed nothing outside the store. A failed delete (a directory in the file's place) keeps its
  row, logs the key with no stack trace, and the rest of the run goes on; the next run deletes it. `STORAGE_UNSAVED_DAYS=2` is honoured. With no
  store the job does nothing and uploads are refused.
- **Saves:** a form left open past the clean-up is told to upload again, and no item is saved; a form saved inside the grace period clears its row.
- **In Chrome:** all five forms (class session, home activity, resource, staff and parent induction) upload and save onto the right column, and the
  row clears. With a file removed and another chosen, the first stays for the clean-up; an abandoned form's file and row stay until then. No
  page or content policy errors.
- **Production** refuses `STORAGE_UNSAVED_DAYS` of 0 and -1 at start-up, and does not object to 1.
- **A save and the clean-up at once, on PostgreSQL** (a throwaway test, not committed): with the job in the middle of a delete, a save waits on the row
  lock, then gets 400 and no item. If that delete fails, the waiting save succeeds and the file stays. With a save in progress, the job waits and then
  leaves the file; if the save rolls back, the job deletes it. 300 saves against three jobs at once: 291 saved, each with its file, 9 told to upload
  again, each with no file and no item, no other answers and no rows left. `UnsavedUploadTest` also passes on PostgreSQL.
- **Not tested:** a signed delete on a real Spaces bucket (no buckets yet; only `S3FileStorageTest` covers it).

**Fix verified (updated by tester, 2026-11-06, `main` at be2dd08, the Project manager's brief, a focused check): pull request #56
(task 78) passes, no bugs.** Backend 241, web build, CI green on `main`; web unit, browser and mobile suites not rerun (no web or app code changed).
- **Download names (41 checks in Chrome):** "O'Brien's sheet" saves as `O'Brien's sheet.pdf`; a curly apostrophe stays curly, with a
  straight one in the plain `filename`; Irish letters stay ("Ciarán Ó Súilleabháin's rhymes"). The file arrives intact, and the name
  stays signed: dropping the apostrophes, swapping straight and curly, another name and a header injection are all refused.
- **Log shipper, real Alloy v1.21.3 with a local Loki 3.7.9:** 68 lines with secrets came out clean: every value of a cookie array,
  also nested or cut off; a JSON value holding a quote; lower-case basic; unquoted `access_token` and its kin; `X-Api-Key`; and everything
  from task 76. On 6 lines, what follows the secret survived. 25 ordinary lines came through unchanged, including `token: expired`.
- **New, low (not claimed by the fix):** `access_token: "x"` with the value in quotes is left, as are `"access_token": x`, `access_token = x`,
  `accessToken: x`, `apikey: x`, a Basic value under 16 characters and upper-case `BASIC`. A cookie value holding an escaped quote before a
  bracket ends the array early. Too much removed, which is safe: "Basic responsibilities of a teacher" and "basic training/programme/materials"
  lose the words after Basic. Nothing is known to log any of these.
- **Not tested:** a download name from Spaces (no bucket).

**Fixes verified (updated by tester, 2026-11-06, `main` at 074ae18, the Project manager's brief): pull requests #51 (task 77)
and #49 (task 76) pass, no bugs.** Backend 237, web 62, build, mobile typecheck, browser tests 69/69, CI green on `main`.
- **Task 77, staff induction films (242 API checks, on-screen checks in Chrome).** With approved staff lessons holding an uploaded
  and a pasted film: the team, and a teacher and head at a licensed school, get both films and the upload plays, also on a
  licence's first and last days, with one licence of two running, and with a licence for another colour only. A teacher or head
  with no licence, or one that ended yesterday, starts tomorrow or starts next month, a trust lead, trainer and practitioner, also
  those three attached to a licensed school, get `videoUrl` null and `filmHeld` true, with no link anywhere in the answer, the
  lesson text kept and `filmHeld` false on a lesson with no film. A change to the licence applies at once on the same sign-in. An
  unlicensed teacher finishes the induction, and progress stays when a licence lapses. Parents are refused; a staff lesson cannot be
  reached through the parent route, nor a parent lesson through the staff route; the admin routes refuse everyone but the team,
  whose list keeps its links. Parent induction films, session films and resources are unchanged.
- **On screen:** the message shows for a teacher, head and practitioner. Trust leads and trainers have no induction section on the
  web (so they see no message), as before; the API rule covers them.
- **Task 76:** (a) Chrome saves "Rím Ghaeilge" as `Rím Ghaeilge.pdf`, and other accented titles the same way; a changed name is
  still refused. (b) In real Alloy v1.21.3 with a local Loki 3.7.9, 33 lines with tokens, Basic credentials and cookies all came out
  clean, and 7 ordinary lines came through unchanged. (c) 19 failed enquiries (mail server down, address refused, message refused)
  all gave 503 with no 429; then 5 went and the sixth got 429. A full scan with a real reset token is also clean (development profile, not the production stack).
- **New, low:** a download name drops apostrophes, so "O'Brien's sheet" saves as `OBriens sheet.pdf`. The shipper leaves the second
  value of `{"Cookie":["a=1","b=x"]}` and the rest of a JSON password holding a quote; it also leaves lower-case `basic` with no
  Authorization, `access_token: x` unquoted, and `X-Api-Key`, which it never claimed to catch. Nothing is known to log these.
- **Not tested:** a real bucket, so the file link and download name on Spaces are unproven; the parent app was not reopened (it does
  not call the staff induction, and its film was checked for task 75); Ireland and Australia.

**Fixes verified (updated by tester, 2026-11-06, `main` at be09a66, the Project manager's brief): pull request #47 (task 75)
passes; the two high bugs are fixed on nginx itself.** Backend 223/223, web 59, build, mobile typecheck, browser tests 69/69,
CI green on `main`.
- **Headers, on the production stack:** with `STORAGE_ORIGIN` set, `/admin/content` allows the storage host and no other, and
  `/live/<id>` has Zoom's policy, camera and microphone. The 21 other paths tried stay strict, including near misses such as
  `/admin/contents`, `/live/abc` and `/ADMIN/CONTENT`. Checked in Chrome too, not only in the headers. No real bucket or Zoom call.
- **Findings 3 to 10, all fixed:** no refused request reaches the log, by Caddy or directly to Tomcat; the app opens a parent
  induction film; a failed enquiry says so (503) and gives the contact details, and the form keeps what was typed; upload
  messages are clear and MOV points to MP4; a made-up key is refused on save on all five forms; downloads are named after
  the item, and a changed name is refused; a 500 in the filter is a problem detail; the shipper removes a bare JWT and JSON tokens,
  nested ones too, against a local Loki only. A refused email logs its code (550, 554, 451, or none with no server), no address.
- **New, low:** a download name loses letters outside plain English: "Rím Ghaeilge" saves as `Rm Ghaeilge.pdf`, which matters
  for Ireland; the file itself is fine. The shipper does not catch `"access_token"`, `"client_secret"`, a Basic header or a
  cookie value; nothing is known to log them. A failed enquiry uses up the limit of 5 an hour.
- **Not tested:** a real bucket (so the store's check that a file exists and the download name on Spaces are unproven), a real
  Zoom call, a phone, Ireland and Australia. The full log scan with a real reset token was not repeated this round.

**File hosting and logs tested (updated by tester, 2026-11-06, `main` at fc4e4b7, the Project manager's brief): two high
bugs, not ready for go-live.** Suites pass: backend 214/214, web 59, build, mobile typecheck, browser tests 69/69.
- **High, both from one line in `frontend/nginx.conf`:** `try_files $uri /index.html` in the `/admin/content` and
  `/live/<id>` locations redirects inside nginx to `location /`, whose strict headers replace the page's own. So on the
  production stack (1) `/admin/content` never sends `STORAGE_ORIGIN`, and a browser upload to a real bucket is blocked
  by the CSP, and (2) `/live/<id>` never sends Zoom's policy or camera and microphone permission, so a session shown
  inside the page (the Meeting SDK keys set) cannot load; that has been so since 2026-10-22. Browser tests miss both because
  `vite preview` sets its headers another way. Tried in a throwaway container only, not in the code:
  `try_files /index.html =404;` in those two locations sends the right policy on both pages, and the other pages stay strict.
- **Medium:** Tomcat writes a refused request's address, query string included, to the API's log (`Http11Processor`,
  once per start, full stack trace): a token and an email reached the log that way, which only a crafted or broken request
  does; the shipper redacts both. The parent app never shows a parent induction film (the website does). An enquiry whose
  email fails is lost: the visitor is told it was sent, nothing is stored, and the log no longer says whose it was.
- **Low:** `.mov` is refused as "Only video, audio, PDF and image files", and an empty file shows "size: must be greater than
  0"; a made-up key of the right shape is accepted and fails only when opened; a resource's Download is named `f.txt` on
  the local store, and with a bucket its `download` name will not apply (a browser rule, not tried); a 500 raised in a filter is
  not a problem detail; the shipper does not catch a bare JWT or JSON like `"token":"x"`.
- **To decide (the spec does not say):** staff induction films are signed for trust leads, trainers, practitioners and
  teachers of a school with no licence; parent induction films for a parent with no child; a session film for a new teacher
  before the induction is done.
- **Passes.** Upload: team only, for all ten types, on every form; wrong types, oversize and every altered link refused;
  progress, toast and retry; a key beats a pasted link; pasted links unchanged; no store works as before. Access: no signed link
  without a licence or a child, as each role, expired licence included; drafts hidden; guessed links refused; read links
  expire. Playback under the strict CSP with a real film (ranges, seeking) and no CSP errors; the parent app's web preview opens
  a signed link. Logs on the real `prod` profile: limits on every service; no token, Referer, password, initials or
  real address in any container after a reset, sign-ins, duplicates, failed email (refused address and no server) and outages,
  apart from the Tomcat line and addresses put in a request's path by hand; PostgreSQL shows no values. Shipper against a local
  Loki only: off without the profile; api and caddy arrive, labelled by country, no health checks, redaction works.
- **How debuggable an error is now:** the team can still tell what failed and where (error types, route, our line), which
  was enough for the database outage. It can no longer tell why an outside service refused: a refused email logs only
  `MailSendException`, not the 550, and not who missed it. Suggested: log safe codes such as SMTP's return code.
- **Not tested:** a real bucket, a real Zoom call, a phone, an upload link's one-hour expiry; Ireland and Australia (the same code).

**Fixes verified (updated by tester, 2026-11-06, `main` at 9d25640): pull request #39 (task 72) passes, nothing new
found.** Bugs 1 to 6 and 8 to 11 below are fixed, checked live in the UK, Ireland and Australia. Backend 189/189, web
59 unit tests and build, parent app typecheck, 68/68 browser tests. Real reminders read "(UK time)", "(Irish time)" and
"(Queensland time)"; the session-open phone notification only through `RegionAustraliaTest` and `RegionIrelandTest`
(push is off locally). A blank `data-held-in` leaves the website's line off. Tax on part refunds was tried on about 60
splits, random ones included: never negative, always adding up to the payment's tax. The app's button and banner
were tried in the web preview, including changing country back and forth. Bugs 7 and 12 stay held for decisions.

**Three-country test pass (updated by tester, 2026-11-06, `main` at 8497412, the Project manager's brief).** Backend
185/185, web 57 unit tests and build, parent app typecheck, 68/68 browser tests. On screen in UK, IE and AU, as eight
roles: the right currency everywhere (no pound sign left on web, app or emails), tax lines, year names on the class
form, the legal line in the footer, payment pages, PDFs and emails (hidden while a placeholder), no browser errors,
no overflow at phone width, 36 of 36 cross-role access checks. Refunds, about 60 checks per country: part and full,
over-refund refused, totals net on every view, gapless numbers, PDFs, only payers see their own; the Stripe webhook by
signed test events. Parent induction gate: passes, website and web preview. Country chooser: passes in the web
preview, including the refusal with unsent items. School year: Australia turns over on 1 January with figures from
zero; Ireland stays on the same year through July and August, at the end point, with sessions still offered on
weekdays (term dates are not modelled), and moves on 1 September.
**12 bugs, sent to the Project manager.** Major: reminder emails and the session-open phone notification say "(UK
time)" in Ireland and Australia, though the time is local (`SessionReminderJob`); the website says "Data is held in the
UK" everywhere (`ProgrammePage`). Medium: the Australian head's card says "school year 2026 to 2027"
(`AssessmentOverviewCard`); "Prices for UK settings" on the price chart; "Start time (UK)" on the cohort form. Minor:
"credit note" on the Australian refund form and app; "This half-term" outside the UK; the certificate email's raw date;
the app's unsent banner counts ratings as sessions; a `clientId` reused on another payment refunds nothing but answers
200; tax on a last tiny part refund can be negative; a failed Stripe refund leaves the payer with a credit note and no
word. Not tested: the team's card refund against Stripe (mock only, `RefundTest`), a real phone, real email and push,
load tests, the production stack.
Earlier (updated by tester, 2026-11-06): **pull request #29 (country settings, go-live task 25 part A) passes**
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
production compose stack and a real phone.

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
requests with `gh pr create` and check CI with `gh pr checks`. GitHub also has `rahul-work`, a teammate's branch: leave it alone. The old stash ("zoom video size tweak") was dropped on 2026-11-16 with the owner's agreement.
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

[PM: Claude, 2026-11-06] **Hosting: DigitalOcean (the owner's decision, 2026-11-06).** One server per country in London,
Amsterdam for Ireland (DigitalOcean has no Dublin) and Sydney, with DigitalOcean Spaces
for files (task 29) and off-site backups. About $80 a month for production as built (4 GB servers, database on the
server), before staging; prices to confirm. Still to settle on go-live task 9: managed databases, and
whether 4 GB is enough (test on staging). Nothing has been created yet.

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
  saying all seven colours have grids. [PM: Claude, 2026-11-16] Rebuilt again 2026-11-16 with the new name (task 84; the files are now
  "Professor Pat Preedy Programme …", the old "Motor Movers …" PDFs removed): the overview's prices are the agreed launch prices for
  the three countries (task 4), and its status page is brought up to date. `capture.mjs` now answers the parent app's country chooser
  (United Kingdom; new shot `app-country`) and waits for "Session plan" in the player. Rebuilt once more the same day after task 85 merged, so the
  screens show the new plans and prices.
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

Recent commits (newest first, 2026-11-26): (added by Dev, 2026-11-26) clean-up before the feature freeze: the Tester's low notes and the licence status; (added by Dev, 2026-11-26) the monthly launch price for the Programme at Home in all three countries; [PM: Claude, 2026-11-22] task 68 merged in the notes; (added by Dev, 2026-11-21) monthly billing for the Programme at Home, with a stand-in for Stripe; (added by Dev, 2026-11-17) session-notice tests put their own session in the window; [PM: Claude, 2026-11-17] task 57 merged in the notes; (added by Dev, 2026-11-16) films for a rhyme, a module or an exercise, in the class player and the home session; [PM: Claude, 2026-11-16] decisions, prices and the board in the notes; [PM: Claude, 2026-11-16] guides rebuilt with the new plans; (added by Dev, 2026-11-16) launch prices per country, Kay's plans; [PM: Claude, 2026-11-16] guides rebuilt with the new name and launch prices; (added by Dev, 2026-11-16) rename the platform and apps to the Professor Pat Preedy Programme, with the logo; [PM: Claude, 2026-11-16] the platform rename is decided (task 84); [PM: Claude, 2026-11-15] handoff files for restarting a session; [PM: Claude, 2026-11-15] brand and Pat's recordings in the notes; (added by Dev, 2026-11-12) home pass follow-ups (refund gap, offline sessions, the check, contact details); (added by Dev, 2026-11-12) own databases for the tests that change the clock, and the demo's session near midnight; (added by Dev, 2026-11-12) the Motor Movers at Home pass; (added by Dev, 2026-11-12) Ireland and Australia details from Pat, six-week modules at home; (added by Dev, 2026-11-10) load Pat's daily session plans; [PM: Claude, 2026-11-10] one branch and one pull request per task; (added by Dev, 2026-11-10) clean up uploaded files that were never saved; (added by Dev, 2026-11-06) keep apostrophes in download names and close the log shipper gaps; (added by Dev, 2026-11-06) keep scheduled jobs out of the backend tests; (added by Dev, 2026-11-06) limit staff induction films to licensed schools and the team; (added by Dev, 2026-11-06) small fixes from the Tester's task 75 check; (added by Dev, 2026-11-06) the Tester's file hosting and log findings; (added by Dev, 2026-11-06) log limits, safe logs and an optional log shipper; (added by Dev, 2026-11-06) file storage for films, recordings and printables; (added by Dev, 2026-11-06) the Tester's findings on country labels and refunds; (added by Dev, 2026-11-06) refunds and credit notes; (added by Dev, 2026-11-06) the parent app asks for the country; (added by Dev, 2026-11-06) parent induction before the first home session; (added by Dev, 2026-11-06) the legal entity on screens, emails and certificates; (added by Dev, 2026-11-06) the school year and year names by country; (added by Dev, 2026-11-06) the tax included in each payment; (added by Dev, 2026-11-06) amounts in the deployment's currency; [PM: Claude, 2026-11-06] one monday.com board for all open work; (added by Dev, 2026-11-06) country settings, Ireland and the open settings endpoint; (added by Dev, 2026-11-06) check the licence when a session is recorded; [PM: Claude, 2026-11-06] where Developer specs live (go-live board); (added by Dev) Pat's revised assessment content and the home cool-down; guides rebuilt without the 0 to 3 check; retire the 0 to 3 readiness check; flow sequence diagrams for every role (`docs/flows/`); Pat's answers (the other five colours' grids, Motor Movers at Home
rules); prototype guides for Pat; price chart and enquiry form on the website; guides rebuilt; Motor Movers at Home on the website; school assessments in the parent app; email a family code; Motor Movers at Home; clearer head assessment figures,
suggested point and flag reasons;
colour stage pills on every screen (web and parent app); Pat's two assessments (developmental charts and exercise scores);
Pat's answers on the new assessments; team home page, a page per course, searchable lists; dates, toasts,
in-page confirmations and live session labels; user guide and refreshed platform guides with their builder; parent
app no longer crashes on the web; card payments (Stripe); phone notifications; quizzes and certificate PDFs; heads
approve staff course places and trainers run their cohorts; training team.

