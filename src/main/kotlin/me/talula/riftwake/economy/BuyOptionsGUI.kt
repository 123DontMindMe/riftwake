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
import me.talula.riftwake.utils.unitalic
import me.talula.riftwake.utils.yellow
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack

class BuyOptionsGUI(player: RiftwakePlayer, val item: ItemStack, val price: Int, parent: InventoryGUI): InventoryGUI(player, 1, "Sell Options".comp()) {
    private val buttons = listOf<Option>(
        BuyButton(2, 1),
        BuyButton(3, 4),
        BuyButton(4, 16),
        BuyButton(5, 32),
        BuyButton(6, 64),
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

    private inner class BuyButton(index: Int, val amount: Int): Button(index, null), Option {
        init {
            updateIcon()
        }

        override fun updateIcon() {
            setIcon(createIcon(
                "x$amount".gold + " for ".gray + "$${price * amount}".gold,
                item,
                amount,
                if (player.balance >= price * amount)
                    "Click to buy!".yellow.unitalic
                else
                    "You don't have enough money to buy that much.".red.unitalic
            ))
        }

        override fun onClick(event: InventoryClickEvent) {
            val cost = price * amount
            if (player.balance >= cost) {
                player.balance -= cost
                repeat(amount / item.maxStackSize) { player.give(item.asQuantity(item.maxStackSize)) }
                val remaining = amount % item.maxStackSize
                if (remaining > 0)
                    player.give(item.asQuantity(remaining))

                player.playSound(Sound.BLOCK_NOTE_BLOCK_HARP, SoundCategory.UI, 1f, 2f)
                player.sendMessage("Bought ".green + item.asQuantity(amount).hoverableStack + " for $$cost.".green)
            } else {
                player.playSound(Sound.ENTITY_VILLAGER_NO, SoundCategory.UI, 1f, 1f)
                player.sendMessage("You don't have enough money to buy that much.".red)
            }
        }
    }
}