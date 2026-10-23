package io.github.peanutpersimmon.rilo.backup

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.peanutpersimmon.rilo.data.local.room.RidoDatabase
import io.github.peanutpersimmon.rilo.data.mapper.toDomain
import io.github.peanutpersimmon.rilo.data.preferences.AppearancePreferences
import io.github.peanutpersimmon.rilo.data.preferences.PlanViewOrderPreferences
import io.github.peanutpersimmon.rilo.data.repository.RoomRidoRepository
import io.github.peanutpersimmon.rilo.domain.model.DomainResult
import io.github.peanutpersimmon.rilo.domain.service.StatisticsGranularity
import io.github.peanutpersimmon.rilo.domain.service.StatisticsPeriod
import io.github.peanutpersimmon.rilo.domain.usecase.AwakeDayRequest
import io.github.peanutpersimmon.rilo.domain.usecase.GetStatisticsUseCase
import io.github.peanutpersimmon.rilo.domain.usecase.GetTodayStableSnapshotUseCase
import io.github.peanutpersimmon.rilo.presentation.plan.MonthContent
import io.github.peanutpersimmon.rilo.presentation.plan.PlanContext
import io.github.peanutpersimmon.rilo.presentation.plan.PlanStableSources
import io.github.peanutpersimmon.rilo.presentation.plan.PlanViewMode
import io.github.peanutpersimmon.rilo.presentation.plan.RoomPlanSourceQueries
import io.github.peanutpersimmon.rilo.presentation.plan.loadPlanStableSources
import io.github.peanutpersimmon.rilo.presentation.plan.planQueryKey
import io.github.peanutpersimmon.rilo.reminder.ReminderPlan
import io.github.peanutpersimmon.rilo.widget.sleep.SleepWidgetGateway
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Runs only against disposable databases and DataStore files under this test's private cache root. */
@RunWith(AndroidJUnit4::class)
class BackupRestoredReadersIntegrationTest {
    private class IsolatedContext(base: Context, private val root: File) : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getFilesDir(): File = File(root, "files").apply { mkdirs() }
        override fun getCacheDir(): File = File(root, "cache").apply { mkdirs() }
        override fun getDatabasePath(name: String): File = File(root, "databases/$name").apply {
            parentFile?.mkdirs()
        }
    }

    @Test fun zipReplacementNewRepositoryAndReadersUseRestoredDataWithoutTouchingProductionDatabase() = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(base.cacheDir, "restore-readers-${UUID.randomUUID()}").apply { mkdirs() }
        val sourceContext = IsolatedContext(base, File(root, "source"))
        val targetContext = IsolatedContext(base, File(root, "target"))
        val zone = ZoneId.of("Asia/Shanghai")
        val date = LocalDate.of(2025, 10, 19)
        val start = date.atTime(10, 0).atZone(zone).toInstant().toEpochMilli()
        val end = start + 60 * 60_000L
        val sleepStart = date.atTime(23, 0).atZone(zone).toInstant().toEpochMilli()
        val source = Room.databaseBuilder(sourceContext, RidoDatabase::class.java, RidoDatabase.DATABASE_NAME).build()
        val original = Room.databaseBuilder(targetContext, RidoDatabase::class.java, RidoDatabase.DATABASE_NAME).build()
        try {
            original.openHelper.writableDatabase.execSQL("INSERT INTO categories(id,name,isArchived,sortOrder,createdAtMillis,updatedAtMillis,colorKey) VALUES(99,'旧数据',0,0,1,1,'neutral')")
            BackupFiles.checkpoint(original)
            original.close()

            val sql = source.openHelper.writableDatabase
            sql.execSQL("INSERT INTO categories(id,name,isArchived,sortOrder,createdAtMillis,updatedAtMillis,colorKey) VALUES(1,'恢复分类',1,0,1,1,'blue')")
            sql.execSQL("INSERT INTO keyword_rules(id,keyword,categoryId,isEnabled,priority,createdAtMillis,updatedAtMillis) VALUES(2,'恢复词',1,1,0,1,1)")
            sql.execSQL("INSERT INTO events(id,title,dateEpochDay,startMinuteOfDay,categoryId,createdAtMillis,updatedAtMillis) VALUES(3,'恢复日程',${date.toEpochDay()},600,1,1,1)")
            val futureDay = LocalDate.of(2099, 10, 19)
            sql.execSQL("INSERT INTO events(id,title,dateEpochDay,startMinuteOfDay,startReminderOffsetMinutes,categoryId,createdAtMillis,updatedAtMillis) VALUES(4,'未来提醒',${futureDay.toEpochDay()},600,30,1,1,1)")
            sql.execSQL("INSERT INTO sessions(id,title,kind,temporalType,startAtMillis,endAtMillis,categoryId,createdAtMillis,updatedAtMillis) VALUES(5,'恢复记录','ACTIVITY','INTERVAL',$start,$end,1,1,1)")
            sql.execSQL("INSERT INTO sessions(id,title,kind,temporalType,startAtMillis,createdAtMillis,updatedAtMillis) VALUES(6,'夜间睡眠','NIGHT_SLEEP','INTERVAL',$sleepStart,1,1)")
            BackupFiles.checkpoint(source)
            source.close()

            val snapshot = File(root, "snapshot.db")
            BackupFiles.copySynced(sourceContext.getDatabasePath(RidoDatabase.DATABASE_NAME), snapshot)
            val settings = BackupSettings(true, PlanViewMode.entries.reversed())
            val manifest = BackupManifest(BACKUP_FORMAT_VERSION, "test", 1, ROOM_VERSION,
                "2026-10-19T00:00:00Z", base.packageName, BackupFiles.sha256(snapshot), snapshot.length())
            val zip = File(root, "backup.zip")
            zip.outputStream().use { BackupZip.write(it, BackupFormat.encodeManifest(manifest), snapshot,
                BackupFormat.encodeSettings(settings)) }
            val prepared = BackupService(targetContext).prepareImport(Uri.fromFile(zip))
            assertEquals(manifest, prepared.manifest)
            BackupFiles.replaceDatabase(targetContext, prepared.candidate)
            val restoredSettings = prepared.settings
            AppearancePreferences(targetContext).setDark(restoredSettings.night)
            PlanViewOrderPreferences(targetContext).setOrderNames(restoredSettings.planViewOrder.map { it.name })
            BackupRecovery.writeMarker(targetContext, ImportMarker(ImportPhase.REPLACED_PENDING_VERIFY,
                ROOM_VERSION, BackupFiles.sha256(prepared.candidate)))

            // This verifies the pre-open recovery path and creates a fresh Room/repository instance.
            // It does not simulate Android process death; a device test must still verify that boundary.
            assertTrue(BackupRecovery.recoverBeforeDatabaseOpen(targetContext))
            val reopened = Room.databaseBuilder(targetContext, RidoDatabase::class.java, RidoDatabase.DATABASE_NAME).build()
            try {
                val repo = RoomRidoRepository(reopened)
                assertEquals(null, reopened.categoryDao().getById(99))
                assertEquals("恢复分类", repo.getCategories().single().name)
                assertFalse(repo.getCategories().single().isArchived)
                assertEquals(1, repo.getEnabledKeywordRules().size)
                val today = GetTodayStableSnapshotUseCase(repo)(AwakeDayRequest(
                    date, 7 * 60, 22 * 60, zone, end + 1))
                assertTrue(today.displaySources.events.any { it.title == "恢复日程" })
                assertTrue(today.displaySources.sessions.any { it.title == "恢复记录" })
                val plan = loadPlanStableSources(RoomPlanSourceQueries(repo),
                    planQueryKey(PlanContext(date, PlanViewMode.MONTH, MonthContent.PLAN)), zone)
                assertTrue((plan as PlanStableSources.Scheduled).events.any { it.title == "恢复日程" })
                val statistics = GetStatisticsUseCase(repo)(StatisticsPeriod(StatisticsGranularity.DAY, date),
                    YearMonth.from(date), zone, end + 1)
                assertTrue(statistics is DomainResult.Success)
                assertTrue(repo.getCompletedActivityIntervalsInRange(start, end + 1).any { it.title == "恢复记录" })
                val reminderRebuildInput = reopened.eventDao().getAll().map { it.toDomain() }
                val scheduled = ReminderPlan.future(
                    reminderRebuildInput.flatMap { ReminderPlan.forEvent(it, zone) }, end)
                assertEquals(listOf(4L), scheduled.map { it.ownerId })
                assertEquals(30, scheduled.single().offsetMinutes)
                assertEquals(6L, repo.getActiveSession()?.id)
                val widget = SleepWidgetGateway(repo, nowMillis = { sleepStart + 60 * 60_000L }, zoneId = { zone })
                assertTrue(widget.wakeCheckIn() is DomainResult.Success)
                assertEquals(sleepStart + 60 * 60_000L, repo.getSession(6)?.endAtMillis)
                assertTrue(AppearancePreferences(targetContext).dark.first())
                assertEquals(settings.planViewOrder.map { it.name }, PlanViewOrderPreferences(targetContext).orderNames.first())
            } finally {
                reopened.close()
                prepared.discard()
            }
        } finally {
            source.close()
            original.close()
            root.deleteRecursively()
        }
    }
}
