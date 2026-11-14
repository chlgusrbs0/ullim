package com.ullim.watch.settings

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.CopyOnWriteArrayList

class SettingsControllerTest {
    private class Store : SettingsStore {
        val loaded = CompletableDeferred<UserSettings>()
        val writes = Channel<Pair<UserSettings, CompletableDeferred<Unit>>>(Channel.UNLIMITED)
        override suspend fun load() = loaded.await()
        override suspend fun save(settings: UserSettings) {
            val done = CompletableDeferred<Unit>()
            writes.send(settings to done)
            done.await()
        }
    }
    private fun fixture(body: suspend (SettingsController, Store, MutableList<UserSettings>, CoroutineDispatcher) -> Unit) = runBlocking {
        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { control ->
            val scope = CoroutineScope(SupervisorJob() + control)
            val store = Store()
            val applied = CopyOnWriteArrayList<UserSettings>()
            val controller = SettingsController(store, scope, { applied.add(it) })
            try { withTimeout(5_000) { body(controller, store, applied, control) } }
            finally { scope.coroutineContext.job.cancelAndJoin() }
        }
    }

    @Test fun initialLoadGuardsEditsAndAppliesStoredSettingsOnControlScope() = fixture { controller, store, applied, control ->
        assertFalse(withContext(control) { controller.setAiEnabled(true) })
        assertTrue(applied.isEmpty())
        store.loaded.complete(UserSettings(.75f, true))
        controller.state.first { it.ready }
        assertEquals(UserSettings(.75f, true), controller.state.value.values)
        assertEquals(listOf(UserSettings(.75f, true)), applied)
    }

    @Test fun currentChoiceAppliesBeforeBlockedDiskAndConflationKeepsNewestOffAndSensitivity() = fixture { controller, store, applied, control ->
        store.loaded.complete(UserSettings(aiEnabled = false)); controller.state.first { it.ready }
        withContext(control) { controller.setAiEnabled(true) }
        val first = store.writes.receive()
        assertTrue(first.first.aiEnabled)
        withContext(control) { controller.setAiEnabled(false); controller.setSensitivity(.75f) }
        assertEquals(UserSettings(.75f, false), applied.last())
        assertTrue(controller.state.value.saving)
        first.second.complete(Unit)
        val latest = store.writes.receive()
        assertEquals(UserSettings(.75f, false), latest.first)
        assertTrue(controller.state.value.saving)
        latest.second.complete(Unit)
        controller.state.first { !it.saving }
        assertNull(controller.state.value.error)
        assertTrue(store.writes.tryReceive().isFailure)
    }

    @Test fun failedSaveIsHonestKeepsLiveChoiceAndExplicitRetryCanPersistIt() = fixture { controller, store, applied, control ->
        store.loaded.complete(UserSettings()); controller.state.first { it.ready }
        withContext(control) { controller.setSensitivity(1.25f) }
        store.writes.receive().second.completeExceptionally(IOException("disk full"))
        controller.state.first { it.error == SettingsError.WRITE }
        assertEquals(1.25f, controller.state.value.values.sensitivity)
        assertEquals(1.25f, applied.last().sensitivity)
        withContext(control) { controller.retrySave() }
        val retry = store.writes.receive()
        assertEquals(UserSettings(1.25f), retry.first)
        retry.second.complete(Unit)
        controller.state.first { !it.saving && it.error == null }
    }

    @Test fun readFailureUsesDefaultsWithoutWritingUntilExplicitReset() = fixture { controller, store, applied, control ->
        store.loaded.completeExceptionally(IOException("unreadable"))
        controller.state.first { it.ready }
        assertEquals(UserSettings(), applied.last())
        assertEquals(SettingsError.READ, controller.state.value.error)
        assertTrue(store.writes.tryReceive().isFailure)
        withContext(control) { controller.reset() }
        val reset = store.writes.receive()
        assertEquals(UserSettings(), reset.first)
        reset.second.complete(Unit)
        controller.state.first { !it.saving && it.error == null }
    }

    @Test fun detailedTuningAppliesBeforeDiskAndResetKeepsAiAndSensitivity() = fixture { controller, store, applied, control ->
        store.loaded.complete(UserSettings(.75f, false)); controller.state.first { it.ready }
        withContext(control) { controller.setTuning(TuningParameter.GAP_HOLD_MS, 80f) }
        val write = store.writes.receive()
        assertEquals(80f, applied.last().tuning[TuningParameter.GAP_HOLD_MS], 0f)
        assertTrue(controller.state.value.saving)
        withContext(control) { controller.resetTuning() }
        assertEquals(UserSettings(.75f, false), applied.last())
        write.second.complete(Unit)
        val reset = store.writes.receive()
        assertEquals(UserSettings(.75f, false), reset.first)
        reset.second.complete(Unit)
        controller.state.first { !it.saving }
    }
}
