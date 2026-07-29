package me.talula.riftwake.theblock

import me.talula.riftwake.utils.getData
import me.talula.riftwake.utils.setData
import org.bukkit.Chunk
import org.bukkit.block.Block
import org.bukkit.persistence.PersistentDataType

object PlayerPlacedRegistry {
    private val map = mutableMapOf<Chunk, MutableSet<Block>>()

    fun registerChunk(chunk: Chunk) {
        if (chunk in map)
            throw IllegalArgumentException("Chunk already registered")

        val blocks = mutableSetOf<Block>()
        val array = chunk.getData("player-placed-blocks", PersistentDataType.INTEGER_ARRAY)
        if (array != null)
            for (i in array.indices step 3)
                blocks += chunk.getBlock(array[i], array[i + 1], array[i + 2])

        map[chunk] = blocks
    }

    fun unregisterChunk(chunk: Chunk) {
        println("unregisterChunk")
        val blocks = map.remove(chunk) ?: throw IllegalArgumentException("Chunk wasn't registered")
        if (blocks.isEmpty())
            return
        val array = IntArray(blocks.size * 3)
        for ((index, block) in blocks.withIndex()) {
            array[index] = block.x - (chunk.x * 16)
            array[index + 1] = block.y
            array[index + 2] = block.z - (chunk.z * 16)
        }
        chunk.setData("player-placed-blocks", PersistentDataType.INTEGER_ARRAY, array)
    }

    fun isPlayerPlaced(block: Block): Boolean {
        val coords = map[block.chunk] ?: throw IllegalArgumentException("Block is in non-registered chunk")
        return block in coords
    }

    fun registerBlock(block: Block): Boolean {
        val coords = map[block.chunk] ?: throw IllegalArgumentException("Block is in non-registered chunk")
        return coords.add(block)
    }

    fun unregisterBlock(block: Block): Boolean {
        val coords = map[block.chunk] ?: throw IllegalArgumentException("Block is in non-registered chunk")
        return coords.remove(block)
    }

    fun getPlayerPlacedBlocks(chunk: Chunk): Set<Block> {
        return map[chunk] ?: throw IllegalArgumentException("Chunk wasn't registered")
    }

    fun unregisterAll() {
        for ((chunk, blocks) in map) {
            if (blocks.isEmpty())
                continue
            val array = IntArray(blocks.size * 3)
            for ((index, block) in blocks.withIndex()) {
                array[index] = block.x - (chunk.x * 16)
                array[index + 1] = block.y
                array[index + 2] = block.z - (chunk.z * 16)
            }
            chunk.setData("player-placed-blocks", PersistentDataType.INTEGER_ARRAY, array)
            println("saved " + array.contentToString())
        }
        map.clear()
    }
}