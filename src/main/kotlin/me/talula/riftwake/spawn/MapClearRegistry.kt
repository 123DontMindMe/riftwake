package me.talula.riftwake.spawn

import com.sk89q.worldedit.math.BlockVector3
import com.sk89q.worldedit.regions.CuboidRegion
import com.sk89q.worldedit.world.block.BlockTypes
import io.papermc.paper.command.brigadier.Commands
import me.talula.riftwake.Riftwake
import me.talula.riftwake.constants.IntConstant
import me.talula.riftwake.constants.TimeConstant
import me.talula.riftwake.islands.Structures
import me.talula.riftwake.utils.Command
import me.talula.riftwake.utils.EventListener
import me.talula.riftwake.utils.LayerTable
import me.talula.riftwake.utils.edit
import me.talula.riftwake.utils.green
import me.talula.riftwake.utils.plus
import me.talula.riftwake.utils.replySender
import me.talula.riftwake.utils.toVector
import me.talula.riftwake.utils.yellow
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.data.Levelled
import org.bukkit.event.EventHandler
import org.bukkit.event.block.*
import org.bukkit.event.player.PlayerBucketEmptyEvent

object MapClearRegistry: EventListener() {
    val radius = IntConstant("map-clear.radius")
    val interval = TimeConstant("map-clear.interval")

    val blocksToClear = mutableSetOf<Block>()
    private var clearTask = createClearTask()
    private val previousStructures = mutableListOf<Structures.StructureInfo>()

    private val islandLocations = arrayOf(
        BlockVector3(160, 50, 160),
        BlockVector3(160, 50, -160),
        BlockVector3(-160, 50, 160),
        BlockVector3(-160, 50, -160),
    )

    init {
        Riftwake.registerCommand(Commands.literal("mapclear")
            .requires { it.sender.isOp }
            .replySender { sender ->
                sender.sendMessage("Clearing map... (${blocksToClear.size} blocks)".yellow)
                clearMap()
                "Map cleared.".green
            }
        )

        Riftwake.registerCommand(Commands.literal("clear-islands")
            .requires { it.sender.isOp }
            .replySender { sender ->
                val structures = Structures.readStructures() ?: Command.fail()
                val widthX = structures.maxOf { it.widthX }
                val height = structures.maxOf { it.height }
                val widthZ = structures.maxOf { it.widthZ }

                var done = 0
                for (center in islandLocations) {
                    var y = 0
                    Riftwake.runTaskTimer(0, 1) { task ->
                        val min = center.add(-widthX / 2, y, -widthZ / 2)
                        val max = center.add(widthX / 2, y, widthZ / 2)
                        Riftwake.world.edit { it.setBlocks(CuboidRegion(min, max), BlockTypes.AIR!!.defaultState) }
                        y++
                        if (y >= height) {
                            task.cancel()
                            done++
                            if (done == 4)
                                sender.sendMessage("Islands cleared".green)
                        }
                    }
                }
                "Clearing islands...".yellow
            }
        )
    }

    override fun onDisable() {
        clearMap()
    }

    fun clearMap() {
        for (block in blocksToClear)
            block.type = Material.AIR

        if (!Riftwake.instance.isEnabled)
            return

        blocksToClear.clear()
        clearTask.cancel()
        clearTask = createClearTask()

        Thread {
            val chestTable = Structures.readChestWeights() ?: return@Thread
            val spawnerTable = Structures.readSpawnerWeights() ?: return@Thread
            val layerTable = LayerTable()

            val structurePool = Structures.readStructures() ?: return@Thread
            val structures = Array(4) { structurePool.random() }
            val palletes = Array(4) { layerTable.pull() }
            val maxStructureY = if (previousStructures.isEmpty())
                structures.maxOf { it.height }
            else
                structures.maxOf { it.height }.coerceAtLeast(previousStructures.maxOf { it.height })
            var height = 0

            Riftwake.runTaskTimer(0, 1) { task ->
                for (i in 0..<4) {
                    val structure = structures[i]
                    val widthX: Int
                    val widthZ: Int
                    if (previousStructures.isEmpty()) {
                        widthX = structure.widthX
                        widthZ = structure.widthZ
                    } else {
                        val previousStructure = previousStructures[i]
                        widthX = structure.widthX.coerceAtLeast(previousStructure.widthX)
                        widthZ = structure.widthZ.coerceAtLeast(previousStructure.widthZ)
                    }
                    val to = islandLocations[i]
                    val pallete = palletes[i]
                    for (x in 0..<widthX) {
                        for (z in 0..<widthZ) {
                            val localX = x - widthX / 2
                            val localZ = z - widthZ / 2
                            val worldX = to.x() + localX
                            val worldY = to.y() + height
                            val worldZ = to.z() + localZ
                            val structureX = localX + structure.widthX / 2
                            val structureZ = localZ + structure.widthZ / 2
                            if (structureX == 0 && structureZ == 0 && height == 0) {
                                Riftwake.world.setType(worldX, worldY, worldZ, Material.AIR)
                                continue
                            }
                            if (structureX == structure.widthX - 1 && structureZ == structure.widthZ - 1 && height == structure.height - 1) {
                                Riftwake.world.setType(worldX, worldY, worldZ, Material.AIR)
                                continue
                            }
                            val originalBlock = structure.blockAt(structureX, height, structureZ)
                            if (originalBlock.blockType == BlockTypes.YELLOW_GLAZED_TERRACOTTA) {
                                Riftwake.world.setType(worldX, worldY, worldZ, Material.FARMLAND)
                                continue
                            }
                            val layer = layerTable.replacementToLayer[originalBlock.blockType]
                            if (layer == null) {
                                Riftwake.world.edit { it.setBlock(BlockVector3(worldX, worldY, worldZ), originalBlock) }
                                continue
                            }
                            val replacement = pallete[layer]
                            Riftwake.world.edit { it.setBlock(BlockVector3(worldX, worldY, worldZ), replacement) }
                        }
                    }
                }
                if (height <= maxStructureY)
                    height++
                else {
                    task.cancel()
                    previousStructures.clear()
                    previousStructures.addAll(structures)
                    for ((structure, to) in structures zip islandLocations) {
                        for (chest in structure.chests) {
                            val block = chest
                                .plus(to.toVector())
                                .plus(-structure.widthX / 2, 0, -structure.widthZ / 2)
                            Structures.placeChest(block, chestTable.pull())
                        }
                        for (spawner in structure.spawners) {
                            val block = spawner
                                .plus(to.toVector())
                                .plus(-structure.widthX / 2, 0, -structure.widthZ / 2)
                            Structures.placeSpawner(block, spawnerTable.pull())
                        }
                    }
                }
            }
        }.start()
    }

    private fun createClearTask() = Riftwake.runTaskLater(interval().toLong()) { clearMap() }

    @EventHandler(ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        if (event.player.gameMode == GameMode.CREATIVE)
            return
        if (isInMapClearRegion(event.block.location))
            blocksToClear += event.block
    }

    @EventHandler(ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        if (event.player.gameMode == GameMode.CREATIVE)
            return
        if (isInMapClearRegion(event.block.location))
            blocksToClear -= event.block
    }

    @EventHandler(ignoreCancelled = true)
    fun onBucketEmpty(event: PlayerBucketEmptyEvent) {
        if (event.player.gameMode == GameMode.CREATIVE)
            return
        if (isInMapClearRegion(event.block.location))
            blocksToClear += event.block
    }

    @EventHandler(ignoreCancelled = true)
    fun onBlockPhysics(event: BlockPhysicsEvent) {
        if (event.changedType != Material.WATER && event.changedType != Material.LAVA)
            return
        val before = event.changedBlockData
        if (before !is Levelled || before.level == 0)  // level of 0 means source
            return

        Riftwake.runTaskLater(6) {
            val after = event.block.blockData
            if (after !is Levelled || after.level != 0)
                return@runTaskLater

            if (isInMapClearRegion(event.block.location))
                blocksToClear += event.block
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onPistonExtend(event: BlockPistonExtendEvent) {
        for (block in event.blocks) {
            if (block !in blocksToClear) {
                event.isCancelled = true
                return
            }
            val newBlock = block.getRelative(event.direction)
            if (isInMapClearRegion(newBlock.location))
                blocksToClear += newBlock
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onPistonRetract(event: BlockPistonRetractEvent) {
        for (block in event.blocks) {
            if (block !in blocksToClear) {
                event.isCancelled = true
                return
            }
            val newBlock = block.getRelative(event.direction, -1)
            if (isInMapClearRegion(newBlock.location))
                blocksToClear += newBlock
        }
    }

    fun isInMapClearRegion(location: Location): Boolean {
        return location.blockX in -radius()..radius() &&
                location.blockZ in -radius()..radius() &&
                !SpawnComponent.isInSpawn(location)
    }
}