package me.talula.riftwake.events

import java.util.*
import java.util.function.Consumer

class Event<T> {
    var listeners: SequencedSet<Consumer<T>> = LinkedHashSet<Consumer<T>>()

    private var isInvoking = false
    private var newListeners: SequencedSet<Consumer<T>>? = null

    operator fun plusAssign(listener: Consumer<T>) {
        if (isInvoking) {
            if (newListeners == null)
                newListeners = LinkedHashSet(listeners)
            newListeners!!.add(listener)
            return
        }
        listeners.add(listener)
    }

    operator fun minusAssign(listener: Consumer<T>) {
        if (isInvoking) {
            if (newListeners == null)
                newListeners = LinkedHashSet(listeners)
            newListeners!!.remove(listener)
            return
        }
        listeners.remove(listener)
    }

    operator fun invoke(event: T) {
        isInvoking = true
        for (listener in listeners) {
            if (newListeners?.contains(listener) == true)
                continue
            listener.accept(event)
        }
        if (newListeners != null) {
            listeners = newListeners!!
            newListeners = null
        }
        isInvoking = false
    }
}