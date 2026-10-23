package io.github.peanutpersimmon.rilo.data.local.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import io.github.peanutpersimmon.rilo.data.local.room.dao.CategoryDao
import io.github.peanutpersimmon.rilo.data.local.room.dao.EventDao
import io.github.peanutpersimmon.rilo.data.local.room.dao.KeywordRuleDao
import io.github.peanutpersimmon.rilo.data.local.room.dao.ProcessedCommandDao
import io.github.peanutpersimmon.rilo.data.local.room.dao.SessionDao
import io.github.peanutpersimmon.rilo.data.local.room.entity.CategoryEntity
import io.github.peanutpersimmon.rilo.data.local.room.entity.EventEntity
import io.github.peanutpersimmon.rilo.data.local.room.entity.KeywordRuleEntity
import io.github.peanutpersimmon.rilo.data.local.room.entity.ProcessedCommandEntity
import io.github.peanutpersimmon.rilo.data.local.room.entity.SessionEntity

@Database(
    entities = [
        EventEntity::class,
        SessionEntity::class,
        CategoryEntity::class,
        KeywordRuleEntity::class,
        ProcessedCommandEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(RidoTypeConverters::class)
abstract class RidoDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao
    abstract fun sessionDao(): SessionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun keywordRuleDao(): KeywordRuleDao
    abstract fun processedCommandDao(): ProcessedCommandDao

    companion object {
        const val DATABASE_NAME = "rilo.db"

        @Volatile
        private var instance: RidoDatabase? = null

        fun getInstance(context: Context): RidoDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    RidoDatabase::class.java,
                    DATABASE_NAME,
                ).addCallback(FreshDatabaseMarkerCallback).build().also { instance = it }
            }

        /** Existing repositories must never be used again after this; import cold-restarts. */
        fun closeForImport() = synchronized(this) { instance?.close() }
    }
}

/** Room invokes onCreate only for a newly created database, never for an existing or upgraded one. */
internal val FreshDatabaseMarkerCallback = object : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "INSERT INTO processed_commands(commandId, processedAtMillis) VALUES (?, ?)",
            arrayOf<Any>(FreshDatabaseMarker, System.currentTimeMillis()),
        )
    }
}
