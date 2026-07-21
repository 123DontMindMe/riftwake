package me.talula.riftwake.economy

import me.talula.riftwake.Riftwake
import me.talula.riftwake.RiftwakePlayer
import me.talula.riftwake.utils.InventoryGUI
import me.talula.riftwake.utils.withCommas
import me.talula.riftwake.utils.comp
import me.talula.riftwake.utils.green
import me.talula.riftwake.utils.playSound
import me.talula.riftwake.utils.red
import me.talula.riftwake.utils.toTimeString
import me.talula.riftwake.utils.unitalic
import me.talula.riftwake.utils.yellow
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.event.inventory.InventoryClickEvent

class AuctionBuyGUI(player: RiftwakePlayer): InventoryGUI(player, 6, "Auction House".comp()) {
    var pageIndex = 0
    val numPages = Math.ceilDiv(AuctionRegistry.items.size, 45).coerceAtLeast(1)

    init {
        for ((index, item) in AuctionRegistry.items.subList(0, 45.coerceAtMost(AuctionRegistry.items.size)).withIndex())
            ItemButton(index, item)

        NextPageButton(53)
        StaticButton(49, createIcon("Page ${pageIndex + 1}/${numPages}".yellow, Material.BOOK))
        PrevPageButton(45)

        fillEmpty()
    }

    override fun onClick(event: InventoryClickEvent) {}

    inner class NextPageButton(index: Int): Button(index, createIcon("Next Page".yellow, Material.ARROW)) {
        init {
            if (pageIndex == numPages - 1) hide()
        }

        override fun onClick(event: InventoryClickEvent) {
            pageIndex = (pageIndex + 1).coerceAtMost(numPages - 1)
            if (pageIndex == numPages - 1) hide() else show()

            player.playSound(Sound.ITEM_BOOK_PAGE_TURN, SoundCategory.UI, 1f, 1f)
        }
    }

    inner class PrevPageButton(index: Int): Button(index, createIcon("Previous Page".yellow, Material.ARROW)) {
        init {
            hide()
        }

        override fun onClick(event: InventoryClickEvent) {
            pageIndex = (pageIndex - 1).coerceAtLeast(0)
            if (pageIndex == 0) hide() else show()

            player.playSound(Sound.ITEM_BOOK_PAGE_TURN, SoundCategory.UI, 1f, 1f)
        }
    }

    inner class ItemButton(index: Int, val auctionItem: AuctionItem): Button(index, null) {
        init {
            updateIcon()
            Riftwake.runTaskTimer(20, 20) { updateIcon() }
        }

        override fun onClick(event: InventoryClickEvent) {
            if (auctionItem.cost > player.balance)
                player.playSound(Sound.ENTITY_VILLAGER_NO, SoundCategory.UI, 1f, 1f)
            else
                AuctionConfirmBuyGUI(player, auctionItem).open()
        }

        private fun updateIcon() {
            val timeElapsed = Riftwake.server.currentTick - auctionItem.timestamp
            if (timeElapsed >= auctionItem.duration) {
                setItem(createIcon("This item has expired.".red, Material.BARRIER))
                return
            }
            val icon = auctionItem.item.clone()
            icon.lore(listOf(
                "Cost: ${auctionItem.cost.withCommas}".yellow.unitalic,
                "Time left: ${(auctionItem.duration - timeElapsed).toTimeString()}".yellow.unitalic,
                if (player.balance >= auctionItem.cost) "Click to purchase".green.unitalic
                else "You can't afford this item.".red.unitalic,
            ))
            setItem(icon)
        }
    }
}