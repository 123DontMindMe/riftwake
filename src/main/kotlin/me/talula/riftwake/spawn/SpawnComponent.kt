package me.talula.riftwake.spawn

import me.talula.riftwake.Riftwake
import me.talula.riftwake.RiftwakePlayer
import me.talula.riftwake.constants.NumConstant
import me.talula.riftwake.constants.TimeConstant
import me.talula.riftwake.utils.playSound
import me.talula.riftwake.utils.plus
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.block.BlockFace
import org.bukkit.util.Vector

class SpawnComponent(val player: RiftwakePlayer) {
    companion object {
        val launchHorizontalSpeed = NumConstant("launchers.horizontal-speed")
        val launchVerticalSpeed = NumConstant("launchers.vertical-speed")
        val launchDuration = TimeConstant("launchers.duration")
    }

    init {
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

            player.playSound(Sound.ENTITY_BREEZE_JUMP, SoundCategory.BLOCKS, 1f, 0.5f)
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