package me.talula.riftwake.economy

import me.talula.riftwake.RiftwakePlayer
import me.talula.riftwake.utils.InventoryGUI
import me.talula.riftwake.utils.bold
import me.talula.riftwake.utils.comp
import me.talula.riftwake.utils.green
import me.talula.riftwake.utils.playSound
import me.talula.riftwake.utils.plus
import me.talula.riftwake.utils.red
import me.talula.riftwake.utils.withRandomUUID
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack

class AuctionConfirmSellGUI(player: RiftwakePlayer, val sellItem: ItemStack, val cost: Long) :
    InventoryGUI(player, 1, "Sell ".comp() + Component.translatable(sellItem) + " for $cost?".comp()
) {
    init {
        inventory.setItem(0, createIcon("Confirm".green.bold, Material.GREEN_STAINED_GLASS_PANE))
        inventory.setItem(4, sellItem.withRandomUUID())
        inventory.setItem(8, createIcon("Cancel".red.bold, Material.RED_STAINED_GLASS_PANE))

        fillEmpty()
    }

    override fun onPlayerInventoryClick(event: InventoryClickEvent) {
       event.isCancelled = true
    }

    override fun onClick(event: InventoryClickEvent) {
        event.isCancelled = true
        when (event.slot) {
            0 -> {
                val index = player.inventory.first(sellItem)
                if (index == -1) {
                    close()
                    player.sendMessage("Sell failed; the item wasn't in your inventory.".red)
                    player.playSound(Sound.ENTITY_VILLAGER_NO, SoundCategory.UI, 1f, 1f)
                    return
                }
                player.inventory.setItem(index, null)
                AuctionRegistry.items += AuctionItem(player, sellItem, cost, AuctionRegistry.sellDuration())
                close()
                player.sendMessage(Component.translatable(sellItem) + " put up for auction for $cost.".green)
                player.playSound(Sound.BLOCK_NOTE_BLOCK_HARP, SoundCategory.UI, 1f, 2f)
            }
            8 -> {
                close()
                player.sendMessage("Sell cancelled.".red)
                player.playSound(Sound.ENTITY_VILLAGER_NO, SoundCategory.UI, 1f, 1f)
            }
        }
    }
}