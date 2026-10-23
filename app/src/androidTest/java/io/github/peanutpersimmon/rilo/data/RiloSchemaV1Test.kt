package io.github.peanutpersimmon.rilo.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.peanutpersimmon.rilo.data.local.room.RidoDatabase
import io.github.peanutpersimmon.rilo.data.local.room.entity.CategoryEntity
import io.github.peanutpersimmon.rilo.data.local.room.entity.EventEntity
import io.github.peanutpersimmon.rilo.data.local.room.entity.KeywordRuleEntity
import io.github.peanutpersimmon.rilo.data.local.room.entity.SessionEntity
import io.github.peanutpersimmon.rilo.domain.model.SessionKind
import io.github.peanutpersimmon.rilo.domain.model.SessionTemporalType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RiloSchemaV1Test {
    private fun database() = Room.inMemoryDatabaseBuilder(
        InstrumentationRegistry.getInstrumentation().targetContext,
        RidoDatabase::class.java,
    ).build()

    @Test fun freshDatabaseContainsOnlyRiloProductTablesAndColumns() {
        val db = database()
        try {
            val sqlite = db.openHelper.readableDatabase
            assertEquals(1, sqlite.version)
            val tables = sqlite.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { cursor ->
                buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
            }
            assertEquals(setOf("events", "sessions", "categories", "keyword_rules", "processed_commands"),
                tables.filterNot { it.startsWith("sqlite_") || it.startsWith("room_") || it == "android_metadata" }.toSet())
            val expected = mapOf(
                "events" to setOf("id", "title", "note", "location", "dateEpochDay", "startMinuteOfDay",
                    "startReminderOffsetMinutes", "endDateEpochDay", "endMinuteOfDay", "categoryId",
                    "createdAtMillis", "updatedAtMillis"),
                "sessions" to setOf("id", "title", "note", "location", "kind", "temporalType",
                    "startAtMillis", "endAtMillis", "categoryId", "sourceEventId", "createdAtMillis", "updatedAtMillis"),
                "categories" to setOf("id", "name", "colorKey", "sortOrder", "isArchived", "createdAtMillis", "updatedAtMillis"),
                "keyword_rules" to setOf("id", "keyword", "categoryId", "isEnabled", "priority", "createdAtMillis", "updatedAtMillis"),
                "processed_commands" to setOf("commandId", "processedAtMillis"),
            )
            expected.forEach { (table, columns) ->
                val actual = sqlite.query("PRAGMA table_info(`$table`)").use { cursor ->
                    buildSet { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) }
                }
                assertEquals(table, columns, actual)
            }
            fun foreignKeys(table: String): Set<Triple<String, String, String>> =
                sqlite.query("PRAGMA foreign_key_list(`$table`)").use { cursor ->
                    buildSet {
                        while (cursor.moveToNext()) add(Triple(
                            cursor.getString(cursor.getColumnIndexOrThrow("from")),
                            cursor.getString(cursor.getColumnIndexOrThrow("table")),
                            cursor.getString(cursor.getColumnIndexOrThrow("on_delete")),
                        ))
                    }
                }
            assertEquals(setOf(Triple("categoryId", "categories", "SET NULL")), foreignKeys("events"))
            assertEquals(setOf(Triple("categoryId", "categories", "SET NULL"),
                Triple("sourceEventId", "events", "SET NULL")), foreignKeys("sessions"))
            assertEquals(setOf(Triple("categoryId", "categories", "SET NULL")), foreignKeys("keyword_rules"))
        } finally { db.close() }
    }

    @Test fun categoryAndEventDeletionPreserveRecordsAndClearNullableLinks() = runBlocking {
        val db = database()
        try {
            val categoryId = db.categoryDao().insert(CategoryEntity(name = "Work", createdAtMillis = 1, updatedAtMillis = 1))
            val eventId = db.eventDao().insert(EventEntity(title = "Meeting", dateEpochDay = 1,
                categoryId = categoryId, createdAtMillis = 1, updatedAtMillis = 1))
            val remainingEventId = db.eventDao().insert(EventEntity(title = "Another", dateEpochDay = 1,
                categoryId = categoryId, createdAtMillis = 1, updatedAtMillis = 1))
            val sessionId = db.sessionDao().insert(SessionEntity(title = "Meeting happened",
                kind = SessionKind.ACTIVITY, temporalType = SessionTemporalType.INTERVAL,
                startAtMillis = 10, endAtMillis = 20, sourceEventId = eventId,
                categoryId = categoryId, createdAtMillis = 1, updatedAtMillis = 1))
            val ruleId = db.keywordRuleDao().insert(KeywordRuleEntity(keyword = "meeting", categoryId = categoryId,
                createdAtMillis = 1, updatedAtMillis = 1))
            db.eventDao().deleteById(eventId)
            assertNull(db.sessionDao().getById(sessionId)?.sourceEventId)
            db.openHelper.writableDatabase.execSQL("DELETE FROM categories WHERE id = $categoryId")
            assertNull(db.eventDao().getById(remainingEventId)?.categoryId)
            assertNull(db.sessionDao().getById(sessionId)?.categoryId)
            assertNull(db.keywordRuleDao().getById(ruleId)?.categoryId)
            assertTrue(db.sessionDao().getById(sessionId) != null)
            assertTrue(db.keywordRuleDao().getById(ruleId) != null)
        } finally { db.close() }
    }

    @Test fun activityFactsAndSleepUseSeparateQueriesAndPointIsNotActive() = runBlocking {
        val db = database()
        try {
            val dao = db.sessionDao()
            val point = dao.insert(SessionEntity(title = "Point", kind = SessionKind.ACTIVITY,
                temporalType = SessionTemporalType.POINT, startAtMillis = 10, createdAtMillis = 1, updatedAtMillis = 1))
            val interval = dao.insert(SessionEntity(title = "Interval", kind = SessionKind.ACTIVITY,
                temporalType = SessionTemporalType.INTERVAL, startAtMillis = 20, endAtMillis = 30,
                createdAtMillis = 1, updatedAtMillis = 1))
            val active = dao.insert(SessionEntity(title = "Active", kind = SessionKind.ACTIVITY,
                temporalType = SessionTemporalType.INTERVAL, startAtMillis = 40, createdAtMillis = 1, updatedAtMillis = 1))
            val nap = dao.insert(SessionEntity(title = "Nap", kind = SessionKind.NAP,
                temporalType = SessionTemporalType.INTERVAL, startAtMillis = 50, endAtMillis = 60,
                createdAtMillis = 1, updatedAtMillis = 1))
            dao.insert(SessionEntity(title = "Night", kind = SessionKind.NIGHT_SLEEP,
                temporalType = SessionTemporalType.INTERVAL, startAtMillis = 70, endAtMillis = 80,
                createdAtMillis = 1, updatedAtMillis = 1))
            assertEquals(active, dao.getActiveSession()?.id)
            assertEquals(setOf(point, interval), dao.getActivityFactEntitiesInRange(0, 100).map { it.id }.toSet())
            assertEquals(listOf(interval), dao.getCompletedActivityEntitiesInRange(0, 100).map { it.id })
            assertEquals(listOf(nap), dao.getNapEntitiesInRange(0, 100).map { it.id })
            assertEquals(1, dao.getNightSleeps().size)
            assertFalse(dao.getActiveSessions().any { it.id == point })
        } finally { db.close() }
    }
}
