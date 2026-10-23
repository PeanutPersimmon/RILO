# RIDO 语音输入第六轮：中文语言协商

仅源码/测试代码修改及静态检查；未运行 Gradle、Git、ADB、模拟器、真机或测试，未 commit / push。不能据此宣称当前 GoogleTTSRecognitionService 已识别成功。

## 调查结果及 API 核对

- 修复前 VoiceSpeechRecognizer 和 VoiceInputSheet 的系统识别 Activity Intent 都硬编码 EXTRA_LANGUAGE="zh-CN"。SpeechRecognitionProbe 只探测服务与 Activity，不设置识别语言，本轮未修改。
- 项目 compileSdk=36、minSdk=26。静态读取 D:/Android/Sdk/platforms/android-36/android.jar 类文件，确认真实签名 checkRecognitionSupport(Intent, Executor, RecognitionSupportCallback)。
- 结合本地 D:/Android/Sdk/sources/android-36.1/android/speech/ 源码核对回调 onSupportResult(RecognitionSupport)、onError(int) 及语言字段语义。未执行 Java、Gradle 或 SDK 工具。
- 读取 installedOnDeviceLanguages、onlineLanguages、pendingOnDeviceLanguages、supportedOnDeviceLanguages 四类集合。后两类分别表示待下载、需要下载，不能当作已就绪。

## 最终流程

1. 保留原服务发现、权限与识别器选择；创建识别器后，在 API 33+ 用省略语言的识别 Intent 查询 support。
2. 查询最多等待 2000ms；异常、callback error、超时和 API <33 均继续识别，使用不带 EXTRA_LANGUAGE 的 Intent。查询取消后及已完成后的晚到回调不再启动监听。
3. 从已安装语言与在线语言选择（on-device 不使用在线集合）。排序为 App/系统中文 Locale 完全匹配 → zh-CN → cmn-Hans-CN → 其它 Hans/Mandarin → 其它声明的中文。比较前 trim、下划线转连字符、Locale 标准化；cmn 与 zh 保持独立。
4. 没有明确可用中文时先省略 EXTRA_LANGUAGE，让服务使用默认策略。不会把查询失败判为服务不存在。
5. 只有 ERROR_LANGUAGE_NOT_SUPPORTED（12）进入语言重试。未知/没有可用中文时：SYSTEM_DEFAULT → zh-CN → cmn-Hans-CN；已有明确中文时：选中的 tag → SYSTEM_DEFAULT → 尚未尝试的已声明中文 tag。去重且最多 3 次语言请求，即最多 2 次自动重试。
6. NO_MATCH、SPEECH_TIMEOUT、NETWORK、NETWORK_TIMEOUT、RECOGNIZER_BUSY、CLIENT、AUDIO 等不进入语言重试。原 on-device 服务绑定/服务器/client 失败的普通服务 fallback 保留。
7. ERROR_LANGUAGE_UNAVAILABLE（13）不重试语言，单独提示“中文识别资源暂不可用”。错误 12 耗尽后提示“当前语音服务暂不支持中文识别”，不会提示没有服务。
8. 语言重试仍属于同一次输入，保持 Listening；取消/释放会移除 support timeout 与待重试任务，旧 generation / 已结束回调被忽略。
9. Activity fallback 使用已协商且服务声明就绪的 selectedLanguage；无有效结果则不传语言。VoiceInputSheet 只替换 Activity Intent 构造，未改布局、键盘、权限、文本回填或提交交互。
10. 识别成功仍只写入可编辑文本框，用户按 ↑ 才执行。Fast Path、Parser、完成事实、Undo、Room、Manifest、BottomBar、IME、Snackbar 均未修改。

## 诊断

新增 requestedLanguage（从实际 Intent 读取，未设置显示 NONE）、initialRequestedLanguage、selectedLanguage、独立的 languageStrategy、RecognitionSupport query/detail、上述四类原始语言集合、languageAttempts、language trace。原 selectedStrategy 仍仅表示识别器类型。

查询输入、结果、可用 tag、实际每次请求、错误名、重试和终止步骤都进入可复制诊断；不记录识别文字或音频。

## 测试源码

- SpeechLanguageNegotiationTest：zh-CN / cmn-Hans-CN / 英文+中文 / 仅英文；Locale 优先及 tag 规范化；待下载与在线集合；三次边界与去重；非语言错误及错误13不切换语言。
- SpeechSupportQueryTest：异常、callback error、超时、晚到/重复回调、取消、低版本均覆盖；确认失败后进入默认语言 continuation。
- SpeechRecognitionDiagnosticsTest：复制文本包括真实当前/首次语言、两类 strategy、四类列表及 fallback trace。
- SpeechLanguageIntentTest（Android 测试）：默认 Intent 真实不含 EXTRA_LANGUAGE，cmn tag 不改写，Activity 有效语言复用及无有效语言省略。
- 仅做静态编码、冲突标记、括号及调用关系核对；未编译、未执行上述测试。

## 本轮文件

- [VoiceSpeechRecognizer.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/presentation/voice/VoiceSpeechRecognizer.kt)
- [SpeechRecognitionDiagnostics.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/presentation/voice/SpeechRecognitionDiagnostics.kt)
- [VoiceSpeechPolicy.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/presentation/voice/VoiceSpeechPolicy.kt)
- [SpeechLanguageNegotiation.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/presentation/voice/SpeechLanguageNegotiation.kt)
- [SpeechRecognitionIntents.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/presentation/voice/SpeechRecognitionIntents.kt)
- [VoiceInputSheet.kt](D:/Wenjian/Study/RIDO/app/src/main/java/io/github/peanutpersimmon/rido/ui/feature/voice/VoiceInputSheet.kt)
- [SpeechLanguageNegotiationTest.kt](D:/Wenjian/Study/RIDO/app/src/test/java/io/github/peanutpersimmon/rido/presentation/voice/SpeechLanguageNegotiationTest.kt)
- [SpeechSupportQueryTest.kt](D:/Wenjian/Study/RIDO/app/src/test/java/io/github/peanutpersimmon/rido/presentation/voice/SpeechSupportQueryTest.kt)
- [SpeechRecognitionDiagnosticsTest.kt](D:/Wenjian/Study/RIDO/app/src/test/java/io/github/peanutpersimmon/rido/presentation/voice/SpeechRecognitionDiagnosticsTest.kt)
- [SpeechLanguageIntentTest.kt](D:/Wenjian/Study/RIDO/app/src/androidTest/java/io/github/peanutpersimmon/rido/ui/SpeechLanguageIntentTest.kt)
- 本报告：docs/voice-quick-input-round6.md

下次真机：长按麦克风说中文；如仍失败，复制设置中的语音识别诊断。可据此判断服务是否声明中文、默认/zh-CN/cmn-Hans-CN 哪些已尝试以及最终失败位置。
