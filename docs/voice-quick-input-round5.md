# RIDO 语音输入第五轮：完成事实 Fast Path

实现已落在源码；只做静态检查，未编译、未执行任何测试、未运行 Git / Gradle / ADB / 模拟器，未操作真机，未 commit / push。

## 行为

- 完成事实使用临时 VoiceActionDraft.completedFact 标志，动作复用 CompleteTask；数据库仍使用 Task，不新增 Entity、表或 Migration。
- 高确定性结果句式（有实际标题内容）：完成了、做完了、写完了、交了、交完了、提交了、拿到了、取了、取到了、买到了、寄了、寄出了、吃了、喝了、洗完了、扔了、丢了、收到了、办完了、处理完了、结束了；另支持拿到/取到/买到/收到 + 对象 + 了。单独的“了”不触发。显式类型、开始/停止入口仍优先。
- 拿到快递了 / 快递拿到了 → 拿快递；论文交了 → 交论文；报告提交了 → 提交报告；药吃了 → 吃药；垃圾扔了 → 扔垃圾；衣服洗完了 → 洗衣服。只做固定动词归一化，不推断不可靠标题。
- 无时间、今天、刚才、刚刚：使用解析时 now；今天16:00（已经发生）和昨天20:00：使用明确完成瞬间；昨天无钟点：只补完成时间。未来时间不直接保存完成事实。
- 有持续时间或两个端点不进入完成事实分支；刚才拿快递用了20分钟（包括后接“已经拿到了”）仍只生成 Record，标题拿快递。下雨了、快迟到了、我想了想明天还是去组会不会被视为完成事实。
- 匹配仅查未完成且未取消的 Tasks；保留所有包含式候选，同时允许固定动词与对象之间有修饰词（拿实验材料快递）。对象本身也可匹配既有标题（交论文 → 论文）。不按相似度或精确匹配优先擅自选中。
- 唯一候选：现有 CompleteTaskUseCase 完成，保留分类标签笔记；零候选：单次 SaveTaskUseCase 直接插入 DONE + completedAtMillis + MANUAL，不先插 TODO；多候选：列表选择或新建完成记录，选择后立即执行。执行事务内再次检查候选/状态。
- 新建时才应用现有关键词分类；完成已有 Task 不重新分类。没有新增零时长/伪一分钟 Record。
- 成功提示复用现有轻量 Snackbar：已完成「标题」+ 撤销。VoiceUndo 本身未修改：已有任务恢复完整快照及祖先效果，新任务撤销删除，保留后续依赖保护和事务。原防重复执行机制继续复用。

## 展示

- Today：原事实侧时间标签 + 小圆点 + 单行标题；不显示完成附文、duration、类型或普通 Card。保留透明触控区域，不改变 timeY、轴线、标签避让、睡眠边界、now、zoom 和碰撞算法。
- Day / Week：现有 Record 列补入 Task.completedAt 完成点，即使没有 Record 也显示；单行“10:36 ● 拿快递”，无背景卡片与时长。
- Todo & Done：沿用工作区最新布局，右侧事实区显示完成点，去掉附文和展开元数据。点击仍使用 Task 编辑入口。
- 工作期间发现 PlanTimeline 有其他并行布局修改；在最新实现上只合入本轮完成点接线，未还原其他布局工作。

## 测试代码与静态检查

- VoiceCompletedFactTest：句式/归一化、now、明确历史时间、昨天仅补时间、模糊钟点、时长优先、负例、显式类型/开始停止优先。
- VoiceCommandParserTest：更新交完了归一化的既有预期。
- VoiceExecutionTest：唯一/无/多候选、单次 DONE 写入、防重复、无 Record、副作用恢复、新建 Undo、分类应用和未来时间拒绝。
- TimelineProjectorTest：后来录入的历史完成事实仍产生含 taskId/title/time 的无 duration 完成点。
- VoiceQuickExecutionTest（Room）：历史完成保存与删除撤销、已有子任务及祖先恢复、多候选选择新建后立即执行。
- CompletedFactUiTest：Today / Day / Week / Todo & Done 的完成点显示及点击任务回调。
- 静态检查了调用签名、可空值、候选分支、事务快照、历史查询，以及以下 17 个 Kotlin 文件的 UTF-8 / 冲突标记 / 分隔符。测试源码均未执行，未声明编译通过或真机验收通过。

## 本轮编辑文件

- [VoiceAction.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/domain/voice/VoiceAction.kt)
- [VoiceCompletedFactParser.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/domain/voice/VoiceCompletedFactParser.kt)
- [VoiceCommandParser.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/domain/voice/VoiceCommandParser.kt)
- [VoiceTargetResolver.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/domain/voice/VoiceTargetResolver.kt)
- [VoiceExecutionPlanner.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/domain/voice/VoiceExecutionPlanner.kt)
- [ExecuteVoiceActionUseCase.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/domain/voice/ExecuteVoiceActionUseCase.kt)
- [VoiceViewModel.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/presentation/voice/VoiceViewModel.kt)
- [VoiceInputSheet.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/voice/VoiceInputSheet.kt)
- [TodayTimeline.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/today/TodayTimeline.kt)
- [PlanScreen.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/plan/PlanScreen.kt)
- [PlanTimeline.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/plan/PlanTimeline.kt)
- [VoiceCompletedFactTest.kt](D:/Wenjian/Study/RIDO/app/src/test/java/io/github/peanutpersimmon/rido/domain/voice/VoiceCompletedFactTest.kt)
- [VoiceCommandParserTest.kt](D:/Wenjian/Study/RIDO/app/src/test/java/io/github/peanutpersimmon/rido/domain/voice/VoiceCommandParserTest.kt)
- [VoiceExecutionTest.kt](D:/Wenjian/Study/RIDO/app/src/test/java/io/github/peanutpersimmon/rido/domain/voice/VoiceExecutionTest.kt)
- [TimelineProjectorTest.kt](D:/Wenjian/Study/RIDO/app/src/test/java/io/github/peanutpersimmon/rido/domain/service/TimelineProjectorTest.kt)
- [VoiceQuickExecutionTest.kt](D:/Wenjian/Study/RIDO/app/src/androidTest/java/io/github/peanutpersimmon/rido/data/VoiceQuickExecutionTest.kt)
- [CompletedFactUiTest.kt](D:/Wenjian/Study/RIDO/app/src/androidTest/java/io/github/peanutpersimmon/rido/ui/CompletedFactUiTest.kt)
- 本报告：docs/voice-quick-input-round5.md

未修改 Room schema、Entity、Migration、关键词规则体系、VoiceUndo、BottomBar、IME、Snackbar 视觉或语音诊断。
