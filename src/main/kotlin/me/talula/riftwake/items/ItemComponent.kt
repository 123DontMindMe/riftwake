package me.talula.riftwake.items

import me.talula.riftwake.Riftwake.Companion.runTaskTimer
import me.talula.riftwake.Riftwake.Companion.world
import me.talula.riftwake.RiftwakePlayer
import me.talula.riftwake.utils.getData
import me.talula.riftwake.utils.lookLocation
import me.talula.riftwake.utils.plus
import org.bukkit.Material
import org.bukkit.entity.EntityType
import org.bukkit.persistence.PersistentDataType

class ItemComponent(val player: RiftwakePlayer) {
    init {
        player.onRightClickItem += rightClick@{ event, item ->
            if (item.itemMeta.getData("item-id", PersistentDataType.STRING) != "bridge-egg")
                return@rightClick
            event.isCancelled = true
            val egg = player.world.spawnEntity(player.lookLocation(0.2), EntityType.EGG)
            egg.velocity = player.eyeLocation.direction.multiply(1)
            var t = 0
            runTaskTimer(0, 1) { task ->
                if (!egg.isValid || t++ > 10) {
                    task.cancel()
                    return@runTaskTimer
                }
                val location = egg.location.plus(0, -2, 0)
                if (world.getType(location) != Material.AIR)
                    return@runTaskTimer
                world.setType(location, Material.DIRT)
            }
        }
    }
}