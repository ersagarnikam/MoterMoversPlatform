# Assessment design (Pat's answers in, 2026-10-27)

Pat's feedback (2026-10-20) replaces today's single 0 to 3 check with two assessments. This note sets out
how the platform stores and shows them. Pat replied on 2026-10-27 with the assessment booklet (the
developmental charts) and the Green and Yellow exercise grids; her answers and what is still missing are in
**Pat's answers** below. The source documents are kept outside the repository (the owner's `F:\IHA\Pat`).

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
- **Readiness profile.** Both assessments, as Pat asked: the developmental charts with secure movements, the
  age in months and the window each was secured in, then Pat's table for each exercise in the current colour
  (point, date, score). A note when the child "may benefit from extra support" (the low-score flag). "Not a
  diagnosis" stays on every profile.
- **Colour summary.** Pat's summary sheet: each exercise's end score, the totals (for Green, out of 108) and the
  two comments, *personal, social and emotional development* and *academic achievement*.
- **Class view.** The children flagged for extra support, for the teacher to act on.
- **Head teacher.** Progress on both assessments: the share of children assessed in each window and movements
  secured, and exercise scores by class as the number of children at each level, not averages. Flagged
  children per class.
- **Trust or group lead.** The same summaries per school, never individual children.
- **Parent app.** Not answered yet (question 6 under *Still to settle*).

## Pat's answers (email of 2026-10-27)

The questions were asked on 2026-10-21. What Pat sent and said:

| # | Question | Pat's answer | What the platform does |
|---|---|---|---|
| 1 | The fine and gross motor charts | **Received**: the *Motor Movers Individual Assessment* booklet. Fine motor: about 64 movements, birth to 7 years. Gross motor: about 80 movements, birth to 7 years. Each has an indicative age band; the teacher records the age in months when a movement is secure. Three dated comment boxes per chart (start, middle and end of year) | Load both charts as `milestone` rows, with a dated comment per window |
| 2 | Exercises per module, with four descriptions each | **Green and Yellow received** as examples. Green: 27 exercises across modules 1 to 6, each with the movement, the four descriptions and its points. Yellow: 18 nursery-rhyme exercises, each with the rhyme, the actions and the four descriptions | Load Green and Yellow; the other five colours are still needed (see below) |
| 3 | What "Start" means | "For the exercises it is the start and end of the colour." Green exercises are also scored at the end of each module they appear in (Yes and No: Start, End module 1, End module 2, Final end of Green). Yellow exercises have only Start and End | Start is the start of the colour. Points per exercise come from the grid: Start, End of each module it is in (Green), End of the colour |
| 4 | Month and year of birth, or age in months | "The age of the child in months is better to correlate with the milestone." | The teacher records the age in months; no date of birth is stored (see the booklet header below) |
| 5 | Is the readiness profile rebuilt or replaced? | "The profile should include both assessments: how they are progressing with the movements and how they are progressing overall against the developmental milestone." | Rebuilt from both assessments, keeping "Not a diagnosis" |
| 6 | What heads and groups see | "Heads/Leaders would need to see the overview of progress for both." | Heads (and trust or group leads, as summaries) see progress on both assessments |
| 7 | Do scores decide moving on to the next colour? | "There is currently not a rule about moving on as there is repetition and overlap when they start the next colour." | No moving-on rule |
| new | Flagging children who need more support | "If the child has a pattern of low scores this would flag when additional intervention is needed." | A flag on the profile and in the class and head views; the rule is still to agree (below) |

Each colour also has a **summary sheet**: the end score of every exercise, a total for the colour (Green: out of
108, 27 exercises × 4, in two columns, probably start and end), and two comments: *personal, social and
emotional development* and *academic achievement*.

### Still to settle with Pat

1. **The other colours.** Exercise grids for Red, Orange, Blue, Indigo and Violet.
2. **Gaps in the grids sent.**
   - Green exercise 4, *Motor Spin*, has no four descriptions.
   - Yellow *Jack and Jill* (11) has all four descriptions copied from *Grand Old Duke of York* (10), and
     *Twinkle Twinkle Little Star* (13) has those of *The Wheels on the Bus* (12).
   - Small typos: "Roudabout March", "Swop Overs", "Half A Pund Of Pudding Rice", "Hickory Tickory Tock",
     "Assessmentt", "achivement".
   - The booklet's gross motor chart repeats some 0 to 6 months rows (for example "Kicks legs vigorously when
     lying on back" three times), has a blank row in 3 to 4 years, and has typos ("caching games", "rise a
     two-wheel bike").
3. **What "a pattern of low scores" is.** Suggested default: flag a child who scores 1 or 2 on the same exercise
   at two points in a row, or 1 or 2 on half or more of the exercises assessed at the end of a module. The flag
   says the child *may benefit from extra support*; it never names a cause.
4. **The booklet header.** The paper booklet asks for date of birth, birth history and "any other significant
   factors". The platform holds initials (or full names when the school chooses), not dates of birth, and
   birth history is health information. Suggested default: the platform does not hold these; the teacher
   records age in months, as Pat prefers.
5. **The summary totals.** Are the two "X/108" columns the start and end totals? Suggested default: yes.
6. **What parents see.** Not answered. Suggested default: the exercises their child is working on and the
   movements now secure, without scores.

## Order of work

1. Migration with the tables above, plus the colour summary (comments and totals) and the low-score flag.
   Load the booklet's charts and the Green and Yellow grids through `StarterContent`, for the team to check.
2. API: list milestones and exercises, record achievements and scores (offline-safe, through `AccessPolicy`),
   and the profile and dashboard summaries.
3. Teacher screens, then the profile, then the head and group dashboards, then the parent app if wanted.
4. Retire the current 0 to 3 check and its readiness rules, and update the induction lesson on assessment.
5. Regenerate the user guide and flow guide PDFs.
