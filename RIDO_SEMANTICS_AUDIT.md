# RIDO Task / Event / Record 源码调查与数据库确认单

调查日期：2026-09-22。范围：当前 app/src/main 源码、Room Entity 和迁移。未运行应用或查询设备数据库。

本轮状态：完成源码调查；发现 Session 缺少 Event 来源字段，按用户“数据库变更保护”暂停数据库相关实现，等待确认。没有修改功能源码、Entity、数据库版本或 schema 导出文件。本文是调查结果，不是功能完成报告。

## 1. 当前 Entity 实际字段

源码目录：app/src/main/java/io/github/peanutpersimmon/rido/。

### TaskEntity（data/local/room/entity/TaskEntity.kt，表 tasks）

- id: Long（自增主键）
- title: String
- note: String?
- status: TaskStatus（TODO / IN_PROGRESS / DONE / CANCELLED）
- parentTaskId: Long?
- plannedDateEpochDay: Long?（start 日期）
- plannedStartMinuteOfDay: Int?（start 时刻）
- plannedEndMinuteOfDay: Int?（已有旧字段，保留）
- dueDateEpochDay: Long?
- dueMinuteOfDay: Int?
- estimatedDurationMinutes: Int?
- categoryId: Long?
- createdAtMillis: Long
- updatedAtMillis: Long
- completedAtMillis: Long?
- cancelledAtMillis: Long?
- completionSource: TaskCompletionSource?（MANUAL / CHILDREN）

createdAt 与 completedAt 均已有等价字段，直接复用，不新增字段或替换状态机。

### EventEntity（data/local/room/entity/EventEntity.kt，表 events）

id: Long；title: String；note: String?；location: String?；dateEpochDay: Long；startMinuteOfDay: Int?；endDateEpochDay: Long?；endMinuteOfDay: Int?；actualStartAtMillis: Long?；actualEndAtMillis: Long?；categoryId: Long?；createdAtMillis: Long；updatedAtMillis: Long。

可选结束日期与结束时刻已存在，但当前 EventEditorSheet 只提供开始日期/时间入口，需要补结束编辑 UI。actualStartAtMillis / actualEndAtMillis 是旧模型字段，不能把它们当作 Record 来源关系；本轮无需删列。

### SessionEntity（data/local/room/entity/SessionEntity.kt，Record 的持久化实体，表 sessions）

id: Long；title: String；note: String?；location: String?；kind: SessionKind；startAtMillis: Long；endAtMillis: Long?；taskId: Long?；categoryId: Long?；createdAtMillis: Long；updatedAtMillis: Long。

Task 关联为 taskId，已有指向 tasks(id) 的 SET_NULL 外键。没有 sourceEventId 或等价来源字段。EventTagCrossRef.eventId 只连接日程与标签，不能连接 Record。

## 2. 需要确认的唯一 schema 增量

需求缺口：保存 Event → Record 的持久来源、Record 编辑页展示“来自日程 + 标题”、删除 Event 时清除来源。

不能可靠派生：标题、时间、分类等均允许复制后修改，也可能重复；同一 Event 可以生成多条 Record。根据这些属性匹配会产生错误关联。taskId 仅表示 Task，不能复用；Event 的 actualStartAtMillis / actualEndAtMillis 也不指向某条 Record。

推荐最小字段：SessionEntity.sourceEventId: Long? = null，同时向领域 Session、Room mapper、NewSessionInput 和 Record 编辑状态透传。

当前数据库版本：2；已有 TagHierarchyMigration: Migration(1, 2)。推荐新增 Migration(2, 3)，保留原迁移，注册完整升级链。

建议 SQL（仅方案，未写入迁移源码、未执行）：

```sql
ALTER TABLE sessions ADD COLUMN sourceEventId INTEGER
    REFERENCES events(id) ON UPDATE NO ACTION ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS index_sessions_sourceEventId ON sessions(sourceEventId);
```

Entity 对应增加 nullable 列、ForeignKey(EventEntity, id → sourceEventId, onDelete = SET_NULL) 与索引。沿用当前 TagHierarchyMigration 的 nullable REFERENCES 增列方式，无需重建 sessions 表。

旧 Record 一律保留 sourceEventId = null，不根据名称/时间猜测回填。普通新建 Record 默认为 null。删除 Event 仅将引用它的 sourceEventId 置 null，Record 内容及 taskId 保留，绝不级联删除。撤销删除 Event 默认也不猜测恢复已经清空的来源。Event 与 Record 后续字段编辑互不自动同步。

此方案不增加 OVERDUE、RESCHEDULED、Completion 表、改期历史、Plan/Actual 字段或新状态。

## 3. 已定位的实现路径及问题

以下路径均相对 app/src/main/java/io/github/peanutpersimmon/rido/。

| 范围 | 现有源码入口 | 调查结果 |
|---|---|---|
| DAO | data/local/room/dao/{Task,Event,Session}Dao.kt | Task 投影候选仅覆盖 start 日期落在范围内或完成时间落在范围内，遗漏 due-only 与跨日当前任务 |
| Repository | data/repository/RoomRidoRepository.kt、RoomProjectionQueries.kt、RoomEditorOperations.kt、RoomSessionOperations.kt、RoomEventOperations.kt | 已有完成/重开和停止并完成任务事务；Event 改期更新原 id，删除入口直接删除 Event |
| Task 完成 | presentation/editor/task/TaskEditorViewModel.kt、ui/feature/editor/task/TaskEditorSheet.kt | toggleCompletion 使用当前时间；完成写 DONE 和 completedAtMillis；重开清空时间，恢复 IN_PROGRESS |
| 停止计时 | presentation/timer/TimerViewModel.kt、ui/feature/timer/TimerScreen.kt、domain/usecase/SessionUseCases.kt | 普通停止不完成 Task；StopSessionAndCompleteTaskUseCase 已有且共用同一结束时间，但 Timer UI 尚未接入第二选项 |
| Record 关联 | presentation/editor/session/SessionEditorViewModel.kt、ui/feature/editor/session/SessionEditorSheet.kt、domain/usecase/EditorUseCases.kt | ParentTaskPicker 使用全量 Task，不受 Today 可见性限制；已有“现在开始”入口 |
| Record 时间校验 | domain/validation/DataValidator.kt、data/repository/RoomSessionOperations.kt | 已检查标题、结束晚于开始、重叠及活动记录唯一性；保存链路未见禁止未来时间校验，需要补齐，不能假定当前已阻止未来 Record |
| Event 编辑 | presentation/editor/event/EventEditorViewModel.kt、ui/feature/editor/event/EventEditorSheet.kt | 保存同一 Event；缺少结束时间 UI、Send 动作与 Record 预填流程 |
| Today 数据 | presentation/today/TodayViewModel.kt、TodayUiMapper.kt、TimelineEndpointMapper.kt；domain/usecase/ProjectionUseCases.kt、GetTimelineDisplaySourcesUseCase.kt | 当前 Today 只取当天 start 任务，完成任务从摘要移除；due 仍可能成为轴锚点，需改为截止辅助信息 |
| Today Header/下拉 | ui/feature/today/TodayHeader.kt、TodayScreen.kt、TodayPullState.kt、TodayPlanSummary.kt | Header、睡眠、+、悬浮摘要和顶部继续下拉均已存在；没有日期切换；展开行无点击回调，UI model 丢失 Task/Event 类型 |
| Today 时间轴 | ui/feature/today/TodayTimeline.kt、TimelineCardLayout.kt、TimelineAxis.kt、TimelineEntryContent.kt | 当前轴在左、卡片均在右；timeY、endpoint 去重、标签避让、睡眠边界、当前时间点+右向线、有限 zoom、小憩样式均已有，可复用 |
| 完成点 | domain/model/TimelineModels.kt、domain/service/TimelineProjector.kt、presentation/today/TimelineEndpointMapper.kt | 已有纯展示 TaskCompletionPoint，不是 Entity；但 endpoint mapper 清空该类 axisTimes，布局又过滤空 axisTimes，所以 Today 不会正常画出；尚无 taskId + end == completedAt 去重 |
| Event 事实旧逻辑 | domain/service/ActualTimeResolver.kt、TimelineProjector.kt | 仍有 EVENT_IMPLICIT / EVENT_EXPLICIT，把计划日程推断或投影为实际；需要从本轮事实展示链路移除，不能当作已满足最终语义 |
| Today 编辑路由 | ui/RidoApp.kt、ui/feature/today/TodayEditorSheet.kt | 现有 Timeline Task/Event/Session 点击可打开共享底部编辑器；顶部尚未接入 |
| 视图查询 | presentation/plan/PlanViewModel.kt | Day/Week 只取已结束 Record，不加载 Task 完成点；Todo & Done / Board / Task List / Month 计划模式取全量 Task |
| Todo & Done | ui/feature/plan/PlanTimeline.kt | 按当前 status 排除 DONE，历史状态不正确；右侧仅 Record |
| Board / Task List / Month | ui/feature/plan/PlanContent.kt | Task 单元格按 start/due 当日放置；No Date 槽已存在；状态显示尚未结合参考日期、删除线或派生 overdue；Month 双模式已存在 |

## 4. 对用户 38 项汇报要求逐项回答

以下“待实现”表示本次调查未修改此功能，不能视为通过验收。

| 编号 | 回答 |
|---|---|
| 1 | Task 时间/状态字段详见第 1 节；含现有 IN_PROGRESS、plannedEnd、cancelledAt、completionSource，均不计划删除。 |
| 2 | 已有 createdAtMillis。 |
| 3 | 已有 completedAtMillis。 |
| 4 | 已有 endMinuteOfDay 和 endDateEpochDay，均可空；编辑 UI 未暴露结束输入。 |
| 5 | Record 关联任务字段是 Session.taskId / SessionEntity.taskId。 |
| 6 | 没有 Event 来源字段。 |
| 7 | 需要一次最小 schema 增量和 2→3 Migration，仅增加 sourceEventId 与索引/外键。 |
| 8 | 已停下数据库相关实现，等待用户按原请求确认；尚未修改 schema。 |
| 9 | Today 当前没有日期切换，保持现状。 |
| 10 | Header、悬浮摘要、顶部继续下拉均保留，未修改。 |
| 11 | 原 timeY/布局保持未改；中央轴左右布局待实现。 |
| 12 | 没有新增普通 Card→轴连接线；现有当前时间指示线未动。 |
| 13 | 尚未实现计划左侧；当前所有卡片在轴右侧，due 仍可参与锚点。 |
| 14 | 顶部已有无时间摘要，但当前有效、跨日、逾期 Task 规则不完整，待修正。 |
| 15 | 否，due-only 从创建后进入 Today 尚未实现。 |
| 16 | 尚未完整实现跨日滚动：当前查询会遗漏跨日 Task；应改为顶部展示，不复用昨日 start 时刻。 |
| 17 | 否，Today 标题还没有完成删除线，需增加仅标题删除线。 |
| 18 | 有完成点展示模型，但当前 axisTimes 处理导致 Today 中不显示；待修正。 |
| 19 | 当前完成动作不生成假 Record；将继续保持。 |
| 20 | 否，现有投影未按 taskId 与 endAtMillis == completedAtMillis 去重。 |
| 21 | 没有 OVERDUE 数据字段；动态逾期与历史参考日期规则仍需实现。 |
| 22 | Event 没有完成/逾期持久状态；但存在旧实际时间字段及隐式事实投影，需清理本轮展示语义。 |
| 23 | 改期更新原 Event，没有改期历史表。 |
| 24 | 尚无 Event Send 纸飞机。 |
| 25 | 因尚无纸飞机，未产生旁边文字说明；实施时保持纯图标。 |
| 26 | 尚无 Event→预填 Record 编辑器流程。 |
| 27 | 来源字段尚不存在；确认后按只表示来源、不自动同步的语义实现。 |
| 28 | 原 Task 关联入口存在且未改，可选择非 Today Task。 |
| 29 | 没有增加通用 Event 关联 picker。 |
| 30 | 否，顶部展开 Task/Event 行目前不可点击编辑。 |
| 31 | 当前已有 Timeline 卡片点击编辑；左右分栏尚未实施。 |
| 32 | Todo & Done 保留。 |
| 33 | 否，Todo & Done 右侧当前仅 Record。 |
| 34 | Day/Week 当前为 Record 事实视图；尚未加入 Task completion point。 |
| 35 | 否，历史 Task 目前直接套用当前 status，待按 createdAt/start/due/completedAt/referenceDate 修正。 |
| 36 | 仅新增本调查文档 RIDO_SEMANTICS_AUDIT.md；没有修改任何功能源码、Entity 或 Migration。 |
| 37 | 未运行 Gradle、Git、ADB、模拟器或真机。仅读取源码、搜索和写本文档。 |
| 38 | 未 commit / push。 |

## 5. 确认后的实现边界

复用现有字段、共享编辑器和 timeY，只增加上述一个来源字段；完成持久化来源链路、Event→Record 预填与严格实际时间校验；用共享派生 helper 统一 Today / 历史状态、完成点去重和逾期；在现有轴布局上分计划/事实两侧；接通顶部编辑及停止计时两种动作；保留其它页面布局与单元格放置规则。

必要的静态检查包括来源字段的 mapper/编辑/保存透传、迁移与 Entity 声明一致、删除不级联、历史日期边界、完成点与 Record 去重、所有编辑回调。遵守不运行 Gradle/Git/ADB/设备的限制，不把静态审阅称作编译或运行验证。
