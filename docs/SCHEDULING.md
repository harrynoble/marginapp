# The scheduling engine

`domain/planner`. Pure Kotlin, no Android, no clock reads, fully unit-tested.

Two stages: **CandidateBuilder** decides *what* the day should hold; **DayPlanner** decides
*where* it goes. `PlanningService` gathers inputs from storage, runs both, and persists.

## What the day holds (`CandidateBuilder`)

| Candidate | Source |
| --- | --- |
| `task:<id>` | Active tasks, weighted by deadline, priority and importance |
| `review:<code>:<theory\|lab>:<day>` | Revision after a class, sized by class length |
| `study:<code>:<type>:<day>` | Coverage: a track untouched for `coverageGapDays` |
| `exam:<id>:<day>` | Exam preparation, growing as the exam approaches |
| `carry:<id>` | Work deferred from an earlier day |
| `quota:leisure` / `quota:build` / `quota:learning` | Protected time |

Theory and lab are separate **tracks** (`SubjectTrack`). Coverage is computed per track, so
a lab can never hide behind its theory. `neglectScore` grows with days untouched and a
fairness term, and at most `maxCoveragePerDay` coverage sessions are added (one more in exam
mode), so no subject is ever forgotten and no day is flooded.

Already-consumed work is subtracted before anything is placed (`Carryover.consumedMinutes`),
so finishing a review early, or skipping a task to tomorrow, never brings it back today.

### Shaping the day

- **Lighten today**: only essentials (the user's choice), anything due within a day and
  imminent exams stay; build and learning can be dropped; priorities get the best slots.
  Nothing dropped is recorded as skipped or piled onto tomorrow.
- **Minimum day**: suggested when capacity is very low; one session per essential.
- **Energy**: low shortens sessions (×0.75) and brings breaks sooner; high allows longer runs.
- **Exam pressure** (`ExamPlanner`): APPROACHING (≤21 days), CLOSE (≤10), IMMINENT (≤3). It
  scales study time up and build, learning and leisure down — leisure never to zero — and
  keeps every other subject's coverage running to avoid tunnel vision.
- **Learned patterns** (`HistoryLearner`): the median of actual over planned time per track
  (three samples minimum, clamped to 0.75–1.6), hours the user usually skips, and the
  two-hour window where most work actually gets done. Deterministic statistics, no model.

## Where it goes (`DayPlanner`)

1. **Frame** wake to sleep.
2. **Commitments**: classes, events, routines, locked and already-settled blocks.
3. **Transitions**: commute after college, settle-in time after long commitments, gaps.
4. **Leisure first**, reserved latest-first in its window, so work cannot eat it.
5. **Academic work**, earliest-first, highest score first. Only urgent work (due within a
   day, critical, or importance ≥ 50) may use the capacity kept for build and learning.
6. **Build, then learning**, earliest in their windows after academics, each with a short
   break before it if the preceding run was 40 minutes or more. They are *offered*: the user
   says yes or not today.
7. **Breaks** where a run exceeds the threshold, sized by how long the run was.
8. **Fill** the rest as free time, and **diff** against the previous plan.

A candidate whose previous start is within five minutes of where it would go gets a
stickiness bonus, so replanning does not reshuffle the day. Every block carries its reasons,
joined into a sentence ("Scheduled because the exam is in 4 days and DSA lab was last studied
5 days ago.").

## Block states

`BlockStateMachine`: UPCOMING (planned) → ACTIVE ⇄ PAUSED → COMPLETED / PARTIAL /
INTERRUPTED; UPCOMING → SKIPPED / MISSED / RESCHEDULED / CANCELLED; ACTIVE → EXTENDED via
continue. Only open states occupy time in a replan. Past blocks are kept as the day's record;
a replan only replaces planned blocks from now on.

## Missed work never disappears

- A session unstarted after the grace period prompts "Are you out?" (I'm out / Start now /
  Later / Skip today).
- A session whose time passes unstarted becomes MISSED, is recorded, and its work is placed
  again later today if there is room.
- At rollover, yesterday's unfinished academic work is deferred to today, capped at
  `carryForwardCap` minutes (60 per item, nothing under 15), one entry per source.

## Breaks

`BreakAdvisor` looks at the actual unbroken run of work, not a timer. A run that was
extended, or a low-energy day, earns a break sooner; longer runs earn longer breaks. Taking a
break pauses the running session and pushes only what it has to.

## Refusing the impossible

If the work does not fit, the engine places what fits and reports why. It never compresses a
session below its minimum, never schedules past midnight, and never quietly removes leisure.

## Determinism

`nowMinute` is passed in. Every sort has a full comparator chain ending in an id. Tests assert
that the same input gives the same plan and that one change moves only what it has to.
