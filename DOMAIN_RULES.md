# RIDO Domain Rules

This file is normative. Later code, tests, migrations and product changes must preserve these rules unless a deliberate product decision updates this document first.

## Product invariant

RIDO minimizes the cost of recording plans, current activity and the reality of a day. Its command flow is **create first, edit or undo later**: parse a completed command, immediately create the corresponding record, then show it in the relevant view. Routine creation has no confirmation dialog. Every automatically created field remains editable.

AI is never a core dependency of manual creation. Voice currently uses Qwen for semantic selection and explicit policy validation. User-configured KeywordRules initialize Category/Tags; they do not classify Task, Event or Record.

## Task, Event and Session

1. One real-world time interval cannot contain two overlapping Sessions.
2. At most one Session may be active at a time. An active Session has `temporalType = INTERVAL`, a start and no end; a Point also has no end but is never active.
3. Starting a Session while another is active atomically ends the old Session and starts the new one.
4. Ending a Session does not complete its related Task.
5. Task `completedAt` is stored independently. It must never be inferred from the last Session.
6. A Task distinguishes an optional planned date/point, `dueDate`/`dueTime`, estimate, `createdAt`, and `completedAt`. Task has no planned end or interval.
7. A Task may have no date or time.
8. A Session may have no related Task.
9. A Task may have no Sessions.
10. A Task may have multiple Sessions.
11. Completing a Todo directly does not require a Session. Todo & Done retains its `completedAt` state; Today does not add a separate completion fact.
12. An Event with planned time may appear directly on the timeline.
13. Event stores date-only, point or interval plans; actual facts are separate Records, optionally linked by `sourceEventId`.
14. A Task's planned time may appear on the timeline, but elapsed planned time never counts as actual time.
15. Every automatically generated record and field is manually editable.
16. Raw voice text is transient and is not stored long-term. Persist only the resulting structured data and the minimum metadata required for correctness.
17. A command must not create duplicates due to retries, duplicate callbacks or Compose recomposition. Future writes use a stable `commandId` and an idempotent transaction/uniqueness mechanism.
18. When every child Task is complete, the parent Task completes automatically.
19. Automatic parent completion does not require an extra timeline completion marker.
20. Category and Tag references use stable identities. Renaming one updates the displayed name everywhere, including historical records; names are not copied into records as the source of truth.
21. Task Category and Session Category are independent. A new Session may initially inherit its Task's Category, but later edits do not force synchronization in either direction.
22. Timeline gaps remain blank. The app does not label them as “unrecorded time”, prompt users to fill them, or compute coverage.
23. An expired plan does not automatically move to another date.
24. Planned time conflicts are allowed.
25. Actual Record intervals do not overlap by default. Point Records do not claim duration. Any future exception requires an explicit rule and migration-safe model change.

## Parent Task completion source

- A directly completed Task becomes `DONE`, stores `completedAtMillis`, and uses `completionSource = MANUAL`.
- When a parent has at least one child and every child is `DONE`, an eligible parent becomes `DONE` with `completionSource = CHILDREN`.
- If any child is later reopened, a parent that is `DONE/CHILDREN` becomes `IN_PROGRESS` and clears `completedAtMillis` and `completionSource`.
- A parent completed with `MANUAL` is not reopened by child changes.
- Completion/reopen reconciliation propagates through ancestors in one transaction. Parent cycles are rejected before persistence.

## Keyword rule resolution

- Category and Tags are optional metadata for Task, Event and Session: Category is `0..1`, Tags are `0..N`. `Category = null` and an empty Tag set are fully valid and never block creation or saving.
- Users may manually choose, clear, add or remove Category/Tags at any time. No confirmation or default classification is required.
- Classification precedence is: current user choice > KeywordRule initial result > empty. Once the user edits a record, its stored Category/Tag values are authoritative and are not recomputed.
- KeywordRule is an advanced, opt-in feature used only once while establishing a newly auto-created record's initial Category/Tags. With no matching rules, creation continues with null Category and empty Tags.
- Changing or deleting a KeywordRule never updates historical Task/Event/Session rows. Manual record edits never modify or create KeywordRules.
- KeywordRules are created, edited and deleted only by explicit user action. RIDO does not learn rules from history, recommend rules, infer frequent Categories, perform implicit personalization, or change rules in the background. Future AI must not modify them silently.
- v1 keyword semantics are `input.contains(keyword.trim(), ignoreCase = true)`. Matching is case-insensitive and uses Kotlin's stable character comparison; it performs no locale-specific transformation, Unicode normalization, tokenization, regex, boolean expression, fuzzy matching or NLP.
- Tags from every matched enabled rule are unioned and deduplicated.
- Archived Tags remain in historical cross-refs and Statistics, but are removed from keyword-rule results for every newly classified Task, Event and Record. Archiving does not delete their rules.
- Category selection is deterministic: higher `priority`, then longer `keyword`, then smaller rule `id`.
- A rule must have a non-blank trimmed keyword and at least one effective target: Category or Tag.

## Manual classification and identity

- Records persist only `categoryId` and Tag cross-references. There is no `categorySource`, `tagSource`, name snapshot or continuous rule binding.
- Category or Tag renaming changes historical display through the stable referenced identity; this is different from changing a KeywordRule.
- A new Session may copy its Task's current Category and Tags as initial values. After creation, Task and Session Category/Tags are independent; later changes never rewrite the other record or historical Sessions.

## Session structure

- `ACTIVITY`, `NIGHT_SLEEP` and `NAP` are structural `SessionKind` values, not user Categories.
- `NIGHT_SLEEP` defines awake-day boundaries and is omitted as a large homepage block. `NAP` may have a special Today presentation, but both Sleep kinds remain outside ordinary Day/Week, Month records, and Statistics.
- Session intervals use half-open semantics `[start, end)`, so adjacent Sessions are valid.

## Actual-time source of truth

Records are the source of actual occurrences. `POINT` Records have one instant and no duration. Completed `ACTIVITY` Records with `INTERVAL` temporal type supply actual activity duration for Day, Week and Statistics. Active Intervals retain a null end; Sleep has separate presentation and statistics handling. Event plans and Task planned points never become actual activity merely because their time passed.

All interval operations use non-empty half-open intervals `[start, end)`. `TimeInterval`/`IntervalMath` is the shared implementation for intersection, containment, subtraction, clipping and duration; feature code must not create alternative overlap semantics. A Record may refer to a source Event, but later edits to either object do not synchronize the other.

## Session lifecycle operations

- Starting a Session with no active Session inserts a new active row.
- Starting one while another is active atomically ends the old Session at the new start and inserts the new one. `newStart <= oldStart` is invalid.
- Stopping requires `end > start` and never completes a linked Task by itself.
- “Stop and complete” is a separate explicit transaction that stops the Session, manually completes its linked Task when present, and reconciles ancestors.
- A completed Interval Record requires a positive duration and cannot overlap another completed Interval Record. A Point Record requires an actual instant and has no end.
- Session edits exclude their own ID from overlap checks and may change all editable metadata and associations.
- Deleting a Record changes no Task, Category, Tag or Event. Deleting a source Event clears `sourceEventId` and keeps the Record.

## Awake Day and timeline projection

- `CalendarDate` remains the natural-date concept for Events, Tasks, deadlines and calendars. `AwakeDay` is a dynamic interval used by Today and daily reconstruction; there is no AwakeDay table.
- The awake-day start is the end of the NIGHT_SLEEP waking on the selected date. Its end is the start of the next NIGHT_SLEEP, including a sleep beginning after midnight.
- With no usable NIGHT_SLEEP boundary, caller-provided default wake/sleep minutes and `ZoneId` define the range. Domain code has no hard-coded user default.
- If the current unresolved awake day passes its default sleep boundary, the end expands to at least `asOfMillis`. Later NIGHT_SLEEP backfill changes the computed historical boundary automatically.
- NIGHT_SLEEP supplies optional `WakePoint`/`SleepPoint` entries and is not rendered as a large homepage block. NAP is a separate Sleep entry when specially presented on Today.
- Timeline is a pure projection, never a Room Entity. Today can show Record points/intervals, Event plans, Task planned points and sleep boundaries; Task completion is represented in Todo & Done.
- Events and Task plans remain planned. They do not produce actual duration.
- A DONE Task with non-null `completedAtMillis` and `completionSource != CHILDREN` may create a completion point. `CHILDREN` completion creates no marker.
- A new historical ACTIVITY Point or completed Interval Record may be saved alone or explicitly saved and used to complete its linked unfinished Task. The compound operation is transactional; Task `completedAtMillis` equals Point start or completed Interval end exactly. A DONE Task's completion time is never overwritten by this operation.
- Same-time ordering is defined by the current timeline projection implementation; no Event actual-state precedence is implied.

## Required field validity

### Task

- `title` is required and must contain non-whitespace content.
- Date, planned point, deadline, estimate, Category, Tags, location and notes are optional. A planned clock time requires a planned date. Task has no planned end.
- Completion is represented only by explicit `completedAt` or the parent-completion domain operation.

### Event

- `title` and calendar date are required.
- Date-only and point Events are valid. An Interval Event needs both endpoints and `end > start`.
- Category, Tags, location and notes are optional.

### Session

- `title`, actual `start`, and `temporalType` are required. A past date-only fact needs clarification of its actual time before creation.
- A `POINT` Session has no `end`; an active `INTERVAL` Session may have no `end`.
- A completed `INTERVAL` Session requires `end > start`; zero-duration intervals are invalid.
- Category, Tags, location, notes and Task relation are optional.
- Session creation/editing must enforce actual-overlap and single-active invariants in one domain transaction, not in a Composable.

## Ownership of decisions

- Voice Qwen chooses the primary semantic object first: responsibility → Task, arrangement → Event, actual fact → Record. It then chooses the object's valid time shape. Policy validates and requests structured clarification; it never changes the primary object. Planner maps the validated draft; it never reclassifies it.
- One Voice instruction creates at most one primary object. Even an explicit request for both a Task and Event asks the user to choose one for this turn. Ambiguous Task/Event meaning receives concrete option cards.
- A past date-only fact retains its known date and asks for the actual clock time. Voice never fills 00:00, 12:00 or now as a substitute. A future fact is a time-semantic conflict, not an Event conversion.
- `create_record` requires explicit `POINT` with `occurred_at`, or `INTERVAL` with `start_at` and `end_at`. `start_record` is the distinct active Interval operation. `complete_task` only completes an existing Task; ordinary facts never create a DONE Task.
- A Voice Record links a Task only when the user explicitly refers to it or selects it in the UI. Stopping a Record does not finish its Task. `stop_record_and_complete_task` requires the explicit combined request.
- `create_record_and_complete_task` likewise requires an explicit combined request, one existing unfinished Task, and the actual Point instant or completed Interval boundaries. Ordinary `create_record` never completes a Task. Historical completed Records may link a DONE Task, but a newly started active Record may link only an unfinished, non-cancelled Task.
- `create_record_from_event` preserves `sourceEventId`. Direct creation requires explicit actual time: `actual_at` for POINT, or `actual_start_at` and `actual_end_at` with end after start for INTERVAL. Missing actual time requires clarification or Editor confirmation; planned Event time is never saved directly as actual time. Missing or ambiguous source Event requires a visible choice; creation without a source is a separate, explicit user choice. Event and Record remain independently editable.
- UI renders state and emits user intent.
- ViewModel owns screen state and calls use cases.
- UseCase/domain service is the single implementation of business invariants.
- Repository exposes domain operations and observable data.
- Room eventually provides durable local storage behind repositories.
- DAO access from UI or ViewModel is forbidden.

## Transaction boundaries

- Starting a Session ends the previous active Session at the new start and inserts the new Session atomically.
- Completing or reopening a Task updates the Task and reconciles affected ancestors atomically.
- Executing a parsed command performs business writes and inserts its `ProcessedCommand` marker atomically.
- A `commandId` already present is a no-op. `ProcessedCommand` stores no original voice, text or prompt.

## Future work

Recurrence, search, reminders and system-calendar integration remain future work. This Voice change adds no Room fields or tables.

## Confirmed metadata/UI refinement (2026-09-21)

- Tags have MyLibrary's two-level structure. Search is confined to the active root and its children; an exact root-name query returns that root. Both levels remain selectable. No third level or global mixed search.
- Tag deletion in settings archives the affected root/children, preserving historical names and references. Rename still updates historical display by ID.
- Record category UI merges the system choices 夜间睡眠 → NIGHT_SLEEP and 小憩 → NAP; ordinary categories and unclassified choices map to ACTIVITY. The system choices are immutable UI options, not deletable user Category rows. Existing stored kind/category pairs are not rewritten unless the user changes classification.
- Category and Tags remain optional and independent. Category color is local display metadata only; it changes no classification, timeline occupancy or statistics.
- Task has no planned end. Event end is a planned interval boundary, not actual time. Record temporal type determines point versus interval editing.
