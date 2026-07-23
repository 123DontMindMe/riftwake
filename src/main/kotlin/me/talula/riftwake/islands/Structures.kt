package me.talula.riftwake.islands

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
import me.talula.riftwake.utils.LayerTable
import me.talula.riftwake.utils.craft
import me.talula.riftwake.utils.edit
import me.talula.riftwake.utils.green
import me.talula.riftwake.utils.red
import me.talula.riftwake.utils.riftwake
import me.talula.riftwake.utils.yellow
import org.bukkit.Material
import org.bukkit.util.BoundingBox
import org.bukkit.util.Vector
import java.io.FileInputStream
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.iterator

object Structures {
    val worldRadius = IntConstant("world-radius")

    fun init() {
        Riftwake.registerCommand(Commands.literal("placestructures")
            .executes { ctx ->
                val player = ctx.source.sender.riftwake ?: return@executes 0
                val layerTable = LayerTable()

                val file = Riftwake.getFile("structures/islandtemplate3.schem")
                val format = ClipboardFormats.findByPath(file.toPath())
                if (format == null) {
                    player.sendMessage("Schematic file not found.".red)
                    return@executes 0
                }
                val reader = format.getReader(FileInputStream(file))

                val worldRadiusInChunks = Math.floorDiv(Math.ceilDiv(worldRadius(), 16), 16) * 16

                reader.use { reader ->
                    val clipboard = reader.read()
                    val halfWidthX = clipboard.dimensions.x() / 2
                    val halfWidthZ = clipboard.dimensions.z() / 2

                    var gridChunkX = -worldRadiusInChunks
                    var gridChunkZ = -worldRadiusInChunks
                    var i = 0
                    var created = 0

                    fun createIsland(): Boolean {
                        i++
                        if (gridChunkX in -1..1 || gridChunkZ in -1..1 || Math.random() > 0.9) {
                            gridChunkZ += 16
                            if (gridChunkZ > worldRadiusInChunks) {
                                gridChunkZ = 0
                                gridChunkX += 16
                            }
                            return false
                        }

                        val chunkXOffset = (Math.random() * 10).toInt() - 5
                        val chunkZOffset = (Math.random() * 10).toInt() - 5
                        val actualChunkX = gridChunkX + chunkXOffset
                        val actualChunkZ = gridChunkZ + chunkZOffset
                        val centerX = actualChunkX * 16
                        val centerZ = actualChunkZ * 16
                        val y = (Math.random() * 100).toInt() - 63 + clipboard.dimensions.y()
                        val to = BlockVector3(centerX + halfWidthX, y, centerZ + halfWidthZ)

                        player.craft.sendActionBar("$i grid points, $created created, creating at chunk ($gridChunkX, $gridChunkZ), coords ($centerX, $y, $centerZ)...".yellow)

                        val layers = layerTable.pull()

                        val boundStart = to.add(1, 1, 1)
                        val boundEnd = boundStart.subtract(clipboard.dimensions)
                        val chunks = Riftwake.world.getIntersectingChunks(BoundingBox.of(
                            Vector(boundStart.x(), boundStart.y(), boundStart.z()),
                            Vector(boundEnd.x(), boundEnd.y(), boundEnd.z()))
                        )

                        for (chunk in chunks)
                            Riftwake.world.loadChunk(chunk)

                        Riftwake.world.edit { session ->
                            Operations.complete(ClipboardHolder(clipboard)
                                .createPaste(session)
                                .to(to)
                                .ignoreAirBlocks(true)
                                .build())
                        }
                        Riftwake.world.setType(to.x(), to.y(), to.z(), Material.AIR)
                        Riftwake.world.setType(boundEnd.x(), boundEnd.y(), boundEnd.z(), Material.AIR)

                        for ((layer, block) in layers)
                            Riftwake.world.edit { session ->
                                session.replaceBlocks(
                                    CuboidRegion(to, to.subtract(clipboard.dimensions)),
                                    BlockTypeMask(session, layer.replaceBlock),
                                    block
                                )
                            }
                        Riftwake.world.edit { session ->
                            session.replaceBlocks(
                                CuboidRegion(to, to.subtract(clipboard.dimensions)),
                                BlockTypeMask(session, BlockTypes.YELLOW_GLAZED_TERRACOTTA),
                                BlockTypes.FARMLAND!!.defaultState
                            )
                        }

                        for (chunk in chunks)
                            Riftwake.world.unloadChunk(chunk)

                        created++
                        println("$i grid points, $created created, at chunk ($actualChunkX, $actualChunkZ), coords ($centerX, $y, $centerZ)")
                        player.craft.sendActionBar("$i grid points, $created created, at chunk ($actualChunkX, $actualChunkZ), coords ($centerX, $y, $centerZ)".green)
                        gridChunkZ += 16
                        if (gridChunkZ > worldRadiusInChunks) {
                            gridChunkZ = -worldRadiusInChunks
                            gridChunkX += 16
                        }
                        return true
                    }

                    fun step() {
                        while (gridChunkX <= worldRadiusInChunks && !createIsland()) {}
                        if (gridChunkX > worldRadiusInChunks) {
                            player.sendMessage("DONE: $i grid points, $created created".green)
                            return
                        }
                        Riftwake.runTaskLater(5) { step() }
                    }

                    step()
                }
                1
            }
        )
    }
}