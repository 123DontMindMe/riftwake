package me.talula.riftwake.economy

import io.papermc.paper.event.player.PlayerInventorySlotChangeEvent
import me.talula.riftwake.RiftwakePlayer
import me.talula.riftwake.utils.InventoryGUI
import me.talula.riftwake.utils.comp
import me.talula.riftwake.utils.gold
import me.talula.riftwake.utils.gray
import me.talula.riftwake.utils.green
import me.talula.riftwake.utils.hoverableStack
import me.talula.riftwake.utils.playSound
import me.talula.riftwake.utils.plus
import me.talula.riftwake.utils.red
import me.talula.riftwake.utils.subtractItem
import me.talula.riftwake.utils.unitalic
import me.talula.riftwake.utils.yellow
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack

class SellOptionsGUI(player: RiftwakePlayer, val item: Material, val price: Int, parent: InventoryGUI): InventoryGUI(player, 1, "Sell Options".comp()) {
    private val sellAllButton = SellAllButton(6)
    private val buttons = listOf<Option>(
        SellButton(2, 1),
        SellButton(3, 16),
        SellButton(4, 32),
        SellButton(5, 64),
        sellAllButton
    )

    init {
        SimpleButton(0, createIcon("Back".yellow, Material.ARROW)) { parent.open() }
        fillEmpty()
    }

    override fun onPlayerInventoryChange(event: PlayerInventorySlotChangeEvent) {
        for (button in buttons)
            button.updateIcon()
    }

    interface Option { fun updateIcon() }

    private inner class SellButton(index: Int, val amount: Int): Button(index, null), Option {
        init {
            updateIcon()
        }

        override fun updateIcon() {
            setIcon(createIcon(
                "x$amount".gold + " for ".gray + "$${amount * price}".gold,
                item,
                amount,
                if (player.inventory.contains(item, amount))
                    "Click to sell".yellow.unitalic
                else
                    "You don't have enough to sell.".red.unitalic
            ))
        }

        override fun onClick(event: InventoryClickEvent) {
            if (player.subtractItem(item, amount)) {
                player.balance += price * amount
                player.playSound(Sound.BLOCK_NOTE_BLOCK_HARP, SoundCategory.UI, 1f, 2f)
                player.sendMessage("Sold ".green + ItemStack.of(item, amount).hoverableStack + " for $${price * amount}.".green)
            } else {
                player.playSound(Sound.ENTITY_VILLAGER_NO, SoundCategory.UI, 1f, 1f)
                player.sendMessage("You don't have enough of this item to sell.".red)
            }
        }
    }

    private inner class SellAllButton(index: Int): Button(index, null), Option {
        var amount = 0

        init {
            updateIcon()
        }

        override fun updateIcon() {
            amount = player.inventory.filter { it?.type == item }.sumOf { it.amount }
            setIcon(createIcon(
                "ALL (x${amount})".gold + " for ".gray + "$${amount * price}".gold,
                item,
                if (amount > 0)
                    "Click to sell!".yellow.unitalic
                else
                    "You don't have enough to sell.".red.unitalic
            ))
        }

        override fun onClick(event: InventoryClickEvent) {
            if (player.subtractItem(item, amount)) {
                player.balance += price * amount
                player.playSound(Sound.BLOCK_NOTE_BLOCK_HARP, SoundCategory.UI, 1f, 2f)
                player.sendMessage("Sold ".green + ItemStack.of(item, amount).hoverableStack + " for $${price * amount}.".green)
                for (button in buttons)
                    button.updateIcon()
            } else {
                // shouldn't happen but just as a failsafe
                player.playSound(Sound.ENTITY_VILLAGER_NO, SoundCategory.UI, 1f, 1f)
                player.sendMessage("You don't have enough of this item to sell.".red)
            }
        }
    }
}