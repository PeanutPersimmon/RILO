package io.github.peanutpersimmon.rilo.backup

import androidx.room.withTransaction
import io.github.peanutpersimmon.rilo.data.local.room.RidoDatabase
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** One process-wide gate for Room writes, backed-up settings, and Reminder schedule changes. */
object BackupOperationCoordinator {
    private val gate = Mutex()
    private val importing = AtomicBoolean(false)
    @Volatile private var restartRequired = false

    private class Permit : AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<Permit>
    }

    suspend fun <T> mutation(block: suspend () -> T): T {
        if (currentCoroutineContext()[Permit] != null) return block()
        if (importing.get()) throw BackupBusyException()
        return gate.withLock {
            if (importing.get()) throw BackupBusyException()
            withContext(Permit()) { block() }
        }
    }

    suspend fun <T> export(block: suspend () -> T): T = gate.withLock {
        if (importing.get()) throw BackupBusyException()
        withContext(Permit()) { block() }
    }

    /** Import remains closed to fresh mutations until the process is restarted. */
    suspend fun <T> withImport(block: suspend () -> T): T {
        if (!importing.compareAndSet(false, true)) throw BackupBusyException()
        try {
            return gate.withLock { withContext(Permit()) { block() } }
        } finally {
            if (!restartRequired) importing.set(false)
        }
    }

    fun freezeForRestart() { restartRequired = true }

    /** Test-only reset after an isolated rollback check. Production imports cold-restart. */
    internal fun resetAfterTest() { restartRequired = false; importing.set(false) }
}

class BackupBusyException : IllegalStateException("数据恢复进行中，请稍候")

/** All repository transactions use this alias, including nested Voice transactions. */
suspend fun <T> RidoDatabase.withBackupGateTransaction(block: suspend () -> T): T =
    BackupOperationCoordinator.mutation { withTransaction { block() } }
