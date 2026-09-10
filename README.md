# Margin

An adaptive personal day planner for Android.

Margin is not a to-do list. Tasks and commitments are **inputs**; the day's schedule is the
**output** of a deterministic scheduling engine. You tell it what exists, and all day it
answers one question:

> Given everything I know about your day, what should you do now?

It ships already knowing a real timetable (MITS Kochi, S3 CS-A, Jul–Dec 2026), so the first
launch is a working plan rather than an empty dashboard.

---

## What it does

- **Knows the week.** The recurring timetable is seeded and editable, with per-date
  exceptions for cancelled classes, holidays and extra periods.
- **Builds the day.** A pure-Kotlin engine places work around classes, meals, travel and
  events, inserts breaks, and protects leisure and build time as floors rather than leftovers.
- **Spreads work across days.** A 90-minute assignment due Friday becomes 45 minutes on
  Wednesday and 45 on Thursday, not one block dumped wherever there was a gap.
- **Reviews what was taught.** It knows which subjects you had today and proposes
  proportionate revision, weighted per subject, capped per day, and switchable off.
- **Adapts.** Finishing early, running over, skipping, taking a break, or adding an event all
  trigger a replan, and the app tells you exactly what moved and what it protected.
- **Explains itself.** Every scheduled block carries the reason it is where it is.
- **Works offline.** The planner, timetable, tasks, notifications, history and check-in never
  touch the network. The assistant is an enhancement, and it degrades to an on-device parser.

## The rule the AI plays by

```
USER INPUT  →  AI understanding  →  structured command  →  validation  →  database
                                                                              ↓
UI / notification  ←  new plan  ←  scheduling engine  ←──────────────────────┘
```

The model can only speak in the commands defined in `ai/AiCommand.kt`, and every one passes
`CommandValidator` before it touches storage. Arbitrary text never becomes app state. With no
API key configured, `LocalCommandParser` handles the common phrasings on-device.

---

## Project structure

```
app/src/main/java/com/margin/app/
  core/          Time helpers: the only bridge between engine integers and the calendar
  domain/
    model/       Domain types, no Android imports
    planner/     The scheduling engine. Pure Kotlin, unit tested
    usecase/     PlanningService, ScheduleActions, SeedService
  data/
    db/          Room entities, DAOs, mappers
    repository/  Timetable, Task, Schedule
    prefs/       DataStore settings, AI settings in their own store
    seed/        The shipped timetable
  ai/            Provider-agnostic client, command schema, validation, offline parser
  notifications/ Channels, alarms, notification actions, WorkManager, focus service
  ui/
    theme/       Design system: colour, type, shape, spacing
    components/  Shared vocabulary every screen is assembled from
    today/ plan/ tasks/ timetable/ insights/ settings/ focus/ assistant/ onboarding/
  di/            AppContainer, the whole object graph
```

Docs: [ARCHITECTURE](docs/ARCHITECTURE.md) · [SCHEDULING](docs/SCHEDULING.md) ·
[DESIGN](docs/DESIGN.md) · [BUILDING](docs/BUILDING.md)

---

## Building

Requires JDK 17 and the Android SDK (platform 35, build-tools 35). See
[docs/BUILDING.md](docs/BUILDING.md) for a from-scratch toolchain setup.

```bash
./gradlew :app:assembleDebug
```

The APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

Run the unit tests (the scheduling engine, the allocator, the validator, the offline parser):

```bash
./gradlew :app:testDebugUnitTest
```

Instrumented tests need a connected device or emulator:

```bash
./gradlew :app:connectedDebugAndroidTest
```

## Installing on a phone

Enable Developer options and USB debugging on the phone, connect it, then:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Or copy the APK to the phone and open it, allowing installation from that source.

## Configuring the assistant

No key is compiled into the app and none is required. To enable free-form natural language:

1. Open **Settings → Assistant**.
2. Choose a provider (Anthropic, or any OpenAI-compatible endpoint).
3. Paste your own API key and set the model.

The key is stored in a DataStore file separate from the rest of the settings and excluded
from cloud backup. Requests contain only the current time, your free windows, the titles and
times of today's blocks, and your open work — never notes, history or the database. Turning
off **Send the shape of my day** narrows that to the clock and free time alone.

---

## Licence

Personal project. No warranty.
