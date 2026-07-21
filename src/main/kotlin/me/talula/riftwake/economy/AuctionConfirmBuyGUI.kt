package me.talula.riftwake.economy

import me.talula.riftwake.RiftwakePlayer
import me.talula.riftwake.utils.InventoryGUI
import me.talula.riftwake.utils.bold
import me.talula.riftwake.utils.comp
import me.talula.riftwake.utils.getBalance
import me.talula.riftwake.utils.green
import me.talula.riftwake.utils.playSound
import me.talula.riftwake.utils.plus
import me.talula.riftwake.utils.red
import me.talula.riftwake.utils.setBalance
import me.talula.riftwake.utils.unitalic
import me.talula.riftwake.utils.withRandomUUID
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.event.inventory.InventoryClickEvent

class AuctionConfirmBuyGUI(player: RiftwakePlayer, val auctionItem: AuctionItem) :
    InventoryGUI(player, 1, "Purchase ".comp() + Component.translatable(auctionItem.item) + " for ${auctionItem.cost}?".comp()
) {
    init {
        inventory.setItem(0, createIcon("Confirm".green.bold, Material.GREEN_STAINED_GLASS_PANE))
        inventory.setItem(4, auctionItem.item.withRandomUUID())
        inventory.setItem(8, createIcon("Cancel".red.bold, Material.RED_STAINED_GLASS_PANE))

        fillEmpty()
    }

    override fun onClick(event: InventoryClickEvent) {
        event.isCancelled = true
        when (event.slot) {
            0 -> {
                if (player.inventory.firstEmpty() == -1) {
                    player.sendMessage("Your inventory is full.".red.unitalic)
                    player.playSound(Sound.ENTITY_VILLAGER_NO, SoundCategory.UI, 1f, 1f)
                    inventory.setItem(0, createIcon("Confirm".green.bold, Material.GREEN_STAINED_GLASS_PANE, "Your inventory is full.".red))
                    return
                }
                if (auctionItem.cost > player.balance) {
                    close()
                    player.sendMessage("Purchase failed; you can't afford this item.".red)
                    player.playSound(Sound.ENTITY_VILLAGER_NO, SoundCategory.UI, 1f, 1f)
                    return
                }
                if (AuctionRegistry.items.remove(auctionItem)) {
                    player.balance -= auctionItem.cost
                    player.inventory.addItem(auctionItem.item)
                    auctionItem.owner.getBalance().thenAcceptAsync { balance ->
                        auctionItem.owner.setBalance(balance + auctionItem.cost)
                    }
                    close()
                    player.sendMessage("Item purchased!".green)
                    player.playSound(Sound.BLOCK_NOTE_BLOCK_HARP, SoundCategory.UI, 1f, 2f)
                } else {
                    close()
                    player.sendMessage("Purchase failed; item expired or was just purchased by someone else.".red)
                    player.playSound(Sound.ENTITY_VILLAGER_NO, SoundCategory.UI, 1f, 1f)
                }
            }
            8 -> {
                close()
                player.sendMessage("Purchase cancelled.".red)
                player.playSound(Sound.ENTITY_VILLAGER_NO, SoundCategory.UI, 1f, 1f)
            }
        }
    }
}