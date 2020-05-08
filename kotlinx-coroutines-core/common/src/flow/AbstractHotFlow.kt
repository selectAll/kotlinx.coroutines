package kotlinx.coroutines.flow

import kotlinx.coroutines.internal.*
import kotlin.jvm.*

internal abstract class AbstractHotFlowSlot {
    abstract fun allocate(): Boolean
    abstract fun free()
}

internal abstract class AbstractHotFlow<S : AbstractHotFlowSlot> : SynchronizedObject() {
    @Suppress("UNCHECKED_CAST")
    @JvmField
    protected var slots: Array<S?>? = null // allocated when needed
        private set
    private var nSlots = 0 // number of allocated (!free) slots
    private var nextIndex = 0 // oracle for the next free slot index

    protected abstract fun createSlot(): S

    @Suppress("UNCHECKED_CAST")
    protected fun allocateSlot(): S = synchronized(this) {
        val slots = when(val curSlots = slots) {
            null -> (arrayOfNulls<AbstractHotFlowSlot?>(2) as Array<S?>).also { slots = it }
            else -> if (nSlots >= curSlots.size) {
                curSlots.copyOf(2 * curSlots.size).also { slots = it }
            } else {
                curSlots
            }
        }
        var index = nextIndex
        var slot: S
        while (true) {
            slot = slots[index] ?: createSlot().also { slots[index] = it }
            index++
            if (index >= slots.size) index = 0
            if (slot.allocate()) break // break when found and allocated free slot
        }
        nextIndex = index
        nSlots++
        slot
    }

    protected fun freeSlot(slot: S): Unit = synchronized(this) {
        slot.free()
        nSlots--
    }
}