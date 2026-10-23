# RIDO 第二轮快速录入交付说明

代码已修改，仅完成静态源码检查；没有编译、运行测试或进行设备验收。下述行为是当前实现，不代表已在真机验证。

1. Today Header 麦克风已删除；底部导航最右侧为独立圆形麦克风按钮。
2. 四个导航项只渲染图标，保留无障碍描述，无可见文字 label。参考了本机 MyLibrary 的 LibraryBottomBar：悬浮圆角导航胶囊 + 右侧独立圆形操作。
3. RootDestination 仍只有 Today、Plan、Statistics、Settings；麦克风不改变页面。
4. 短按：打开空文本框，获取输入焦点，显示键盘；不自动录音。
5. 长按：打开同一个文本框并启动 SpeechRecognizer；首次需系统麦克风授权。部分／最终识别结果写入框内，完成后可编辑。
6. 短按、长按共用 VoiceInputSheet 和同一个 BasicTextField。
7. 输入框在底部导航上方，遵循页面左右 margin，与导航相隔 8dp；圆角轻量 Surface，文本最多显示四行，随键盘避让。
8. 删除“解析”按钮及单独解析步骤，右侧向上箭头为“执行输入”。空白文本禁用。
9. 唯一人为确认是提交原文：Parser → Resolver → 必填／歧义检查 → 现有 Validator／业务保存 → 关闭输入 → 现有数据观察刷新页面 → Snackbar 撤销。识别完成不会自动提交。
10. 字段满足实体要求、目标唯一、时间明确且业务校验通过时直接创建／修改／开始／结束／完成；不要求编辑器确认。
11. 类型未知、必填缺失、时间模糊、目标多个或业务校验失败才 fallback。优先显示必要选择／单字段 Picker；完整编辑器仅为错误纠正入口。
12. Task 仅标题可以直接创建，例如“任务 买牙膏”。日期、分类、标签、父任务均可选。
13. Event 仅标题和日期可以直接创建，例如“明天有组会”；不强求开始或结束时间。
14. “今晚19-20在父亲楼有一节选修课”：CreateEvent，标题“选修课”，日期为提交时本地今天，开始 19:00，结束 20:00，location“父亲楼”。
15. 原解析器只识别带“点／时”的钟点，漏掉裸数字范围及冒号时间，导致下游 seed 缺少 start/end。VoiceDateTimeParser 新增数字范围、半角／全角冒号、分隔符归一化、时段继承和歧义标记；CommandParser 传递正确字段。已检查 VoiceEditorSeed，并用跨层测试约束时间不丢失。
16. 19-20 按明确 24 小时制处理，不要求 AM／PM；没有日期的命令仍按实体需要补日期。
17. 晚上7-8、今晚7-8 解析为 19:00–20:00；下午3点到4点半为 15:00–16:30。
18. 裸 3-4 保持时间歧义，不直接写入；通过时间 Picker 确认。
19. 在父亲楼正确提取到 location；支持限定句型的在 X、地点 X、地点是 X 和保守的去 X。不是通用中文 NLP。
20. 有一节／有个等明确前缀被移除，核心标题为“选修课”。
21. 三个编辑器及快速输入 fallback 的时钟调用 LocalTime 重载，打开 AppTimePicker。
22. 日历调用 LocalDate 重载，打开 AppDatePicker。原先混合入口在初始时间为空时只显示日历，是时钟入口错开的原因。
23. 核心完整命令的 missing 集合为空，正常直接路径不进入 Picker。
24. 完整且合法的创建命令不打开 Task／Event／Record Editor；编辑器不再是默认保存流程。
25. 唯一修改目标自动应用字段补丁，保留未提及字段，直接保存并提供撤销。
26. 多目标时列出候选，选择后立即继续；仅在仍有其他歧义／校验问题时继续补充。StartRecord 的关联任务可选，多个候选也提供“不关联并开始”。
27. 所有成功的直接执行发出包含前后快照的 Undo receipt，复用现有 Snackbar；创建撤销删除新项，修改撤销恢复原字段。写入与撤销使用 Room 事务；后续编辑或新增依赖导致快照失效时拒绝覆盖。
28. Event → Record 仍为一次性复制：复制现有日程字段，创建记录不写 sourceEventId，不修改源 Event，不新增关联。满足实际记录校验即可直接创建；缺时间或未来时间则纠正。
29. Category／Tag 复用 InitialClassificationUseCase 及已有关键词规则；无匹配保持空，不阻止保存，不增加学习规则。
30. 新增四个测试文件、21 个用例：VoiceQuickInputTest（9）、VoiceExecutionTest（7）、VoiceQuickExecutionTest（3，Room／ViewModel）、VoiceQuickInputUiTest（2，Compose）。覆盖指定时间／地点、可选字段、修改保留元数据、Record 时长、直接保存／Undo、后续编辑保护、活动记录冲突、独立复制、识别不自动提交及导航／Picker 接线。均未运行。
31. 已加入指定核心原文的 Parser → Seed／Planner 测试、Parser → 执行／Undo 测试、Room 保存／撤销和 VoiceViewModel 确认后执行用例；尚未做真实识别服务或 Today 卡片的设备端验收。
32. 本轮修改／新增 23 个代码和测试文件，详见下表；另新增本交付说明。未使用 Git 推断文件范围。
33. 未运行 Gradle、Git、ADB、模拟器，未操作真机。
34. 未 commit，未 push。

文件清单：

| 操作 | 文件 |
| --- | --- |
| 修改 | [VoiceAction.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/domain/voice/VoiceAction.kt) |
| 修改 | [VoiceDateTimeParser.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/domain/voice/VoiceDateTimeParser.kt) |
| 修改 | [VoiceCommandParser.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/domain/voice/VoiceCommandParser.kt) |
| 新增 | [VoiceLocationParser.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/domain/voice/VoiceLocationParser.kt) |
| 新增 | [VoiceExecutionPlanner.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/domain/voice/VoiceExecutionPlanner.kt) |
| 新增 | [ExecuteVoiceActionUseCase.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/domain/voice/ExecuteVoiceActionUseCase.kt) |
| 新增 | [VoiceUndo.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/domain/voice/VoiceUndo.kt) |
| 修改 | [RidoCoreRepository.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/domain/repository/RidoCoreRepository.kt) |
| 修改 | [RoomRidoRepository.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/data/repository/RoomRidoRepository.kt) |
| 修改 | [VoiceViewModel.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/presentation/voice/VoiceViewModel.kt) |
| 修改 | [RidoApp.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/RidoApp.kt) |
| 修改 | [AppNavigationBar.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/components/AppNavigationBar.kt) |
| 修改 | [TodayHeader.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/today/TodayHeader.kt) |
| 修改 | [TodayScreen.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/today/TodayScreen.kt) |
| 修改 | [VoiceInputSheet.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/voice/VoiceInputSheet.kt) |
| 修改 | [EditorFields.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/editor/components/EditorFields.kt) |
| 修改 | [TaskEditorSheet.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/editor/task/TaskEditorSheet.kt) |
| 修改 | [EventEditorSheet.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/editor/event/EventEditorSheet.kt) |
| 修改 | [SessionEditorSheet.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/editor/session/SessionEditorSheet.kt) |
| 新增 | [VoiceQuickInputTest.kt](D:/Wenjian/Study/RIDO/app/src/test/java/io/github/peanutpersimmon/rido/domain/voice/VoiceQuickInputTest.kt) |
| 新增 | [VoiceExecutionTest.kt](D:/Wenjian/Study/RIDO/app/src/test/java/io/github/peanutpersimmon/rido/domain/voice/VoiceExecutionTest.kt) |
| 新增 | [VoiceQuickExecutionTest.kt](D:/Wenjian/Study/RIDO/app/src/androidTest/java/io/github/peanutpersimmon/rido/data/VoiceQuickExecutionTest.kt) |
| 新增 | [VoiceQuickInputUiTest.kt](D:/Wenjian/Study/RIDO/app/src/androidTest/java/io/github/peanutpersimmon/rido/ui/VoiceQuickInputUiTest.kt) |

未修改 Room schema、实体／迁移、关键词规则体系、统计页或计划页；没有 AI 接入、聊天界面或第五个导航页面。
