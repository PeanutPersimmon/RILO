# RIDO UI Rules

This file is normative for every new screen and component.

## Interaction philosophy

- **Create first, edit or undo later.** Completed input creates data immediately; no routine pre-creation confirmation.
- Every record opens editing when clicked; only validity-critical fields are required.
- Routine create/delete/update feedback uses Snackbar plus Undo where feasible.
- Confirmation Dialog is reserved for genuinely high-risk, irreversible actions such as deleting all data.
- Animations are few, short and restrained: fade, simple expand/collapse and smooth timeline zoom. No springs, dramatic scale/translation, glow, breathing or dynamic shadow.

## Global visual prohibitions

- Material ripple is forbidden everywhere, including Cards, rows, chips, navigation, timeline blocks, settings rows, icon buttons and buttons.
- Default/native elevation and visible shadow are forbidden. Card, button, FAB, top bar, navigation, dialog and bottom sheet elevation is `0`.
- Hierarchy uses semantic surface color, border, spacing and typography.
- Click feedback may use only a subtle opacity or background change. `AppClickableSurface` is the current shared implementation (80 ms, slight opacity change) and `RidoTheme` globally provides a no-op indication.
- Screen code must not use raw `Color.White`/`Color.Black`, random dp, radius or font-size literals.

## Tokens

Spacing: XS `4dp`, S `8dp`, M `16dp`, L `24dp`, XL `32dp`. Page horizontal and initial top spacing are `16dp`; section/component spacing must select from these tokens.

Layout: top bar `64dp`, bottom navigation `72dp`, minimum touch target `48dp`, standard icon `24dp`, border `1dp`. Insets must use `safeDrawing`; input surfaces must remain usable with IME insets when forms are implemented.

Shapes: Small `8dp`, Medium `16dp`, Large `24dp`. Add a token only after confirming no existing semantic size fits.

Typography semantics: PageTitle, SectionTitle, ItemTitle, Body, Secondary, Caption, Time and Number. Components consume semantic roles from `AppTheme.type`; they do not invent local text sizes.

Colors are semantic. Light and Dark palettes define primary, background, surface tiers, text, outline/error and actual/planned/completed/inactive/border roles. Only explicit 日间/夜间 are supported; Follow System, Dynamic Color and accent/theme customization are not supported. Material colors remain black, white and neutral gray. Light mode uses a cool neutral-gray background, white surfaces and dark selected controls; dark mode reverses selected contrast. Existing semantic sleep colors are preserved. Category colors must remain low-noise, readable in both themes and accompanied by non-color cues where status matters.

## Shared components

- `AppScaffold`: edge-to-edge content/insets, shared container, top/bottom bars and Snackbar host.
- `AppTopBar`: one height, horizontal inset, title style and action alignment. Today intentionally has no “时间轴” title. Plan uses its current view name as title/switch entry.
- `AppNavigationBar`: fixed Today, Plan, Statistics, Settings order; uniform icon/label/touch area, selected surface treatment, no ripple/elevation. Users cannot replace first-level destinations.
- `AppDialog`: shared maximum width, Large corner, L padding, semantic title/body/actions and border. Avoid when inline or sheet interaction is sufficient.
- `AppBottomSheet`: shared top shape, handle, L padding, title position, action area and inset-safe presentation. Maximum-height/scroll behavior must be added once real content exists and tested on small/landscape screens.
- `AppSnackbar`: zero-elevation bordered surface, message plus optional Undo action.
- `AppCard`, `SectionHeader`, `EmptyState`, `ErrorState`, `AppTextField` and `AppButton`: common building blocks only; feature variants must reuse tokens.

## Feature file responsibilities

- Production UI is feature-oriented under `presentation/<feature>` and `ui/feature/<feature>`. A Screen collects UiState, forwards intent and composes feature components only.
- UiState and UiModel definitions live outside Screen files. ViewModels call UseCases and map results; they never access DAOs, reproduce Domain rules or contain Composables.
- Complex visuals such as `TodayTimeline`, `FlipClock` and `FlipDigit` have dedicated files. Pure geometry/timing/state calculations remain independently unit-testable Kotlin.
- Feature-only components remain inside their feature. Promotion to `ui/components` requires actual reuse by at least two features or Design System responsibility.
- Generic dumping-ground names such as `Utils`, `Common`, `Components` and `Helpers` are prohibited. File names must state their single responsibility.
- Preview-only example UiModels belong in dedicated preview files. Production ViewModels and Room never contain demonstration data.
- A production Kotlin file above roughly 300 lines requires an explicit responsibility audit and should be split when it owns more than one concern.

## Today / awake-day timeline

- No “时间轴” top title. The main content is today's actual awake-day timeline.
- Interval is night-sleep end/wake through next night-sleep start. Night sleep is not a giant block; naps inside the interval are normal blocks.
- Without sleep data, use the user's default awake range. Never use the first record as wake time.
- Current day before bedtime ends at default bedtime; history switches to real sleep start when known.
- Default `OVERVIEW` maps the complete awake interval to the remaining one-screen timeline height. Two-finger zoom is bounded, keeps its focal time stable and never enables one-finger vertical timeline scrolling.
- A real start/end interval is a strict proportional TimeBlock; a single instant is an inline PointItem. Overview shows title and duration inside blocks, while zoom reveals metadata only when the true block height permits it.
- Planned and actual blocks require distinct visual treatments that do not rely only on color.
- Blank is blank: no missing-time label, prompt or coverage.
- A very light Today plan hint may show remaining count and next item. It is an independently dragged overlay; expanding it must not recompute or move the timeline.
- Timeline geometry, clipping and Compact/Normal/Detailed density thresholds are centralized; individual blocks do not invent their own thresholds.
- Active Session end is visual `now`, recomputed without database writes. The current-time line is static between clock ticks: no glow, pulse or breathing animation.

## Active Session timer

- Timer is a full-screen secondary Today destination, never a fifth bottom-navigation item. Back leaves the Session active; Stop calls the normal stop UseCase and does not complete a Task.
- Timer uses semantic black-background/panel and white/secondary text tokens in both app themes. No raw page colors, elevation, shadow, gradient, glow or ripple.
- Elapsed time is always recomputed as `now - persisted startAtMillis`. Ticks align to wall-clock second boundaries and never increment a stored counter or update Room each second.
- `FlipDigit` uses two clipped halves: old upper half rotates `0° → -90°` from its bottom edge, then new lower half rotates `90° → 0°` from its top edge. The shared duration is 320 ms with no spring.
- Only digits whose character changed animate. Hours have at least two digits but are not modulo-limited, so durations beyond 99 hours remain valid.
- The complete FlipClock exposes one elapsed-time accessibility description; individual digit semantics are cleared.

## Plan, Statistics and Settings

Plan has no extra unified “计划” page title. Current view name is the title and switch entry. Future views may be shown/hidden, reordered and selected as default: week, month overview, schedule, four quadrants, Todo and Today Plan.

Statistics prioritizes time, Category, long-term patterns, heatmaps and day structure only where a documented question exists. It has no generic “task statistics” mega-module. A single Task may have its own metrics.

Settings currently provides fixed 日间/夜间 appearance and two-level Tag management. Future settings may contain Category, keyword rules, default awake range, Plan views, data management, privacy, about, optional AI and calendar connection.

## Date, time and calendar controls

- Do not substitute Material DatePicker/TimePicker.
- Date uses linked year/month/day wheels with a centered selection region. Month/year changes clamp the day via shared date-validity logic.
- Time follows the established wheel interaction from earlier apps.
- Month calendar uses Monday–Sunday, seven columns, month previous/next and one shared selected-date treatment.
- `DatePickerLauncher`, `TimePickerLauncher` and `CalendarVisualRules` are the current contracts. Full visuals are **Future / not implemented in v1**.

## Accessibility and resilience

- Preserve at least `48dp` touch targets even when visuals are compact.
- Provide meaningful content descriptions for standalone controls; decorative icons are excluded and their enclosing tab/row carries semantics.
- Maintain logical TalkBack order and selected/state semantics.
- Do not communicate state by color alone.
- Support dark mode, large font, small screens, long/localized text and landscape without crashes or clipped critical actions.
- Use edge-to-edge, system-bar insets and IME insets from the first functional form.

## Editor and metadata update (2026-09-21)

- Task/Event/Session creation and editing share EditorLayout inside AppBottomSheet; Today stays composed underneath.
- Top-right icon-only actions are Check / Delete (edit only) / Close. Disabled saving is gray. No bottom save button.
- Main inputs always use 标题 / 细节. Shared input typography is 15/21sp and 13/19sp; sheet title is 16/22sp; tools 12/16sp. Module spacing is 8dp and main inputs have no intervening section gap.
- Time tools use one row: PlayArrow=start, Flag=deadline, HourglassEmpty=estimate, Stop=end, Schedule=event start. No visible repeated labels; accessibility labels retain meanings. Items wrap as whole units; time text never wraps.
- Empty auxiliary fields are bare gray icons on transparent 48dp targets. Filled values use content-sized rounded pills. Category alone can carry data color; tags and other pills are grayscale.
- Record category offers immutable 夜间睡眠 / 小憩 UI choices mapped to SessionKind; no separate record-type tool.
- Tag management and selection port MyLibrary parent tabs, sibling ordering, root-scoped search, staged multiselect and batch-add interaction. Root itself remains selectable as in MyLibrary. Selection is committed only by confirmation; dismissal discards selection edits.
- Category color is a per-ID DataStore display preference, never a theme accent. Low-contrast colors are adjusted for the current surface.
