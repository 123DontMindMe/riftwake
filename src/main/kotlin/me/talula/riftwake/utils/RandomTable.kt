package me.talula.riftwake.utils

import java.util.*

class RandomTable<E> {
    inner class Entry(val value: E, val weight: Double)

    private val distribution: NavigableMap<Double?, E> = TreeMap<Double?, E>()
    private val entries: MutableList<Entry> = ArrayList<Entry>()
    var totalWeight: Double = 0.0
        private set

    constructor(vararg entries: Pair<E, Double>) {
        for ((value, weight) in entries)
            add(value, weight)
    }

    fun add(value: E, weight: Double) {
        totalWeight += weight
        distribution[totalWeight] = value
        entries.add(Entry(value, weight))
    }

    fun add(value: E, weight: Double, index: Int) {
        totalWeight += weight
        distribution[totalWeight] = value
        entries.add(index, Entry(value, weight))
    }

    fun remove(index: Int): Entry {
        distribution.clear()
        totalWeight = 0.0
        val removedEntry = entries.removeAt(index)
        for (entry in entries) {
            totalWeight += entry.weight
            distribution[totalWeight] = entry.value
        }
        return removedEntry
    }

    fun remove(value: E): Entry? {
        val index = entries.indexOfFirst { it.value == value }
        if (index == -1)
            return null
        return remove(index)
    }

    operator fun get(index: Int): Entry {
        return entries[index]
    }

    operator fun set(value: E, weight: Double) {
        remove(value)
        add(value, weight)
    }

    fun pull(): E {
        if (isEmpty)
            throw IllegalStateException("Can't pull from empty table")
        return distribution.higherEntry(Math.random() * totalWeight).value
    }

    val size: Int
        get() = entries.size

    val isEmpty: Boolean
        get() = entries.isEmpty()
}