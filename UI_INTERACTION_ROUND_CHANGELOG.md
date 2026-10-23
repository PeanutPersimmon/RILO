# RIDO 本轮 UI 修改记录

范围：地点、预计用时、Task 固定时间布局、MyLibrary 标签 UI 迁移、四主页面程序化切换、模式选择和 Today 时间轴范围。

## 实现结果

1. 地点 Sheet：顶部“地点”及确认/关闭图标，一个预填的单行无框输入，输入旁 X 只清空草稿；关闭不提交，确认才回填。取消了重复字段名和底部按钮。
2. 最近地点：从现有 Event/Session.location 合并读取，去空白、去重，以记录 updatedAtMillis 倒序取 5 项，点击填入草稿。它是基于记录最近修改时间的近似“最近使用”，没有新增地点历史表或配置。
3. 预计快捷值：15m、30m、45m、1h、1h30m、2h、3h，以及自定义；快捷值直接提交。
4. 自定义预计复用原 ui/components/picker/WheelPicker.kt，没有复制滚轮或更改其吸附/state 逻辑。小时 0–99（已有更大值保留），分钟 0–55、步进 5。已有非 5 分钟倍数的分钟值额外保留，避免静默取整。0 小时 0 分钟遵循既有业务语义，保存为未设置。
5. Task：TaskTimeLayout 固定第一行开始/截止，第二行预计。第一行空间不足时允许横向滚动，而不是随机换行或截断。时间 Text 使用 maxLines=1、softWrap=false；清除按钮仍有独立触控区域。
6. 开始=计划开始、截止=最晚完成、预计=独立投入估计；未添加“截止减开始”的自动赋值或限制保存。
7. Record/Event 有两个真实时间点时显示 end-start；没有结束则不创建一个虚假结束。Active Session 原有 elapsed 文案可保留，但 now 不进入竖轴终点。未为 Event/Record 新增预计输入；既有 Event 结束数据仍保留。

## MyLibrary 实际来源与直接迁移

原项目根：D:/Wenjian/Study/MyLibrary/app/src/main/java/com/example/mylibrary/

| 原文件 | 直接迁移内容 | RIDO 目标 |
| --- | --- | --- |
| ui/settings/TagManagementScreen.kt | 主结构、RootTagCapsuleBar/RootTagCapsule、CurrentRootHeader、ReorderableChildList/ChildTagRow、添加入口、空态、长按一级排序和二级手柄排序 | ui/feature/tags/library/TagManagementScreen.kt、RootTagCapsules.kt、ChildTagList.kt、TagManagementMessages.kt |
| ui/settings/TagManagementDialogs.kt | TagNameInputDialog、TagBatchAddDialog、TagActionDialog、CompactManagementInput、PendingTagCapsule | ui/feature/tags/library/TagManagementDialogs.kt、TagDialogControls.kt |
| ui/settings/TagManagementUiState.kt | 一级/二级筛选和固定排序 | ui/feature/tags/library/TagManagementUiState.kt |
| ui/components/TagSelectionSheet.kt | 搜索在上、横向一级区、当前范围胶囊区、临时多选、完成提交/关闭取消、新建后选择 | ui/feature/tags/library/TagSelectionSheet.kt |
| ui/components/AppCapsule.kt | 选中和未选中胶囊 | ui/feature/tags/library/AppCapsule.kt |
| ui/components/LibrarySearchField.kt | 紧凑搜索输入 | ui/feature/tags/library/LibrarySearchField.kt |
| ui/components/TagSearchPolicy.kt | 只搜索当前一级及其二级；精确匹配一级时只显示该一级；同级查重 | 沿用上一轮已经直接迁移的 ui/feature/tags/TagSearchPolicy.kt，本轮未改 |
| ui/item/ItemForm.kt | filter selectedTagIds 后逐项 AppCapsule 展示，并点击重开选择器 | ClassificationPickers.kt 的已选标签调用原 AppCapsule；空值仍保留 RIDO 裸标签图标 |

真实入口还核对了 ui/navigation/AppNavHost.kt 的标签设置路由，以及 ItemForm.kt 的 TagSelectionSheet 调用，未凭名称猜测。

重要现状：MyLibrary 当前标签设置页没有搜索框；搜索仅在 TagSelectionSheet。RIDO 本轮取消了上一轮额外加入的设置页搜索，按真实来源布局迁移，没有宣称迁移不存在的功能。

适配边界：
- LibraryTag 通过 typealias 接 RIDO Tag，enabled 映射 !isArchived；Repository/ViewModel 回调接 MetadataViewModel。
- 设置页仍被现有 AppBottomSheet 承载，不新增二级 route；返回按钮适配成 Sheet 关闭。
- 原有 RIDO 归档/保留历史关联的删除语义不变，删除文案不照搬 MyLibrary 的物理删除/作品措辞。
- 分类色和标签不混用。LibraryTagAdapters 只把原组件所需外观映射到 RIDO 现有灰阶 palette/typography，不新增主题系统。
- 原布局和拖拽算法保留；字体、外侧 padding、选中黑白反转、零阴影/无 ripple 做 RIDO 适配；移除了拖拽缩放。
- 已选归档标签仍可看见并取消；不可重新新增选择归档标签。
- 旧 RootTagReorder / ChildTagReorder / TagBatchAddDialog / TagPill 简化实现已删除，避免两套并存。

## 主页面与模式

8. 四页固定索引 Today=0、Plan=1、Statistics=2、Settings=3，使用一个 HorizontalPager。
9. userScrollEnabled=false，仅底部导航更新目标，再由 LaunchedEffect(destination) 调用 animateScrollToPage。
10. 初始页直接 initialPage 定位；只有当前位置不同或仍有偏移才动画，无启动切页动画。
11. 180ms tween；较大索引从右进入、较小索引从左进入，由 Pager 物理位置决定方向。快速切换取消上一次 effect，转向最新目标。
12. 没有叠加 AnimatedContent/slideIn/slideOut。底部选中状态直接跟随点击目标。
13. 四页保持组合，MainActivity 既有 ViewModel 不重新创建；Today 的滚动和缩放状态在四页切换及 Sheet 覆盖期间保留。
14. 设置为一行“模式 + LightMode/DarkMode”。未选中裸灰图标，选中 32dp 灰阶反转圆底，48dp 点击区，无 Switch/彩色强调。

## Timeline

15. GetTimelineDisplaySourcesUseCase 只读取现有模型；TimelineEndpointMapper 为 UI 提供真实绝对时间点，不改 ActualTimeResolver、统计或保存规则。
16. 参与范围：Task 计划开始和截止（日期与时间都存在才算）、Event 实际起止或计划起止、Session 保存的开始和结束。Task 预计、日期型字段、清醒日边界和当前时刻不参与。
17. 单独补齐原 interval projector 未包含的开始型/截止型 Task 显示；Task 的开始到截止不作为“实际持续时间”展示。
18. axisStart=min(axisTimes)，axisEnd=max(axisTimes)。同一时间只画一个共享圆点；时间段开始和结束进入同一全局轴，空档不拆断。
19. TimelineCardLayout 使用全局单调时间→Y 映射；重叠记录不再各自定义互相冲突的时间坐标。
20. 竖线仅从 axisStart 圆心画到 axisEnd 圆心，删除原满清醒日高度竖线及 Awake 起止占位圆点。无绝对时间点不画竖线；单点只有圆点、竖线长度 0。
21. 当前时间横线只使用映射查询自己的位置，不加入 axisTimes、不改变竖线范围。清醒日仍作为总览画布参考范围，并不作为竖线范围。
22. 真实跨日端点不替换为默认醒来/入睡时间；日期型 Event 仍可显示内容，但不生成伪造时间圆点。保持 Today 上覆盖编辑 BottomSheet 的入口不变。

## 验证与限制

- 未运行 Gradle、Git、ADB、模拟器或真机；没有 commit/push。
- 未进行编译、单元测试执行或截图验收。不能将静态检查等同于构建/真机通过。
- 对本轮涉及的 35 个生产 Kotlin 文件做括号配对检查，未发现配对错误；这些文件均未超过 300 行。
- 新增 TimelineCardLayoutTest、TimelineEndpointMapperTest，补充 EditorMetadataTest 固定时间行用例；测试源码未执行。
- Room version 仍为 2；Entity、Tag 数据结构、Migration、schema、依赖和权限未改。
- EventDao 只增加最近地点只读查询，不改变表结构。
- v1 schema SHA-256：C042C003C76D115C80B1A4805A086CB3F55864176A2A49D3855FD2D8047D96A9。
- v2 schema SHA-256：53D677F752A09F6D0BF48E5C782FCB40AC2AB5A1BBAC59D493247DDD1200FB8F。
- 保留工作区已有的首页日期格式、睡眠入口等其他改动；不把这些内容计为本轮成果。

## 文件清单

### 新增（17 个生产文件、2 个测试文件、本报告）

- [ui/feature/editor/components/TaskTimeLayout.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/editor/components/TaskTimeLayout.kt)
- [ui/feature/editor/components/EditorTimeTool.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/editor/components/EditorTimeTool.kt)
- [domain/usecase/GetRecentLocationsUseCase.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/domain/usecase/GetRecentLocationsUseCase.kt)
- [data/repository/RoomLocationHistoryRepository.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/data/repository/RoomLocationHistoryRepository.kt)
- [domain/usecase/GetTimelineDisplaySourcesUseCase.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/domain/usecase/GetTimelineDisplaySourcesUseCase.kt)
- [presentation/today/TimelineEndpointMapper.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/presentation/today/TimelineEndpointMapper.kt)
- [ui/feature/tags/library/TagManagementScreen.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/tags/library/TagManagementScreen.kt)
- [ui/feature/tags/library/RootTagCapsules.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/tags/library/RootTagCapsules.kt)
- [ui/feature/tags/library/ChildTagList.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/tags/library/ChildTagList.kt)
- [ui/feature/tags/library/TagManagementMessages.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/tags/library/TagManagementMessages.kt)
- [ui/feature/tags/library/TagManagementDialogs.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/tags/library/TagManagementDialogs.kt)
- [ui/feature/tags/library/TagDialogControls.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/tags/library/TagDialogControls.kt)
- [ui/feature/tags/library/TagManagementUiState.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/tags/library/TagManagementUiState.kt)
- [ui/feature/tags/library/TagSelectionSheet.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/tags/library/TagSelectionSheet.kt)
- [ui/feature/tags/library/AppCapsule.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/tags/library/AppCapsule.kt)
- [ui/feature/tags/library/LibrarySearchField.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/tags/library/LibrarySearchField.kt)
- [ui/feature/tags/library/LibraryTagAdapters.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/tags/library/LibraryTagAdapters.kt)
- [app/src/test/java/io/github/peanutpersimmon/rido/ui/feature/today/TimelineCardLayoutTest.kt](D:/Wenjian/Study/RIDO/app/src/test/java/io/github/peanutpersimmon/rido/ui/feature/today/TimelineCardLayoutTest.kt)
- [app/src/test/java/io/github/peanutpersimmon/rido/presentation/today/TimelineEndpointMapperTest.kt](D:/Wenjian/Study/RIDO/app/src/test/java/io/github/peanutpersimmon/rido/presentation/today/TimelineEndpointMapperTest.kt)
- [UI_INTERACTION_ROUND_CHANGELOG.md](D:/Wenjian/Study/RIDO/UI_INTERACTION_ROUND_CHANGELOG.md)

### 修改（18 个生产文件、1 个测试文件）

- [MainActivity.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/MainActivity.kt)
- [data/local/room/dao/EventDao.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/data/local/room/dao/EventDao.kt)
- [presentation/settings/MetadataViewModel.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/presentation/settings/MetadataViewModel.kt)
- [presentation/today/TodayViewModel.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/presentation/today/TodayViewModel.kt)
- [presentation/today/TodayUiMapper.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/presentation/today/TodayUiMapper.kt)
- [presentation/today/TodayUiModels.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/presentation/today/TodayUiModels.kt)
- [ui/RidoApp.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/RidoApp.kt)
- [ui/feature/settings/SettingsContent.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/settings/SettingsContent.kt)
- [ui/feature/editor/components/LocationTool.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/editor/components/LocationTool.kt)
- [ui/feature/editor/components/EstimatedDurationField.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/editor/components/EstimatedDurationField.kt)
- [ui/feature/editor/components/EditorFields.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/editor/components/EditorFields.kt)
- [ui/feature/editor/components/ClassificationPickers.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/editor/components/ClassificationPickers.kt)
- [ui/feature/editor/task/TaskEditorSheet.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/editor/task/TaskEditorSheet.kt)
- [ui/feature/tags/TagManagementSheet.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/tags/TagManagementSheet.kt)
- [ui/feature/tags/TagSelectionSheet.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/tags/TagSelectionSheet.kt)
- [ui/feature/today/TimelineCardLayout.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/today/TimelineCardLayout.kt)
- [ui/feature/today/TimelineAxis.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/today/TimelineAxis.kt)
- [ui/feature/today/TodayTimeline.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/today/TodayTimeline.kt)
- [app/src/androidTest/java/io/github/peanutpersimmon/rido/ui/EditorMetadataTest.kt](D:/Wenjian/Study/RIDO/app/src/androidTest/java/io/github/peanutpersimmon/rido/ui/EditorMetadataTest.kt)

### 删除（4 个被直接迁移版本替代的旧组件）

- D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/tags/RootTagReorder.kt
- D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/tags/ChildTagReorder.kt
- D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/tags/TagBatchAddDialog.kt
- D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/tags/TagPill.kt

删除的仅是未被调用的旧 UI 组件，不涉及用户数据；未操作 Git，因此本轮未创建 Git 恢复点。

