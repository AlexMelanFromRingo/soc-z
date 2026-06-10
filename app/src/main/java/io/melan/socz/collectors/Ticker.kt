package io.melan.socz.collectors

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * A simple "tick every N ms with a fresh sample" flow that we use to drive all
 * live-updating screens (CPU frequencies, battery, memory). The factory function
 * `sample` is called on each tick — keep it cheap; don't do blocking I/O.
 */
fun <T> tickerFlow(intervalMs: Long, sample: () -> T): Flow<T> = flow {
    while (true) {
        emit(sample())
        delay(intervalMs)
    }
}
