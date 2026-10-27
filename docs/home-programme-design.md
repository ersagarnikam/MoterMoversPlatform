# Motor Movers at Home: design note for Pat

*27 October 2026. For Pat Preedy, from the platform team. Built and waiting for your answers; not yet offered to
families.*

## What this is

Today a parent can only follow their child's progress in the Motor Movers app if the child's school is on Motor
Movers. **Motor Movers at Home** lets any parent or carer use the programme with their own child, with no school
involved. It uses your assessment booklet and your exercise grids, the same content schools use, through a simple
daily routine at home.

It is built and can be tried with the demo account (below). It stays switched off for real families until you and
the owner are happy with the questions at the end of this note.

## What a parent does

1. **Signs up and adds their child.** A first name or nickname, and the child's age in years and months. We do not
   ask for a date of birth. A second parent, carer or grandparent can join with a code and share the child on their
   own phone.
2. **Does a short starting check.** The app shows the movements from your fine and gross motor charts that children
   usually manage in the year up to the child's age, in your words (for example "Can stand on one foot for five
   seconds"). For each, the parent answers **Yes**, **Not yet** or **Not sure**. Every "Yes" is kept as a secure
   movement with the child's age in months, exactly as a teacher would record it.
3. **Gets a suggested colour.** The colour for the child's age, or the colour before it if most answers were "Not
   yet". The parent can choose another colour. Every three months the app suggests doing the check again.
4. **Does a daily session, about ten minutes.** Four exercises from the child's current module, shown one at a time
   with your movement text. Ten sessions make a module, so a module takes two or three weeks at home. Green follows
   your six modules. Yellow has no modules in your grid, so the rhymes are worked through three at a time.
5. **Rates exercises from time to time.** The first time the child does an exercise, and again at the end of each
   module the exercise is in and at the end of the colour (your points: Start, End of module, End of the colour).
   Parents choose from four plain words (below), and your description of that level is shown underneath so they can
   check what it looks like.
6. **Sees progress.** Movements now secure on each chart (including those secured since the first check), and each
   exercise's first and latest rating. Never a score, a percentage or a comparison with other children. "Not a
   diagnosis" is shown wherever the check or progress is.
7. **Gets a gentle prompt, not a flag.** Where a school would see "may benefit from extra support" (your pattern of
   low scores), the parent sees a short reassuring message suggesting they talk to someone who knows the child (see
   question 6).
8. **Links the school later, if it joins.** If the child's school starts Motor Movers, the school gives the parent a
   code. The parent agrees to share what they recorded at home; the class teacher then sees it, and the school's
   record carries on from there. The parent keeps doing home sessions; the teacher does the assessing.

Colours you have not sent grids for yet (Red, Orange, Blue, Indigo, Violet) use the existing home activities for
that colour until your grids arrive.

### Try it

In the parent app (or its web preview), sign in as **home@example.test** with the password **password**. Priya Shah
has two children: **Ava**, 3 years 7 months, on Green, part way through module 2 with her module 1 ratings due, and
**Leo**, 1 year 8 months, who has not done the starting check yet.

## Questions for you

Each has a suggested answer, which is what the app does now. A short reply such as "3: yes" is plenty.

1. **Using your materials at home.** Are you happy for your exercises, rhymes and charts to be used by parents at
   home, without a teacher or practitioner? *Suggested: yes, for parents who sign up through the app.*

2. **Which colour suits which age.** The programme covers 6 months to 8 years. The app currently assumes:

   | Colour | From |
   |---|---|
   | Red | birth to 1 year 5 months (the programme starts at 6 months) |
   | Orange | 1 year 6 months |
   | Yellow | 2 years 6 months |
   | Green | 3 years 6 months |
   | Blue | 4 years 6 months |
   | Indigo | 5 years 6 months |
   | Violet | 6 years 6 months |

   Is this right, or what would you use? (Schools place classes by their own choice, so this is only for home.)

3. **The starting check.** It asks about the movements from the year up to the child's age (about 25 to 30
   questions), and suggests the colour before when fewer than half the answered movements are "Yes" (with at least
   four answered). *Is this the right set of questions, and the right rule?*

4. **Words for parents.** The four levels are shown to parents as **Not yet**, **Getting there**, **Nearly** and
   **Got it** (your 1 Not yet, 2 Developing, 3 Nearly there, 4 Confident and accurate). Your full descriptions are
   written for teachers. *Would you write shorter versions for parents, or are yours fine as they are?*

5. **Should parents rate at all?** *Suggested: yes, at the points above, because it is what shows progress. The
   alternative is for parents only to tick chart movements.*

6. **The gentle prompt.** The app currently says: *"Some of these movements are taking a little longer. That is
   common: every child moves at their own pace, and little and often helps. If you would like to talk it through,
   your child's nursery, school or health visitor can help."* It appears with the same pattern of low scores as the
   school flag. *Is the wording right, and who should parents be pointed to?*

7. **The shape of a session.** Four exercises a day, ten sessions a module, Yellow rhymes three at a time. *Right for
   home?*

8. **Doing the check again.** Every three months. *Right?*

9. **Safety and space.** Schools have staff and an induction; parents do not. *Would you write a short safety note for
   parents (space, supervision, stopping if anything hurts) for the app to show before the first session?* None is
   shown yet.

10. **Videos.** Sessions show your movement text only. For parents, filmed demonstrations matter more than for
    teachers. *Are videos of the exercises planned, and could they be used at home?*

## For the owner (not for Pat)

- **Privacy.** For school children Motor Movers handles data for the school. For home children Motor Movers is
  responsible itself: it needs its own privacy notice and the parent's agreement, deletion on request (deleting a
  child, or the last person leaving a family, deletes everything recorded), and the UK ICO's Children's Code (no
  adverts, nothing public, no contact between families). Ask a data protection adviser before switching it on.
- **What is kept.** The child's first name or nickname, their age in months on a given day, chart movements secure,
  exercise ratings and session dates. No date of birth, no birth history, no photos or videos.
- **Who sees it.** Only the family. The Motor Movers team and schools cannot see a home child unless the parent links
  a school.
- **Price.** Not built. Card payments already work, so a subscription could be added once the price is decided.
- **Switching it on.** `HOME_PROGRAMME=true` in production (it is on in development and the demo).

## How it is built

- A child belongs to a school class or to a family (migration V16: `family`, `family_member`, `family_invite`, and
  `child.family_id`, `age_months`, `age_recorded_on`, `home_check_on`; `home_programme_session` for the daily
  sessions). A home child's chart answers and ratings use Pat's assessment tables (V15) like a school child's.
- Access: `AccessPolicy.requireFamilyOf` (only the family's parents and carers), used by the assessment endpoints too,
  so a teacher, head, trust lead or the team gets "no access" to a home child. A home child is not in the school's
  record access log, because the record is the family's own.
- API under `/api/home` (PARENT only): the family, children, the check, the colour, today's session, sessions
  (offline-safe), invites and joining, and linking a school (`join-school`: the school's parent code, then the home
  record moves onto the school's, the school's own records winning where both have one).
- The rules are in `home/util/HomeRules.java` (colour by age, the check's questions and rule, modules and reviews),
  each marked as a default for Pat to confirm. Parent wording is in `home/dto/HomeViews.java`.
- Parent app: Today (the session or the check), the session and first-time ratings, the module review, the starting
  check, the child's progress and settings, family and sharing, and linking a school.
