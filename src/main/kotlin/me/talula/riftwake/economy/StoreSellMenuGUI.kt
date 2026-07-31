package me.talula.riftwake.economy

import me.talula.riftwake.Riftwake
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
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack

class StoreSellMenuGUI(player: RiftwakePlayer): InventoryGUI(player, 3, "Shop » Sell".comp()) {
    private companion object {
        val farming = readFile("shop/sell/farming.txt")
        val mining = readFile("shop/sell/mining.txt")
        val building = readFile("shop/sell/building.txt")
        val mobDrops = readFile("shop/sell/mob_drops.txt")

        fun readFile(filePath: String): List<ItemInfo> {
            val info = mutableListOf<ItemInfo>()
            for (line in Riftwake.getFile(filePath).readLines()) {
                val (first, second, third) = line.split(" ")
                val index = first.trim().toInt()
                val item = Material.valueOf(second.trim())
                val price = third.trim().toInt()
                info += ItemInfo(index, item, price)
            }
            return info
        }
    }

    init {
        SimpleButton(x=1, y=1, createIcon("Farming".gold, Material.CARROT)) {
            StoreSellGUI(player, "Farming", 3, farming).open()
            player.playSound(Sound.ITEM_BOOK_PAGE_TURN, SoundCategory.UI, 1f, 1f)
        }
        SimpleButton(x=3, y=1, createIcon("Mining".gold, Material.DIAMOND_PICKAXE)) {
            StoreSellGUI(player, "Mining", 3, mining).open()
            player.playSound(Sound.ITEM_BOOK_PAGE_TURN, SoundCategory.UI, 1f, 1f)
        }
        SimpleButton(x=5, y=1, createIcon("Building".gold, Material.ANDESITE)) {
            StoreSellGUI(player, "Building", 3, building).open()
            player.playSound(Sound.ITEM_BOOK_PAGE_TURN, SoundCategory.UI, 1f, 1f)
        }
        SimpleButton(x=7, y=1, createIcon("Mob Drops".gold, Material.ROTTEN_FLESH)) {
            StoreSellGUI(player, "Mob Drops", 6, mobDrops).open()
            player.playSound(Sound.ITEM_BOOK_PAGE_TURN, SoundCategory.UI, 1f, 1f)
        }
        fillEmpty()
    }
}

class ItemInfo(val index: Int, val item: Material, val price: Int)

class StoreSellGUI(player: RiftwakePlayer, val name: String, numRows: Int, items: List<ItemInfo>):
    InventoryGUI(player, numRows, "Shop » Sell » $name".comp())
{
    init {
        for (item in items)
            SellButton(item.index, item.item, item.price)
        SimpleButton((numRows - 1) * 9, createIcon("Back".yellow, Material.ARROW)) {
            StoreSellMenuGUI(player).open()
            player.playSound(Sound.ITEM_BOOK_PAGE_TURN, SoundCategory.UI, 1f, 1f)
        }
        fillEmpty()
    }

    inner class SellButton(index: Int, val item: Material, val price: Int):
        Button(index, createIcon(Component.translatable(item), item,
            ("Sells for ".gray + "$$price".gold + " per item".gray).unitalic,
            "".comp(),
            ("Left-click".yellow + " to sell ".gray + "1".gold).unitalic,
            ("Right-click".yellow + " for more options".gray).unitalic
        )) {
        override fun onClick(event: InventoryClickEvent) {
            if (event.isRightClick) {
                SellOptionsGUI(player, item, price, this@StoreSellGUI).open()
                player.playSound(Sound.ITEM_BOOK_PAGE_TURN, SoundCategory.UI, 1f, 1f)
                return
            }
            if (player.subtractItem(item, 1)) {
                player.balance += price
                player.playSound(Sound.BLOCK_NOTE_BLOCK_HARP, SoundCategory.UI, 1f, 2f)
                player.sendMessage("Sold ".green + ItemStack.of(item, 1).hoverableStack + " for $$price.".green)
            } else {
                player.playSound(Sound.ENTITY_VILLAGER_NO, SoundCategory.UI, 1f, 1f)
                player.sendMessage("You don't have enough of this item to sell.".red)
            }
        }
    }
}