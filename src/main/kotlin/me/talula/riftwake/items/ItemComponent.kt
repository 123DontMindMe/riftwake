package me.talula.riftwake.items

import me.talula.riftwake.Riftwake.Companion.runTaskTimer
import me.talula.riftwake.Riftwake.Companion.world
import me.talula.riftwake.RiftwakePlayer
import me.talula.riftwake.spawn.MapClearRegistry
import me.talula.riftwake.spawn.SpawnComponent
import me.talula.riftwake.utils.itemId
import me.talula.riftwake.utils.lookLocation
import me.talula.riftwake.utils.plus
import me.talula.riftwake.utils.red
import me.talula.riftwake.utils.times
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.entity.Egg
import org.bukkit.entity.EntityType
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack

class ItemComponent(val player: RiftwakePlayer) {
    class PlatformEgg(val isWide: Boolean, val egg: Egg)
    private var thrownPlatformEgg: PlatformEgg? = null

    init {
        player.onRightClickItem += { event, item ->
            when (item.itemId) {
                "bridge-egg" -> onUseBridgeEgg(event, item, 0)
                "wide-bridge-egg" -> onUseBridgeEgg(event, item, 1)
                "platform-egg" -> onUsePlatformEgg(event, item, false)
                "large-platform-egg" -> onUsePlatformEgg(event, item, true)
            }
        }

        player.onLeftClick += attack@{
            val egg = thrownPlatformEgg ?: return@attack
            val r = if (egg.isWide) 6 else 3
            val h = if (egg.isWide) 1 else 0
            for (x in -r..r)
                for (y in -h..h)
                    for (z in -r..r) {
                        val location = egg.egg.location.plus(x, y, z)
                        if (world.getType(location).isAir) {
                            world.setType(location, if (egg.isWide) Material.DEEPSLATE_BRICKS else Material.STONE_BRICKS)
                            if (MapClearRegistry.isInMapClearRegion(location))
                                MapClearRegistry.blocksToClear += location.block
                        }
                    }
            thrownPlatformEgg = null
        }
    }

    fun onUseBridgeEgg(event: PlayerInteractEvent, item: ItemStack, width: Int) {
        if (player.spawn.isInSpawn) {
            event.isCancelled = true
            player.sendMessage("You can't use that in spawn.".red)
            return
        }
        event.isCancelled = true
        item.subtract()

        val egg = player.world.spawnEntity(player.lookLocation(0.2), EntityType.EGG)
        egg.velocity = player.eyeLocation.direction
        egg.world.playSound(egg, Sound.ENTITY_EGG_THROW, SoundCategory.PLAYERS, 0.9f, 0.5f)

        val sideways = egg.velocity.clone().apply { y = 0.0 }.rotateAroundY(Math.PI / 4).normalize()
        var t = 0
        runTaskTimer(0, 1) { task ->
            if (!egg.isValid || t++ > 15) {
                task.cancel()
                return@runTaskTimer
            }
            for (i in -width..width) {
                val location = egg.location.plus(0, -2, 0).plus(sideways.times(i))
                if (SpawnComponent.isInSpawn(location))
                    continue
                if (!world.getType(location).isAir)
                    continue
                world.setType(location, Material.DIRT)
                if (MapClearRegistry.isInMapClearRegion(location))
                    MapClearRegistry.blocksToClear += location.block
            }
        }
    }

    fun onUsePlatformEgg(event: PlayerInteractEvent, item: ItemStack, isLarge: Boolean) {
        if (player.spawn.isInSpawn) {
            event.isCancelled = true
            player.sendMessage("You can't use that in spawn.".red)
            return
        }
        event.isCancelled = true
        item.subtract()

        val egg = player.world.spawnEntity(player.lookLocation(0.2), EntityType.EGG)
        (egg as Egg).item = ItemStack.of(Material.BLUE_EGG)
        egg.velocity = player.eyeLocation.direction
        egg.world.playSound(egg, Sound.ENTITY_EGG_THROW, SoundCategory.PLAYERS, 0.9f, 0.5f)
        thrownPlatformEgg = PlatformEgg(isLarge, egg)
    }
}