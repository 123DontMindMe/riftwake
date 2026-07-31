package me.talula.riftwake.economy

import me.talula.riftwake.Riftwake
import me.talula.riftwake.RiftwakePlayer
import me.talula.riftwake.utils.*
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.event.inventory.InventoryClickEvent

class AuctionConfirmBuyGUI(player: RiftwakePlayer, val auctionItem: AuctionItem) :
    InventoryGUI(player, 1, "Purchase ".comp() + auctionItem.item.nameWithAmount + " for $${auctionItem.cost}?".comp()
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
                    val onlineOwner = auctionItem.owner.riftwake
                    if (onlineOwner == null)
                        Riftwake.enqueueTask { auctionItem.owner.modifyOfflineBalance(auctionItem.cost) }
                    else {
                        onlineOwner.balance += auctionItem.cost
                        onlineOwner.playSound(Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.MASTER, 1f, 2f)
                        onlineOwner.playSound(Sound.ENTITY_VILLAGER_YES, SoundCategory.MASTER, 1f, 1f)
                        onlineOwner.sendMessage(
                            player.name.white + " purchased your auction item ".green +
                            auctionItem.item.hoverableStack + " for $${auctionItem.cost}!".green
                        )
                    }
                    close()
                    player.sendMessage(auctionItem.item.hoverableStack + " purchased for $${auctionItem.cost}!".green)
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