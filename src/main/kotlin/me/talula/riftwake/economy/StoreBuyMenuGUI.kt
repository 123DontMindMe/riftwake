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
import me.talula.riftwake.utils.unitalic
import me.talula.riftwake.utils.yellow
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.PotionMeta
import org.bukkit.potion.PotionType

class StoreBuyMenuGUI(player: RiftwakePlayer): InventoryGUI(player, 3, "Shop » Buy".comp()) {
    private companion object {
        val special = readFile("shop/buy/special.txt")
        val pvp = readFile("shop/buy/pvp.txt")
        val misc = readFile("shop/buy/misc.txt")
        val food = readFile("shop/buy/food.txt")

        fun readFile(filePath: String): List<BuyItemInfo> {
            val info = mutableListOf<BuyItemInfo>()
            for (line in Riftwake.getFile(filePath).readLines()) {
                try {
                    val (first, second, third) = line.split(" ")
                    val index = first.trim().toInt()
                    val item = try {
                        ItemStack.of(Material.valueOf(second.trim()))
                    } catch (_: IllegalArgumentException) {
                        val item = ItemStack.of(Material.SPLASH_POTION)
                        item.editMeta {
                            check(it is PotionMeta)
                            it.basePotionType = PotionType.valueOf(second.trim())
                        }
                        item
                    }
                    val price = third.trim().toInt()
                    info += BuyItemInfo(index, item, price)
                } catch (error: Throwable) {
                    Riftwake.broadcastToOperators("Invalid entry in $filePath: '$line', ${error.message}".red)
                    continue
                }
            }
            return info
        }
    }

    init {
        SimpleButton(x=1, y=1, createIcon("Special Items".gold, Material.TNT)) {
            StoreBuyGUI(player, "Special Items", 3, special).open()
            player.playSound(Sound.ITEM_BOOK_PAGE_TURN, SoundCategory.UI, 1f, 1f)
        }
        SimpleButton(x=3, y=1, createIcon("PvP".gold, Material.DIAMOND_SWORD)) {
            StoreBuyGUI(player, "PvP", 3, pvp).open()
            player.playSound(Sound.ITEM_BOOK_PAGE_TURN, SoundCategory.UI, 1f, 1f)
        }
        SimpleButton(x=5, y=1, createIcon("Miscellaneous".gold, Material.BLUE_HARNESS)) {
            StoreBuyGUI(player, "Miscellaneous", 3, misc).open()
            player.playSound(Sound.ITEM_BOOK_PAGE_TURN, SoundCategory.UI, 1f, 1f)
        }
        SimpleButton(x=7, y=1, createIcon("Food".gold, Material.GOLDEN_CARROT)) {
            StoreBuyGUI(player, "Food", 6, food).open()
            player.playSound(Sound.ITEM_BOOK_PAGE_TURN, SoundCategory.UI, 1f, 1f)
        }
        fillEmpty()
    }
}

class BuyItemInfo(val index: Int, val item: ItemStack, val price: Int)

class StoreBuyGUI(player: RiftwakePlayer, val name: String, numRows: Int, items: List<BuyItemInfo>):
    InventoryGUI(player, numRows, "Shop » Buy » $name".comp())
{
    init {
        for (item in items)
            BuyButton(item.index, item.item, item.price)
        SimpleButton((numRows - 1) * 9, createIcon("Back".yellow, Material.ARROW)) {
            StoreSellMenuGUI(player).open()
            player.playSound(Sound.ITEM_BOOK_PAGE_TURN, SoundCategory.UI, 1f, 1f)
        }
        fillEmpty()
    }

    inner class BuyButton(index: Int, val item: ItemStack, val price: Int):
        Button(index, createIcon(Component.translatable(item), item, 1,
            ("Costs for ".gray + "$$price".gold + " per item".gray).unitalic,
            "".comp(),
            ("Left-click".yellow + " to buy ".gray + "1".gold).unitalic,
            ("Right-click".yellow + " for more options".gray).unitalic
        )) {
        override fun onClick(event: InventoryClickEvent) {
            if (event.isRightClick) {
                BuyOptionsGUI(player, item, price, this@StoreBuyGUI).open()
                player.playSound(Sound.ITEM_BOOK_PAGE_TURN, SoundCategory.UI, 1f, 1f)
                return
            }
            if (player.balance >= price) {
                player.balance -= price
                player.give(item.clone())
                player.playSound(Sound.BLOCK_NOTE_BLOCK_HARP, SoundCategory.UI, 1f, 2f)
                player.sendMessage("Bought ".green + item.hoverableStack + " for $$price.".green)
            } else {
                player.playSound(Sound.ENTITY_VILLAGER_NO, SoundCategory.UI, 1f, 1f)
                player.sendMessage("You don't have enough money to buy this item.".red)
            }
        }
    }
}