# Head teacher / SENCo: sequence diagrams

Their school: progress, setup, staff course places and payments.

[All roles](README.md) · Traced from the code at commit 93da753 (4 Nov 2026). Errors are problem details: 400 a rule broken, 403 no access, 429 a rate limit. When an endpoint or rule shown here changes, update the diagram in the same pull request.

## Flows

1. [Progress dashboard and exports](#head-dashboard)
2. [School setup: staff, classes and children](#school-setup)
3. [Approve a staff course place](#course-approval)

<a id="head-dashboard"></a>

## Progress dashboard and exports

Participation by class (below 70% flagged), assessments by class, flagged children, the record access log and two CSV exports.

**Endpoints:** `GET /api/schools/{id}/dashboard` · `GET /api/schools/{id}/assessment-overview` · `GET /api/schools/{id}/access-log` · `GET /api/schools/{id}/exports/governor-summary.csv` · `GET /api/schools/{id}/exports/research.csv`

```mermaid
sequenceDiagram
  autonumber
  actor H as Head
  participant W as Web app (HeadDashboardPage)
  participant API as DashboardService
  participant AS as AssessmentService
  participant DB as PostgreSQL
  H->>W: Sign in, land on Progress
  W->>API: GET /api/schools/{id}/dashboard
  API->>API: requireSchoolAccess
  API->>DB: classes, session records (20 days), stage moves
  API-->>W: assessed, moved up, participation, classes below 70%
  W->>AS: GET /api/schools/{id}/assessment-overview
  AS->>DB: per class points, movements secured, levels
  AS-->>W: children at each level, flagged children
  opt record access log
    W->>API: GET /api/schools/{id}/access-log
    API-->>W: who viewed or changed which child, when
  end
  opt exports
    H->>W: Governor summary or research data
    W->>API: GET .../exports/governor-summary.csv
    W->>API: GET .../exports/research.csv (anonymised)
    API-->>W: CSV file
  end
```

**Rules in the code**

- requireSchoolAccess: the head's own school, or the team.
- The research export is anonymised.
- Participation looks back 20 school days.

<a id="school-setup"></a>

## School setup: staff, classes and children

The head (or the team for them) adds staff, classes and children, moves children and marks leavers.

**Endpoints:** `GET /api/schools/{id}/setup` · `PUT /api/schools/{id}/settings` · `POST /api/schools/{id}/classes` · `POST /api/schools/{id}/classes/{cid}/children` · `POST /api/schools/{id}/children/{chid}/move` · `POST /api/schools/{id}/children/{chid}/left`

```mermaid
sequenceDiagram
  autonumber
  actor H as Head
  participant W as Web app (SchoolSetupPage)
  participant API as SchoolSetupService
  participant DB as PostgreSQL
  H->>W: School setup
  W->>API: GET /api/schools/{id}/setup
  API->>API: requireSchoolAccess
  API-->>W: settings, staff, classes
  opt settings
    H->>W: Full names on or off, trust can see children
    W->>API: PUT /api/schools/{id}/settings
  end
  H->>W: Add class (name, stage, teacher)
  W->>API: POST /api/schools/{id}/classes
  API->>DB: save SchoolClass
  H->>W: Add children
  W->>API: POST /api/schools/{id}/classes/{cid}/children
  API->>API: full names off? only initials accepted
  API->>DB: save Child rows, log CHILD_ADDED
  opt move or leave
    W->>API: POST .../children/{chid}/move
    API->>DB: log CHILD_MOVED
    W->>API: POST .../children/{chid}/left
    API->>DB: set leftOn, log CHILD_LEFT
  end
  Note over API,DB: RetentionJob 02:30 daily deletes leavers after 365 days
```

**Rules in the code**

- Children are initials unless the school switches full names on. While off, full names are refused and never sent.
- Leavers are deleted 365 days after leaving (RetentionJob).
- Every add, move and leave is in the record access log.

<a id="course-approval"></a>

## Approve a staff course place

Staff whose school pays ask for a place. The head approves it, which books the place and invoices the school.

**Endpoints:** `POST /api/training/cohorts/{id}/request` · `GET /api/schools/{id}/course-requests` · `POST /api/course-requests/{id}/approve` · `POST /api/course-requests/{id}/decline` · `POST /api/training/requests/{id}/withdraw`

```mermaid
sequenceDiagram
  autonumber
  actor T as Teacher
  actor H as Head
  participant W as Web app
  participant API as TrainingService
  participant DB as PostgreSQL
  participant M as Mailer
  T->>W: Training, Ask for a place
  W->>API: POST /api/training/cohorts/{id}/request
  API->>API: needs approval? already booked?
  API->>DB: save CourseRequest REQUESTED
  API->>M: A course place to approve (to each head)
  M-->>H: email with link to /school/payments
  H->>W: School payments, course requests
  W->>API: GET /api/schools/{id}/course-requests
  alt approve
    H->>W: Approve
    W->>API: POST /api/course-requests/{id}/approve
    API->>API: still REQUESTED, seat free
    API->>DB: save Enrolment
    API->>DB: save Payment by school, COURSE, DUE
    API->>DB: request APPROVED by head
    API->>M: Your course place is booked
    M-->>T: email
  else decline
    W->>API: POST /api/course-requests/{id}/decline
    API->>M: Your course request
  end
  opt teacher changes their mind first
    T->>W: Withdraw
    W->>API: POST /api/training/requests/{id}/withdraw
  end
```

**Rules in the code**

- Approval is needed when the learner has a school, is not a head, and the course costs money.
- Approving checks the cohort still has a seat.
