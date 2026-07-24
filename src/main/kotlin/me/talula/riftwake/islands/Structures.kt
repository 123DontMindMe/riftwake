package me.talula.riftwake.islands

import com.sk89q.worldedit.extent.clipboard.Clipboard
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats
import com.sk89q.worldedit.function.mask.BlockTypeMask
import com.sk89q.worldedit.function.operation.Operations
import com.sk89q.worldedit.math.BlockVector3
import com.sk89q.worldedit.regions.CuboidRegion
import com.sk89q.worldedit.session.ClipboardHolder
import com.sk89q.worldedit.world.block.BlockTypes
import io.papermc.paper.command.brigadier.Commands
import me.talula.riftwake.Riftwake
import me.talula.riftwake.constants.IntConstant
import me.talula.riftwake.constants.NumConstant
import me.talula.riftwake.constants.TimeConstant
import me.talula.riftwake.utils.ConfigurationException
import me.talula.riftwake.utils.LayerTable
import me.talula.riftwake.utils.RandomTable
import me.talula.riftwake.utils.edit
import me.talula.riftwake.utils.green
import me.talula.riftwake.utils.plus
import me.talula.riftwake.utils.red
import me.talula.riftwake.utils.riftwake
import me.talula.riftwake.utils.toLocation
import me.talula.riftwake.utils.toVector
import me.talula.riftwake.utils.yellow
import org.bukkit.Bukkit
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
import java.util.Random
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

    fun init() {
        Riftwake.registerCommand(Commands.literal("placestructures")
            .executes { ctx ->
                val player = ctx.source.sender.riftwake ?: return@executes 0

                val chestTable = RandomTable<LootTable>()
                for ((index, line) in Riftwake.getFile("structures/weights/chest-weights.txt").readLines().withIndex()) {
                    val (type, weight) = line.split(" ")
                    val lootTable = try { LootTables.valueOf(type) } catch (_: IllegalArgumentException) {
                        player.sendMessage(
                            "No entity type named '$type' on line ${index + 1} of spawner weights file: '$line'".red)
                        return@executes 0
                    }
                    chestTable.add(lootTable.lootTable, weight.toDouble())
                }

                val spawnerTable = RandomTable<EntityType>()
                for ((index, line) in Riftwake.getFile("structures/weights/spawner-weights.txt").readLines().withIndex()) {
                    val (type, weight) = line.split(" ")
                    val entityType = try { EntityType.valueOf(type) } catch (_: IllegalArgumentException) {
                        player.sendMessage(
                            "No entity type named '$type' on line ${index + 1} of spawner weights file: '$line'".red)
                        return@executes 0
                    }
                    spawnerTable.add(entityType, weight.toDouble())
                }

                val layerTable = LayerTable()
                val structures = try {
                    Array(11) { readStructure("structures/islandtemplate${it + 1}.schem") }
                } catch (error: ConfigurationException) {
                    player.sendMessage(error.message.red)
                    return@executes 0
                }

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
                        val to = BlockVector3(centerX + structure.widthX / 2, y, centerZ + structure.widthZ / 2)

                        player.sendMessage(
                            "Creating structure at chunk ($gridChunkX, $gridChunkZ), coords ($centerX, $y, $centerZ)...".yellow)
                        player.sendMessage(
                            "dimensions: ${structure.widthX} x ${structure.height} x ${structure.widthZ}")
                        player.sendMessage("origin: ${structure.clipboard.origin}")
                        player.sendMessage("to: $to")

                        val layers = layerTable.pull()

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

                        for (spawner in structure.spawners) {
                            val state = Riftwake.world.getBlockState(spawner.plus(boundEnd.toVector()))
                            if (state !is CreatureSpawner) {
                                player.sendMessage("Spawner not found at (${spawner.x()}, ${spawner.y()}, ${spawner.z()})".red)
                                continue
                            }
                            val entityType = spawnerTable.pull()
                            val entitySnapshot = Bukkit.getEntityFactory().createEntitySnapshot("{id:\"${entityType.key}\"}")
                            state.spawnedType = entityType
                            state.minSpawnDelay = minDelay()
                            state.maxSpawnDelay = maxDelay()
                            state.spawnCount = spawnCount()
                            state.maxNearbyEntities = maxNearby()
                            state.delay = initialDelay()
                            state.setSpawnedEntity(SpawnerEntry(entitySnapshot, 1, SpawnRule(0, 15, 0, 15)))
                            state.update()
                        }

                        for (chest in structure.chests) {
                            val state = Riftwake.world.getBlockState(chest.plus(boundEnd.toVector()))
                            if (state !is Chest) {
                                player.sendMessage("Chest not found at (${chest.x()}, ${chest.y()}, ${chest.z()})".red)
                                continue
                            }
                            chestTable.pull().fillInventory(
                                state.inventory,
                                Random(),
                                LootContext.Builder(chest.toLocation(Riftwake.world)).build()
                            )
                        }

                        for (chunk in intersectingChunks)
                            Riftwake.world.unloadChunk(chunk)

                        player.sendMessage(
                            "Created structure at chunk ($actualChunkX, $actualChunkZ), coords ($centerX, $y, $centerZ)".green)
                    }

                player.sendMessage("Done :)".green)
                1
            }
        )
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
    }

    private fun readStructure(fileName: String): StructureInfo {
        val file = Riftwake.getFile(fileName)
        val format = ClipboardFormats.findByPath(file.toPath()) ?:
            throw ConfigurationException("Schematic file '$fileName' not found.")
        return StructureInfo(format.getReader(FileInputStream(file)).read())
    }
}