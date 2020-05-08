/*
 * Copyright 2016-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

@file:JvmMultifileClass
@file:JvmName("FlowKt")

package kotlinx.coroutines.flow

import kotlinx.coroutines.*
import kotlin.jvm.*

public fun <T> Flow<T>.shareIn(
    scope: CoroutineScope,
    start: SharedStart = SharedStart.Eager,
    cache: SharedCache = SharedCache.None
): SharedFlow<T> {
    val upstreamFlow = this
    val sharedFlow = cache.createMutableSharedFlow<T>()
    scope.launch { // the single coroutine to rule the sharing
        start.startFlow(sharedFlow.numberOfCollectors)
            .distinctUntilChanged()
            .collectLatest { doStart -> // cancels block on emission
                if (doStart) {
                    try {
                        upstreamFlow.collect(sharedFlow) // can be cancelled
                    } finally {
                        sharedFlow.complete()
                    }
                }
            }
    }
    return sharedFlow
}

public fun <T> Flow<T>.stateIn(
    scope: CoroutineScope,
    start: SharedStart = SharedStart.Eager,
    initialValue: T
): StateFlow<T> {
    TODO()
}

public suspend fun <T> Flow<T>.stateIn(scope: CoroutineScope): StateFlow<T> = TODO()

public interface SharedStart {
    public companion object {
        public val Eager: SharedStart = TODO()
        public val Lazy: SharedStart = TODO()
        public val OnDemand: SharedStart = TODO()
        public fun OnDemand(timeout: Long): SharedStart = TODO()
    }

    public fun startFlow(numberOfCollectors: StateFlow<Int>): Flow<Boolean>
}

public interface SharedCache {
    public companion object {
        public val None: SharedCache = TODO()
        public val Last: SharedCache = TODO()
        public fun Last(size: Int = 1, expiration: Long = 0): SharedCache = TODO()
        public fun <T> Last(size: Int = 1, expiration: Long = 0, initialValue: T): SharedCache = TODO()
    }

    public fun <T> createMutableSharedFlow(): MutableSharedFlow<T>
}

// test
fun main() {
    val flow: Flow<Int> = flowOf(42)
    val scope: CoroutineScope = GlobalScope

    // Basic event sharing
    flow.shareIn(scope) // Eager connect
    flow.shareIn(scope, SharedStart.Lazy) // Lazy auto-connect
    flow.shareIn(scope, SharedStart.OnDemand) // refCount
    flow.shareIn(scope, SharedStart.OnDemand(timeout = 1000L)) // refCount with timeout
    // State sharing
    flow.shareIn(scope, cache = SharedCache.Last) // Eager connect
    flow.shareIn(scope, SharedStart.Lazy, SharedCache.Last) // Lazy auto-connect
    flow.shareIn(scope, SharedStart.OnDemand, SharedCache.Last) // refCount
    flow.shareIn(scope, SharedStart.OnDemand(timeout = 1000L), SharedCache.Last) // refCount with timeout
    flow.shareIn(scope, SharedStart.OnDemand, SharedCache.Last(expiration = 1000L)) // refCount with expiration
    flow.shareIn(scope, SharedStart.OnDemand, SharedCache.Last(initialValue = null)) // refCount with initial value
    // Log sharing (cache last 100)
    flow.shareIn(scope, cache = SharedCache.Last(100)) // Eager connect
    flow.shareIn(scope, SharedStart.Lazy, SharedCache.Last(100)) // Lazy auto-connect
    flow.shareIn(scope, SharedStart.OnDemand, SharedCache.Last(100)) // refCount
    flow.shareIn(scope, SharedStart.OnDemand(timeout = 1000L), SharedCache.Last(100)) // refCount with timeout
    flow.shareIn(scope, SharedStart.OnDemand, SharedCache.Last(size = 100, expiration = 1000L)) // refCount with expiration

}
