package me.talula.riftwake.items

import com.mojang.brigadier.arguments.StringArgumentType
import io.papermc.paper.command.brigadier.Commands
import me.talula.riftwake.Riftwake.Companion.registerCommand
import me.talula.riftwake.utils.*
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

object Items {
    fun init() {
        registerCommand(Commands.literal("item")
            .then(Commands.argument("id", StringArgumentType.string())
                .runPlayer { ctx, player ->
                    player.give(fromId(StringArgumentType.getString(ctx, "id")) ?: Command.fail("Not an item.".red))
                }
            )
        )
    }

    fun fromId(id: String) =
        when (id) {
            "bridge-egg" -> createBridgeEgg()
            "wide-bridge-egg" -> createWideBridgeEgg()
            "platform-egg" -> createPlatformEgg()
            "large-platform-egg" -> createLargePlatformEgg()
            else -> null
        }

    fun createBridgeEgg(): ItemStack {
        val item = ItemStack.of(Material.EGG)
        item.editMeta { meta ->
            meta.itemName("Bridge Egg".yellow)
            meta.itemId = "bridge-egg"
            meta.lore(listOf("Throw to create a bridge.".gray.unitalic))
        }
        return item
    }

    fun createWideBridgeEgg(): ItemStack {
        val item = ItemStack.of(Material.HUSK_SPAWN_EGG)
        item.editMeta { meta ->
            meta.itemName("Wide Bridge Egg".yellow)
            meta.itemId = "wide-bridge-egg"
            meta.lore(listOf("Throw to create a bridge.".gray.unitalic))
        }
        return item
    }

    fun createPlatformEgg(): ItemStack {
        val item = ItemStack.of(Material.BLUE_EGG)
        item.editMeta { meta ->
            meta.itemName("Platform Egg".darkAqua)
            meta.itemId = "platform-egg"
            meta.lore(listOf("Throw and then left-click to create a platform.".gray.unitalic))
        }
        return item
    }

    fun createLargePlatformEgg(): ItemStack {
        val item = ItemStack.of(Material.PHANTOM_SPAWN_EGG)
        item.editMeta { meta ->
            meta.itemName("Large Platform Egg".darkAqua)
            meta.itemId = "large-platform-egg"
            meta.lore(listOf("Throw and then left-click to create a platform.".gray.unitalic))
        }
        return item
    }

    fun createBloodToken(): ItemStack {
        val item = ItemStack.of(Material.RED_DYE)
        item.editMeta { meta ->
            meta.itemName("Blood Token".red.bold)
            meta.itemId = "blood-token"
            meta.lore(listOf(
                "Obtained by killing players.".gray.unitalic,
                ("Use to open ".gray + "Blood Crates".red.bold + ".".gray).unitalic
            ))
        }
        return item
    }
}