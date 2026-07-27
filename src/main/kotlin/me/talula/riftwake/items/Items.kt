package me.talula.riftwake.items

import me.talula.riftwake.utils.*
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

object Items {
    fun createBridgeEgg(): ItemStack {
        val item = ItemStack(Material.EGG)
        item.editMeta { meta ->
            meta.itemName("Bridge Egg".yellow)
            meta.setData("item-id", PersistentDataType.STRING, "bridge-egg")
            meta.lore(listOf("Throw to create a bridge.".gray.unitalic))
        }
        return item
    }

    fun createBloodToken(): ItemStack {
        val item = ItemStack(Material.RED_DYE)
        item.editMeta { meta ->
            meta.itemName("Blood Token".red.bold)
            meta.setData("item-id", PersistentDataType.STRING, "blood-token")
            meta.lore(listOf(
                "Obtained by killing players.".gray.unitalic,
                ("Use to open ".gray + "Blood Crates".red.bold + ".".gray).unitalic
            ))
        }
        return item
    }
}