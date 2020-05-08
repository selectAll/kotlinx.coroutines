package kotlinx.coroutines.flow

import kotlinx.coroutines.*
import kotlinx.coroutines.internal.*
import kotlin.coroutines.*
import kotlin.jvm.*

public interface SharedFlow<T> : Flow<T> {
    public val cache: List<T>
}

public interface MutableSharedFlow<T> : SharedFlow<T>, FlowCollector<T> {
    public fun tryEmit(value: T): Boolean
    public val numberOfCollectors: StateFlow<Int>
    public fun complete(): Boolean // signal that upstream is over
}

/**
 * Creates a [MutableSharedFlow] with the given configuration parameters.
 */
@Suppress("FunctionName")
@ExperimentalCoroutinesApi
public fun <T> MutableSharedFlow(cacheSize: Int, bufferSize: Int = cacheSize): MutableSharedFlow<T> =
    SharedFlowImpl(cacheSize, bufferSize)

// ------------------------------------ Implementation ------------------------------------

private class SharedFlowSlot : AbstractHotFlowSlot() {
    override fun allocate(): Boolean {
        TODO("Not yet implemented")
    }

    override fun free() {
        TODO("Not yet implemented")
    }
}

private class SharedFlowImpl<T>(
    private val cacheSize: Int,
    private val bufferSize: Int
) : AbstractHotFlow<SharedFlowSlot>(), MutableSharedFlow<T> {
    init {
        require(cacheSize >= 0) { "cacheSize($cacheSize) cannot be negative" }
        require(bufferSize >= cacheSize) { "bufferSize($bufferSize) cannot be smaller than cacheSize($cacheSize)" }
    }

    private var buffer: Array<Any?>? = null // allocated when needed
    private var head = 0L
    private var size = 0
    private var queue: LockFreeLinkedListHead? = null // allocated when needed

    override val cache: List<T>
        get() = TODO("Not yet implemented")

    override val numberOfCollectors: StateFlow<Int>
        get() = TODO("Not yet implemented")

    override fun tryEmit(value: T): Boolean = synchronized(this) {
        tryEmitLocked(value)
    }

    private fun tryEmitLocked(value: T): Boolean {
        if (size >= bufferSize) return false // buffer is full, cannot put
        val buffer = when (val curBuffer = buffer) {
            null -> growBuffer(null, 2)
            else -> if (curBuffer.size <= size) growBuffer(curBuffer, curBuffer.size * 2) else curBuffer
        }
        buffer.setBufferAt(head + size, value)
        size++
        return true
    }

    private fun growBuffer(curBuffer: Array<Any?>?, nextSize: Int): Array<Any?> {
        val newBuffer = arrayOfNulls<Any?>(minOf(nextSize, bufferSize)).also { buffer = it }
        if (curBuffer == null) return newBuffer
        for (i in 0 until size) {
            newBuffer.setBufferAt(head + i, curBuffer.getBufferAt(head + i))
        }
        return newBuffer
    }

    override suspend fun emit(value: T) {
        if (bufferSize > 0 && tryEmit(value)) return // fast-path, no suspension (no fast path w/o buffer!)
        emitSuspend(value)
    }

    private suspend fun emitSuspend(value: T) = suspendCancellableCoroutine<Unit> sc@{ cont ->
        val node = synchronized(this) {
            // recheck buffer under lock again (make sure it is really full)
            if (tryEmitLocked(value)) {
                cont.resume(Unit)
                return@sc
            }
            // register suspended emitter in the queue
            val queue = queue ?: LockFreeLinkedListHead().also { queue = it }
            EmitterNode(value, cont).also { queue.addLast(it) }
        }
        // outside of the lock register dispose on cancellation
        cont.disposeOnCancellation(node)
    }

    @InternalCoroutinesApi
    override suspend fun collect(collector: FlowCollector<T>) {
        val slot = allocateSlot()
        try {
            while (true) {

            }
        } finally {
            freeSlot(slot)
        }
    }

    override fun createSlot() = SharedFlowSlot()

    override fun complete(): Boolean {
        TODO("Not yet implemented")
    }

    private class EmitterNode(
        @JvmField val value: Any?,
        @JvmField val cont: Continuation<Unit>
    ) : LockFreeLinkedListNode(), DisposableHandle {
        override fun dispose() {
            remove()
        }
    }
}

private fun Array<Any?>.getBufferAt(index: Long) = get((index % size).toInt())
private fun Array<Any?>.setBufferAt(index: Long, value: Any?) = set((index % size).toInt(), value)

