# Margin — Architecture

Margin is an adaptive day planner. Tasks and commitments are **inputs**; the day's
schedule is an **output** produced by a deterministic scheduling engine. The user
interacts with the schedule, not with a task list.

## Layering

```
ui/            Compose screens + ViewModels. No business logic, no scheduling.
domain/        Pure Kotlin. Models, the scheduling engine, use cases. No Android imports.
data/          Room entities/DAOs, repositories, DataStore preferences, seed data.
ai/            Provider-agnostic AI client, structured command schema, validation, execution.
notifications/ AlarmManager + WorkManager + notification channels/actions.
```

The dependency direction is strictly `ui -> domain -> data`. The scheduling engine
(`domain/planner`) has **no Android dependencies at all** so it can be unit-tested on the JVM.

## The control flow that matters

```
USER INPUT (natural language)
      |
      v
AI UNDERSTANDING  (ai/AiProvider -> JSON)      offline fallback: ai/LocalCommandParser
      |
      v
STRUCTURED COMMAND (ai/AiCommand, @Serializable)
      |
      v
VALIDATION (ai/CommandValidator - rejects impossible times, absurd durations, unknown ids)
      |
      v
DATABASE (data/repository)
      |
      v
SCHEDULING ENGINE (domain/planner/DayPlanner - deterministic, pure)
      |
      v
NEW PLAN (+ PlanDiff explaining what moved and why)
      |
      v
UI / NOTIFICATION
```

Arbitrary AI text never reaches the database or the UI as state. The AI can only
express itself as one of the commands in `AiCommand`, and every command passes
`CommandValidator` before it is applied.

## Offline-first

Everything except natural-language *understanding* runs locally:
timetable, tasks, events, plan generation, completion, skipping, breaks,
rescheduling, history, insights, notifications, the daily check-in.

If no AI key is configured or the network is down, the natural-language bar falls back
to `LocalCommandParser`, a deterministic phrase parser that handles the common cases
(out from 6 to 8; add task X by friday; 1 hour of build tonight; tired today).
Anything it cannot parse produces a clear message, never a crash and never a silent no-op.

## Scheduling engine

See `docs/SCHEDULING.md`. Two properties are non-negotiable:

1. **Deterministic** - same input, same output. No wall-clock reads inside the engine,
   no randomness, stable sorts with explicit tiebreaks.
2. **Minimal churn** - replanning takes the previous plan as an input and preserves
   blocks that do not need to move, so the day does not reshuffle under the user.

## Data model

| Entity | Purpose |
| --- | --- |
| `SubjectEntity` | A course (DELD, DSA, ...) with a review weight |
| `TimetableEntryEntity` | One recurring weekly class slot |
| `TimetableExceptionEntity` | Holiday / cancellation / one-off change for a date |
| `RoutineEntity` | Recurring non-class commitment (sleep, meals, commute, gym) |
| `TaskEntity` | Work that needs time; may be split into sessions |
| `TaskSessionEntity` | A recorded chunk of work against a task |
| `CalendarEventEntity` | A one-off dated commitment |
| `ProjectEntity` | Groups tasks under a build/learning project |
| `ScheduleBlockEntity` | One placed block in a day plan (the planner output) |
| `DailyPlanEntity` | Metadata for a generated day (version, generatedAt, headline) |
| `CompletionRecordEntity` / `SkipRecordEntity` / `RescheduleRecordEntity` | History |
| `DailyCheckInEntity` | Evening review answers |
| `ResourceLinkEntity` | Links attached to tasks/projects |
| `AiInteractionEntity` | What was asked, what command came back, whether it applied |

Times are stored as **minutes from midnight** (`Int`) and dates as **epoch day** (`Long`).
This keeps the engine free of timezone ambiguity: a plan belongs to a calendar date, and
the only place a timezone is consulted is when converting now into a date+minute pair.

## Threading

Repositories expose `Flow`. ViewModels use `stateIn` with `SharingStarted.WhileSubscribed(5_000)`.
Planning runs on `Dispatchers.Default`, database work on the Room executor,
network on `Dispatchers.IO`.

## Why no DI framework

One app, one user, a handful of singletons. `MarginApplication` builds an `AppContainer`
(`di/AppContainer.kt`) that constructs the database, repositories, planner and AI client once.
ViewModels get it through a `ViewModelProvider.Factory`. Adding Hilt later is mechanical;
adding it now would be ceremony without benefit.
