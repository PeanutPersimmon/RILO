# RIDO 编辑器、固定外观与二级标签变更记录

## 完成范围

本记录涵盖连续进行中的编辑器重做及最新的固定外观、MyLibrary 二级标签适配。未增加业务页面或无功能工具入口。任务完成、现在开始、计时器等既有动作保留；普通时间占用与完成规则不变。

| 编辑器 | 显示字段 |
| --- | --- |
| Task | 标题、细节、开始、截止、预计、分类、标签、父任务 |
| Event | 标题、细节、开始、分类、标签、地点 |
| Record | 标题、细节、开始、结束、分类、标签、地点、关联任务 |

新建/编辑共用 EditorLayout 和同一套字段组件。右上角为保存 Check、编辑态删除 Delete、关闭 Close；有 contentDescription，禁用保存灰化，无底部保存按钮。Today 保持在组成树中，因此关闭 Sheet 不重建时间轴。原来的 Task plannedEnd、Event end/actual 数据保留在加载/保存模型中，不因隐藏字段而清空。

主输入为标题 15/21sp、细节 13/19sp，无边框，连续排列；Sheet 标题 16/22sp、工具 12/16sp。拖柄上下 8dp，模块间距 8dp。空辅助字段只有裸图标，目标 48dp；已有值显示自适应 pill，FlowRow 按整个项换行。时间值 maxLines=1、softWrap=false，PlayArrow / Flag / HourglassEmpty / Stop / Schedule 分别表示开始 / 截止 / 预计 / 结束 / 事件开始。

分类入口：FolderOpen；标签：Label；地点：Place；关联任务：Link；父任务：AutoMirrored.SubdirectoryArrowRight。Record 不再有记录类型入口，夜间睡眠/小憩是分类选择器里永久的系统项，映射现有 NIGHT_SLEEP/NAP；普通分类或清空映射 ACTIVITY。系统项不在用户表中，因此用户管理操作不能删除。未更改分类时保留存量 kind/category 组合。

## 固定灰阶外观

只有日间/夜间，偏好由 DataStore 保存。移除 Follow System 分支；没有引入自定义主题、主题预设、主题 JSON、动态色或 accent 入口。保留 RidoTheme 作为固定 Material/字体/尺寸包装，不是主题编辑系统。

日间背景 #FAFAFA、Surface #FFFFFF、主文字 #111111、次文字 #626262；夜间背景 #0C0C0C、Surface #121212、主文字 #F2F2F2、次文字 #B8B8B8。全部 Material 颜色槽（包括 error、secondary/tertiary、container/fixed）明确设为灰阶，启动窗口也为灰阶。Material ripple 全局关闭，共用点击组件无阴影。

唯一可彩色的分类按 ID 存储 DataStore 显示偏好；选择分类后可设置 #RRGGBB 或恢复灰阶。分类 pill/图标及 Timeline 圆点、分类标签可用该色，低对比度时只调整显示以可读；不改变保存按钮、Tag pill、AppBar 或 Sheet。

## MyLibrary 实际复用来源

所有来源只读，未修改 MyLibrary：

- data/entity/TagEntity.kt：同表 parentId、两级结构和父级索引/FK。
- data/repository/OfflineTagRepository.kt：同级名称校验、批量创建、父级深度校验、完整同级排序事务。
- domain/usecase/TagManagementUseCases.kt：观察、创建/重命名/删除/排序职责。
- ui/components/TagSearchPolicy.kt：直接移植当前一级范围搜索；精确命中一级名称时只返回一级；一级本身可选。
- ui/components/TagSelectionSheet.kt：一级切换、搜索、临时多选、确认提交/关闭丢弃、搜索新增子标签。
- ui/settings/TagManagementScreen.kt：迁移一级长按拖动、二级拖柄拖动及边缘滚动算法；一级切换、子级列表、管理菜单。
- ui/settings/TagManagementUiState.kt / TagManagementViewModel.kt：一级/二级过滤、排序、当前一级回退。
- ui/settings/TagManagementDialogs.kt：重命名与批量输入、临时名称 pill、重复校验后提交。

RIDO 适配：Compose Material 主题替换为固定灰阶 token、无 ripple/阴影、共用 BottomSheet/Dialog；管理入口位于设置的标签 Sheet。搜索时不允许对部分结果拖拽，避免覆盖隐藏同级排序。删除采用 RIDO 归档语义而非 MyLibrary 的物理删除，历史引用仍可显示/移除。分类与标签完全分开，标签不读取分类颜色。

## 数据及迁移

Room version 1 → 2，只新增 Tag.parentId（nullable、自引用 SET_NULL 和索引）。显式注册 TagHierarchyMigration；未改变 Task/Event/Session/Category/KeywordRule Entity 或 cross-ref 定义。

旧标签保留原 ID、名称、顺序、归档、时间戳及所有关联，移至新一级“原有标签”下；空库不创建默认根。同级重名（trim/ignoreCase）被拒绝，只能创建两级。用户删除根时归档其子项；不会重写历史记录。

工作期间外部进程生成了 app/schemas/.../2.json。本任务未运行 Gradle，未写该文件；只读比较确认相对 v1 仅 tags 发生变化。v1 SHA-256：C042C003C76D115C80B1A4805A086CB3F55864176A2A49D3855FD2D8047D96A9。

## 验证边界

已执行：

- 纯内存 SQLite 使用源码中的迁移 SQL：空库与旧数据场景通过，四类 cross-ref 保留，foreign_key_check 无异常。没有读取用户实际 rido.db。
- v1/v2 schema 文件只读比较：仅 tags 变化。
- Kotlin/XML 显式颜色扫描：无非灰阶字面颜色。
- Kotlin 括号/分隔符静态扫描无候选错误（不是编译器验证）。
- 行数审计：新增生产文件均小于 300 行；唯一超过 300 行的是既有 RoomRidoRepository.kt（310 行），本轮未修改。

新增未执行测试：TagSearchPolicyTest、TagHierarchyMigrationTest、TagRepositoryTest、EditorMetadataTest（320dp、1.3 倍字体、时间文本单行）。

未执行 Gradle/编译/单元测试任务/lint/Git/ADB/模拟器/真机；未 commit/push。不声称通过编译或设备验收。未新增依赖或权限；app/build.gradle.kts 只为迁移测试配置现有 schema 资产目录。

## 文件清单

### 新增（28）

- [app/src/main/java/io/github/peanutpersimmon/rido/data/local/room/TagHierarchyMigration.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/data/local/room/TagHierarchyMigration.kt)
- [app/src/main/java/io/github/peanutpersimmon/rido/data/preferences/AppearancePreferences.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/data/preferences/AppearancePreferences.kt)
- [app/src/main/java/io/github/peanutpersimmon/rido/data/repository/RoomTagRepository.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/data/repository/RoomTagRepository.kt)
- [app/src/main/java/io/github/peanutpersimmon/rido/data/repository/RoomCategoryRepository.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/data/repository/RoomCategoryRepository.kt)
- [app/src/main/java/io/github/peanutpersimmon/rido/domain/repository/TagRepository.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/domain/repository/TagRepository.kt)
- [app/src/main/java/io/github/peanutpersimmon/rido/domain/repository/CategoryRepository.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/domain/repository/CategoryRepository.kt)
- [app/src/main/java/io/github/peanutpersimmon/rido/domain/usecase/MetadataUseCases.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/domain/usecase/MetadataUseCases.kt)
- [app/src/main/java/io/github/peanutpersimmon/rido/presentation/settings/MetadataViewModel.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/presentation/settings/MetadataViewModel.kt)
- [app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/editor/components/EditorTools.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/editor/components/EditorTools.kt)
- [app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/editor/components/EditorInput.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/editor/components/EditorInput.kt)
- [app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/editor/components/LocationTool.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/editor/components/LocationTool.kt)
- [app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/editor/components/CategoryPicker.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/editor/components/CategoryPicker.kt)
- [app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/settings/MetadataContext.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/settings/MetadataContext.kt)
- [app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/settings/SettingsContent.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/settings/SettingsContent.kt)
- [app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/settings/MetadataNameDialog.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/settings/MetadataNameDialog.kt)
- [app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/settings/CategoryColorDialog.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/settings/CategoryColorDialog.kt)
- [app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/tags/TagSearchPolicy.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/tags/TagSearchPolicy.kt)
- [app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/tags/TagPill.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/tags/TagPill.kt)
- [app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/tags/TagSelectionSheet.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/tags/TagSelectionSheet.kt)
- [app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/tags/TagManagementSheet.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/tags/TagManagementSheet.kt)
- [app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/tags/TagBatchAddDialog.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/tags/TagBatchAddDialog.kt)
- [app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/tags/RootTagReorder.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/tags/RootTagReorder.kt)
- [app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/tags/ChildTagReorder.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/tags/ChildTagReorder.kt)
- [app/src/test/java/io/github/peanutpersimmon/rido/ui/feature/tags/TagSearchPolicyTest.kt](D:/Wenjian/Study/RIDO/app/src/test/java/io/github/peanutpersimmon/rido/ui/feature/tags/TagSearchPolicyTest.kt)
- [app/src/androidTest/java/io/github/peanutpersimmon/rido/data/TagHierarchyMigrationTest.kt](D:/Wenjian/Study/RIDO/app/src/androidTest/java/io/github/peanutpersimmon/rido/data/TagHierarchyMigrationTest.kt)
- [app/src/androidTest/java/io/github/peanutpersimmon/rido/data/TagRepositoryTest.kt](D:/Wenjian/Study/RIDO/app/src/androidTest/java/io/github/peanutpersimmon/rido/data/TagRepositoryTest.kt)
- [app/src/androidTest/java/io/github/peanutpersimmon/rido/ui/EditorMetadataTest.kt](D:/Wenjian/Study/RIDO/app/src/androidTest/java/io/github/peanutpersimmon/rido/ui/EditorMetadataTest.kt)
- [EDITOR_THEME_TAGS_CHANGELOG.md](D:/Wenjian/Study/RIDO/EDITOR_THEME_TAGS_CHANGELOG.md)

### 修改（35）

- [app/src/main/java/io/github/peanutpersimmon/rido/MainActivity.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/MainActivity.kt)
- [app/src/main/java/io/github/peanutpersimmon/rido/data/local/room/RidoDatabase.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/data/local/room/RidoDatabase.kt)
- [app/src/main/java/io/github/peanutpersimmon/rido/data/local/room/dao/TagDao.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/data/local/room/dao/TagDao.kt)
- [app/src/main/java/io/github/peanutpersimmon/rido/data/local/room/dao/CategoryDao.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/data/local/room/dao/CategoryDao.kt)
- [app/src/main/java/io/github/peanutpersimmon/rido/data/local/room/entity/TagEntity.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/data/local/room/entity/TagEntity.kt)
- [app/src/main/java/io/github/peanutpersimmon/rido/data/mapper/RoomMappers.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/data/mapper/RoomMappers.kt)
- [app/src/main/java/io/github/peanutpersimmon/rido/domain/model/DomainModels.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/domain/model/DomainModels.kt)
- [app/src/main/java/io/github/peanutpersimmon/rido/presentation/AppViewModel.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/presentation/AppViewModel.kt)
- [app/src/main/java/io/github/peanutpersimmon/rido/presentation/editor/EditorModels.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/presentation/editor/EditorModels.kt)
- [app/src/main/java/io/github/peanutpersimmon/rido/presentation/today/TodayUiModels.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/presentation/today/TodayUiModels.kt)
- [app/src/main/java/io/github/peanutpersimmon/rido/presentation/today/TodayUiMapper.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/presentation/today/TodayUiMapper.kt)
- [app/src/main/java/io/github/peanutpersimmon/rido/ui/RidoApp.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/RidoApp.kt)
- [app/src/main/java/io/github/peanutpersimmon/rido/ui/theme/Theme.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/theme/Theme.kt)
- [app/src/main/java/io/github/peanutpersimmon/rido/ui/theme/Color.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/theme/Color.kt)
- [app/src/main/java/io/github/peanutpersimmon/rido/ui/theme/Dimens.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/theme/Dimens.kt)
- [app/src/main/java/io/github/peanutpersimmon/rido/ui/theme/Type.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/theme/Type.kt)
- [app/src/main/java/io/github/peanutpersimmon/rido/ui/components/AppBottomSheet.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/components/AppBottomSheet.kt)
- [app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/editor/components/EditorLayout.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/editor/components/EditorLayout.kt)
- [app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/editor/components/EditorFields.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/editor/components/EditorFields.kt)
- [app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/editor/components/ClassificationPickers.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/editor/components/ClassificationPickers.kt)
- [app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/editor/task/TaskEditorSheet.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/editor/task/TaskEditorSheet.kt)
- [app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/editor/event/EventEditorSheet.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/editor/event/EventEditorSheet.kt)
- [app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/editor/session/SessionEditorSheet.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/editor/session/SessionEditorSheet.kt)
- [app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/editor/task/TaskEditorPreview.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/editor/task/TaskEditorPreview.kt)
- [app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/editor/event/EventEditorPreview.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/editor/event/EventEditorPreview.kt)
- [app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/editor/session/SessionEditorPreview.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/editor/session/SessionEditorPreview.kt)
- [app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/today/TimelineAxis.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/today/TimelineAxis.kt)
- [app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/today/TimelineEntryContent.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/today/TimelineEntryContent.kt)
- [app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/today/TodayTimeline.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/today/TodayTimeline.kt)
- [app/build.gradle.kts](D:/Wenjian/Study/RIDO/app/build.gradle.kts)
- [app/src/main/res/values/styles.xml](D:/Wenjian/Study/RIDO/app/src/main/res/values/styles.xml)
- [app/src/main/res/values-night/styles.xml](D:/Wenjian/Study/RIDO/app/src/main/res/values-night/styles.xml)
- [UI_RULES.md](D:/Wenjian/Study/RIDO/UI_RULES.md)
- [DATA_MODEL.md](D:/Wenjian/Study/RIDO/DATA_MODEL.md)
- [DOMAIN_RULES.md](D:/Wenjian/Study/RIDO/DOMAIN_RULES.md)

### 删除

无。本轮未删除用户文件。

### 其他观察到的外部生成文件

- app/schemas/io.github.peanutpersimmon.rido.data.local.room.RidoDatabase/2.json：只读保留，不列为本任务生成。

