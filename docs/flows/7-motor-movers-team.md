# Motor Movers team (admin): sequence diagrams

Content, schools and licences, courses and trainers, plans, prices and payments.

[All roles](README.md) · Traced from the code at commit 93da753 (4 Nov 2026). Errors are problem details: 400 a rule broken, 403 no access, 429 a rate limit. When an endpoint or rule shown here changes, update the diagram in the same pull request.

## Flows

1. [Write and approve content](#content)
2. [Sign up a trust and a school](#sign-up-school)
3. [Courses, cohorts, quizzes and trainers](#courses)
4. [Record payments and the team's home page](#payments-admin)

<a id="content"></a>

## Write and approve content

Sessions, home activities, guidance, resources and induction lessons start as drafts. Approval runs the wording guard. A
film, recording or printable is a pasted link, or a file uploaded first: `POST /api/admin/files` (ADMIN; type and size
checked) gives an upload link, the browser sends the file straight to the store, and the form saves its key. The draft
shows Open the file. The content page alone may reach the store, so it opens with a full page load.

**Endpoints:** `POST /api/admin/content` · `GET /api/admin/content` · `POST /api/admin/content/{id}/approve` · `POST /api/admin/content/{id}/reject`

```mermaid
sequenceDiagram
  autonumber
  actor A as Team member
  participant W as Web app (ContentPage)
  participant API as ContentController
  participant G as WordingGuard
  participant DB as PostgreSQL
  A->>W: New session (stage, module, day, plan)
  W->>API: POST /api/admin/content
  API->>DB: save ProgrammeSession DRAFT
  W->>API: GET /api/admin/content
  API->>G: check(wording)
  API-->>W: drafts with wording issues listed
  A->>W: Approve
  W->>API: POST /api/admin/content/{id}/approve
  API->>G: requireClean(wording)
  alt diagnostic term found
    G-->>W: 400 Wording check failed
  else clean
    API->>DB: status APPROVED, reviewed by, at
    API-->>W: ContentView
  end
  opt reject
    W->>API: POST /api/admin/content/{id}/reject
  end
  Note over API,DB: Teachers' Today now offers the session
```

**Rules in the code**

- Diagnostic terms block approval (WordingGuard).
- Only approved content reaches teachers and parents.
- The same pattern covers /home-activities, /guidance, /resources and /induction.
- An upload link takes one file of the agreed type and size; video, audio, PDF and image types only.
- An uploaded file gets a signed link only after the reader's access checks; pasted links are sent as they are.

<a id="sign-up-school"></a>

## Sign up a trust and a school

The team signs schools up: trust and lead, school with plan and licence, an agreed price, parent links, then setup.

**Endpoints:** `POST /api/admin/trusts` · `POST /api/admin/trusts/{id}/leads` · `POST /api/admin/schools` · `POST /api/admin/schools/{id}/agreed-price` · `POST /api/admin/schools/{id}/parent-links` · `POST /api/admin/plans/{code}/prices`

```mermaid
sequenceDiagram
  autonumber
  actor A as Team member
  participant W as Web app
  participant API as Admin controllers
  participant PS as PasswordService
  participant DB as PostgreSQL
  participant M as Mailer
  opt plans and prices
    A->>W: Plans and prices
    W->>API: POST /api/admin/plans, POST .../{code}/prices
    API->>DB: save LicencePlan, LicencePlanPrice (dated)
  end
  A->>W: New trust
  W->>API: POST /api/admin/trusts
  W->>API: POST /api/admin/trusts/{id}/leads
  API->>DB: save AppUser TRUST_LEAD
  API->>PS: sendInvite
  PS->>M: account email to lead
  A->>W: New school (name, trust, plan, stages)
  W->>API: POST /api/admin/schools
  API->>DB: save School and Licence
  W->>API: POST /api/admin/schools/{id}/agreed-price
  W->>API: POST /api/admin/schools/{id}/parent-links (on)
  A->>W: /admin/schools/{id}/setup
  Note over W,API: Same setup flow as the head: staff, classes, children
```

**Rules in the code**

- There is no self-service school sign-up yet.
- Prices are dated, so a new price applies from its date.

<a id="courses"></a>

## Courses, cohorts, quizzes and trainers

Adding a cohort schedules its live sessions. Trainers get an invite and their own Zoom login.

**Endpoints:** `POST /api/admin/trainers` · `PUT /api/admin/trainers/{id}/zoom-email` · `POST /api/admin/courses` · `POST /api/admin/courses/{id}/cohorts` · `PUT /api/admin/cohorts/{id}` · `POST /api/admin/courses/{id}/quizzes` · `GET /api/admin/cohorts/{id}/enrolments`

```mermaid
sequenceDiagram
  autonumber
  actor A as Team member
  participant W as Web app (CoursesPage)
  participant API as CourseAdminService
  participant LS as LiveSessionService
  participant QS as QuizService
  participant DB as PostgreSQL
  participant M as Mailer
  A->>W: Add trainer
  W->>API: POST /api/admin/trainers
  API->>DB: save AppUser TRAINER
  API->>M: invite email
  W->>API: PUT /api/admin/trainers/{id}/zoom-email
  A->>W: New course (track, price, live sessions)
  W->>API: POST /api/admin/courses
  A->>W: Add cohort (trainer, start, capacity)
  W->>API: POST /api/admin/courses/{id}/cohorts
  API->>DB: save Cohort
  API->>LS: schedule(cohort)
  LS->>DB: save CohortSession 1..n
  A->>W: Add quiz after session n
  W->>QS: POST /api/admin/courses/{id}/quizzes
  QS->>DB: save CourseQuiz and questions
  A->>W: Course page, bookings
  W->>API: GET /api/admin/cohorts/{id}/enrolments
```

**Rules in the code**

- A quiz that learners have taken cannot be deleted, only changed.
- Changing a cohort reschedules its sessions.

<a id="payments-admin"></a>

## Record payments and the team's home page

The team records invoices for schools or people and marks bank transfers paid. Home lists today's sessions, drafts, overdue invoices and renewals.

**Endpoints:** `POST /api/admin/payments` · `POST /api/admin/payments/{id}/paid` · `POST /api/admin/payments/{id}/cancel` · `POST /api/admin/payments/{id}/refund` · `GET /api/payments/refunds/{id}/credit-note.pdf` · `GET /api/admin/payments/summary` · `GET /api/admin/overview`

```mermaid
sequenceDiagram
  autonumber
  actor A as Team member
  participant W as Web app
  participant API as PaymentService
  participant PU as PushService
  participant S as Stripe
  participant M as Mailer
  participant DB as PostgreSQL
  A->>W: Home (/admin)
  W->>API: GET /api/admin/overview
  API-->>W: sessions today, drafts, overdue, renewals, Zoom clashes, expiring certificates
  A->>W: Record payment (school or person, purpose, amount)
  W->>API: POST /api/admin/payments
  API->>API: exactly one payer, date not in future
  API->>DB: save Payment (DUE, or PAID if paidOn given) with the country's tax rate
  opt person payer and DUE
    API->>PU: notify(payer, A payment is due, /payments)
  end
  Note over A,DB: Bank transfer arrives
  A->>W: Mark paid
  W->>API: POST /api/admin/payments/{id}/paid
  opt cancel
    W->>API: POST /api/admin/payments/{id}/cancel
  end
  opt refund a paid payment (all or part)
    A->>W: Amount and reason, confirm
    W->>API: POST /api/admin/payments/{id}/refund
    API->>API: lock the payment, not more than is left
    alt paid by card
      API->>S: POST /v1/refunds (idempotency key)
      S-->>API: refund, or refused: nothing is saved
    else paid another way
      Note over A,API: the money goes back by hand
    end
    API->>DB: save the refund with the next credit note number
    API->>M: credit note PDF to the payer or the school's heads
  end
  W->>API: GET /api/admin/payments/summary
  API-->>W: spending summaries, net of refunds
```

**Rules in the code**

- The payer is a school or a person, not both. Team accounts never pay.
- A DUE payment for a person also goes to their phone.
- A payment stays PAID when refunded; several part refunds are allowed, never more than was paid. Totals are net, and in
  spend by month a refund counts in the month the money went back.
- Credit note numbers have no gaps per deployment: a refund Stripe refuses takes no number.
- A repeated request (same `clientId`, same payment) changes nothing; the same `clientId` on another payment is refused.
- Prices include tax. A payment keeps the tax rate in force when it was created (`tax_rate_bp`); payment views show
  "includes VAT £x" (amount x rate / (1 + rate), rounded to the minor unit). Older payments have no rate and no tax line.
