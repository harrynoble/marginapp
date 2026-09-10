# The scheduling engine

`domain/planner/DayPlanner.kt`. Pure Kotlin, no Android, fully unit-tested.

## Input

```kotlin
PlannerInput(
    date,                 // the calendar day being planned
    nowMinute,            // null when planning a future day; set when replanning mid-day
    prefs,                // wake/sleep, buffers, leisure + build targets, energy curve
    commitments,          // hard blocks: classes, events, sleep, meals, commute
    candidates,           // work that wants time: tasks, reviews, build, leisure quota
    previousBlocks,       // the plan being revised, for churn minimisation
)
```

## Passes

1. **Frame the day.** Wake to sleep, clamped to the date. A sleep time earlier than the
   wake time belongs to the following morning; the frame ends at midnight and the
   remainder is handled by the next day plan. Nothing is scheduled outside the frame.
2. **Place hard commitments.** Classes from the timetable (minus exceptions), calendar
   events, fixed routines. An overlap between two hard commitments is reported as a
   `Diagnostic.HardConflict` rather than silently resolved.
3. **Apply transitions.** A commute buffer after the last class of the day, a decompression
   buffer after any commitment longer than `longCommitmentMinutes`, and a minimum gap
   between adjacent activities. This is what stops "college ends 13:40, study starts 13:41".
4. **Compute free intervals**, dropping anything shorter than `minUsefulSlotMinutes`.
5. **Reserve protected time.** The leisure quota and build quota are reserved *before* work
   is placed, in their preferred windows, so productivity cannot eat them.
6. **Score candidates.** Deterministic, highest first: urgency (deadline pressure against
   work remaining), plus priority, plus importance, minus a fragmentation penalty, plus
   energy fit, plus preferred-window fit. Ties break on id so the result is stable.
7. **Place.** Greedy best-fit with session splitting. A task with 90 minutes of work,
   `minSession` 25 and `maxSession` 50 becomes two sessions rather than one 90-minute
   block crammed into whatever gap exists. Sessions of one task spread across days when
   the deadline allows it.
8. **Insert breaks.** After `continuousWorkBeforeBreak` minutes of back-to-back work a
   break block is inserted. Breaks are placed by the engine, not left to willpower.
9. **Fill the remainder** with free/leisure blocks so the timeline has no unexplained holes.
10. **Diff** against `previousBlocks` to produce a `PlanDiff`, the list of moves the UI
    explains to the user.

## Protecting leisure

`prefs.minLeisureMinutes` is a floor, not a target. The engine reserves it in pass 5 and
work placement in pass 7 cannot claim reserved intervals. If the day is genuinely too full
to honour the floor, the engine does **not** quietly delete leisure. It emits
`Diagnostic.Overloaded` with the shortfall and the UI offers the user the choice.

The same mechanism protects the daily build/project quota.

## Refusing the impossible

If a task needs five hours and ninety minutes exist before its deadline, the engine places
what fits and reports `Diagnostic.InsufficientTime`. It never pretends, never compresses a
task below its `minSession`, and never schedules past midnight to make the numbers work.

## Determinism

The engine never reads the clock. `nowMinute` is passed in. Every sort specifies a full
comparator chain ending in id. `DayPlannerTest` asserts that planning the same input twice
produces identical output, and the churn test asserts that adding one evening event moves
only the blocks it has to.
