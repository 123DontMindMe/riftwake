package me.talula.riftwake.spawn

import io.papermc.paper.command.brigadier.Commands
import me.talula.riftwake.Riftwake
import me.talula.riftwake.constants.IntConstant
import me.talula.riftwake.constants.TimeConstant
import me.talula.riftwake.utils.EventListener
import me.talula.riftwake.utils.green
import me.talula.riftwake.utils.replySender
import me.talula.riftwake.utils.yellow
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.data.Levelled
import org.bukkit.event.EventHandler
import org.bukkit.event.block.*
import org.bukkit.event.player.PlayerBucketEmptyEvent
import org.bukkit.scheduler.BukkitTask

object MapClearRegistry: EventListener() {
    val radius = IntConstant("map-clear.radius")
    val interval = TimeConstant("map-clear.interval")

    private val blocksToClear = mutableSetOf<Block>()
    private var clearTask: BukkitTask

    init {
        clearTask = Riftwake.runTaskTimer(interval().toLong(), interval().toLong()) {
            for (block in blocksToClear)
                block.type = Material.AIR
        }

        Riftwake.registerCommand(Commands.literal("mapclear")
            .requires { it.sender.isOp }
            .replySender { sender ->
                clearTask.cancel()
                sender.sendMessage("Clearing map... (${blocksToClear.size} blocks)".yellow)

                for (block in blocksToClear)
                    block.type = Material.AIR
                blocksToClear.clear()

                clearTask = Riftwake.runTaskTimer(interval().toLong(), interval().toLong()) {
                    for (block in blocksToClear)
                        block.type = Material.AIR
                }
                "Map cleared.".green
            }
        )
    }

    override fun onDisable() {
        for (block in blocksToClear)
            block.type = Material.AIR
    }

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
        println("${event.block.x} ${event.block.y} ${event.block.z} is water")
        val before = event.changedBlockData
        if (before !is Levelled || before.level == 0)  // level of 0 means source
            return
        println("is not source")

        Riftwake.runTaskLater(6) {
            val after = event.block.blockData
            if (after !is Levelled || after.level != 0)
                return@runTaskLater
            println("becomes source")

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

    private fun isInMapClearRegion(location: Location): Boolean {
        return location.blockX in -radius()..radius() &&
                location.blockZ in -radius()..radius() &&
                !SpawnComponent.isInSpawn(location)
    }
}