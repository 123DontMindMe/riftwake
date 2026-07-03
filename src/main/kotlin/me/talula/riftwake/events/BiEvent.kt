package me.talula.riftwake.events

import java.util.*
import java.util.function.BiConsumer

class BiEvent<T, U> {
    var listeners: SequencedSet<BiConsumer<T, U>> = LinkedHashSet<BiConsumer<T, U>>()

    private var isInvoking = false
    private var newListeners: SequencedSet<BiConsumer<T, U>>? = null

    operator fun plusAssign(listener: BiConsumer<T, U>) {
        if (isInvoking) {
            if (newListeners == null)
                newListeners = LinkedHashSet(listeners)
            newListeners!!.add(listener)
            return
        }
        listeners.add(listener)
        return
    }

    operator fun minusAssign(listener: BiConsumer<T, U>) {
        if (isInvoking) {
            if (newListeners == null)
                newListeners = LinkedHashSet(listeners)
            newListeners!!.remove(listener)
            return
        }
        listeners.remove(listener)
    }

    operator fun invoke(arg1: T, arg2: U) {
        isInvoking = true
        for (listener in listeners) {
            if (newListeners?.contains(listener) == true)
                continue
            listener.accept(arg1, arg2)
        }
        if (newListeners != null) {
            listeners = newListeners!!
            newListeners = null
        }
        isInvoking = false
    }
}