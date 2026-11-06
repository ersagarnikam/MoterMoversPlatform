# Visitor and scheduled jobs: sequence diagrams

The public website without signing in, and the jobs that run on their own.

[All roles](README.md) · Traced from the code at commit 93da753 (4 Nov 2026). Errors are problem details: 400 a rule broken, 403 no access, 429 a rate limit. When an endpoint or rule shown here changes, update the diagram in the same pull request.

## Flows

1. [Price chart and enquiry form](#enquiry)
2. [Scheduled jobs](#jobs)

<a id="enquiry"></a>

## Price chart and enquiry form

For schools shows the active plans at today's price and an enquiry form. The enquiry is emailed, not stored.

**Endpoints:** `GET /api/plans` · `POST /api/enquiries`

```mermaid
sequenceDiagram
  autonumber
  actor V as Visitor
  participant W as Website (/for-schools)
  participant API as API
  participant DB as PostgreSQL
  participant M as Mailer
  V->>W: Ask for the price guide
  W->>API: GET /api/plans
  API->>DB: active plans, price on today's date
  API-->>W: price chart (trial shown as Free)
  V->>W: Fill in enquiry, choose a plan
  W->>API: POST /api/enquiries
  API->>API: RateLimiter 5 an hour per address
  alt too many
    API-->>W: 429 Too many requests
  else hidden website field filled in (a bot)
    API-->>W: 200, nothing sent
  else ok
    API->>M: enquiry to PLATFORM_ENQUIRY_EMAIL
    API-->>W: 200
    W-->>V: Thank you message
  end
```

**Rules in the code**

- Both are open without signing in.
- 5 enquiries an hour per address, and a hidden field catches bots.
- Sent to PLATFORM_ENQUIRY_EMAIL (the owner's address for now).

<a id="jobs"></a>

## Scheduled jobs

Four jobs run on the API's clock (the deployment's time zone): session reminders, session opening, certificate expiry and deleting leavers.

**Endpoints:** `SessionReminderJob.remind` · `SessionReminderJob.announceOpening` · `CertificateService.remindExpiring` · `RetentionJob.purgeLeavers`

```mermaid
sequenceDiagram
  autonumber
  participant CL as Scheduler (Clock bean, the deployment's time zone)
  participant SR as SessionReminderJob
  participant CS as CertificateService
  participant RJ as RetentionJob
  participant DB as PostgreSQL
  participant M as Mailer
  participant PU as Expo push
  loop every 10 minutes
    CL->>SR: remind()
    SR->>DB: sessions starting within 24 h, not yet reminded
    SR->>M: learners and the trainer
    SR->>PU: learners' phones
  end
  loop every 2 minutes
    CL->>SR: announceOpening()
    SR->>DB: sessions opening now (15 min before)
    SR->>PU: Your live session is open
  end
  CL->>CS: 08:00 daily remindExpiring()
  CS->>DB: certificates expiring soon
  CS->>M: Your certificate expires soon
  CL->>RJ: 02:30 daily purgeLeavers()
  RJ->>DB: delete children who left more than 365 days ago
```

**Rules in the code**

- Time always comes from the Clock bean.
- Rate limits and job state are per API instance today.
