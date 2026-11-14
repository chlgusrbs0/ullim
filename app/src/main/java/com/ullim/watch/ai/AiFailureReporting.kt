package com.ullim.watch.ai

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Process/control scope outlives services and observes asynchronous native cleanup. */
internal fun observeAiFailures(worker: AiWorker, scope: CoroutineScope): Job = scope.launch {
    var last: Pair<Long, AiFault>? = null
    worker.state.collect { snapshot ->
        val key = snapshot.fault?.let { snapshot.sessionId to it }
        if (key != null && key != last) Log.w("UllimError", "session=${key.first} code=AI_${key.second.name}")
        last = key
    }
}
