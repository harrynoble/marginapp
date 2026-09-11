# Margin — design system

## Intent

Margin follows Apple's current design language (iOS 26, "Liquid Glass") as closely as an
Android app honestly can. The rules that matter most:

- **Glass is for navigation, never for content.** The floating tab bar, the add button beside
  it and the round toolbar buttons are glass. Cards, lists and sheets are solid surfaces.
- **No glass on glass.** A glass control only ever sits over content.
- **Tint means emphasis.** Only the add button is tinted glass; everything else is clear.
- **Content first.** Bold, left-aligned large titles; inset grouped lists; one hero card on
  Today and nothing else competing with it.
- **Out of the way.** The timetable is set up once and then folded into a single "College"
  line. Individual classes are never listed on Today or Plan and never announced.

## Brand

The logo is the wordmark **margin.** — lowercase, white on pure black, with the full stop in
amber (`#F5A524`) as the only colour. It is drawn as geometry in
`res/drawable/ic_launcher_foreground.xml`, not set in a font:

- a heavy geometric sans: strokes about a quarter of the x-height, true-circle bowls
- single-storey `a` and `g`, each closed by a straight stem; the `g` ends in a short hook
- flat stem ends at the x-height and baseline; the `r` shoulder ends in a vertical cut
- tight, even spacing, with the full stop tucked close to the `n` and slightly larger than
  a stroke

The launcher icon centres the whole wordmark inside the adaptive icon's safe circle on a
black ground. The status-bar icon, which must be a flat silhouette and is too small for the
full word, is `m.` in the same geometry.

## Liquid Glass

`ui/glass` renders glass without a library (the toolchain predates Haze 1.7 / 2.x):

1. `Modifier.glassSource` records the content under the chrome into a `GraphicsLayer`.
2. Each `GlassSurface` redraws that recording, offset to its own position, through a
   `RenderEffect` chain: Gaussian blur → saturation lift (vibrancy) → an AGSL runtime shader
   (`GlassShader`) that refracts the edge band along a rounded-rect SDF with a little
   chromatic dispersion.
3. On top: tint, a specular sheen across the upper half, a lit gradient rim and a hairline.

| Android | Result |
| --- | --- |
| 13+ (API 33) | Blur, vibrancy and edge refraction |
| 12 (API 31–32) | Blur and vibrancy |
| Below 12 | Near-opaque material, as iOS shows with Reduce Transparency |

Variants in `GlassDefaults`: `regular()` for bars, `prominent(color)` for the one primary
action, `clear()` over the vivid focus screen. Where content meets the status bar, a
progressive blur (`TopScrollEdge`) replaces an opaque navigation bar.

## Colour

Apple's semantic system colours (`ui/theme/Color.kt`): grouped background `#F2F2F7` /
`#000000`, surfaces `#FFFFFF` / `#1C1C1E`, translucent label greys, low-alpha fills, and
system blue as the tint. Categories draw from the system accents Calendar uses: Academics
indigo, Build orange, Learning teal, Personal blue, Health pink, Leisure green, Other grey.
Structural blocks (breaks, travel, meals) are greys and browns so work stands out.

Today's header is a sky (`DaySky`) keyed to the phase of the day: dawn, day, dusk, night.
It is content, not glass, and gives the glass something real to refract.

## Type

SF Pro cannot ship on Android, so type is set in **Inter 4.1** (bundled, SIL OFL) using its
optical-size axis the way SF uses its Text and Display cuts: 14pt optics below 20sp, 32pt
optics for titles, with Inter's dynamic tracking. Sizes are Apple's Dynamic Type defaults.

| Style | Size / weight |
| --- | --- |
| Large title | 34 / Bold |
| Title 1 · 2 · 3 | 28 / Bold · 22 / Bold · 20 / Semibold |
| Headline · Body | 17 / Semibold · 17 / Regular |
| Subheadline · Footnote | 15 · 13 |
| Caption 1 · 2 | 12 · 11 |
| Timer | 64 / Semibold, tabular |

Times and counts use tabular figures so columns line up.

## Shape and space

Continuous-corner squircles (`SmoothRoundedCornerShape`, Figma's corner smoothing at 0.6)
with concentric radii: hero 28, card 24, tile 18, field 12, icon tile 8, sheet 38. Controls
near an edge are capsules. A 4dp grid with a 16dp screen margin, iOS's inset-grouped margin.

## Controls

Rebuilt at iOS proportions rather than restyled Material: 51×31 switch, capsule segmented
control with a sliding thumb, minus/plus stepper, wheel time picker in five-minute steps,
capsule buttons, and sheets with a grabber, Cancel on the left and the confirming action on
the right. Rows highlight grey under the finger instead of rippling; buttons compress, glass
lifts. Haptics mark selection changes and completions only.

## Motion

Springs, not tweens, for anything that moves under the finger: the tab lozenge, the switch
thumb, the segmented control. Pushed screens slide in from the edge with a parallax on the
screen underneath; tabs cross-fade. The focus ring counts down to the second.

## Screens

- **Today** — the sky, a hero Now card, four quick actions (Lighten today, Take a break,
  Energy, I'm out), at most one card per decision (exam mode, minimum day, build offer,
  learning offer), then the day by part with college as one line. The Now card changes shape
  with the moment: ready (Start), running (Finish, Pause), paused (Resume), late to start
  (Start now, I'm out, Later, Skip today) and past its planned end (Move to next, Continue).
  Nothing is shown until the day has loaded, and after bedtime the screen stops asking for
  decisions: it says the day is over and that anything missed carries to tomorrow.
- **Sheets** — Lighten (what must happen, priorities, drop build or learning), Out (later
  today, tonight, tomorrow, a chosen time), and the build and learning offers, where "Not
  today" sits where Cancel would.
- **Plan** — a Calendar-style day grid with a week strip and a red now-line. Tap empty time
  to add an event there.
- **Tasks** — Reminders-style: completion rings, `!!` priority, grouped by due date; projects
  and learning goals below.
- **Exams** — the exam timetable: countdowns, theory or lab, import from a photo or PDF with
  a review step before anything is saved.
- **Insights** — Health-style cards: follow-through, work, day balance, every subject's
  theory and lab with planned against done and when it was last studied, and what Margin has
  learned about how long things take.
- **Settings** — iOS Settings with coloured icon tiles: study, revision, build, learning,
  leisure, each notification on its own switch, appearance, the timetable and exams, data.
- **Focus** — always dark, lit by the colour of the work, clear glass controls. At the
  planned end it offers the next session or more time rather than stopping the clock, and it
  suggests a break once the real run of work has earned one.

No emoji, no streaks, no guilt: a declined offer is never mentioned again that day.

## Components

`ui/components`: `LargeTitleScreen`, `GroupedSection`, `GroupedRow`, `RowSeparator`,
`SectionTitle`, `FormHeader`/`FormFooter`, `IconTile`, `ProgressRing`, `ProportionBar`,
`StatTile`, `EmptyState`, `PrimaryButton`, `SecondaryButton`, `CircleIconButton`,
`TextAction`, `IosSwitch`, `SegmentedControl`, `CapsuleChip`, `OptionChips`, `IosStepper`,
`FormTextField`, `CapsuleTextField`, `TimeRow`, `DurationRow`, `WheelTimePicker`,
`MarginSheet`, `TimelineRow`, `CollegeRow`, `BlockActionsSheet`, `DaySky`.
`ui/glass`: `GlassSurface`, `GlassTabBar`, `GlassCircleButton`, `GlassTextButton`,
`TopScrollEdge`.
