package me.talula.riftwake.spawn

import me.talula.riftwake.Riftwake
import me.talula.riftwake.RiftwakePlayer
import me.talula.riftwake.constants.NumConstant
import me.talula.riftwake.constants.TimeConstant
import me.talula.riftwake.utils.playSound
import me.talula.riftwake.utils.plus
import me.talula.riftwake.utils.red
import me.talula.riftwake.utils.xzDistance2
import me.talula.riftwake.utils.yellow
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.block.BlockFace
import org.bukkit.util.Vector

class SpawnComponent(val player: RiftwakePlayer) {
    companion object {
        val spawnCenter = Location(Riftwake.world, 0.5, 100.0, 0.5)
        val spawnRadius = 63.0

        val launchHorizontalSpeed = NumConstant("launchers.horizontal-speed")
        val launchVerticalSpeed = NumConstant("launchers.vertical-speed")
        val launchDuration = TimeConstant("launchers.duration")

        val dropItemMessage =
            "You can't drop items in spawn. Use ".red() + "/trash".yellow() + " to dispose of items.".red()
    }

    val isInSpawn get() = player.location.xzDistance2(spawnCenter) < spawnRadius * spawnRadius
    fun isInSpawn(location: Location) = location.xzDistance2(spawnCenter) < spawnRadius * spawnRadius

    init {
        player.onBreakBlock += { event ->
            if (player.gameMode == GameMode.SURVIVAL && (isInSpawn || isInSpawn(event.block.location)))
                event.isCancelled = true
        }

        player.onPlaceBlock += { event ->
            if (player.gameMode == GameMode.SURVIVAL && (isInSpawn || isInSpawn(event.block.location)))
                event.isCancelled = true
        }

        player.onPlaceEntity += { event ->
            if (player.gameMode == GameMode.SURVIVAL && (isInSpawn || isInSpawn(event.entity.location)))
                event.isCancelled = true
        }

        player.onDamageEntity += {event ->
            if (player.gameMode == GameMode.SURVIVAL && (isInSpawn || isInSpawn(event.entity.location)))
                event.isCancelled = true
        }

        player.onReceiveDamage += { event ->
            if (player.gameMode == GameMode.SURVIVAL && (isInSpawn || isInSpawn(event.entity.location)))
                event.isCancelled = true
        }

        player.onRightClickBlock += { event, block ->
            if (player.gameMode == GameMode.SURVIVAL && (isInSpawn || isInSpawn(block.location)))
                event.isCancelled = true
        }

        player.onDropItem += { event ->
            if (player.gameMode == GameMode.SURVIVAL && isInSpawn) {
                event.isCancelled = true
                player.sendMessage(dropItemMessage)
            }
        }

        player.onPhysicalInteract += launch@{ event ->
            val block = event.clickedBlock ?: return@launch

            val direction = when (block.location.toVector()) {
                Vector(-1, 99, -20),
                Vector(0, 99, -20),
                Vector(+1, 99, -20),
                Vector(-1, 101, 48),
                Vector(0, 101, 48),
                Vector(+1, 101, 48) -> BlockFace.NORTH.direction

                Vector(-1, 99, 20),
                Vector(0, 99, 20),
                Vector(+1, 99, 20),
                Vector(-1, 101, -48),
                Vector(0, 101, -48),
                Vector(+1, 101, -48) -> BlockFace.SOUTH.direction

                Vector(20, 99, -1),
                Vector(20, 99, 0),
                Vector(20, 99, +1),
                Vector(-48, 101, -1),
                Vector(-48, 101, 0),
                Vector(-48, 101, +1) -> BlockFace.EAST.direction

                Vector(-20, 99, -1),
                Vector(-20, 99, 0),
                Vector(-20, 99, +1),
                Vector(48, 101, -1),
                Vector(48, 101, 0),
                Vector(48, 101, +1) -> BlockFace.WEST.direction

                else -> return@launch
            }

            player.playSound(Sound.ENTITY_BREEZE_JUMP, SoundCategory.BLOCKS, 0.8f, 0.5f)
            player.velocity = direction.plus(0.0, launchVerticalSpeed(), 0.0)
            var t = launchDuration()
            Riftwake.runTaskTimer(0, 1) { task ->
                if (!player.isConnected)
                    task.cancel()
                player.velocity = player.velocity.apply {
                    x = launchHorizontalSpeed() * direction.x
                    z = launchHorizontalSpeed() * direction.z
                }
                player.setFallDistance(-999f)
                if (--t == 0)
                    task.cancel()
            }
        }
    }
}