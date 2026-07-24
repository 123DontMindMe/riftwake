package me.talula.riftwake.utils

import com.sk89q.worldedit.world.block.BlockState
import com.sk89q.worldedit.world.block.BlockType
import com.sk89q.worldedit.world.block.BlockTypes
import me.talula.riftwake.Riftwake
import java.util.*
import kotlin.collections.set

class LayerTable {
    interface Entry { fun resolve(layer: Layer, results: MutableMap<Layer, BlockState>) }
    interface ConcreteEntry: Entry { val block: BlockState }

    class BlockEntry(override val block: BlockState): ConcreteEntry {
        override fun resolve(layer: Layer, results: MutableMap<Layer, BlockState>) {
            results[layer] = block
        }
    }

    inner class CopyEntry(val copyLayer: Layer): Entry {
        override fun resolve(layer: Layer, results: MutableMap<Layer, BlockState>) {
            if (copyLayer !in results)
                pullConcreteFromTable(copyLayer).resolve(copyLayer, results)
            results[layer] = results[copyLayer]!!
        }
    }

    inner class PullEntry(val pullLayer: Layer): Entry {
        override fun resolve(layer: Layer, results: MutableMap<Layer, BlockState>) {
            results[layer] = pullConcreteFromTable(pullLayer).block
        }
    }

    class WoodEntry(override val block: BlockState, val leaves: BlockState): ConcreteEntry {
        override fun resolve(layer: Layer, results: MutableMap<Layer, BlockState>) {
            results[layer] = block
            if (Layer.LEAVES !in results)
                results[Layer.LEAVES] = leaves
        }
    }

    inner class DefaultLeavesEntry: Entry {
        override fun resolve(layer: Layer, results: MutableMap<Layer, BlockState>) {
            getTable(Layer.WOOD).pull().resolve(Layer.WOOD, results)
        }
    }

    inner class PullLeavesEntry(val pullLayer: Layer): Entry {
        override fun resolve(layer: Layer, results: MutableMap<Layer, BlockState>) {
            results[layer] = pullWoodFromTable(pullLayer).leaves
        }
    }

    enum class Layer(val replaceBlock: BlockType) {
        LEAVES(BlockTypes.RED_WOOL!!),  // must be at top for non-default leaves to work, otherwise a wood/copy-of-wood entry could set leaves
        WOOD(BlockTypes.ORANGE_WOOL!!),
        ALT_DIRT(BlockTypes.WHITE_WOOL!!),
        ALT_STONE(BlockTypes.GRAY_WOOL!!),
        BUILDING_BLOCK(BlockTypes.PURPLE_WOOL!!),
        CROPS(BlockTypes.YELLOW_WOOL!!),
        DIRT(BlockTypes.BROWN_WOOL!!),
        GRASS(BlockTypes.GREEN_WOOL!!),
        LIQUID(BlockTypes.WATER!!),
        ORE(BlockTypes.LIGHT_BLUE_WOOL!!),
        STONE(BlockTypes.LIGHT_GRAY_WOOL!!),
        VEGETATION(BlockTypes.LIME_WOOL!!)
    }

    val entryFormat = Regex("(.+?)(?::(.+?))?(?:\\[(.+)=(.+)])? ([0-9]*\\.[0-9]+|[0-9]+)")
    val colors = arrayOf(
        "WHITE", "LIGHT_GRAY", "GRAY", "BLACK",
        "RED", "ORANGE", "YELLOW", "LIME",
        "GREEN", "LIGHT_BLUE", "CYAN", "BLUE",
        "PURPLE", "MAGENTA", "PINK", "BROWN"
    )
    val tables = arrayOf<RandomTable<Entry>>(
        readWeights("structures/weights/leaves-weights.txt"),
        readWeights("structures/weights/wood-weights.txt"),
        readWeights("structures/weights/alt-dirt-weights.txt"),
        readWeights("structures/weights/alt-stone-weights.txt"),
        readWeights("structures/weights/building-block-weights.txt"),
        readWeights("structures/weights/crops-weights.txt"),
        readWeights("structures/weights/dirt-weights.txt"),
        readWeights("structures/weights/grass-weights.txt"),
        readWeights("structures/weights/liquid-weights.txt"),
        readWeights("structures/weights/ore-weights.txt"),
        readWeights("structures/weights/stone-weights.txt"),
        readWeights("structures/weights/vegetation-weights.txt"),
    )

    init {
        getTable(Layer.WOOD).add(object : Entry {
            override fun resolve(layer: Layer, results: MutableMap<Layer, BlockState>) {
                pullConcreteFromTable(Layer.BUILDING_BLOCK).resolve(layer, results)
                pullConcreteFromTable(Layer.BUILDING_BLOCK).resolve(Layer.LEAVES, results)
            }
        }, 1.0)
    }

    private fun readWeights(fileName: String): RandomTable<Entry> {
        val table = RandomTable<Entry>()
        for ((index, line) in Riftwake.getFile(fileName).readLines().withIndex()) {
            val (entryType, other, stateKey, stateValue, weight) = entryFormat.matchEntire(line.trim())?.destructured ?:
                throw ConfigurationException("weight file $fileName incorrectly formatted on line ${index + 1}: $line")

            val entryWeight = try { weight.toDouble() } catch (_: NumberFormatException) {
                throw ConfigurationException(
                    "weight file $fileName has invalid weight '$weight' on line ${index + 1}: '$line'")
            }

            when (entryType) {
                "COPY" -> {
                    val copyFromLayer = try { Layer.valueOf(other) } catch (_: IllegalArgumentException) {
                        throw ConfigurationException(
                            "weight file $fileName copies unknown layer '$other' on line ${index + 1}: '$line'")
                    }
                    table.add(CopyEntry(copyFromLayer), entryWeight)
                }
                "COPYNEW" -> {
                    val pullFromLayer = try { Layer.valueOf(other) } catch (_: IllegalArgumentException) {
                        throw ConfigurationException(
                            "weight file $fileName pulls from unknown layer '$other' on line ${index + 1}: '$line'")
                    }
                    table.add(PullEntry(pullFromLayer), entryWeight)
                }
                "COPYNEWLEAVES" -> {
                    val pullFromLayer = try { Layer.valueOf(other) } catch (_: IllegalArgumentException) {
                        throw ConfigurationException(
                            "weight file $fileName pulls leaves from unknown layer '$other' on line ${index + 1}: '$line'")
                    }
                    table.add(PullLeavesEntry(pullFromLayer), entryWeight)
                }
                "RANDOMCOLOR" -> {
                    for (color in colors) {
                        val blockType = BlockTypes.get(color.lowercase() + "_" + other.lowercase()) ?:
                            throw ConfigurationException(
                                "weight file $fileName has unknown block type '$other' on line ${index + 1}: '$line'")

                        if (stateKey.isEmpty())
                            table.add(BlockEntry(blockType.defaultState), entryWeight / colors.size)
                        else {
                            val property = blockType.getProperty<Any>(stateKey)
                            val blockState = blockType.defaultState.with(property, property.getValueFor(stateValue))
                            table.add(BlockEntry(blockState), entryWeight / colors.size)
                        }
                    }
                }
                "DEFAULT" -> table.add(DefaultLeavesEntry(), entryWeight)
                else -> {
                    val blockType = BlockTypes.get(entryType.lowercase()) ?:
                        throw ConfigurationException(
                            "weight file $fileName has unknown block type '$entryType' on line ${index + 1}: '$line'")

                    if (other.isEmpty()) {
                        val blockState = if (stateKey.isEmpty())
                            blockType.defaultState
                        else {
                            val property = blockType.getProperty<Any>(stateKey)
                            blockType.defaultState.with(property, property.getValueFor(stateValue))
                        }
                        table.add(BlockEntry(blockState), entryWeight)
                    }
                    else {
                        val leavesType = BlockTypes.get(other.lowercase()) ?:
                            throw ConfigurationException(
                                "weight file $fileName has unknown leaves type '$other' on line ${index + 1}: '$line'")

                        val leavesState = if (stateKey.isEmpty())
                            leavesType.defaultState
                        else {
                            val property = leavesType.getProperty<Any>(stateKey)
                            leavesType.defaultState.with(property, property.getValueFor(stateValue))
                        }

                        table.add(WoodEntry(blockType.defaultState, leavesState), entryWeight)
                    }
                }
            }
        }
        return table
    }

    private fun getTable(layer: Layer) = tables[layer.ordinal]
    private fun pullConcreteFromTable(layer: Layer): ConcreteEntry {
        var pull = getTable(layer).pull()
        while (pull !is ConcreteEntry)
            pull = getTable(layer).pull()
        return pull
    }
    private fun pullWoodFromTable(layer: Layer): WoodEntry {
        var pull = getTable(layer).pull()
        while (pull !is WoodEntry)
            pull = getTable(layer).pull()
        return pull
    }

    fun add(layer: Layer, weight: Double, entry: Entry) {
        getTable(layer).add(entry, weight)
    }

    fun add(layer: Layer, weight: Double, block: BlockState) {
        getTable(layer).add(BlockEntry(block), weight)
    }

    fun add(layer: Layer, weight: Double, block: BlockType?) {
        getTable(layer).add(BlockEntry(block!!.defaultState), weight)
    }

    fun add(layer: Layer, weight: Double, other: Layer, new: Boolean = false) {
        getTable(layer).add(if (new) PullEntry(other) else CopyEntry(other), weight)
    }

    fun pull(): Map<Layer, BlockState> {
        val results = EnumMap<Layer, BlockState>(Layer::class.java)
        for (layer in Layer.entries)
            if (layer !in results)
                getTable(layer).pull().resolve(layer, results)
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