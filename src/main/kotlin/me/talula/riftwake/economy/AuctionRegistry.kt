package me.talula.riftwake.economy

import com.mojang.brigadier.arguments.LongArgumentType
import io.papermc.paper.command.brigadier.Commands
import me.talula.riftwake.Riftwake
import me.talula.riftwake.constants.TimeConstant
import me.talula.riftwake.utils.green
import me.talula.riftwake.utils.playSound
import me.talula.riftwake.utils.playerRun
import me.talula.riftwake.utils.plus
import me.talula.riftwake.utils.red
import me.talula.riftwake.utils.riftwake
import me.talula.riftwake.utils.yellow
import net.kyori.adventure.text.Component
import org.bukkit.OfflinePlayer
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.inventory.ItemStack

object AuctionRegistry {
    val sellDuration = TimeConstant("auction.sell-duration")

    val items = mutableListOf<AuctionItem>()

    fun init() {
        Riftwake.registerCommand(Commands.literal("auction")
            .then(Commands.literal("buy")
                .playerRun { player ->
                    AuctionBuyGUI(player).open()
                    true
                }
            )
            .then(Commands.literal("sell")
                .then(Commands.argument("cost", LongArgumentType.longArg(0))
                    .playerRun { ctx, player ->
                        val item = player.inventory.itemInMainHand
                        if (item.isEmpty) {
                            player.sendMessage("You must be holding the item you want to sell.".red)
                            return@playerRun false
                        }
                        val cost = ctx.getArgument("cost", Long::class.java)
                        items += AuctionItem(player, item.clone(), cost, sellDuration().toLong())
                        player.inventory.setItem(player.inventory.heldItemSlot, null)
                        player.sendMessage(Component.translatable(item) + " put up for auction for $cost.".green)
                        true
                    }
                )
            )
        )
    }
}

class AuctionItem(val owner: OfflinePlayer, val item: ItemStack, val cost: Long, val duration: Long) {
    val timestamp = Riftwake.server.currentTick
    init {
        Riftwake.runTaskLater(duration) {
            AuctionRegistry.items.remove(this)
            val player = owner.riftwake ?: return@runTaskLater
            player.sendMessage("Your auction item " + Component.translatable(item) + " has expired.".yellow)
            player.playSound(Sound.BLOCK_NOTE_BLOCK_BELL, SoundCategory.MASTER, 2f, 1f)
        }
    }
}