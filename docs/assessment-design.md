# Assessment design (all seven colours, 2026-11-04)

**Status.** Built on 2026-10-27 for Green and Yellow (migration `V15__assessments.sql`, the `assessment` package in
the API, the teacher's Assess page and developmental charts, the profile, and the head and trust or group summaries),
and on 2026-11-04 for the other five colours, from the grids Pat sent on 2026-11-03. See **What was built** at the
end. Pat agreed the suggested defaults on 2026-11-03 (**Pat's answers of 2026-11-03**); what is still open is under
*Still to settle with Pat*. The 0 to 3 readiness check was retired on 2026-11-04 (owner's decision; migration
`V17__retire_readiness_check.sql`).

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
- **The child's profile.** Both assessments, as Pat asked: the developmental charts with secure movements, the
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
- **Parent app.** Movements secure on each chart, and each exercise's first and latest rating in plain words (Not yet,
  Getting there, Nearly, Got it); no scores and no flag. Agreed by Pat on 2026-11-03.

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

## Pat's answers (email of 2026-11-03)

Questions sent on 2026-11-03 (the owner's email, 16 questions; 7 to 16 are about Motor Movers at Home, in
`docs/home-programme-design.md`). Pat's replies on the assessments in school:

| # | Question | Pat's answer | What the platform does |
|---|---|---|---|
| 1 | The other five colours' grids | **Received**: Red and Orange (18 rhymes each, start and end), Blue (40 exercises), Indigo (34) and Violet (24), each across modules 1 to 6 | All seven colours loaded (see *What was built*) |
| 2 | The gaps in the grids (Motor Spin; Jack and Jill; Twinkle Twinkle) | A revised file was attached, but it arrived **empty (0 bytes)**, also in the saved `.msg`. **Sent again 2026-11-05** ("Revised 2", PDF and Word) | Loaded: see *Pat's revisions (2026-11-05)* |
| 3 | The flag rule | **Yes** | `ExtraSupportRule` stays as it is |
| 4 | No date of birth or birth history | **Yes** | Not held |
| 5 | What parents see (plain words, no scores, no flag) | **Yes** | `ParentProgress` stays as it is |
| 6 | Retire the 0 to 3 check | Asked what "the old 0 to 3 readiness check" means | **Retired 2026-11-04** (the owner decided; not asked again): see *Retiring the 0 to 3 check* |

### Still to settle with Pat

1. **Copied descriptions still open** after Pat's revisions of 2026-11-05: Orange *See Saw Marjory Daw* (2) keeps the
   descriptions *Rock a Bye Baby* had before (they speak of arm swings and turns, which See Saw does not have), and
   Violet *Motor Lift Four* still has those of *Motor Lift Three*. Pat sent new descriptions for Rock a Bye Baby only.
2. **Gaps in the grids sent** (2026-10-27): Motor Spin, Jack and Jill and Twinkle Twinkle were settled on 2026-11-05.
   - Small typos: "Roudabout March", "Swop Overs", "Half A Pund Of Pudding Rice", "Hickory Tickory Tock",
     "Assessmentt", "achivement".
   - The booklet's gross motor chart repeats some 0 to 6 months rows (for example "Kicks legs vigorously when
     lying on back" three times), has a blank row in 3 to 4 years, and has typos ("caching games", "rise a
     two-wheel bike").
3. **The summary totals.** Are the two "X/108" columns the start and end totals? Suggested default: yes (not asked
   again on 2026-11-03).

Settled on 2026-11-03: the flag rule, not holding a date of birth or birth history, and what parents see. Decided on
2026-11-04: the 0 to 3 check is retired.

## Order of work

1. Migration with the tables above, plus the colour summary (comments and totals) and the low-score flag.
   Load the booklet's charts and the Green and Yellow grids through `StarterContent`, for the team to check.
2. API: list milestones and exercises, record achievements and scores (offline-safe, through `AccessPolicy`),
   and the profile and dashboard summaries.
3. Teacher screens, then the profile, then the head and group dashboards, then the parent app if wanted.
4. Retire the current 0 to 3 check and its readiness rules, and update the induction lesson on assessment.
5. Regenerate the user guide and flow guide PDFs.

Steps 1 to 3 are done for all seven colours, and the parent app shows the plain-words summary (2026-10-28). Step 4 is
done (2026-11-04, below). Step 5 is still to do.

## What was built (2026-10-27)

**Content.** `backend/src/main/resources/assessment/milestones.json` (fine motor 59 movements, gross motor 74),
`exercises-green.json` (27) and `exercises-yellow.json` (18), made from Pat's Word documents. `AssessmentContent` loads
them on start-up in every deployment, adding only what is missing (the charts on an empty platform, a colour's
exercises if it has none). To add a colour: a new `exercises-<colour>.json` and the colour in
`AssessmentContent.STAGES_WITH_EXERCISES`. To correct a description already loaded, change the file: since 2026-11-05
the next start brings each exercise's title, instructions and descriptions up to date (never its points, since scores
are recorded against them). A test checks every text against the wording guard.

Changes made to Pat's text when loading it, for her to confirm:
- Typos corrected: "Roudabout March" to Roundabout March, "Half A Pund" to Half A Pound, "caching games" to
  catching games, "Can rise a two-wheel bike" to ride.
- The gross motor chart's repeated 0 to 6 months rows are loaded once (74 movements, not 80); the blank row is
  dropped. "Handedness well established" is loaded without its "Left/right" box.
- Yellow titles are taken from each rhyme's heading (for example "Little Bo Peep", not "Little Bo Beep"), without
  "(CD Track n)". The rhyme words and actions are the exercise's instructions; *Sing a Song of Sixpence*, set in two
  columns, is put back in reading order.
- Kept as sent: "Swop Overs", "Hickory Tickory Tock", Motor Spin with no descriptions (scores 1 to 4 can still be
  recorded), and the copied descriptions of Jack and Jill and Twinkle Twinkle (the last three replaced 2026-11-05).

**The other five colours (2026-11-04).** `exercises-red.json`, `-orange`, `-blue`, `-indigo` and `-violet`, made from
Pat's grids of 2026-11-03 with a converter that reads the Word tables (checked against Green and Yellow first: it
gives the same titles, descriptions and points). An existing deployment loads them on its next start. Red and Orange
are rhymes with only a start and an end, like Yellow (Red is written for an adult with a baby); Blue, Indigo and
Violet have modules 1 to 6, like Green. For Pat to confirm:
- **Blue: Thumbs Up appears twice.** First Green's Thumbs Up (same text and descriptions, no points table), then a
  shorter Blue version with its own points (start, end of modules 2, 3 and 6, end of Blue). Blue's version was loaded
  ("thumbsup" corrected), so Blue has 40 exercises, as in its contents list. Pat chose Green's version on 2026-11-05.
- **Indigo 14, Puppy Fun, has no instructions**, and its four descriptions are copies of Cat Stretch's (13). Loaded with
  the title and the copied descriptions; the instructions were empty until Pat sent them on 2026-11-05.
- **Copied descriptions**, kept as sent: Orange *See Saw Marjory Daw* (2) has those of *Rock a Bye Baby* (1); Violet
  *Motor Lift Four* has those of *Motor Lift Three*.
- Titles are taken from each exercise's own table, not the contents list (so "Ring a Ring of Roses", "See Saw Marjory
  Daw"); "Pat - a – Cake" is written "Pat-a-Cake", and capitals evened out in "Big Hugs" and "If You're Happy and You
  Know It". One Blue points table says "Final end of Green"; it is read as the end of Blue.

**Pat's revisions (2026-11-05).** Her file "Revised 2" (sent with her reply to the go-live plan) answered the grid
queries. Loaded into the files above; an existing deployment takes the new wording on its next start:
- Green 4 *Motor Spin*: her four descriptions (it had none).
- Yellow 11 *Jack and Jill* and 13 *Twinkle Twinkle Little Star*: their own four descriptions, in place of those
  copied from the rhyme before. The words and actions were already right.
- Indigo 14 *Puppy Fun*: its instructions and four descriptions; its points (start, end of modules 2 and 3, end of
  Indigo) are as loaded.
- Orange 1 *Rock a Bye Baby*: new descriptions, so See Saw Marjory Daw no longer shares them (see *Still to settle*).
- Blue 5 *Thumbs Up*: "Use the version in Green": Green's instructions and descriptions, with Blue's points (start,
  end of modules 2, 3 and 6, end of Blue), which match her table.
- "Final end of Green" in Blue: confirmed as the end of Blue.

**Storage (migration V15).** As planned above, with these differences: the points an exercise is assessed at are
stored per exercise (`exercise_point`: START, MODULE_n, END, in order), which replaces `module_exercise`; the
developmental window is `year_point` (START, MIDDLE, END; "window" is a reserved word in PostgreSQL); the comment boxes
are `development_comment` (one per child, chart, school year and point); the summary sheet's comments are
`colour_summary` (the totals are worked out from the scores). All children's rows are deleted with the child.

**API** (all through `AccessPolicy`; teachers their class, heads their school; trust or group leads see only
counts and cannot record):

| Method and path | What it does |
|---|---|
| `GET /api/assessment/milestones`, `GET /api/assessment/exercises?stage=` | The charts and a colour's exercises |
| `GET /api/classes/{id}/exercise-sheet[?stage=]` | A class's exercises and every child's scores, with who is flagged |
| `POST /api/assessment/exercise-scores` | A batch of scores from the offline queue; resending is safe; a later score at the same point replaces the earlier one |
| `GET /api/children/{id}/development` | Both charts with what is secure, and the comments (logged as `VIEW_DEVELOPMENT`) |
| `POST /api/assessment/milestones`, `DELETE /api/children/{id}/milestones/{milestoneId}` | Record movements as secure (offline queue), or take one off recorded by mistake |
| `PUT /api/children/{id}/development-comments` | A chart's comment for this school year and point (empty removes it) |
| `GET /api/children/{id}/assessment[?stage=]` | The profile: both assessments, totals, the flag, the summary (logged as `VIEW_ASSESSMENT`) |
| `PUT /api/children/{id}/colour-summary` | The two summary comments for a colour |
| `GET /api/schools/{id}/assessment-overview` | The head's view per class, and the flagged children |

The trust or group dashboard gains `developmentalRate` and `extraSupport` per school.

**The flag** (`assessment/util/ExtraSupportRule`) uses the suggested default from question 3: 1 or 2 on the same
exercise at two points in a row, or 1 or 2 on half or more of the exercises (at least two) at the end of a module or
of the colour. Low scores at the start alone never flag a child. It says "May benefit from extra support" with the
reasons, never a cause. Up to three exercises low at two points in a row are named one by one; more are summed up in
one line, since the profile's table shows every score.

**The head's figures** (changed 2026-10-27 after a first look): per class, the average movements secured this school
year per child assessed, and the number of children at each exercise level, each child counted once at the middle of
their latest scores (the lower middle when even), rather than raw counts of scores.

**School year.** It follows the deployment's country (`assessment/util/SchoolYear`, since 2026-11-06). The UK: September to
August, with the point in the year suggested from the date (September to December start, January to April middle, May
to August end). Ireland: the end point starts in April. Australia: the calendar year (January to April start, May to
August middle, September to December end). Ireland's and Australia's points are for Pat to confirm. The teacher can
change the suggested point.

## Retiring the 0 to 3 check (2026-11-04)

Pat asked on 2026-11-03 what "the old 0 to 3 readiness check" was. The owner decided to retire it without asking her
again, since her two assessments replace it.

- **Gone:** the `screening` package (items, results, the six-area readiness profile, `ReadinessRules`), its endpoints
  (`/api/screening/items`, `/api/screening/results`, `/api/children/{id}/profile`), the teacher's Readiness check
  page and its offline queue, the readiness card on the child's profile, and the six areas in the parent app.
  Migration `V17__retire_readiness_check.sql` drops `screening_result`, `screening_item_level` and `screening_item`.
- **Placement:** the old check placed a child at a suggested colour. Now a school child works at the colour they are
  assessed at: their first exercise score places them there, and a score at a later colour moves them up, never down
  (`AssessmentService.submitScores`). A home child's colour still follows `HomeRules`.
- **Counts:** "Children assessed" on the head's dashboard, the trust or group's "Assessed" column and the governor
  summary (`children_assessed`) count children with an exercise score or a secure movement.
- **Induction:** the starter lesson "Assessing the children" describes the two assessments, and the practitioner
  course's second quiz asks about them. Starter content loads only into an empty deployment, so one that already
  has the old lesson keeps it (none is live yet).
- **Website:** the public pages describe the two assessments instead of a readiness profile.
