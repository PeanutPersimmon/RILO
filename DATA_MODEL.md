# RIDO Data Model Contract

This document describes the current Room v5 schema. `RidoDatabase` uses database file `rido.db`, version `5`, and exports its canonical schema to `app/schemas/`. Entities use stable primitive storage; UI formatting never enters the database.

## Time representation

- Absolute occurrences use epoch milliseconds (`Long`): Record start/end, Task created/updated/completed/cancelled, and audit timestamps.
- Date-only semantics use epoch day (`Long`): Task planned/due dates and Event planned dates. A date-only deadline is never converted to `23:59:59`.
- Local planned clock time uses minute of day (`Int`, `0..1439`) alongside epoch day. It is not an absolute instant.
- Task has an optional planned date and point, never a planned interval. Event has optional `endDateEpochDay` and may cross midnight. Record intervals use absolute milliseconds and may cross midnight without splitting rows.

## Tables

### `tasks` / `TaskEntity`

`id`, `title`, `note`, `status`, `parentTaskId`, `plannedDateEpochDay`, `plannedStartMinuteOfDay`, `dueDateEpochDay`, `dueMinuteOfDay`, `estimatedDurationMinutes`, `categoryId`, `createdAtMillis`, `updatedAtMillis`, `completedAtMillis`, `cancelledAtMillis`, `completionSource`.

`TaskStatus` is `TODO`, `IN_PROGRESS`, `DONE`, or `CANCELLED`. `TaskCompletionSource` is nullable and, when complete, is `MANUAL` or `CHILDREN`. Enums are stored as stable `TEXT` names through centralized converters.

Task title is trimmed and non-blank. Planned time requires a planned date. Due time requires due date. Estimated duration is nullable and positive; it does not create an end time. Parent self-reference and cycles are rejected by `DataValidator` before writes.

`categoryId` is nullable and the Task may have zero Tag cross-refs. Unclassified Tasks are valid and must not be blocked or assigned a default.

### `events` / `EventEntity`

`id`, `title`, `note`, `location`, `dateEpochDay`, `startMinuteOfDay`, `endDateEpochDay`, `endMinuteOfDay`, `categoryId`, `createdAtMillis`, `updatedAtMillis`.

Date-only and point Events are valid. An interval end requires a start and must follow it; a missing end date means the start date. Events store plans, not actual facts.

`categoryId` is nullable and the Event may have zero Tag cross-refs. Category and Tags can be manually selected, cleared or replaced without affecting creation validity.

### `sessions` / `SessionEntity`

`id`, `title`, `note`, `location`, `kind`, `temporalType`, `startAtMillis`, `endAtMillis`, `taskId`, `sourceEventId`, `categoryId`, `createdAtMillis`, `updatedAtMillis`.

`SessionKind` is stored as `TEXT`: `ACTIVITY`, `NIGHT_SLEEP`, or `NAP`. `SessionTemporalType` is `POINT` or `INTERVAL`. A Point has one actual instant and no end. An active Interval has null end; a completed Interval requires `endAtMillis > startAtMillis`. Intervals are half-open `[start,end)` and cannot overlap; adjacent intervals are valid. Repository health semantics require globally zero or one active Interval. Sleep remains Interval.

`endAtMillis = null` alone does not identify an active Record: `POINT` also has no end. `NIGHT_SLEEP` and `NAP` are a separate Sleep data domain and must have `temporalType = INTERVAL`, null `taskId`, `categoryId`, and `sourceEventId`, and no Tag cross-refs.

`categoryId` is nullable and the Session may have zero Tag cross-refs. A Session may copy a related Task's Category/Tags at creation, but the persisted Session values are independent thereafter.

### `categories` / `CategoryEntity`

`id`, `name`, `isArchived`, `sortOrder`, `createdAtMillis`, `updatedAtMillis`.

Categories are user-defined. Trimmed names are non-blank but are deliberately not database-unique or case-folded, avoiding unstable Unicode/collation behavior. Settings may warn about duplicates later. Ordinary removal archives the Category so historical references and statistics remain readable.

### `tags` / `TagEntity`

`id`, `name`, `isArchived`, `sortOrder`, `createdAtMillis`, `updatedAtMillis`.

Tags use MyLibrary-style two levels: nullable parentId references tags(id), with an index and SET_NULL on hard delete. A root has no parent; its children cannot have children. Repository writes validate depth, sibling names (trimmed, case-insensitive), and complete sibling sort order.

### Tag cross-reference tables

- `task_tag_cross_refs(taskId, tagId)`
- `event_tag_cross_refs(eventId, tagId)`
- `session_tag_cross_refs(sessionId, tagId)`

Each pair is a composite primary key. Records store Tag identity only—never comma-separated names or snapshots—so a rename is visible historically.

### `keyword_rules` / `KeywordRuleEntity`

`id`, `keyword`, `categoryId`, `isEnabled`, `priority`, `createdAtMillis`, `updatedAtMillis`.

`keyword_rule_tag_cross_refs(keywordRuleId, tagId)` provides zero-to-many Tag targets. The keyword is trimmed/non-blank. A valid rule has at least a Category or one Tag. v1 uses a case-insensitive trimmed substring match and no normalization, regex, fuzzy matching, boolean expression, NLP or AI. Multiple matches union Tags; Category order is priority descending, keyword length descending, id ascending.

KeywordRule is optional and applies only when calculating initial classification for a newly auto-created record. No rules or no matches produces null Category and empty Tags without preventing creation. Later manual edits directly replace the record's current Category/cross-refs and take precedence; they neither update the rule nor create a new one. Rule changes never backfill historical records. No automatic learning, recommendation, implicit personalization or background rule mutation exists.

Archived Tag targets remain stored on KeywordRules for possible future restoration, but rule results exclude those Tag IDs for new objects. Historical Tag entities, cross-refs, display and Statistics remain intact.

### `processed_commands` / `ProcessedCommandEntity`

`commandId` string primary key and `processedAtMillis`. It stores only idempotency metadata—never original voice, text, prompt or parsed transcript.

## Foreign keys and deletion

- `tasks.parentTaskId -> tasks.id`: `ON DELETE SET NULL`; deleting a parent keeps children.
- `tasks/events/sessions.categoryId -> categories.id`: `ON DELETE SET NULL`; Category deletion never cascades into records. Normal UI archives instead.
- `sessions.taskId -> tasks.id`: `ON DELETE SET NULL`; Task deletion keeps historical Sessions.
- `sessions.sourceEventId -> events.id`: `ON DELETE SET NULL`; Event deletion keeps the independent historical Record.
- `keyword_rules.categoryId -> categories.id`: `ON DELETE SET NULL`.
- Record-to-Tag cross-refs: deleting the owning record or hard-deleting a Tag cascades only the cross-ref row.
- KeywordRule-to-Tag cross-ref: deleting the rule or hard-deleting a Tag cascades only the cross-ref row.
- Deleting Task/Event/Session cascades its Tag cross-refs. Event deletion clears a linked Record's `sourceEventId`, but preserves that Record and its actual time.

Category and Tag display names are never snapshotted into records. Task Category and Session Category are independent after Session creation.

Historical ACTIVITY Record creation plus completion of an explicitly linked unfinished Task is one transaction without new schema. For POINT, `completedAtMillis` equals Record `startAtMillis`; for completed INTERVAL, it equals Record `endAtMillis`. Plain Record creation never updates Task completion. Historical completed Records may link DONE Tasks; starting an active Record rejects DONE and CANCELLED Task links.

The schema deliberately has no `categorySource` or `tagSource`. The current FK and cross-ref rows are the complete current truth regardless of how their initial values were chosen.

## Index strategy

- `tasks`: `status`, `parentTaskId`, `plannedDateEpochDay`, `dueDateEpochDay`, `categoryId`, `completedAtMillis` support status, hierarchy, date, grouping and completion-point queries.
- `events`: `dateEpochDay`, `categoryId` support planned-day and grouping queries.
- `sessions`: `startAtMillis`, `endAtMillis`, `taskId`, `sourceEventId`, `categoryId`, `kind` support actual-time queries, source relations, Task history and sleep structure. `temporalType` is a stored field used in filters, not a separate index.
- `categories` and `tags`: composite `(isArchived, sortOrder)` supports active management lists.
- `keyword_rules`: composite `(isEnabled, priority)` plus `categoryId` supports enabled deterministic rule loading and FK access.
- Every cross-ref composite primary key indexes its owner-side prefix; an additional Tag-side index supports reverse lookup and FK maintenance. No speculative statistics indexes are created.

## Views, not tables

Today is a projection of Task and Event plans and actual Record points/intervals; it does not show a separate Task completion fact. Todo & Done combines Task/Event plans, Point and completed Interval Records, and Task `completedAtMillis` state. There are no Timeline, Todo, Done or Calendar entities.

The runtime projection model adds no schema objects:

- `TimeInterval` and `IntervalMath` provide shared half-open `[start,end)` operations.
- Completed Activity Intervals are the source of actual duration. Point Records carry an occurrence but no duration. Event plans do not become actual time implicitly.
- `AwakeDay` is calculated from NIGHT_SLEEP boundaries or caller-provided defaults; it is never persisted.
- Today entries include Record points/blocks, Event plans, Task planned points and sleep boundaries; Nap may have a special Today presentation but does not enter ordinary Day/Week, Month records, or Statistics.
- `TaggedTask`, `TaggedEvent`, and `TaggedSession` combine existing rows with current Tag identities for Domain projection only; they are not entities.

DAO queries supply bounded Record, Event and Task projections and NIGHT_SLEEP boundaries. Voice Phase 3 adds no tables, views, triggers, indexes or columns.

## Transactions and idempotency

- Start Session: finish the existing active Session at the new start and insert the new active Session atomically.
- Complete/reopen Task: update the Task and reconcile eligible ancestors atomically.
- Parsed command: check `commandId`, perform business writes, and insert `ProcessedCommand` atomically; an existing id is a no-op.
- Cross-ref replacement commits with its owning record write.

## Data health checks

A future `DataHealthChecker` must detect: more than one active Interval Session, invalid Point/Interval shapes, Session overlap, Task self-parenting, Task parent cycles, invalid foreign keys/cross-refs, and KeywordRule rows with no effective target. SQLite foreign keys prevent dangling references during normal Room writes; complex temporal and graph invariants remain centralized in `DataValidator` and repository transactions.

## DataStore boundary

DataStore remains for low-volume preferences such as theme mode, default awake range and Plan-view configuration. Business records and histories belong in Room.

## Future work

Recurrence, reminders, search/FTS, AI-specific metadata, raw capture storage, GPS/location databases and system-calendar identifiers are absent. Future additions require a versioned schema, explicit Migration and migration tests. Import/export must carry `schemaVersion`; destructive migration is forbidden.

## Historical v1 → v2 migration notes

Only TagEntity gains parentId plus its index/FK. Task/Event/Session/Category/KeywordRule entities and all cross-ref schemas are unchanged. TagHierarchyMigration uses ALTER TABLE, never destructive migration. Existing flat tags retain IDs, names, archive flags, ordering, timestamps and every cross-reference; they become children of a new 原有标签 root. An empty database gains no synthetic root.

Tag settings deletion uses archive semantics: a root and its children leave the selectable list while historical references remain intact. Existing selected archived tags remain visible/removable in the editor. This intentionally differs from MyLibrary's physical delete to preserve RIDO history. New root/child creation and batch updates are transactional. Both root and child tags can be selected, matching MyLibrary.

Category display colors live in appearance DataStore keyed by category ID; CategoryEntity is unchanged. The night preference is the only application appearance setting. Record sleep choices are virtual system UI entries mapped to existing SessionKind; no fake category ID or extra table is inserted.

The canonical v1 and v2 schema exports remain historical migration artifacts. This section records the earlier tag-hierarchy migration; the current schema and runtime contract are v5 as described above.
