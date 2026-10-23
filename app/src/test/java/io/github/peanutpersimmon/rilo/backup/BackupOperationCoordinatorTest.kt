package io.github.peanutpersimmon.rilo.backup

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class BackupOperationCoordinatorTest {
    @Test fun exportWaitsForExistingMutationAndBlocksNewMutationsUntilSnapshotFinishes(): Unit = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val first = async {
            BackupOperationCoordinator.mutation {
                entered.complete(Unit)
                release.await()
            }
        }
        entered.await()
        val exportEntered = CompletableDeferred<Unit>()
        val exportRelease = CompletableDeferred<Unit>()
        val export = async {
            BackupOperationCoordinator.export {
                exportEntered.complete(Unit)
                exportRelease.await()
            }
        }
        assertFalse(exportEntered.isCompleted)
        release.complete(Unit)
        withTimeout(5_000) { exportEntered.await() }
        val laterMutation = CompletableDeferred<Unit>()
        val later = async { BackupOperationCoordinator.mutation { laterMutation.complete(Unit) } }
        assertFalse(laterMutation.isCompleted)
        exportRelease.complete(Unit)
        withTimeout(5_000) { laterMutation.await() }
        first.await(); export.await(); later.await()
    }

    @Test fun importRejectsConcurrentWritesButAllowsItsOwnWhitelistedSettingWrites(): Unit = runBlocking {
        BackupOperationCoordinator.withImport {
            val outsider = CoroutineScope(Dispatchers.Default).async {
                runCatching { BackupOperationCoordinator.mutation { 1 } }.exceptionOrNull()
            }
            assertTrue(outsider.await() is BackupBusyException)
            assertEquals(7, BackupOperationCoordinator.mutation { 7 })
        }
        assertEquals(8, BackupOperationCoordinator.mutation { 8 })
    }

    @Test fun successfulRestoreRequiresUserRestartAndKeepsWritesFrozen(): Unit = runBlocking {
        var launches = 0
        val flow = BackupRestartFlow { launches++ }
        assertFalse(flow.launchIfRequired(BackupUiPhase.IDLE))
        val state = flow.onRestore(RestoreOutcome.SUCCESS_RESTART)
        assertEquals(BackupUiPhase.RESTART_REQUIRED, state.phase)
        assertEquals(0, launches)
        try {
            BackupOperationCoordinator.withImport { BackupOperationCoordinator.freezeForRestart() }
            assertTrue(runCatching { BackupOperationCoordinator.mutation { 1 } }
                .exceptionOrNull() is BackupBusyException)
            assertTrue(flow.launchIfRequired(state.phase))
            assertEquals(1, launches)
        } finally {
            BackupOperationCoordinator.resetAfterTest()
        }
    }
}
