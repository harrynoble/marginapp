# Margin — design system

## Intent

Calm, mature, quiet. The app is used every day, often several times an hour, so it is
built to be read at a glance and then put down. Nothing decorative. No gradients, no
neon, no glass, no mascots, no motivational copy. Emoji appear only if the user types one.

## Colour

A near-neutral, slightly warm paper ground with a single deep evergreen accent. Category
colours are desaturated and used at small sizes, a 3dp rail on a block or a dot in a list,
never as large fills that would turn the timeline into a rainbow.

| Role | Light | Dark |
| --- | --- | --- |
| Background | `#FBFAF7` | `#0F1110` |
| Surface | `#FFFFFF` | `#181A19` |
| Surface raised | `#F4F2EC` | `#212423` |
| Primary | `#1F5245` | `#8FC5B1` |
| Outline | `#E4E1D8` | `#2C302E` |
| Text | `#15181A` | `#ECEDEA` |
| Muted text | `#6B6E6A` | `#9BA09B` |

Category accents, tuned per theme in `ui/theme/CategoryColors.kt`:
Academics slate blue, Build evergreen, Learning muted violet, Personal taupe,
Health clay, Leisure ochre, Other grey.

Urgency uses a single clay red, only for genuinely late or at-risk work.

## Type

The platform sans at deliberate sizes. Times in the timeline gutter are tabular so they
align in a column. Weight, not colour, carries hierarchy.

| Style | Size / weight | Used for |
| --- | --- | --- |
| Display | 34 / Medium, -0.5 tracking | The current activity name |
| Title | 20 / Medium | Screen titles, sheet titles |
| Body | 15 / Regular | Most content |
| Label | 13 / Medium, 0.1 tracking | Metadata, chips |
| Time | 13 / Medium, tabular figures | Timeline gutter |

## Space and shape

A 4dp base scale: 4, 8, 12, 16, 20, 24, 32. Screen gutter is 20dp. Corner radii are
restrained: 10dp for cards, 12dp for sheets, 8dp for chips, full only for small round
icon buttons. Elevation is used almost nowhere; separation comes from a 1dp outline
and a surface shift.

## Motion

Short and functional. 150ms for state changes, 250ms for entering content. The only
expressive moment is the progress ring on the focus screen, and it moves once a second.

## Components

`ui/components` holds the shared vocabulary: `SectionHeader`, `MarginCard`, `BlockRow`,
`CategoryDot`, `AccentRail`, `MetaChip`, `StatRow`, `ProportionBar`, `EmptyState`,
`ErrorState`, `LabeledField`, `MarginTextField`, `TimeField`, `DurationPicker`,
`CategoryPicker`, `PriorityPicker`, `DifficultyPicker`, `StepperRow`, `SheetGrabber`,
`SheetAction` and `BlockActionsSheet`. `NowCard` lives with the Today screen because it is
the only place display type is used. Every screen is assembled from these, which is what
keeps spacing and hierarchy consistent without a spec to police.
