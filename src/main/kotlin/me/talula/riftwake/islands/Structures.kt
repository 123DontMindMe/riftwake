package me.talula.riftwake.islands

import com.sk89q.worldedit.extent.clipboard.Clipboard
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats
import com.sk89q.worldedit.function.mask.BlockTypeMask
import com.sk89q.worldedit.function.operation.Operations
import com.sk89q.worldedit.math.BlockVector3
import com.sk89q.worldedit.regions.CuboidRegion
import com.sk89q.worldedit.session.ClipboardHolder
import com.sk89q.worldedit.world.block.BlockState
import com.sk89q.worldedit.world.block.BlockTypes
import io.papermc.paper.command.brigadier.Commands
import me.talula.riftwake.Riftwake
import me.talula.riftwake.constants.IntConstant
import me.talula.riftwake.constants.NumConstant
import me.talula.riftwake.constants.TimeConstant
import me.talula.riftwake.utils.*
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Chest
import org.bukkit.block.CreatureSpawner
import org.bukkit.block.spawner.SpawnRule
import org.bukkit.block.spawner.SpawnerEntry
import org.bukkit.entity.EntityType
import org.bukkit.loot.LootContext
import org.bukkit.loot.LootTable
import org.bukkit.loot.LootTables
import org.bukkit.util.BoundingBox
import org.bukkit.util.Vector
import java.io.FileInputStream
import java.util.*
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.iterator

object Structures {
    val worldRadius = IntConstant("structures.world-radius")
    val generationChance = NumConstant("structures.generation-chance")
    val initialDelay = TimeConstant("structures.spawners.initial-delay")
    val minDelay = TimeConstant("structures.spawners.min-delay")
    val maxDelay = TimeConstant("structures.spawners.max-delay")
    val maxNearby = IntConstant("structures.spawners.max-nearby")
    val spawnCount = IntConstant("structures.spawners.count")

    fun readChestWeights(): RandomTable<LootTable>? {
        val chestTable = RandomTable<LootTable>()
        for ((index, line) in Riftwake.getFile("structures/weights/chest-weights.txt").readLines().withIndex()) {
            val (type, weight) = line.split(" ")
            val lootTable = try { LootTables.valueOf(type) } catch (_: IllegalArgumentException) {
                Riftwake.broadcastToOperators("No entity type named '$type' on line ${index + 1} of spawner weights file: '$line'".red)
                return null
            }
            chestTable.add(lootTable.lootTable, weight.toDouble())
        }
        return chestTable
    }

    fun readSpawnerWeights(): RandomTable<EntityType>? {
        val spawnerTable = RandomTable<EntityType>()
        for ((index, line) in Riftwake.getFile("structures/weights/spawner-weights.txt").readLines().withIndex()) {
            val (type, weight) = line.split(" ")
            val entityType = try { EntityType.valueOf(type) } catch (_: IllegalArgumentException) {
                Riftwake.broadcastToOperators("No entity type named '$type' on line ${index + 1} of spawner weights file: '$line'".red)
                return null
            }
            spawnerTable.add(entityType, weight.toDouble())
        }
        return spawnerTable
    }

    fun init() {
        Riftwake.registerCommand(Commands.literal("placestructures")
            .executes { ctx ->
                val player = ctx.source.sender.riftwake ?: return@executes 0

                val chestTable = readChestWeights() ?: return@executes 0
                val spawnerTable = readSpawnerWeights() ?: return@executes 0

                val layerTable = LayerTable()
                val structures = readStructures() ?: return@executes 0

                val worldRadiusInChunks = Math.floorDiv(Math.ceilDiv(worldRadius(), 16), 16) * 16
                val structurePool = mutableListOf(*structures)

                for (gridChunkX in -worldRadiusInChunks..worldRadiusInChunks step 16)
                    for (gridChunkZ in -worldRadiusInChunks..worldRadiusInChunks step 16) {
                        if (structurePool.isEmpty())
                            structurePool.addAll(structures)
                        val structure = structurePool.removeAt(structurePool.indices.random())

                        if (gridChunkX == 0 && gridChunkZ == 0) {
                            player.sendMessage("Skipped grid point (0, 0) too close to spawn")
                            continue
                        }
                        if (Math.random() > generationChance()) {
                            player.sendMessage("Skipped grid point ($gridChunkX, $gridChunkZ) from chance")
                            continue
                        }

                        val chunkXOffset = (Math.random() * 10).toInt() - 5
                        val chunkZOffset = (Math.random() * 10).toInt() - 5
                        val actualChunkX = gridChunkX + chunkXOffset
                        val actualChunkZ = gridChunkZ + chunkZOffset
                        val centerX = actualChunkX * 16
                        val centerZ = actualChunkZ * 16
                        val y = (Math.random() * 100).toInt() - 63 + structure.height

                        player.sendMessage(
                            "Creating structure at chunk ($gridChunkX, $gridChunkZ), coords ($centerX, $y, $centerZ)...".yellow)

                        placeStructure(structure, BlockVector3(centerX, y, centerZ), layerTable, chestTable, spawnerTable)

                        player.sendMessage(
                            "Created structure at chunk ($actualChunkX, $actualChunkZ), coords ($centerX, $y, $centerZ)".green)
                    }

                player.sendMessage("Done :)".green)
                1
            }
        )
    }

    fun placeStructure(
        structure: StructureInfo, center: BlockVector3,
        layerTable: LayerTable, chestTable: RandomTable<LootTable>, spawnerTable: RandomTable<EntityType>
    ): Pair<StructureInfo, BlockVector3> {
        val layers = layerTable.pull()
        val to = BlockVector3(center.x() + structure.widthX / 2, center.y() + structure.height / 2, center.z() + structure.widthZ / 2)

        val boundStart = to.add(1, 1, 1)
        val boundEnd = boundStart.subtract(structure.clipboard.dimensions)
        val intersectingChunks = Riftwake.world.getIntersectingChunks(BoundingBox.of(
            Vector(boundStart.x(), boundStart.y(), boundStart.z()),
            Vector(boundEnd.x(), boundEnd.y(), boundEnd.z()))
        )

        for (chunk in intersectingChunks)
            Riftwake.world.loadChunk(chunk)

        Riftwake.world.edit { session ->
            Operations.complete(ClipboardHolder(structure.clipboard)
                .createPaste(session)
                // origin is 1 above so structure gets pasted 1 below, so shift it up by 1
                .to(to.add(0, 1, 0))
                .ignoreAirBlocks(true)
                .build())
        }
        Riftwake.world.setType(to.x(), to.y(), to.z(), Material.AIR)
        Riftwake.world.setType(boundEnd.x(), boundEnd.y(), boundEnd.z(), Material.AIR)

        for ((layer, block) in layers)
            Riftwake.world.edit { session ->
                session.replaceBlocks(
                    CuboidRegion(to, to.subtract(structure.clipboard.dimensions)),
                    BlockTypeMask(session, layer.replaceBlock),
                    block
                )
            }
        Riftwake.world.edit { session ->
            session.replaceBlocks(
                CuboidRegion(to, to.subtract(structure.clipboard.dimensions)),
                BlockTypeMask(session, BlockTypes.YELLOW_GLAZED_TERRACOTTA),
                BlockTypes.FARMLAND!!.defaultState
            )
        }

        for (spawner in structure.spawners)
            placeSpawner(spawner.plus(boundEnd.toVector()), spawnerTable.pull())

        for (chest in structure.chests)
            placeChest(chest.plus(boundEnd.toVector()), chestTable.pull())

        for (chunk in intersectingChunks)
            Riftwake.world.unloadChunk(chunk)

        return structure to to
    }

    fun placeSpawner(block: Location, type: EntityType) {
        val state = Riftwake.world.getBlockState(block)
        if (state !is CreatureSpawner) {
            Riftwake.broadcastToOperators("Spawner not found at (${block.x()}, ${block.y()}, ${block.z()})".red)
            return
        }
        val entitySnapshot = Bukkit.getEntityFactory().createEntitySnapshot("{id:\"${type.key}\"}")
        state.spawnedType = type
        state.minSpawnDelay = minDelay()
        state.maxSpawnDelay = maxDelay()
        state.spawnCount = spawnCount()
        state.maxNearbyEntities = maxNearby()
        state.delay = initialDelay()
        state.setSpawnedEntity(SpawnerEntry(entitySnapshot, 1, SpawnRule(0, 15, 0, 15)))
        state.update()
    }

    fun placeChest(block: Location, loot: LootTable) {
        val state = Riftwake.world.getBlockState(block)
        if (state !is Chest) {
            Riftwake.broadcastToOperators("Chest not found at (${block.x()}, ${block.y()}, ${block.z()})".red)
            return
        }
        loot.fillInventory(state.inventory, Random(), LootContext.Builder(block).build())
    }

    class StructureInfo(val clipboard: Clipboard) {
        val spawners = clipboard.region
            .filter { clipboard.getBlock(it).blockType == BlockTypes.SPAWNER }
            .map { it.subtract(clipboard.region.minimumPoint).toLocation(Riftwake.world) }
        val chests = clipboard.region
            .filter { clipboard.getBlock(it).blockType == BlockTypes.CHEST }
            .map { it.subtract(clipboard.region.minimumPoint).toLocation(Riftwake.world) }
        val widthX = clipboard.dimensions.x()
        val height = clipboard.dimensions.y()
        val widthZ = clipboard.dimensions.z()

        fun blockAt(x: Int, y: Int, z: Int): BlockState =
            if (CuboidRegion(clipboard.dimensions, BlockVector3.ZERO).contains(BlockVector3(x, y, z)))
                clipboard.getBlock(clipboard.region.minimumPoint.add(x, y, z))
            else
                BlockTypes.AIR!!.defaultState
    }

    fun readStructure(fileName: String): StructureInfo {
        val file = Riftwake.getFile(fileName)
        val format = ClipboardFormats.findByPath(file.toPath()) ?:
            throw ConfigurationException("Schematic file '$fileName' not found.")
        return StructureInfo(format.getReader(FileInputStream(file)).read())
    }

    fun readStructures(): Array<StructureInfo>? {
        try {
            return Array(11) { readStructure("structures/islandtemplate${it + 1}.schem") }
        } catch (error: ConfigurationException) {
            Riftwake.broadcastToOperators(error.message.red)
            return null
        }
    }
}