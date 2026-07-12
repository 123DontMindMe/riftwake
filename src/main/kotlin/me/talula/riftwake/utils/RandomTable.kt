package me.talula.riftwake.utils

import java.util.*
import kotlin.collections.set

class TieredTable<E> {
    inner class Entry(val value: E, val chance: Double)
    private val tiers = TreeMap<Int, Entry>()

    fun pull(): E {
        for ((index, entry) in tiers.descendingMap().values.withIndex()) {
            if (index == tiers.size - 1 || Math.random() < entry.chance / 100)
                return entry.value
            else continue
        }
        throw IllegalStateException("Can't pull from empty table")
    }

    fun set(tier: Int, chance: Double, value: E) {
        tiers[tier] = Entry(value, chance)
    }
}

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
        if (weight <= 0)
            return
        totalWeight += weight
        distribution[totalWeight] = value
        entries.add(Entry(value, weight))
    }

    fun add(value: E, weight: Double, index: Int) {
        if (weight <= 0)
            return
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

    fun pull(totalWeight: Double): E? {
        if (isEmpty)
            throw IllegalStateException("Can't pull from empty table")
        return distribution.higherEntry(Math.random() * totalWeight.coerceAtLeast(this.totalWeight)).value
    }

    val size: Int
        get() = entries.size

    val isEmpty: Boolean
        get() = entries.isEmpty()
}