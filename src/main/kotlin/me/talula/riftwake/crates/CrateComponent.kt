package me.talula.riftwake.crates

import me.talula.riftwake.RiftwakePlayer
import me.talula.riftwake.items.Items
import me.talula.riftwake.utils.getStringData
import me.talula.riftwake.utils.playSound
import me.talula.riftwake.utils.plus
import me.talula.riftwake.utils.red
import org.bukkit.Sound
import org.bukkit.SoundCategory

class CrateComponent(val player: RiftwakePlayer) {
    init {
        player.onRightClickBlock += rightClick@{ event, block ->
            val crate = CrateRegistry[block.location] ?: return@rightClick
            event.isCancelled = true
            if (crate.keyItemId == null) {
                player.sendMessage("This crate's key has not been set yet.".red)
                player.playSound(Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1f, 1f)
                return@rightClick
            }
            if (player.inventory.itemInMainHand.getStringData("item-id") != crate.keyItemId) {
                player.sendMessage("You must be holding a ".red + crate.keyName!! + " to open this crate.".red)
                player.playSound(Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1f, 1f)
                return@rightClick
            }
            if (crate.numRewards == 0) {
                player.sendMessage("This crate is empty.".red)
                player.playSound(Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1f, 1f)
                return@rightClick
            }
            CratePullGUI(player, crate).open()
        }

        player.onKillPlayer += { _, victim ->
            if (player.inventory.firstEmpty() == -1)
                victim.dropItem(Items.createBloodToken())
            else
                player.give(Items.createBloodToken())
        }
    }
}