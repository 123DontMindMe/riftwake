package me.talula.riftwake.economy

import me.talula.riftwake.RiftwakePlayer
import me.talula.riftwake.utils.InventoryGUI
import me.talula.riftwake.utils.comp
import me.talula.riftwake.utils.gold
import me.talula.riftwake.utils.playSound
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.SoundCategory

class StoreMenuGUI(player: RiftwakePlayer): InventoryGUI(player, 3, "Shop".comp()) {
    init {
        SimpleButton(x=3, y=1, createIcon("Buy".gold, Material.DIAMOND)) {
            StoreBuyMenuGUI(player).open()
            player.playSound(Sound.ITEM_BOOK_PAGE_TURN, SoundCategory.UI, 1f, 1f)
        }
        SimpleButton(x=5, y=1, createIcon("Sell".gold, Material.EMERALD)) {
            StoreSellMenuGUI(player).open()
            player.playSound(Sound.ITEM_BOOK_PAGE_TURN, SoundCategory.UI, 1f, 1f)
        }
        fillEmpty()
    }
}