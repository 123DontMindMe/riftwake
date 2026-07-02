package me.talula.riftwake.utils

import java.util.*

class RandomTable<E> {
    inner class Entry(val value: E, val weight: Double)

    private val distribution: NavigableMap<Double?, E> = TreeMap<Double?, E>()
    private val entries: MutableList<Entry> = ArrayList<Entry>()
    var totalWeight: Double = 0.0
        private set

    fun add(value: E, weight: Double): RandomTable<E> {
        this.totalWeight += weight
        distribution[this.totalWeight] = value
        entries.add(Entry(value, weight))
        return this
    }

    fun add(value: E, weight: Double, index: Int): RandomTable<E> {
        this.totalWeight += weight
        distribution[this.totalWeight] = value
        entries.add(index, Entry(value, weight))
        return this
    }

    fun remove(index: Int): Entry {
        distribution.clear()
        this.totalWeight = 0.0
        val removedEntry = entries.removeAt(index)
        for (entry in entries) {
            this.totalWeight += entry.weight
            distribution[this.totalWeight] = entry.value
        }
        return removedEntry
    }

    fun remove(value: E): Entry? {
        val index = entries.indexOfFirst { it.value == value }
        if (index == -1)
            return null
        return remove(index)
    }

    fun get(index: Int): Entry {
        return entries[index]
    }

    fun pull(): E {
        if (isEmpty)
            throw IllegalStateException("Can't pull from empty table")
        return distribution.higherEntry(Math.random() * this.totalWeight).value
    }

    val size: Int
        get() = entries.size

    val isEmpty: Boolean
        get() = entries.isEmpty()
}