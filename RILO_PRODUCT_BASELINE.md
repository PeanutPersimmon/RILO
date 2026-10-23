# RILO 产品范围基线

本文件记录 Phase 1 已冻结的后续方向。当前代码仍是 RIDO 的完整副本，Task、Tag、对应页面、提醒和 Room 表在 Phase 1 均保留。以下要求留待后续阶段实现。

## 业务域

- **Event**：计划何时做什么；支持 date-only、point、interval。保留标题、详情、时间、分类、现有 location、reminder，以及 Event → Record 预填。最终不含 completed、completedAt、due、overdue、estimatedDuration、priority。
- **Record**：实际发生的事实；保留 point、completed interval、active interval，及标题、详情、实际时间、分类、location、现有 `sourceEventId?`。保留编辑器“上一事实 end → now”的默认时间逻辑。
- **Sleep**：独立保留 NIGHT_SLEEP 和 NAP；Nap 不转为普通 Record。保留 Today 睡眠边界、Nap 特殊卡片、Sleep Statistics、睡眠编辑与语音逻辑。
- **Category**：保留，供分类统计使用。
- **Task / Tag**：最终完全删除，包括相关业务入口、统计、提醒和持久化；Phase 1 不执行删除。

## 页面与数据源目标

- 底部主要功能继续沿用当前结构。
- Today 最终仅展示 Event、Record、Nap 和 Sleep boundary。
- Plan 保留 Day、Week、2×4 Week、Todo & Done、Month 五个视图，移除 Task List。
- Day 和 Week 使用 completed ordinary Interval Record；2×4 Week 仅使用 Event；Todo & Done 左侧 Event、右侧 Record + Nap，Night Sleep 继续作为清醒边界；Month / Plan 使用 Event，Month / Record 使用 ordinary Record。
- Statistics 最终仅保留 Category 和 Sleep。Category 仅统计 completed ordinary Activity Interval Record；Sleep 使用独立统计来源。

## Voice 与 Reminder 目标

- Voice 最终只区分 Event、Record、Sleep。未来安排 → Event，已发生事实 → Record，睡眠语义 → Sleep。沿用当前“先判断语义，再判断时间形态”，不以存在 end 时间作为 Event 判据。
- 保留 Event reminder。Event 有具体 start time 时支持全局默认提前量，显式“提前 N 分钟提醒我”覆盖默认值。最终移除 Task reminder。

## Phase 1 边界

Phase 1 仅复制项目、隔离 Android 身份与本地命名空间、替换品牌和图标，并验证构建。它不修改业务实体、Room schema/version/Migration、Today/Plan/Statistics 数据源或 Voice/Reminder 业务。
