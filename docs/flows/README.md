# Platform flows: sequence diagrams

30 sequence diagrams, one file per role, showing how each role moves through the web app or parent app, the API and the services it calls (PostgreSQL, Zoom, Stripe, email and Expo push). GitHub draws the diagrams from the Mermaid source in each file.

Traced from the code at commit 93da753 (4 Nov 2026). Errors are problem details: 400 a rule broken, 403 no access, 429 a rate limit. When an endpoint or rule shown here changes, update the diagram in the same pull request.

| Role | Flows |
|---|---|
| [Everyone](0-everyone.md) | [Sign in and every request after it](0-everyone.md#sign-in); [Staff invite, set password and reset](0-everyone.md#invite); [Pay by card (Stripe Checkout)](0-everyone.md#pay-card) |
| [Class teacher](1-class-teacher.md) | [Induction gate](1-class-teacher.md#induction); [Run the daily 10-minute session](1-class-teacher.md#daily-session); [Exercise assessment (scores 1 to 4, works offline)](1-class-teacher.md#exercise-assessment); [Developmental assessment (fine and gross motor charts)](1-class-teacher.md#developmental); [Child's assessment profile and the extra-support flag](1-class-teacher.md#profile); [Invite a parent to the app](1-class-teacher.md#parent-invite) |
| [Head teacher / SENCo](2-head-teacher-and-senco.md) | [Progress dashboard and exports](2-head-teacher-and-senco.md#head-dashboard); [School setup: staff, classes and children](2-head-teacher-and-senco.md#school-setup); [Approve a staff course place](2-head-teacher-and-senco.md#course-approval) |
| [Trust or group lead](3-trust-or-group-lead.md) | [Our schools and spending](3-trust-or-group-lead.md#trust-dashboard) |
| [Practitioner](4-practitioner.md) | [Book or cancel a course place](4-practitioner.md#book); [Reminders and joining a live session](4-practitioner.md#join-live); [Quizzes and the certificate](4-practitioner.md#quiz-cert) |
| [Parent (school child)](5a-parent-school-child.md) | [Link a child and see progress](5a-parent-school-child.md#link-child); [Home activities and guidance (works offline)](5a-parent-school-child.md#home-activities); [Phone notifications](5a-parent-school-child.md#push) |
| [Parent (Motor Movers at Home)](5b-parent-motor-movers-at-home.md) | [Add a child and do the starting check](5b-parent-motor-movers-at-home.md#home-add-check); [Today's home session and ratings](5b-parent-motor-movers-at-home.md#home-today); [Share with another carer, or move to a school](5b-parent-motor-movers-at-home.md#home-family) |
| [Trainer](6-trainer.md) | [Start a live session as host](6-trainer.md#host); [Register, end-of-course assessment and certificates](6-trainer.md#register) |
| [Motor Movers team (admin)](7-motor-movers-team.md) | [Write and approve content](7-motor-movers-team.md#content); [Sign up a trust and a school](7-motor-movers-team.md#sign-up-school); [Courses, cohorts, quizzes and trainers](7-motor-movers-team.md#courses); [Record payments and the team's home page](7-motor-movers-team.md#payments-admin) |
| [Visitor and scheduled jobs](8-visitor-and-scheduled-jobs.md) | [Price chart and enquiry form](8-visitor-and-scheduled-jobs.md#enquiry); [Scheduled jobs](8-visitor-and-scheduled-jobs.md#jobs) |

## What every flow shares

- **Access is decided in the API.** After the JWT is checked, every endpoint calls `AccessPolicy`: own class, own school, own trust, linked child or own family.
- **Offline-safe records.** Assessment scores, movements and home sessions queue on the device with a `clientId`; the API skips any clientId it already has, so resending is safe.
- **Logged and non-diagnostic.** Each view or change of a school child goes into the record access log. Content approval blocks diagnostic wording, and flags say "may benefit from extra support".
