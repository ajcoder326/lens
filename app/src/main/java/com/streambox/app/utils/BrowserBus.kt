package com.streambox.app.utils

import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.ConcurrentHashMap

/**
 * Singleton to pass data between JSApis and CloudflareSolverActivity
 */
object BrowserBus {
    // Map requestId -> Deferred Result (HTML)
    val requests = ConcurrentHashMap<String, CompletableDeferred<String>>()
}
