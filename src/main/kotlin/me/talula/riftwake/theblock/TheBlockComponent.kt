package me.talula.riftwake.theblock

import me.talula.riftwake.Riftwake
import me.talula.riftwake.RiftwakePlayer
import me.talula.riftwake.utils.parse
import me.talula.riftwake.utils.playSound
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.util.Vector

class TheBlockComponent(val player: RiftwakePlayer) {
    val block get() = TheBlockRegistry.blocksByOwner[player.uniqueId]

    init {
        player.onBreakBlock += blockBreak@{ event ->
            val block = TheBlockRegistry.blocksByLocation[event.block] ?: return@blockBreak
            event.isDropItems = false
            // even though RiftwakePlayer implements Player, some Paper methods like this one explicitly
            // cast the Player to a CraftPlayer (which a RiftwakePlayer obviously isn't), hence passing in
            // the craftPlayer being required
            for (drop in event.getBlock().getDrops(player.inventory.itemInMainHand, player.craftPlayer)) {
                val item = player.world.dropItem(block.location.toCenterLocation().add(0.0, 0.5, 0.0), drop)
                item.velocity = Vector(
                    Math.random() * 0.2 - 0.1,
                    Math.random() * 0.02 + 0.1,
                    Math.random() * 0.2 - 0.1
                )
            }
            Riftwake.runTask { block.spawn() }
        }
        player.onRightClickBlock += { event, block ->
            if (block.location == this.block?.location) {
                UpgradeMenuGUI(player).open()
                event.isCancelled = true
            }
            else TheBlockRegistry.blocksByLocation[block]?.let {
                player.sendMessage(
                    ("<yellow|This block is owned by <green|${Riftwake.server.getOfflinePlayer(it.owner).name}>. " +
                    "You can still mine it for resources!>").parse())
                player.playSound(Sound.ENTITY_VILLAGER_TRADE, SoundCategory.MASTER, 1f, 1f)
            }
        }
    }

    fun setBlockLocation(location: Location) {
        val currentBlock = block
        if (currentBlock != null) {
            currentBlock.location = location
            return
        }
        val block = TheBlock(player.uniqueId, location)
        TheBlockRegistry.register(block)
    }

    fun previewPull() = block?.previewTable?.pull() ?: (if (Math.random() < 0.7) Material.DIRT else Material.OAK_LOG)
}