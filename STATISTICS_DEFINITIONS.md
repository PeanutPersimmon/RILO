# Statistics Definitions

Statistics answer an explicit user question. A chart is selected only after its question, population, time boundary and aggregation are defined. Ordinary Category/Tag statistics and the separate Sleep views have different input populations.

## Source-of-truth rules

- Ordinary Category/Tag statistics consume only completed `ACTIVITY` Records with `temporalType = INTERVAL`, `endAtMillis > startAtMillis`, and an end no later than the calculation time. A non-null `sourceEventId` does not exclude a valid Record.
- Point Records contribute zero duration and zero count to total, Category, Tag, trend, daily cells and ranks. Active Intervals, Task completion, Task plans and Event plans contribute zero.
- `NIGHT_SLEEP` and `NAP` are a separate Sleep data domain. Their intervals never enter ordinary Category/Tag duration or count. Sleep views use their own completed Session endpoints.
- Timeline gaps are blank and are excluded. There is no “unrecorded time”, coverage percentage or completion pressure.
- Planned conflicts are allowed. Planned time contributes no actual duration.
- Actual Session overlap is invalid by default, preventing double-counted duration.
- Category/Tag grouping resolves current names through stable identities; rename does not split history.

## Time boundary: awake day

The Today timeline uses an awake-day interval rather than fixed `00:00–24:00`. Ordinary Statistics uses its selected local-calendar period.

1. With sleep data: night-sleep end/wake time through the next night-sleep start.
2. Without sleep data: the user's default awake range.
3. For the current day before sleep begins: wake time through default sleep time.
4. For a historical day with known real sleep start: use that real endpoint.
5. Never infer wake time from the day's first record.
6. Night sleep itself is excluded from the large daytime timeline block. Nap may have a special Today presentation but remains outside ordinary Day/Week and Statistics.

`AwakeDayCalculator` now implements these dynamic boundaries from `NIGHT_SLEEP`; persistence of default settings remains **Future / not implemented in v1**.

## Candidate questions and metrics

These definitions constrain future implementation; they do not authorize a dashboard full of charts.

### “Where did actual time go?”

- Population: completed `ACTIVITY` Interval Records intersecting the selected local-calendar range.
- Measure: valid interval duration clipped to the selected period.
- Grouping: each Record uses its own `categoryId` and Tag identities, regardless of `sourceEventId`.
- Uncategorized remains a neutral group; it is not auto-labeled with a built-in Category.

### “How is my day structured?”

- Population: completed `ACTIVITY` Interval Records ordered by start, with plans kept separate.
- Measure: interval position and duration.
- Gaps remain visually empty and have no score.
- Planned Task and Event entries never contribute actual duration.

### “What are my longer-term patterns?”

- Population and interval must be stated by the feature before aggregation.
- Candidate measures include actual Session duration by day or Category and time-of-day distribution.
- Heatmap cell meaning, normalization and missing-day behavior must be documented before implementation.

### Single Task statistics

- `actualTotalDuration`: sum of valid completed `ACTIVITY` Interval Records linked to that Task within the chosen scope.
- `sessionCount`: count of linked completed `ACTIVITY` Interval Records included in the same scope.
- `firstStartedAt`: earliest linked Session start, or absent.
- `completedAt`: explicit Task value, including domain-driven parent completion; never inferred.
- `estimatedTotalDuration`: future explicit estimate field if product design approves it.
- `actualTotalDuration` and `estimatedTotalDuration` are never substituted for one another.

## Exclusions and quality rules

- Active Session duration is excluded from ordinary Statistics.
- Existing records are queried regardless of their Category/Tag archive state; archived names remain readable through stable IDs. Deleted records contribute nothing. A hard-deleted Category reference becomes Uncategorized.
- No percentage of the awake day is called “coverage”.
- No metric should reward filling all time or shame blank intervals.
- Rounding, locale, week-start, timezone-change behavior and partial-interval clipping require unit tests.
- Data corrections must flow through the same source-of-truth queries so past statistics update consistently.

## Future / not implemented in v1

Task detail metrics and sleep-derived awake-day statistics remain future work. Implemented Category/Tag charts and heatmaps are defined in the Statistics page contract below.

## Statistics page contract (2026-09-22)

- Category and Tag use the same local calendar period: day, Monday-start week, month, or year. Previous/next moves exactly one selected period. The period and anchor survive overview/detail and Category/Tag switching.
- Confirmed duration is calculated from completed `ACTIVITY` Interval Records only, clipped to the selected local-calendar interval. Invalid or future ends, active Intervals, Point Records, Sleep, Event plans and Tasks contribute no duration or ordinary count.
- Total actual duration counts each valid interval once. A Record belongs to one Category: its stored Category ID or Uncategorized. Each stored Tag ID independently receives the full interval duration, so Tag ranks can sum above the total.
- Detail trends split those same intervals at local midnight: week/month by day and year by month. Day detail has no synthetic sub-day trend. Month/year calendar cells measure confirmed duration for that date; missing days have zero duration.
- Sleep calendar adapts Sleepc's Bed Time, Wake Time, and Duration modes. A NIGHT_SLEEP Session uses its end date when finished and start date when unfinished. The latest updated night record for a date is displayed. Bed/wake time can appear independently; duration requires a positive persisted start/end pair. No subjective fields are inferred.
- Sleep line charts use seven-day windows clipped at month boundaries, one latest NIGHT_SLEEP per date, a 24-hour offset for bedtimes before noon, and 24-hour clock labels. Night duration columns show each date's NIGHT_SLEEP minutes. Nap columns sum completed NAP minutes by end date. Empty and incomplete values leave chart gaps.
