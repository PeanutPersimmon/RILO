# RIDO Task / Event / Record 实施现状

本文记录当前冻结模型的生产语义。Room 当前为 v5；本轮三个 Blocker 的修复不增加字段、不修改 schema 或 Migration。

## 一级对象与时间形态

- Task 表示责任：可无日期、有计划日期、计划日期加时间点、due、估计时长和 `completedAtMillis`。Task 没有计划结束时刻或计划区间。
- Event 表示安排：date-only、point、interval 均为计划，没有完成、逾期、估计或实际时间列。
- Record 以 Session 存储事实：`POINT` 有 start 且 end 为 null；完成的 `INTERVAL` 有正向 start/end；active `INTERVAL` 有 start 且 end 为 null。判断 active 必须同时检查 `temporalType = INTERVAL`。
- `NIGHT_SLEEP` 与 `NAP` 共用 Session 表，但属于独立 Sleep 数据域，只能为 `INTERVAL`，不携带普通 `taskId`、`categoryId`、`sourceEventId` 或 Tags。

## 页面与统计边界

- Today 的计划侧显示 Task Point、Event Point、Event Interval；事实侧显示 Point、完成和 active Interval Record。Task completion 不作为独立 Today fact。
- Todo & Done 左侧为 Task/Event，右侧为 Point/完成 Interval Record 及 Task `completedAtMillis` 状态；active 与 Sleep 不进入。关联 Record 与 Task 完成时刻仅按 ID 和精确毫秒去重。
- Day/Week 只使用完成的 `ACTIVITY INTERVAL` 专用查询。Month 计划浏览 Task/Event；Month 记录浏览 `ACTIVITY POINT` 和完成的 `ACTIVITY INTERVAL`。普通 Statistics 仅统计完成的 `ACTIVITY INTERVAL`；Point 的所有次数与时长指标为零。
- Nap 可以在 Today 特别呈现，但不进入普通 Day/Week、Month 记录、Category/Tag 或普通 Statistics。Sleep 图表读取独立 Sleep 数据域。

## 关系与删除

- `Record.taskId` 和 `Record.sourceEventId` 是可选来源关系。Record 结束默认不完成 Task；只有明确的 `stop_record_and_complete_task` 同时完成。
- 历史 ACTIVITY Record 可通过独立“保存并完成任务”操作，在同一事务中创建 Record 并完成明确关联的未完成 Task；Point 用 start、完成 Interval 用 end 精确写入 `completedAtMillis`。普通保存和普通 `create_record` 不完成 Task；已完成 Task 的完成时刻不被覆盖。Voice 对应显式 `create_record_and_complete_task`。
- 历史 Point/完成 Interval 可明确关联 DONE Task；新 active Record 仅接受未完成且未取消的 Task。编辑已有 Record 时保留后来变为 DONE 的原关联。CHILDREN 自动完成父 Task 仍保存 `completedAtMillis`，但不生成 Todo & Done 独立 completion fact。
- Archived Tag 不进入新对象的关键词自动分类结果；历史 Tag 实体、交叉引用和 Statistics 聚合继续保留。
- Event 转 Record 只预填并保留 `sourceEventId`，保存后两者独立。删除 Task/Event 分别将 Record 的关联 ID 置空并保留 Record；删除 Record 不影响 Task/Event。Task 与 Event 没有直接关系。

## 本轮三个 Blocker 的闭环

1. Voice 更新完成 Interval 时清除 end，将它转换为 Point，保留 start；更新既有 active Interval 的普通字段仍保持 active。Point 补入明确 end 转成完成 Interval。
2. Event Interval 在计划开始后、结束前打开 Record Editor 时，预填计划 start，但维持待补完的 Interval 草稿，end 为空；结束时间未晚于实际开始、晚于当前时刻或未填写时，保存和“现在开始”均不可用。已经结束的 Event Interval 可以预填完成 Interval；Point 仍预填 Point；date-only 不编造时刻。
3. `create_record_from_event` 只有收到明确 `actual_at` 或明确的 `actual_start_at`、`actual_end_at` 才直接写 Record。缺实际时间时交由澄清或 Record Editor 确认；Event 计划时间不直接当事实。直接写入保留 `sourceEventId`。

Voice 先判责任/安排/事实，再判时间形态。Policy 校验或澄清，Planner 映射已确定类型；普通 Record 不按标题自动关联 Task。`complete_task` 找不到已有目标时不新建 DONE Task。一条语音默认最多处理一个一级对象。

## 验证范围

本轮新增三个 Blocker 的回归用例，覆盖 Editor 中未完成 Event Interval、Voice 清除 end 的形态转换，以及 Event 转 Record 缺实际时刻禁止直接写入。构建和测试结果以本轮验收回复为准。
