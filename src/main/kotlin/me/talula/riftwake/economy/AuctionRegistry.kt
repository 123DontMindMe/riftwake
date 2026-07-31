package me.talula.riftwake.economy

import com.mojang.brigadier.arguments.LongArgumentType
import io.papermc.paper.command.brigadier.Commands
import me.talula.riftwake.Riftwake
import me.talula.riftwake.constants.IntConstant
import me.talula.riftwake.constants.TimeConstant
import me.talula.riftwake.utils.*
import net.kyori.adventure.text.Component
import org.bukkit.OfflinePlayer
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.inventory.ItemStack

object AuctionRegistry {
    val sellDuration = TimeConstant("auction.sell-duration")
    val maxItems = IntConstant("auction.max-items")

    val items = mutableListOf<AuctionItem>()

    fun init() {
        Riftwake.registerCommand(Commands.literal("auction")
            .then(Commands.literal("buy")
                .runPlayer { player -> AuctionBuyGUI(player).open() }
            )
            .then(Commands.literal("sell")
                .then(Commands.argument("cost", LongArgumentType.longArg(0))
                    .runPlayer { ctx, player ->
                        if (items.count { it.owner == player } >= maxItems()) {
                            player.playSound(Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1f, 1f)
                            throw CommandFail("You can't have more than ${maxItems()} items up for auction at once.")
                        }
                        val item = player.itemHeld ?: throw CommandFail("You must be holding the item you want to sell.")
                        val cost = ctx.getArgument("cost", Long::class.java)
                        AuctionConfirmSellGUI(player, item.clone(), cost).open()
                    }
                )
            )
        )
    }
}

class AuctionItem(val owner: OfflinePlayer, val item: ItemStack, val cost: Long, val duration: Int) {
    val timestamp = Riftwake.server.currentTick
    init {
        Riftwake.runTaskLater(duration.toLong()) {
            AuctionRegistry.items.remove(this)
            val player = owner.riftwake ?: return@runTaskLater
            player.sendMessage("Your auction item " + Component.translatable(item) + " has expired.".yellow)
            player.playSound(Sound.BLOCK_NOTE_BLOCK_BELL, SoundCategory.MASTER, 2f, 1f)
        }
    }
}