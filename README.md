# RILO

关注计划、实际生活记录与时间回顾的 Android 应用。RILO 独立围绕 Event、Record、Sleep 和 Category 组织数据与界面，当前不提供 Task 或 Tag 产品功能。

## 核心功能

| 模块 | 当前能力 |
| --- | --- |
| Today | 在时间轴上查看实际活动、日程、小憩和睡眠边界。 |
| Event / Record | 分别保存计划与真实发生的活动；支持日期、时间点或时间段等现有时间形式。 |
| Plan | 提供 Day、Week、2×4 周计划、Todo & Done 和 Month 五个视图；Month 可在「计划」与「记录」之间切换。 |
| Category | 管理分类，并查看分类统计；Keyword Rule 可按关键词匹配已有分类。 |
| Sleep | 记录夜间睡眠与小憩，查看睡眠统计，以及月度、年度报告。 |
| 桌面小组件 | 使用 Glance 横向或纵向小组件进行入睡、起床打卡。 |
| Voice / Reminder | 使用语音输入及已有操作，为 Event 设置提醒；在线语音服务由用户自行配置。 |
| 数据管理 | 在本机保存数据，并提供 ZIP 备份与恢复。 |

## 设计思路

以简洁的黑、白、灰界面呈现计划和实际记录，让时间回顾有清晰依据。Event 表达计划，Record 与 Sleep 保存实际发生的时间；日常数据由用户在本机管理。

## 技术栈

Kotlin、Jetpack Compose、Room、DataStore、Glance、WorkManager 与 Gradle。应用源码位于 [app/src/main](app/src/main)，当前 Room Schema 见 [Schema JSON](app/schemas/io.github.peanutpersimmon.rilo.data.local.room.RidoDatabase/1.json)。独立的 [benchmark 模块](benchmark)用于 Macrobenchmark 测试；其中的结果不代表所有设备的性能。

## 本地构建

- JDK 21（项目配置了 Gradle Daemon Java 21 工具链）。
- Android SDK Platform 36；项目的 `compileSdk` 和 `targetSdk` 均为 36，最低支持 API 26。
- Gradle 9.3.1 由仓库中的 Wrapper 管理；首次使用需下载发行包，后续可使用本地缓存。
- 通过 `ANDROID_HOME` 或未跟踪的 `local.properties` 配置 Android SDK 路径。不要提交密钥、签名文件或用户数据。

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin
```

macOS / Linux 可将命令中的 `.\gradlew.bat` 换为 `./gradlew`。依赖和 Gradle 发行包已缓存后，可按需添加 `--offline`。

## 项目状态与使用许可

项目仍在开发中。Release 小组件已有针对 R8 反射构造函数的保留规则，签名 Release 安装后的完整点击和数据写入行为仍待真机确认。

本项目源码采用 [PolyForm Noncommercial License 1.0.0](LICENSE) 授权，允许符合协议要求的非商业使用、学习、研究、修改与分发。未经另行授权，不允许商业用途。具体权利与限制以 LICENSE 原文为准。
