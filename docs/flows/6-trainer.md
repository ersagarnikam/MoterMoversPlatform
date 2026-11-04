# Trainer: sequence diagrams

Only the cohorts the team assigns: run sessions on Zoom and keep the register.

[All roles](README.md) · Traced from the code at commit 93da753 (4 Nov 2026). Errors are problem details: 400 a rule broken, 403 no access, 429 a rate limit. When an endpoint or rule shown here changes, update the diagram in the same pull request.

## Flows

1. [Start a live session as host](#host)
2. [Register, end-of-course assessment and certificates](#register)

<a id="host"></a>

## Start a live session as host

The trainer's own Zoom login hosts, so sessions can run at the same time. The portal embeds the meeting beside the rules and register.

**Endpoints:** `GET /api/training/live` · `POST /api/training/live/{id}/join`

```mermaid
sequenceDiagram
  autonumber
  actor TR as Trainer
  participant W as Web app (LiveRoomPage)
  participant API as LiveSessionService
  participant DB as PostgreSQL
  participant Z as Zoom (S2S OAuth API)
  participant SDK as Zoom Meeting SDK
  TR->>W: Live sessions (own cohorts only)
  W->>API: GET /api/training/live
  TR->>W: Start session
  W->>API: POST /api/training/live/{id}/join
  API->>API: canHost(me, cohort)?
  alt another trainer's cohort
    API-->>W: 403 This session belongs to another trainer's cohort
  else host
    API->>Z: hostToken(zoom_email or ZOOM_HOST_EMAIL)
    Z-->>API: ZAK
    API->>DB: lock CohortSession
    opt no meeting yet
      API->>Z: createMeeting(...)
      Z-->>API: id, passcode, join URL
      API->>DB: attach meeting
    end
    alt embedded
      API->>API: ZoomSignature.sign(HOST)
      API-->>W: sdkKey, signature, meeting id, ZAK
      W->>SDK: start meeting in page, speaker view
    else not embedded
      API-->>W: zoom.us/s/{id}?zak=...
      W->>Z: open Zoom as host
    end
  end
```

**Rules in the code**

- canHost: the cohort's trainer or the team. Another trainer gets 403.
- The host token is fetched before the row lock, so a Zoom refusal creates no meeting.
- Host user is the trainer's zoom_email, else ZOOM_HOST_EMAIL.

<a id="register"></a>

## Register, end-of-course assessment and certificates

During the session the trainer marks who is present (or everyone who joined). After the course they record pass or not.

**Endpoints:** `GET /api/admin/live/{id}/register` · `POST /api/admin/live/{id}/register/mark-joined` · `PUT /api/admin/live/{id}/register/{enrolmentId}` · `GET /api/trainer/cohorts` · `POST /api/admin/enrolments/{id}/assessment`

```mermaid
sequenceDiagram
  autonumber
  actor TR as Trainer
  participant W as Web app
  participant API as LiveSessionService
  participant TS as TrainingService
  participant CS as CertificateService
  participant DB as PostgreSQL
  participant M as Mailer
  W->>API: GET /api/admin/live/{id}/register
  API->>API: requireHost
  API-->>W: booked learners, who joined, who is present
  alt mark everyone who joined
    TR->>W: Mark joined as present
    W->>API: POST .../register/mark-joined
  else one learner
    TR->>W: Tick present
    W->>API: PUT .../register/{enrolmentId} (present)
  end
  API->>DB: save SessionAttendance
  API->>CS: issueIfComplete(enrolment)
  Note over TR,M: After the last session
  TR->>W: My cohorts
  W->>TS: GET /api/trainer/cohorts
  TR->>W: End-of-course assessment, passed
  W->>TS: POST /api/admin/enrolments/{id}/assessment
  TS->>TS: requireHost
  TS->>DB: record assessment
  TS->>CS: issueIfComplete
  opt all done
    CS->>M: certificate PDF to learner
  end
```

**Rules in the code**

- Attendance cannot change once the certificate is issued.
- Every register mark and assessment re-checks certificate eligibility.
