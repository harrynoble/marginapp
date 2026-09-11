# Margin — Architecture

Margin is a personal day OS for a student. The timetable is the foundation; tasks, exams,
projects and learning goals are **inputs**; the day's schedule is an **output** produced by a
deterministic engine. The user interacts with the schedule and a handful of decisions
("lighten today", "I'm out", "build tonight?"), not with a task list.

## Layering

```
ui/             Compose screens + ViewModels. No business logic, no scheduling.
domain/usecase  PlanningService, ScheduleActions, DayRollover, DataExporter, SeedService.
domain/planner  The engine. Pure Kotlin, no Android imports, unit-tested on the JVM.
domain/model    Models, enums and the block state machine.
data/           Room entities/DAOs, repositories, DataStore preferences, seed data.
ai/             Provider clients, local parser, validator, context builder, vision import.
notifications/  NudgePlanner output -> AlarmManager, WorkManager fallback, actions, channels.
```

```
UI -> ViewModels -> use cases -> planner (pure) -> repositories -> Room / DataStore
                         ^                                 ^
                 AI (commands only)             notifications (read plan, post nudges)
```

The planner never touches storage and never reads the clock; the use cases gather its inputs,
call it, and persist the result. AI and notifications sit to the side: both go through the
same use cases as a button press would.

## The control flow that matters

```
USER INPUT (typed sentence)
      |
      v
LocalCommandParser  -- handles common sentences on the device; nothing leaves the phone
      |  (only if it cannot, and a key is configured)
      v
ContextBuilder      -- only the sections the message needs: schedule, subjects, exams, tasks
      |
      v
AI PROVIDER         -- returns JSON, nothing else
      |
      v
CommandValidator    -- unknown subjects, past exams, impossible times and absurd durations
      |                are rejected with a reason the user sees
      v
ScheduleActions / repositories
      |
      v
PlanningService -> CandidateBuilder -> DayPlanner (deterministic)
      |
      v
NEW PLAN (+ PlanDiff explaining what moved)  ->  UI, NudgePlanner -> notification
```

Arbitrary AI text never reaches the database or the UI as state. Questions like "what should
I do now" and "why is maths at 5" are answered from the stored plan and the block's recorded
reasons, never from the model's imagination.

## Offline-first

Everything except free-form language understanding and image import runs locally: timetable,
tasks, exams, plan generation, sessions, breaks, rescheduling, carryover, history, learning,
insights, notifications and the end-of-day review.

## Day lifecycle

1. **Rollover** (`DayRollover`, at app start, on resume and at 4 am): earlier days are closed
   out. Unstarted work becomes MISSED, running work INTERRUPTED, and yesterday's unfinished
   academic work is deferred to today as `DeferredWork`, capped so a missed day never doubles
   the next one.
2. **Plan** (`PlanningService.ensurePlan`): candidates are built from the timetable, tasks,
   exams, coverage gaps and deferred work, then placed.
3. **Live** (`ScheduleActions`): start, pause, finish, continue, move to next, skip, take a
   break, go out, lighten. Each records history and replans from now, keeping the morning's
   record intact.
4. **Stale check** (`refreshIfStale`): a session whose time passed unstarted is marked MISSED
   and its work placed again, without the user opening anything.
5. **Review**: the evening check-in records workload and energy.

## Notifications

`NudgePlanner` (pure) derives the day's nudges from the plan: study start, transitions, break
suggestions, the "are you out?" check after a grace period, break over, up next, build and
learning offers, and the daily review. `AlarmScheduler` arms one exact alarm for the next
one; a 15-minute `NudgeWorker` catches anything missed. A `nudge_log` ledger guarantees a
nudge is posted at most once, quiet hours are honoured, and only one Margin notification is
ever on screen. Notification buttons call the same `ScheduleActions`; answers that need a
choice ("when will you be back?") open the app straight into the right sheet.

## Data model

| Entity | Purpose |
| --- | --- |
| `SubjectEntity` | A course with review weight, importance and difficulty |
| `TimetableEntryEntity` | One weekly class slot; theory and lab are separate entries |
| `TimetableExceptionEntity` | Holiday / cancellation / extra class on a date |
| `RoutineEntity` | Recurring commitments (sleep, commute; meals are off by default) |
| `TaskEntity` / `TaskSessionEntity` | Work that needs time, and the chunks done against it |
| `CalendarEventEntity` | A one-off dated commitment, including "going out" |
| `ProjectEntity` | Build work |
| `LearningGoalEntity` | A skill to learn, with a weekly target |
| `ExamEntity` | The exam timetable, kept apart from the weekly one |
| `ScheduleBlockEntity` | One placed block, with state, planned vs actual time and its reasons |
| `DailyPlanEntity` | Plan metadata plus the work it intended, for carryover |
| `DayStateEntity` | The user's choices for a date: light day, essentials, build/learning answers, out until |
| `DeferredWorkEntity` | Work moved to a later day on purpose; unique per source, never doubled |
| `CompletionRecordEntity` / `SkipRecordEntity` / `RescheduleRecordEntity` / `BreakRecordEntity` | History the planner learns from |
| `NudgeLogEntity` | Which nudges were posted, so none repeats |
| `DailyCheckInEntity` | Evening review answers, including workload |
| `ResourceLinkEntity` | Links attached to tasks |
| `AiInteractionEntity` | What was asked, what came back, whether it applied |

Times are **minutes from midnight** and dates **epoch days**. The database is at version 2
with an automatic migration from version 1; every new column has a default. Schemas are
exported to `app/schemas` and `MigrationTest` checks the upgrade.

## Privacy

- All data stays on the device. The only network traffic is the optional assistant.
- API keys are entered by the user and stored on the device; they are never in source,
  never committed, and never included in an export.
- Settings → Data exports everything the user owns as JSON through the share sheet
  (`FileProvider`, export folder only) and can delete all history.

## Threading

Repositories expose `Flow`. ViewModels use `stateIn` with `WhileSubscribed(5_000)`. Planning
is serialised by a `Mutex` in `PlanningService` so a notification action and a tap cannot
interleave two replans.

## Why no DI framework

One app, one user, a handful of singletons. `MarginApplication` builds an `AppContainer`
that constructs everything once; ViewModels receive it through a small factory.
