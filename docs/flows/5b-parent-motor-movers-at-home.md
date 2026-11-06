# Parent (Motor Movers at Home): sequence diagrams

Families whose school is not on Motor Movers. Behind HOME_PROGRAMME, off in production.

The app asks which country the parent is in at first launch: see [First launch](5a-parent-school-child.md#first-launch).

[All roles](README.md) · Traced from the code at commit 93da753 (4 Nov 2026). Errors are problem details: 400 a rule broken, 403 no access, 429 a rate limit. When an endpoint or rule shown here changes, update the diagram in the same pull request.

## Flows

1. [Add a child and do the starting check](#home-add-check)
2. [Today's home session and ratings](#home-today)
3. [Share with another carer, or move to a school](#home-family)

<a id="home-add-check"></a>

## Add a child and do the starting check

The first child creates the family. The check asks about movements up to the child's age and sets the starting colour.

**Endpoints:** `GET /api/home/available` · `POST /api/home/children` · `GET /api/home/children/{id}/check` · `POST /api/home/children/{id}/check`

```mermaid
sequenceDiagram
  autonumber
  actor P as Parent
  participant A as Parent app or website
  participant API as HomeService
  participant HR as HomeRules
  participant DB as PostgreSQL
  A->>API: GET /api/home/available (open)
  API-->>A: enabled?
  P->>A: Add child (name, age in months)
  A->>API: POST /api/home/children
  opt no family yet
    API->>DB: save Family and FamilyMember OWNER
  end
  API->>API: 6 children at most
  API->>DB: save Child.atHome(familyId, name, age)
  API-->>A: HomeChild
  A->>API: GET /api/home/children/{id}/check
  API->>HR: askAbout(milestone, age)
  API-->>A: movements to ask about
  P->>A: Yes, Not yet or Not sure for each
  A->>API: POST /api/home/children/{id}/check
  API->>API: requireFamilyOf(child)
  loop each Yes
    API->>DB: save MilestoneAchievement with age
  end
  API->>HR: startingColour(age)
  API->>DB: set stage and homeCheckOn
  API-->>A: CheckResult (colour)
```

**Rules in the code**

- Up to 6 children a family.
- Children start at Orange when young and Green from school age (Red under 2, to confirm).
- The check only records movements. It is suggested again at the start, middle and end of each school year.

<a id="home-today"></a>

## Today's home session and ratings

HomeRules builds today's plan: one rhyme a week for the rhyme colours, modules built up over five weeks from Green on.

**Endpoints:** `GET /api/home/children/{id}/today` · `GET /api/home/induction` · `POST /api/home/induction/lessons/{id}/complete` · `POST /api/home/sessions` · `POST /api/assessment/exercise-scores` · `GET /api/home/children/{id}`

```mermaid
sequenceDiagram
  autonumber
  actor P as Parent
  participant A as Parent app
  participant Q as homeOutbox
  participant API as HomeService
  participant HR as HomeRules
  participant AS as AssessmentService
  participant DB as PostgreSQL
  A->>API: GET /api/home/children/{id}/today
  API->>API: requireFamilyOf
  API->>DB: exercises for stage, sessions done, ratings
  API->>HR: plan(list, done) and reviewDue
  API->>API: CoolDowns.forModule (Green to Indigo)
  API-->>A: week, sessionTitle, exercises, newThisWeek, safetyNote, coolDown
  opt inductionDone is false
    P->>A: Read the parent induction
    A->>API: GET /api/home/induction
    loop each lesson
      A->>API: POST /api/home/induction/lessons/{id}/complete
    end
    API->>DB: inductionCompletedOn set after the last lesson
  end
  P->>A: Do the session, then the cool-down
  A->>Q: enqueue session (clientId)
  Q->>API: POST /api/home/sessions
  API->>API: requireReadyForSessions (400 until the induction is done)
  API->>DB: save HomeProgrammeSession
  opt first time or end of module
    P->>A: Rate each exercise 1 to 4
    A->>AS: POST /api/assessment/exercise-scores
    AS->>AS: requireRecordAccess (family for a home child)
    AS->>DB: save ExerciseScore
  end
  A->>API: GET /api/home/children/{id}
  API->>API: ExtraSupportRule flagged?
  API-->>A: progress, gentle prompt if flagged
```

**Rules in the code**

- A parent finishes the parent induction before their first session; each carer does their own. The API refuses a
  session until then, and finishing is permanent: lessons added later lock nobody out.
- The safety note shows before the first session.
- From Green to Indigo the session ends with the module's cool-down (the latest one sent for that colour); a rhyme
  ends with its own, in its words (Pat, 2026-11-05).
- Parents rate with the same 1 to 4 scale. A flag shows only the gentle prompt (nursery or health visitor).

<a id="home-family"></a>

## Share with another carer, or move to a school

An owner invites a carer by code or email. If the child's school joins later, the home record moves onto the school's record.

**Endpoints:** `POST /api/home/invites` · `POST /api/home/join` · `DELETE /api/home/members/{id}` · `POST /api/home/children/{id}/join-school`

```mermaid
sequenceDiagram
  autonumber
  actor O as Parent (owner)
  actor C as Carer
  participant A as Parent app
  participant API as HomeService
  participant FS as FamilyService
  participant DB as PostgreSQL
  participant M as Mailer
  O->>A: Family, invite (optional email)
  A->>API: POST /api/home/invites
  API->>DB: save FamilyInvite (code, 14 days)
  opt email given
    API->>M: you have been invited (names sender, not children)
  end
  API-->>A: code
  C->>A: Join family, type code
  A->>API: POST /api/home/join
  API->>DB: save FamilyMember CARER
  Note over O,M: The child's school joins Motor Movers
  O->>A: Link a school, type the school's parent code
  A->>API: POST /api/home/children/{id}/join-school
  API->>FS: redeem(code)
  FS->>DB: ParentLink to the school child
  API->>DB: move missing achievements, scores, sessions
  API->>DB: delete the home child
  API-->>A: LinkedChild (now a school child)
```

**Rules in the code**

- Invite emails: 20 a day per account. Joining: 10 tries an hour.
- The last member leaving deletes the family and its records.
- On join-school the school's own rows win; only missing ones move across.
