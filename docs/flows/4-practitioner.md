# Practitioner: sequence diagrams

Live courses: booking, joining on Zoom, quizzes and the certificate.

[All roles](README.md) · Traced from the code at commit 93da753 (4 Nov 2026). Errors are problem details: 400 a rule broken, 403 no access, 429 a rate limit. When an endpoint or rule shown here changes, update the diagram in the same pull request.

## Flows

1. [Book or cancel a course place](#book)
2. [Reminders and joining a live session](#join-live)
3. [Quizzes and the certificate](#quiz-cert)

<a id="book"></a>

## Book or cancel a course place

The catalogue shows courses for the account's track. Booking takes a seat. For staff with a school, the school is invoiced.

**Endpoints:** `GET /api/training/courses` · `POST /api/training/cohorts/{id}/enrol` · `GET /api/training/enrolments/mine` · `POST /api/training/enrolments/{id}/cancel`

```mermaid
sequenceDiagram
  autonumber
  actor P as Practitioner
  participant W as Web app (TrainingPage)
  participant API as TrainingService
  participant DB as PostgreSQL
  P->>W: Training
  W->>API: GET /api/training/courses
  API-->>W: courses and cohorts for my track, seats left
  P->>W: Book
  W->>API: POST /api/training/cohorts/{id}/enrol
  API->>API: requireTrack (course open to my account type)
  API->>DB: already booked? return that place
  alt school pays and I am not a head
    API-->>W: 400 Ask for a place instead
  else book
    API->>DB: count enrolments vs capacity
    API->>DB: save Enrolment
    opt has a school and course is paid
      API->>DB: save Payment by school DUE
    end
    API-->>W: EnrolmentView
  end
  opt cancel
    P->>W: Cancel place
    W->>API: POST /api/training/enrolments/{id}/cancel
    API->>API: own place, no certificate yet
    API->>DB: DUE invoice cancelled, delete enrolment
    API-->>W: message (refund note if already paid)
  end
```

**Rules in the code**

- A full cohort refuses the booking.
- Cancelling cancels a DUE invoice. A PAID one is refunded by the team.
- A place cannot be cancelled once the certificate is issued.

<a id="join-live"></a>

## Reminders and joining a live session

Reminders go out 24 hours ahead. From 15 minutes before the start the learner can join, inside the portal or in Zoom.

**Endpoints:** `GET /api/training/live` · `POST /api/training/live/{id}/join`

```mermaid
sequenceDiagram
  autonumber
  participant J as SessionReminderJob
  participant M as Mailer
  participant PU as Expo push
  actor P as Practitioner
  participant W as Web app (LiveRoomPage)
  participant API as LiveSessionService
  participant DB as PostgreSQL
  participant Z as Zoom API
  J->>DB: every 10 min: sessions in the next 24 h
  J->>M: Reminder to learners and trainer
  J->>PU: Live session is coming up
  J->>PU: every 2 min: Your live session is open (15 min before)
  P->>W: Live sessions, Join
  W->>API: POST /api/training/live/{id}/join
  API->>DB: booked on this cohort?
  alt not booked, finished or not open yet
    API-->>W: 403 or 400
  else ok
    API->>DB: record SessionJoin
    alt Zoom not configured
      API-->>W: rules only, no meeting
    else Zoom configured
      API->>DB: lock CohortSession for update
      opt no meeting yet
        API->>Z: createMeeting(host user, title, start, length)
        Z-->>API: meeting id, passcode, join URL
        API->>DB: attach meeting to session
      end
      alt Meeting SDK keys set
        API->>API: ZoomSignature.sign(PARTICIPANT)
        API-->>W: sdkKey, signature, meeting id, passcode, join URL
        W->>Z: Meeting SDK joins inside /live/{id}
      else no SDK keys
        API-->>W: join URL
        W->>Z: open Zoom in a new tab
      end
    end
  end
```

**Rules in the code**

- Only booked learners can join, from 15 minutes before until the session ends.
- The first person to join creates the Zoom meeting, under a row lock so only one is made.
- Without the Meeting SDK keys the session opens in Zoom in a new tab. The parent app always uses the Zoom app.

<a id="quiz-cert"></a>

## Quizzes and the certificate

Each quiz opens when its live session starts. When attendance, quizzes and the end-of-course assessment are all done, the certificate is emailed as a PDF.

**Endpoints:** `GET /api/training/enrolments/{id}/quizzes` · `GET .../quizzes/{qid}` · `POST .../quizzes/{qid}/attempts` · `GET /api/training/enrolments/{id}/certificate.pdf`

```mermaid
sequenceDiagram
  autonumber
  actor P as Practitioner
  participant W as Web app (QuizPage)
  participant API as QuizService
  participant CS as CertificateService
  participant DB as PostgreSQL
  participant M as Mailer
  W->>API: GET /api/training/enrolments/{id}/quizzes
  API-->>W: quizzes, open or not, best score
  P->>W: Open quiz
  W->>API: GET .../quizzes/{qid}
  API->>API: own enrolment, session started or marked present
  API-->>W: questions without answers
  P->>W: Answer every question
  W->>API: POST .../quizzes/{qid}/attempts
  API->>API: mark answers, score percent, passed?
  API->>DB: save QuizAttempt
  opt passed
    API->>CS: issueIfComplete(enrolment)
    CS->>CS: attendance, assessment passed, all quizzes passed?
    opt eligible
      CS->>DB: issue certificate with expiry date
      CS->>M: Your certificate, PDF attached (PDFBox)
    end
  end
  API-->>W: score, wrong questions, certificate issued?
  P->>W: Certificate
  W->>API: GET /api/training/enrolments/{id}/certificate.pdf
  API-->>W: PDF
```

**Rules in the code**

- Only the learner takes their quizzes, and every question must be answered.
- Certificate eligibility is checked after a quiz pass, a register mark and the trainer's assessment.
- Daily at 08:00 learners whose certificate is close to expiry are emailed.
