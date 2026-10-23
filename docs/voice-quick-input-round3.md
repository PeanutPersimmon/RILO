# RIDO 语音输入第三轮交付说明

仅修改源码和测试代码并静态检查。未编译，未执行测试、模拟器或真机验收；不能把以下实现说明当作真机测试结果。

1. MyLibrary 当前实现：[LibraryBottomBar.kt](D:/Wenjian/Study/MyLibrary/app/src/main/java/com/example/mylibrary/ui/navigation/LibraryBottomBar.kt)，由 MainPagerScreen 的底部 overlay 使用。参数定义：[Dimens.kt](D:/Wenjian/Study/MyLibrary/app/src/main/java/com/example/mylibrary/ui/theme/Dimens.kt)。
2. 复用 11 项 dp 参数：高度 52、圆角 28、阴影 4、底部 padding 36、胶囊水平内 padding 14、item 40、item 间距 8、icon 22、右侧按钮 52、右侧 icon 26、胶囊到按钮 12。背景为不透明 surface、无 outline、tonalElevation 0；选中项为黑白主色圆形，点击无 ripple。
3. 与 MyLibrary 一样：navigationBarsPadding() 再加 bottom 36dp；实际距物理屏幕底边包含系统导航栏 inset，不是忽略系统栏的固定 36dp。
4. 胶囊 52dp 高、28dp 圆角，内部水平 padding 14dp。MyLibrary 没有固定左右外 margin，而是固定宽度居中；RIDO 同样处理。四项胶囊宽 212dp，含右侧按钮整组宽 276dp，左右 margin 随窗口宽度等分。
5. 麦克风圆按钮 52dp、icon 26dp，与胶囊间距 12dp，均来自 MyLibrary 原参数。
6. 已移除导致 Timeline 缩短的布局依赖：输入框不再属于 bottomBar Column；根和嵌套页面 Scaffold 不消费 IME，只处理稳定 system bars／cutout。MainActivity 原有 enableEdgeToEdge 保留，Manifest 显式 adjustResize 用于旧 Android 的 IME inset 分发；没有 adjustPan。实际厂商窗口行为尚未真机验证。
7. BottomBar 不再跟随 IME 上移；键盘显示时不渲染底栏，页面仍保留相同固定底栏空间。
8. 快速输入路径中，只有 VoiceOverlayHost 内承载 VoiceInput 的浮层使用 IME windowInsetsPadding；页面和 Snackbar 不消费 IME。现有完整编辑器 BottomSheet 的自身 imePadding 未改。
9. 短按请求输入焦点并 show keyboard；已打开时只增加请求序号，不重建或清空输入。
10. 长按 clearFocus／hide keyboard，启动识别，不 requestFocus；聆听时输入字段不可获得编辑焦点，避免结束后自动唤起 IME。
11. 识别结束后仍可编辑；主动点击文本框即可修改，没有自动提交。
12. 点击页面外部只 clearFocus／hide keyboard；不关闭输入、不清空文字。
13. 外部按下由祖先 pointerInput 的 Initial pass 观察，不调用 consume，不添加全屏 clickable 遮罩，卡片点击和滚动仍向原组件传播。
14. IME 可见时 Back 只收键盘；IME 已关闭再 Back 关闭快速输入（执行中不取消写入）。未提交文本直接丢弃，不维护草稿。
15. AndroidManifest 原本已有 RECORD_AUDIO 和 RecognitionService queries。
16. 原声明未缺失，因此没有重复添加 queries；静态 XML 核对只有一个匹配声明。
17. 服务选择抽为纯函数：on-device 优先；不可用继续普通系统识别；只有两者均不可用才显示没有服务。
18. 普通服务可用不会因 on-device=false 而禁用。另增加可选探测异常、on-device 创建失败及中文模型／本地服务错误时的普通服务重试。探测异常显示暂不可用，不假称设备没有服务。原先真机提示的具体运行时原因尚无法在不操作真机的约束下确认。
19. pendingListening 保留授权前意图；首次允许后立即继续。如果授权回调时页面尚未 STARTED，在 ON_START 恢复；关闭／切换文字输入会取消该意图。
20. 错误区分：无权限、未检测到语音、超时、网络连接、网络服务、服务忙、客户端、麦克风、无可用服务、服务暂不可用、未匹配、中文语言不可用。UI 使用简短文案，不显示错误码。
21. 失败恢复识别前的原文；错误和取消不会清空已有输入。
22. 新结果追加到识别前文本末尾。每次 partial 替换本次追加片段，不重复追加所有 partial；最终成功提交这一个片段。
23. 识别结果使用 TextFieldValue，selection 设置为文本末尾。用户自己移动光标不会被普通重组重置。
24. 默认一行，最多四行，更多内容在 BasicTextField 内滚动。
25. 空文本时显示“正在聆听…”；已有文本时显示小型“● 正在聆听…”状态和停止按钮，无全屏遮罩或大型动画。
26. 执行期间按钮禁用，成功关闭输入；fallback／错误结束后恢复可提交。
27. UI 使用 busy 禁用箭头／键盘提交；ViewModel 使用 AtomicBoolean 同步互斥；UseCase 接收一次输入持有的 VoiceExecutionAttempt，Mutex 合并并发，成功结果复用而不重复写入，fallback 允许纠正后继续。令牌仅在内存中，不改 schema。
28. Snackbar 正常最小高度 40dp、圆角 14dp、字号 13sp，无阴影。字体缩放时允许高度增加以免裁切。
29. 可见 Surface 按内容收窄、最大 360dp，容器居中并保留 20dp 水平安全边距；不是满屏背景。
30. 文案：已创建「标题」、已修改「标题」、已完成「标题」、已开始「标题」、已结束「标题」、已重新打开「标题」。
31. VoiceUndo 的快照、依赖保护、事务和恢复逻辑未修改；Snackbar 继续调用原 performAction 和 ViewModel.undo。
32. Snackbar 位于固定底栏高度上方 8dp；输入框打开且无 IME 时再让出输入框高度。成功先 clearFocus／hide IME／关输入，再等待 IME 收起显示提示，不覆盖麦克风。
33. 保持 Parser → Resolver → Validator → 直接保存 Fast Path；未增加 Preview、默认编辑器或额外类型／时间确认。Parser 文件和主规则未修改。
34. 新增 16 个用例：服务／文本／Back／入口策略 7，执行令牌并发与可重试 2，UseCase 同次写入一次 1，Compose 输入／语音／外部点击／IME 布局／Snackbar 6。修改原执行文案断言和 Room ViewModel 用例以覆盖同帧重复 submit；原四图标与短／长按导航测试保留。测试均未运行，不宣称通过。
35. 本轮 17 个源码／测试文件及本说明，清单见下表。未使用 Git 确定文件范围。
36. 未运行 Gradle、Git、ADB、模拟器，未操作真机。工具仅用于读取、源码编辑与静态结构检查。
37. 未 commit，未 push。

静态检查：Manifest XML 层级与唯一 queries；11 个 dp 值逐个与 MyLibrary 定义比对；相关 Kotlin 分隔符与调用接线；未触及 Parser、Room schema／Entity、关键词规则、计划／统计页面、Timeline 业务规则或 Undo 数据实现。静态检查不能替代 Kotlin 编译及设备交互验证。

官方 API 核对：IME padding 会影响所在布局及其子布局，因此这里仅将其放在输入浮层，[Android Insets 文档](https://developer.android.com/develop/ui/compose/system/insets-ui)；服务可用性、on-device 与普通服务 API 及 queries 要求核对了 [SpeechRecognizer 文档](https://developer.android.com/reference/android/speech/SpeechRecognizer)。

文件清单：

| 文件 | 本轮 |
| --- | --- |
| [AndroidManifest.xml](D:/Wenjian/Study/RIDO/app/src/main/AndroidManifest.xml) | 修改 |
| [AppNavigationBar.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/components/AppNavigationBar.kt) | 修改 |
| [AppScaffold.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/components/AppScaffold.kt) | 修改 |
| [AppSnackbar.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/components/AppSnackbar.kt) | 修改 |
| [RidoApp.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/RidoApp.kt) | 修改 |
| [VoiceInputSheet.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/voice/VoiceInputSheet.kt) | 修改 |
| [VoiceOverlayHost.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/voice/VoiceOverlayHost.kt) | 新增 |
| [VoiceViewModel.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/presentation/voice/VoiceViewModel.kt) | 修改 |
| [VoiceSpeechRecognizer.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/presentation/voice/VoiceSpeechRecognizer.kt) | 修改 |
| [VoiceSpeechPolicy.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/presentation/voice/VoiceSpeechPolicy.kt) | 新增 |
| [ExecuteVoiceActionUseCase.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/domain/voice/ExecuteVoiceActionUseCase.kt) | 修改 |
| [VoiceExecutionAttempt.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/domain/voice/VoiceExecutionAttempt.kt) | 新增 |
| [VoiceExecutionTest.kt](D:/Wenjian/Study/RIDO/app/src/test/java/io/github/peanutpersimmon/rido/domain/voice/VoiceExecutionTest.kt) | 修改 |
| [VoiceExecutionAttemptTest.kt](D:/Wenjian/Study/RIDO/app/src/test/java/io/github/peanutpersimmon/rido/domain/voice/VoiceExecutionAttemptTest.kt) | 新增 |
| [VoiceSpeechPolicyTest.kt](D:/Wenjian/Study/RIDO/app/src/test/java/io/github/peanutpersimmon/rido/presentation/voice/VoiceSpeechPolicyTest.kt) | 新增 |
| [VoiceQuickExecutionTest.kt](D:/Wenjian/Study/RIDO/app/src/androidTest/java/io/github/peanutpersimmon/rido/data/VoiceQuickExecutionTest.kt) | 修改 |
| [VoiceInteractionTest.kt](D:/Wenjian/Study/RIDO/app/src/androidTest/java/io/github/peanutpersimmon/rido/ui/VoiceInteractionTest.kt) | 新增 |
