package me.talula.riftwake.items

import me.talula.riftwake.utils.gray
import me.talula.riftwake.utils.setData
import me.talula.riftwake.utils.yellow
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

object Items {
    fun createBridgeEgg(): ItemStack {
        val item = ItemStack(Material.EGG)
        item.editMeta { meta ->
            meta.itemName("Bridge Egg".yellow)
            meta.setData("item-id", PersistentDataType.STRING, "bridge-egg")
            meta.lore(listOf("Throw to create a bridge.".gray))
        }
        return item
    }
}