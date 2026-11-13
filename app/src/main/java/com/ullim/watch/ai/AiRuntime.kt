package com.ullim.watch.ai

import android.content.Context
import java.util.concurrent.Executors

/** One process-owned lane: services/toggles can never accumulate replacement native workers. */
object AiRuntime {
    private var instance: AiWorker? = null
    @Synchronized fun worker(context: Context): AiWorker {
        instance?.let { return it }
        val application = context.applicationContext
        val lane = Executors.newSingleThreadExecutor { task ->
            Thread(task, "ullim-ai").apply { isDaemon = true; priority = Thread.NORM_PRIORITY - 1 }
        }
        return AiWorker({ AndroidYamnet.load(application) }, lane).also { instance = it }
    }
}
