package sk.dilino.pulseclientmobile.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

/**
 * Runs [block] every [intervalSecs] seconds, forever. A new interval takes effect at once: the
 * wait in progress is dropped and the next one starts with the new length.
 */
suspend fun pollEvery(intervalSecs: Flow<Int>, block: suspend () -> Unit) {
    intervalSecs.collectLatest { secs ->
        while (true) {
            delay(secs.coerceAtLeast(1) * 1000L)
            block()
        }
    }
}
