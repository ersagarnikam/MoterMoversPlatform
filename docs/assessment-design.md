# Assessment design (draft, waiting on Pat)

Pat's feedback (2026-10-20) replaces today's single 0 to 3 check with two assessments. This note sets out
how the platform would store and show them, so that once Pat's charts and exercise descriptions arrive the
work is mostly loading data. Open questions are at the end; each has a suggested default.

On screen it is always "assess" and "assessment", never "screen". The current check lives in the code as
`screening` (tables `screening_item`, `screening_item_level`, `screening_result`); it stays in use until the
new assessments replace it, then is retired.

## The two assessments

**1. Developmental assessment.** Two charts, fine motor and gross motor, from birth to 7 years. Each
movement has an indicative age. Done at the beginning, middle and end of the school year: when a child can
do a movement confidently and securely, the teacher records the child's age in months. It gives a starting
point and shows progress across the year.

**2. Exercise assessment.** Each programme exercise (for example Starfish) has four descriptions: 1 Not yet,
2 Developing, 3 Nearly there, 4 Confident and accurate. A child is scored at the start, at the end of each
module the exercise appears in, and at the end of the colour ("Final end of Green").

"Module" is what the code calls a `unit`: sessions are colour stage, module, day. Screens already say Module.

## Storage

New tables arrive in one Flyway migration (V7 or later) once the questions below are settled. The charts and
exercises are programme content, so they load through `seed/StarterContent` and can be edited by the team,
rather than being fixed in a migration.

### Developmental assessment

| Table | Holds | Notes |
|---|---|---|
| `milestone` | One movement on a chart | `chart` (FINE, GROSS), `position`, `description`, `age_from_months`, `age_to_months`. "6 to 12 months" is 6 to 12; "Birth" is 0 to 0; rows with no age of their own take the one above, as on Pat's chart |
| `milestone_achievement` | That a child can do a movement securely | `child_id`, `milestone_id`, `achieved_on`, `age_months`, `window` (START, MIDDLE, END), `school_year`, `recorded_by`. One row per child and movement: once secure, it stays secure. Deleted with the child |

Progress for the year is the number of movements secure at each window. The profile can also show which
movements are secure below, at or above the child's age, without any score or centile.

### Exercise assessment

| Table | Holds | Notes |
|---|---|---|
| `exercise` | A programme exercise | `stage` (colour), `code`, `title`, `instructions` (the movement text), `position` |
| `exercise_level` | The four descriptions | `exercise_id`, `score` 1 to 4, `description` |
| `module_exercise` | Which exercises a module features | `stage`, `module`, `exercise_id`. Decides what is assessed at the end of each module |
| `exercise_score` | One score | `client_id` (for the offline queue), `child_id`, `exercise_id`, `point` (START, END_OF_MODULE, END_OF_COLOUR), `module` (for END_OF_MODULE), `score` 1 to 4, `assessed_on`, `recorded_by`. A later score at the same point replaces the earlier one |

This gives exactly Pat's table per exercise: Start, End module 1, 2, 3, Final end of the colour, each with a
date and a score.

### What carries over from today

- The offline queue (`frontend/src/outbox.ts`): scores are saved on the device and sent when the
  connection allows. Both assessments use it.
- Access control in `AccessPolicy`: teachers assess their own class, heads see their school, trust or group
  leads summaries only, parents only linked children.
- Retention: assessment rows are deleted with the child, 365 days after they leave.
- The wording check applies to exercise and milestone descriptions as it does to all content.

## Screens

- **Teacher, Assess children.** Two choices: *Developmental (start, middle or end of year)* and
  *Exercises*.
  - **Developmental:** one child at a time. The charts open at the movements around the child's indicative
    age. The teacher taps "Secure now" for each movement the child can do confidently, and the age in months
    is recorded.
  - **Exercises:** the whole class does an exercise together, so scoring is one exercise at a time with the
    class list. For each child the teacher taps 1 to 4, with the four descriptions on screen. The platform
    suggests the right point: the start of the colour, the end of the current module, or the end of the
    colour.
- **Readiness profile.** The developmental charts with secure movements and the window each was secured in,
  then Pat's table for each exercise in the current colour. "Not a diagnosis" stays on every profile.
- **Head teacher.** The share of children assessed in each window. Exercise scores by class, as the number of
  children at each level, not averages.
- **Trust or group lead.** The same summaries per school, never individual children.
- **Parent app.** Depends on Pat's answer (question 6).

## Open questions for Pat (asked 2026-10-21)

| # | Question | Suggested default if there is no answer |
|---|---|---|
| 1 | The full fine and gross motor charts | Needed: nothing to build on without them |
| 2 | Exercises per module and colour, with the four descriptions each | Needed. Starfish is the worked example |
| 3 | Is "Start" the start of the colour or of the school year? | The start of the colour, since the table ends at "Final end of Green" |
| 4 | Month and year of birth, or the teacher types the age in months? | The teacher types it, so no new personal data is held. If month and year of birth are wanted, this is a change to record in the privacy notice and DPIA |
| 5 | Is the readiness profile rebuilt from the two assessments, or replaced? | Rebuilt: keep the profile and its "Not a diagnosis" note, fed by the new data |
| 6 | What heads, groups and parents should see | Heads and groups as above; parents see the exercises their child is working on, not scores |
| 7 | Do exercise scores decide moving on to the next colour? | No automatic rule: the teacher decides, and the platform shows how many children are at 3 or 4 |

## Order of work once the answers arrive

1. Migration with the tables above. Load the charts and exercises through `StarterContent`.
2. API: list milestones and exercises, record achievements and scores (offline-safe, through `AccessPolicy`),
   and the profile and dashboard summaries.
3. Teacher screens, then the profile, then the head and group dashboards, then the parent app if wanted.
4. Retire the current 0 to 3 check and its readiness rules, and update the induction lesson on assessment.
5. Regenerate the flow guide PDFs.
