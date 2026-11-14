package com.ullim.watch.ai

import android.content.Context
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** One process-owned lane: services/toggles can never accumulate replacement native workers. */
object AiRuntime {
    private var instance: AiWorker? = null
    private val reportingScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    /** Main/control thread only; loading settings never constructs a native worker. */
    @Synchronized fun refreshEnabled(enabled: Boolean) {
        instance?.let { it.setEnabled(it.state.value.sessionId, enabled) }
    }
    @Synchronized fun worker(context: Context): AiWorker {
        instance?.let { return it }
        val application = context.applicationContext
        val lane = Executors.newSingleThreadExecutor { task ->
            Thread(task, "ullim-ai").apply { isDaemon = true; priority = Thread.NORM_PRIORITY - 1 }
        }
        return AiWorker({ AndroidYamnet.load(application) }, lane).also {
            instance = it
            observeAiFailures(it, reportingScope)
        }
    }
}
