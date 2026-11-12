# Two teams, one board: how the agents work together

Since 2026-11-12, two people run their own set of agents on the Motor Movers Platform, each on their own computer:

| Team | Person | GitHub | monday.com | Agents |
|---|---|---|---|---|
| **Sagar** | Sagar Nikam (owner, CTO) | ersagarnikam | SAGAR NIKAM (121033742) | Developer, Tester, Project manager |
| **Rahul** | Rahul Shirode (build team) | rahulshirode | Rahul Shirode (121034015) | Developer, Tester, Project manager |

Both teams use the same agent definitions (`.claude/agents/`). **Which team you are on** is set on each computer in
`CLAUDE.local.md` at the repository root (git ignores it; Claude Code loads it on its own), with a line
`Team: Sagar` or `Team: Rahul`. If it is missing, ask your person which team you are on before you take any task.

Agents on different computers **cannot message each other** (cross-session messages stay on one account and one
machine). Within a team, agents message each other as before. **Between teams, the go-live board on monday.com is
the only channel**: the columns say who has a task and where it stands, and an item's updates (its notes) carry the
hand-offs.

## The board

"Motor Movers Go-Live (Dec 2026)", id **5032300138**, https://ihau-company.monday.com/boards/5032300138.

| Column | id | Used for |
|---|---|---|
| Status | `color_mm8trzvb` | Not started, Working on it, Stuck, Done. **Done is set by a person** once the work is merged. |
| Team | `color_mm91dnk4` | **Sagar**, **Rahul** or People only. Whose agents do the task. One team per task. |
| Agent stage | `color_mm9110pk` | Spec ready, Dev working, Ready to test, Testing, Bug found, Passed, Merged, Blocked. |
| Pull request | `link_mm911h2y` | The task's one pull request. |
| Owner | `text_mm8tma1w` | Free text: "Dev agent (Sagar)" or "Dev agent (Rahul)" for DEV tasks. |
| Due, Task #, Area | `date_mm8tg2s0`, `numeric_mm8tc43q`, `color_mm8t8bkf` | As before. |

Developer tasks are items named **"DEV: ..."**, with the full spec in the item's first update.

## The hand-off, stage by stage

1. **Spec ready.** A Project manager writes the spec as the item's update and sets Team and Agent stage. Only Sagar
   or Rahul decide which team takes a task; a PM may suggest one in the spec. A PM never sets the other team's
   tasks to Spec ready without its person agreeing on the board or in chat.
2. **Dev working.** The team's Developer picks the item, sets Agent stage to Dev working and Status to Working on it,
   and works on one branch named `task-<Task #>` from the latest `origin/main`.
3. **Ready to test.** The Developer opens the pull request, puts it in the Pull request column, replies to the spec
   with what changed and what ran, and sets Ready to test once CI is green. It then stops pushing to the branch.
4. **Testing.** The same team's Tester sets Testing, tests the branch and either sets **Bug found** (with the bug as a
   reply, and the Developer fixes it on the same branch, back to Ready to test) or **Passed** (with the results as a
   reply, and its CLAUDE.md note on the branch).
5. **Merged.** The same team's Project manager merges (Sagar decided, 2026-11-12: each team merges its own) once the Tester passed and every CI check is green on the
   latest commit, sets Merged and replies with the merge commit. The person sets Status to Done.
6. **Blocked.** Waiting on someone outside the agents (Pat, Kay, a file, a decision): say who and what in a reply.

Every step leaves a reply on the item, tagged with the role and team (below), so the other team can follow without
asking. Within a team, also send the usual cross-session message so the next agent starts at once.

## Checking the board

Agents on the other computer only learn of a change by reading the board. At the start of a session, and whenever
your person asks "anything new?", read the items with your team in Team (or Team empty) and look at Agent stage:
- Developer: Spec ready or Bug found for your team.
- Tester: Ready to test for your team.
- Project manager: Passed for your team (to merge), anything Blocked, and replies from the other team.

## Not stepping on each other

- **One task, one team, one branch, one pull request.** Never push to the other team's branch, and never pick up a
  task whose Team is the other team's.
- **Migrations.** Before adding a Flyway migration, check `main` and the open pull requests (`gh pr list`, then the
  files under `db/migration`) for the next free number, and say "Migration V<n> claimed" in a reply on your item.
  If two clash, the one merged second renumbers.
- **CLAUDE.md** is shared: add your own tagged notes, keep everyone else's, and before merging bring the branch up
  to date with `main` (merge or rebase), resolving CLAUDE.md conflicts by keeping both sides' notes.
- **Shared files** (`application.yml`, `AccessPolicy.java`, `DemoDataLoader`, `styles.css`): if your task changes
  one, say so in a reply on the item, so the other team can expect a conflict.
- `rahul-work` is Rahul's own branch from before; agents leave it alone.

## Tags

| Who | In CLAUDE.md and board updates |
|---|---|
| Sagar's Project manager | `[PM: Claude, YYYY-MM-DD]` |
| Sagar's Developer | `(added by Dev, YYYY-MM-DD)` |
| Sagar's Tester | `(updated by tester, YYYY-MM-DD)` |
| Rahul's Project manager | `[PM-Rahul: Claude, YYYY-MM-DD]` |
| Rahul's Developer | `(added by Dev-Rahul, YYYY-MM-DD)` |
| Rahul's Tester | `(updated by tester-Rahul, YYYY-MM-DD)` |

## Messages outside the team

A Project manager drafts messages only for its own person, who sends them. Pat, Kay and anyone outside the build
team are never contacted by an agent. Board updates on the go-live board are fine: both people and their agents
read them.

## Setting up a computer for a team

1. Clone the repository and install Claude Code (desktop app or CLI); sign in to GitHub with `gh auth login` as
   yourself (both people are collaborators on `ersagarnikam/MoterMoversPlatform`).
2. Connect the **monday.com** connector in Claude, signed in as yourself (account ihau-company), so your agents can
   read and update the go-live board.
3. Create `CLAUDE.local.md` at the repository root (git ignores it), for example:

   ```markdown
   Team: Rahul

   ## This computer
   - JDK 21 or later: <path>, set as JAVA_HOME for the backend tests
   - Free ports: API on 8080 (or --server.port=8090 and API_URL=http://localhost:8090 for the browser tests)
   - Agents on this computer: Developer, Tester, Project manager (one Claude session each, named so)
   ```

4. The **Project manager** agent definition (`.claude/agents/project-manager.md`) is not in the repository, because
   the repository is public and it holds private contacts and business details. Get the file from Sagar and save it
   there; git ignores it. The Developer and Tester definitions come with the repository.
5. Start three sessions named **Developer**, **Tester** and **Project manager**, each told to act as that agent
   (or use the agent of that name). Within the team they message each other by those names.
6. Ask your Project manager: "Read the board: what is there for our team?"
