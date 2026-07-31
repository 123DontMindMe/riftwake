package me.talula.riftwake.spawn

import me.talula.riftwake.Riftwake
import me.talula.riftwake.RiftwakePlayer
import me.talula.riftwake.constants.NumConstant
import me.talula.riftwake.constants.TimeConstant
import me.talula.riftwake.utils.*
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.block.BlockFace
import org.bukkit.util.Vector
import kotlin.math.absoluteValue

class SpawnComponent(val player: RiftwakePlayer) {
    companion object {
        val spawnCenter = Location(Riftwake.world, 0.5, 100.0, 0.5)
        val spawnRadius = 63.0

        val launchHorizontalSpeed = NumConstant("launchers.horizontal-speed")
        val launchVerticalSpeed = NumConstant("launchers.vertical-speed")
        val launchDuration = TimeConstant("launchers.duration")

        val dropItemMessage =
            "You can't drop items in spawn. Use ".red + "/trash".yellow + " to dispose of items.".red

        fun isInSpawn(location: Location) = location.xzDistance2(spawnCenter) < spawnRadius * spawnRadius
    }

    var isInSpawn = player.location.xzDistance2(spawnCenter) < spawnRadius * spawnRadius
        private set

    init {
        player.onMove += { event ->
            isInSpawn = isInSpawn(event.to)
            if (isInSpawn)
                Riftwake.server.scoreboardManager.mainScoreboard.getTeam("in-spawn")?.addPlayer(player.craft)
            else
                Riftwake.server.scoreboardManager.mainScoreboard.getTeam("in-spawn")?.removePlayer(player.craft)

            val origin = Vector(15.5, 0.0, 7.5)
            val relativePos = event.to.toVector().subtract(origin)
            relativePos.rotateAroundY(Math.PI / 4)
            if (relativePos.x.absoluteValue < 1 && relativePos.z > 0 && relativePos.z < 11.3 && relativePos.y > 98 && relativePos.y < 102) {
                val cooldown = player.block.randomTeleportCooldownRemaining
                if (cooldown > 0) {
                    if (player.sendMessageOnCooldown("rtp-portal", 40, "You must wait ${cooldown.toTimeString()} to random teleport again.".red)) {
                        player.playSound(Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1f, 1f)
                        player.velocity = event.to.toVector().subtract(Vector(11.5, 98.0, 11.5)).normalize()
                    }
                } else {
                    player.teleport(Location(Riftwake.world, 0.0, 1000.0, 0.0))
                    player.block.randomTeleportNow()
                    player.playSound(Sound.BLOCK_PORTAL_TRAVEL, SoundCategory.MASTER, 0.8f, 2f)
                }
            }
        }

        player.onTeleport += { event ->
            isInSpawn = isInSpawn(event.to)
            if (isInSpawn)
                Riftwake.server.scoreboardManager.mainScoreboard.getTeam("in-spawn")?.addPlayer(player.craft)
            else
                Riftwake.server.scoreboardManager.mainScoreboard.getTeam("in-spawn")?.removePlayer(player.craft)
        }

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