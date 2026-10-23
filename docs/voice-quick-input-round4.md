# RIDO 语音输入第四轮交付说明

本轮仅源码修改、测试代码补充与静态检查。未编译或运行测试，未操作真机；现在具备收集运行时证据的入口，尚不能确认当前手机究竟在哪一层失败。

下次真机操作：长按尝试识别后，打开「设置 → 语音识别诊断 → 复制」，可得到同一次尝试的探测结果、组件和执行轨迹。打开诊断不会清掉上次失败；数据仅保留当前进程内的最近一次尝试，重启 App 后不持久保存。

1. 新增字段：SDK、采集时间、麦克风授权、两项独立 availability、RecognitionService 组件列表与数量、识别 Activity 组件列表与数量、probeErrors、selectedStrategy、recognizerCreated、creationError、startListeningAttempted、最后 Android error code/name、Activity 启动尝试／返回码、outcome 和最多 40 条阶段 trace。不记录原文或音频，不写数据库。
2. 可以看到 isRecognitionAvailable 的真实结果；探测异常为 UNKNOWN，并记录异常类型。
3. 可以看到 isOnDeviceRecognitionAvailable；API < 31 显示 N/A，API 31+ 探测失败显示 UNKNOWN，不假装 false。
4. 可以看到 RecognitionService 数量及 package/component；查询失败的例外单独列在 probeErrors，避免把失败查询误当确定无服务。
5. 可以看到 ACTION_RECOGNIZE_SPEECH 的可启动 Activity 数量和组件。Manifest 在原 queries 下追加这一 action，RecognitionService 原声明保留。
6. 记录实际策略 ON_DEVICE、SYSTEM_DEFAULT、RECOGNIZER_ACTIVITY 或 NONE；trace 保留本地到普通策略切换，最后字段表示当前策略。
7. 两种 recognizer 的创建异常均记录策略和异常类型；普通 UI 不展示这些内部信息。
8. startListeningAttempted 在调用之前设为 true；onError 记录原始 code 和 Android 名称。同步异常也进 trace，因此可区分探测失败、创建失败、启动异常和异步错误。
9. 入口为「设置 → 语音识别诊断」，用已有轻量 AppDialog，不新增页面或 destination。
10. 提供「复制」按钮，复制完整诊断，完成后显示「已复制」。
11. 普通输入 UI 仍只有简短错误；package、component、ERROR_CLIENT 等仅在诊断弹窗显示。
12. 已增加 ACTION_RECOGNIZE_SPEECH 最后 fallback，系统返回值追加到原文本末尾；不自动解析或提交。取消／空结果不清空原文。
13. 优先级：on-device SpeechRecognizer → 普通 SpeechRecognizer → 系统识别 Activity → unavailable。Activity 只在 API 探测确认直接服务不可用且有 handler 时启用，不因一般网络或客户端错误跳转系统 Activity。
14. 三者确实均不可用时提示「当前设备没有可用的语音识别服务」，保留文字输入。探测结果未知时提示暂不可用，并保留诊断异常，不断言设备没有服务。
15. 17.40 → 17:40。
16. 17.40-18.20 → 17:40–18:20。
17. 17.40到18.20 → 17:40–18:20。
18. 17.4 → 17:40；点号一位分钟右补零。冒号一位分钟使用标准左补零：17:4 → 17:04。
19. 17.04 → 17:04，不丢失分钟前导零。
20. 8.30 → 08:30；明天8.30组会仍为明天 08:30 的 Event。
21. 冒号、全角冒号、中文点、原时段和范围继续使用原 parser；-、~、～、—、到、至在明确时间边界归一化为同一 range。只改时间提取，不改命令类型判断。
22. 25.30、17.75 等由原 hour 0..23／minute 0..59 检查拒绝，命令不进入直接保存。
23. 未加入裸 1740 解析。
24. 未加入裸 19 20 范围解析。
25. 今晚17.40-18.20在父亲楼选修课 → CreateEvent；title=选修课；date=提交时本地今天；start=17:40；end=18:20；location=父亲楼。
26. VoiceLocationParser 未修改。周五17.40交材料仍按原规则为 Task deadline，不因点号改变为 Event。
27. 新增 20 个用例：VoiceDotTimeTest 8（每个覆盖多条格式），SpeechRecognitionDiagnosticsTest 7，SpeechDiagnosticsUiTest 2，现有 VoiceInteractionTest 新增 Activity 回填／取消／空结果 3。后者使用模拟 ActivityResultRegistry，不依赖真实识别 Activity。测试代码已写，均未运行。
28. 修改／新增 14 个源码／测试文件，见下表；另新增本说明。未使用 Git 获取文件范围。
29. 未运行 Gradle、Git、ADB、模拟器，未操作真机。
30. 未 commit，未 push。

静态检查范围：Manifest XML 层级和两个查询 action 的唯一性、策略分支、错误记录、Activity 启动／回填／生命周期、回填段没有 submit 调用、归一化入口与原验证路径。未改 BottomBar、IME 浮层、Snackbar、Fast Path 保存逻辑、实体、schema、关键词体系、地点规则或 Timeline 业务。

Activity fallback 使用 Android 标准 action 和结果契约，核对依据为 [RecognizerIntent 官方文档](https://developer.android.com/reference/android/speech/RecognizerIntent)；可见组件查询使用 [PackageManager 官方 API](https://developer.android.com/reference/android/content/pm/PackageManager)。

文件清单：

| 文件 | 操作 |
| --- | --- |
| [AndroidManifest.xml](D:/Wenjian/Study/RIDO/app/src/main/AndroidManifest.xml) | 修改 |
| [VoiceDateTimeParser.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/domain/voice/VoiceDateTimeParser.kt) | 修改 |
| [VoiceTimeNormalizer.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/domain/voice/VoiceTimeNormalizer.kt) | 新增 |
| [SpeechRecognitionDiagnostics.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/presentation/voice/SpeechRecognitionDiagnostics.kt) | 新增 |
| [SpeechRecognitionProbe.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/presentation/voice/SpeechRecognitionProbe.kt) | 新增 |
| [VoiceSpeechPolicy.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/presentation/voice/VoiceSpeechPolicy.kt) | 修改 |
| [VoiceSpeechRecognizer.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/presentation/voice/VoiceSpeechRecognizer.kt) | 修改 |
| [VoiceInputSheet.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/voice/VoiceInputSheet.kt) | 修改 |
| [SettingsContent.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/settings/SettingsContent.kt) | 修改 |
| [SpeechDiagnosticsDialog.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/settings/SpeechDiagnosticsDialog.kt) | 新增 |
| [VoiceDotTimeTest.kt](D:/Wenjian/Study/RIDO/app/src/test/java/io/github/peanutpersimmon/rido/domain/voice/VoiceDotTimeTest.kt) | 新增 |
| [SpeechRecognitionDiagnosticsTest.kt](D:/Wenjian/Study/RIDO/app/src/test/java/io/github/peanutpersimmon/rido/presentation/voice/SpeechRecognitionDiagnosticsTest.kt) | 新增 |
| [VoiceInteractionTest.kt](D:/Wenjian/Study/RIDO/app/src/androidTest/java/io/github/peanutpersimmon/rido/ui/VoiceInteractionTest.kt) | 修改 |
| [SpeechDiagnosticsUiTest.kt](D:/Wenjian/Study/RIDO/app/src/androidTest/java/io/github/peanutpersimmon/rido/ui/SpeechDiagnosticsUiTest.kt) | 新增 |
