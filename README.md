# RILO

RILO is an independent Android app focused on Events, activity Records, Sleep, and Categories. It provides Today and Plan views, Category and Sleep statistics, keyword-based Category selection, reminders, a sleep home-screen widget, voice actions, and local backup and restore. Task and Tag are absent from its current product model.

The project uses Kotlin, Jetpack Compose, Room, DataStore, Glance, WorkManager, and Gradle. It is under active development. The Release sleep widget has recent R8 fixes whose full click and data-write behavior still needs verification on a signed Release installation.

## Local build

Install JDK 21 and Android SDK 36, then provide the SDK path through your environment or an untracked `local.properties`. Run:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin
```

The `benchmark` module provides Macrobenchmark scenarios. Do not commit `local.properties`, signing material, credentials, user data, or build outputs.
