# Parent (school child): sequence diagrams

The parent app and the parent course, for a child whose school is on Motor Movers.

[All roles](README.md) · Traced from the code at commit 93da753 (4 Nov 2026). Errors are problem details: 400 a rule broken, 403 no access, 429 a rate limit. When an endpoint or rule shown here changes, update the diagram in the same pull request.

## Flows

1. [Link a child and see progress](#link-child)
2. [Home activities and guidance (works offline)](#home-activities)
3. [Phone notifications](#push)

<a id="link-child"></a>

## Link a child and see progress

The parent types the school's code in the app. The school's link decides what they can see, in plain words.

**Endpoints:** `POST /api/auth/register` · `POST /api/family/children` · `GET /api/family/children` · `GET /api/family/children/{id}/progress` · `DELETE /api/family/children/{id}`

```mermaid
sequenceDiagram
  autonumber
  actor P as Parent
  participant A as Parent app (Expo)
  participant API as FamilyService
  participant DB as PostgreSQL
  P->>A: Create account or sign in
  A->>API: POST /api/auth/register or /login
  API-->>A: JWT (30 days)
  P->>A: Link a child, type code
  A->>API: POST /api/family/children (code)
  API->>API: RateLimiter redeem 10 an hour
  API->>DB: invite by normalised code, unused, in date
  alt bad code, child left or parent links off
    API-->>A: 400 That code is not valid
  else ok
    API->>DB: redeem invite, save ParentLink
    API->>DB: log PARENT_LINKED
    API-->>A: LinkedChild
  end
  P->>A: Progress tab
  A->>API: GET /api/family/children/{id}/progress
  API->>API: AccessPolicy.requireParentOf
  API->>DB: log the view
  API-->>A: stage, sessions, plain-words progress
  opt remove child
    A->>API: DELETE /api/family/children/{id}
  end
```

**Rules in the code**

- 10 code attempts an hour per account.
- Parents see a child only through the school's link, while parent links are on and the child is on roll.
- Parents see plain words, not scores or the flag. Each view is in the school's access log.

<a id="home-activities"></a>

## Home activities and guidance (works offline)

The app shows the approved activities for the child's colour. Done activities queue on the phone and are sent when online.

**Endpoints:** `GET /api/family/home` · `GET /api/family/activities/{id}` · `POST /api/family/home/sessions` · `GET /api/family/guidance`

```mermaid
sequenceDiagram
  autonumber
  actor P as Parent
  participant A as Parent app
  participant Q as homeOutbox (device storage)
  participant API as FamilyService
  participant DB as PostgreSQL
  A->>API: GET /api/family/home?childId
  API-->>A: activities for the child's stage, home counter
  P->>A: Open activity
  A->>API: GET /api/family/activities/{id}
  P->>A: Done
  A->>Q: enqueue (clientId, activityId, childId, doneOn)
  Q->>API: POST /api/family/home/sessions (batch)
  loop each entry
    API->>DB: skip known clientId
    API->>API: linked child? within backdate limit?
    API->>DB: save HomeSessionRecord
  end
  API-->>Q: SubmitResult
  opt offline
    Q->>Q: keep and resend later
  end
  P->>A: Guidance tab
  A->>API: GET /api/family/guidance?stage
  API-->>A: approved articles
```

**Rules in the code**

- clientId makes resending safe.
- Sessions can only be backdated a few days.

<a id="push"></a>

## Phone notifications

The app registers its Expo push token. The API sends through Expo for live sessions and payments due.

**Endpoints:** `POST /api/me/push-tokens` · `POST /api/me/push-tokens/remove`

```mermaid
sequenceDiagram
  autonumber
  actor P as Parent
  participant A as Parent app
  participant E as Expo push service
  participant API as PushService
  participant DB as PostgreSQL
  A->>P: Allow notifications?
  P-->>A: Allow
  A->>E: getExpoPushTokenAsync(projectId)
  E-->>A: ExponentPushToken
  A->>API: POST /api/me/push-tokens (token, platform)
  API->>DB: save PushToken for user
  Note over API,E: Later, for example a payment recorded or a session opening
  API->>DB: tokens for these users
  API->>E: send (title, body, screen)
  E-->>A: notification
  P->>A: Tap, open Payments or Course
  opt sign out
    A->>API: POST /api/me/push-tokens/remove
  end
```

**Rules in the code**

- Off until PUSH_ENABLED=true and the EAS project id is set.
- A tap opens the screen named in the message, for example Payments.
