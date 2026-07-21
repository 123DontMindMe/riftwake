package me.talula.riftwake.utils

import org.bukkit.Material
import java.util.*
import kotlin.collections.set

class LayerTable {
    interface Entry { fun onPull(layer: Layer, results: MutableMap<Layer, Material>) }

    class BlockEntry(val block: Material): Entry {
        override fun onPull(layer: Layer, results: MutableMap<Layer, Material>) {
            results[layer] = block
        }
    }

    inner class CopyEntry(val copyLayer: Layer): Entry {
        override fun onPull(layer: Layer, results: MutableMap<Layer, Material>) {
            if (copyLayer !in results) {
                var pull = tables[copyLayer]!!.pull()
                while (pull !is BlockEntry)
                    pull = tables[copyLayer]!!.pull()
                pull.onPull(copyLayer, results)
            }
            results[copyLayer] = results[layer]!!
        }
    }

    enum class Layer {
        LEAVES,
        WOOD,
        GRASS,
        FLORA,
        DIRT,
        ALT_DIRT,
        CROPS,
        FARMLAND,
        LIQUID,
        STONE,
        ALT_STONE,
        ORE,
        BUILDING_BLOCK,
    }

    val tables = EnumMap<Layer, RandomTable<Entry>>(Layer::class.java)

    init {
        for (layer in Layer.entries)
            tables[layer] = RandomTable()
    }

    fun add(layer: Layer, weight: Double, entry: Entry) {
        tables[layer]!!.add(entry, weight)
    }

    fun add(layer: Layer, weight: Double, block: Material) {
        tables[layer]!!.add(BlockEntry(block), weight)
    }

    fun add(layer: Layer, weight: Double, copy: Layer) {
        tables[layer]!!.add(CopyEntry(copy), weight)
    }

    fun pull(): Map<Layer, Material> {
        val results = EnumMap<Layer, Material>(Layer::class.java)
        for (layer in tables.keys)
            if (layer !in results)
                tables[layer]!!.pull().onPull(layer, results)
        return results
    }
}

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