package me.talula.riftwake.theblock

import me.talula.riftwake.Riftwake
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
                try {
                    blocks += chunk.getBlock(array[i], array[i + 1], array[i + 2])
                } catch (_: IllegalArgumentException) {
                    Riftwake.logger.error(
                        "Stored player-placed block in chunk (${chunk.x}, ${chunk.z}) " +
                        "has invalid block coords (${array[i]}, ${array[i + 1]}, ${array[i + 2]})")
                }

        map[chunk] = blocks
    }

    fun unregisterChunk(chunk: Chunk) {
        val blocks = map.remove(chunk) ?: throw IllegalArgumentException("Chunk wasn't registered")
        if (blocks.isEmpty())
            return
        val array = IntArray(blocks.size * 3)
        var i = 0
        for (block in blocks) {
            // don't bother saving "player-placed" blocks that are actually just empty since
            // they're just false positives (which is fine, it's too hard to keep track of all of them)
            if (block.type.isAir)
                continue
            array[i * 3] = block.x - (chunk.x * 16)
            array[i * 3 + 1] = block.y
            array[i * 3 + 2] = block.z - (chunk.z * 16)
            i++
        }
        chunk.setData("player-placed-blocks", PersistentDataType.INTEGER_ARRAY, array.sliceArray(0 until i * 3))
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
            var i = 0
            for (block in blocks) {
                if (block.type.isAir)
                    continue
                array[i * 3] = block.x - (chunk.x * 16)
                array[i * 3 + 1] = block.y
                array[i * 3 + 2] = block.z - (chunk.z * 16)
                i++
            }
            val saved = array.sliceArray(0 until i * 3)
            chunk.setData("player-placed-blocks", PersistentDataType.INTEGER_ARRAY, saved)
            println("saved " + saved.contentToString())
        }
        map.clear()
    }
}