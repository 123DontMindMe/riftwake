package me.talula.riftwake.items

import me.talula.riftwake.Riftwake.Companion.runTaskTimer
import me.talula.riftwake.Riftwake.Companion.world
import me.talula.riftwake.RiftwakePlayer
import me.talula.riftwake.spawn.MapClearRegistry
import me.talula.riftwake.spawn.SpawnComponent
import me.talula.riftwake.utils.getStringData
import me.talula.riftwake.utils.lookLocation
import me.talula.riftwake.utils.plus
import me.talula.riftwake.utils.red
import org.bukkit.Material
import org.bukkit.entity.EntityType

class ItemComponent(val player: RiftwakePlayer) {
    init {
        player.onRightClickItem += rightClick@{ event, item ->
            if (item.getStringData("item-id") != "bridge-egg")
                return@rightClick
            if (player.spawn.isInSpawn) {
                event.isCancelled = true
                player.sendMessage("You can't use that in spawn.".red)
                return@rightClick
            }
            event.isCancelled = true
            item.subtract()

            val egg = player.world.spawnEntity(player.lookLocation(0.2), EntityType.EGG)
            egg.velocity = player.eyeLocation.direction.multiply(1)
            var t = 0
            runTaskTimer(0, 1) { task ->
                if (!egg.isValid || t++ > 10) {
                    task.cancel()
                    return@runTaskTimer
                }
                val location = egg.location.plus(0, -2, 0)
                if (SpawnComponent.isInSpawn(location))
                    return@runTaskTimer
                if (world.getType(location) != Material.AIR)
                    return@runTaskTimer
                world.setType(location, Material.DIRT)
                if (MapClearRegistry.isInMapClearRegion(location))
                    MapClearRegistry.blocksToClear += location.block
            }
        }
    }
}