package me.talula.riftwake.dialogue

import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity
import io.papermc.paper.event.player.AsyncChatEvent
import me.talula.riftwake.RiftwakePlayer
import org.bukkit.block.Block
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.inventory.ItemStack

abstract class DialogueStage {
    abstract fun start(player: RiftwakePlayer)
    abstract fun cleanUp()

    open fun onMove(event: PlayerMoveEvent) {}
    open fun onRightClickEntity(event: PlayerInteractEntityEvent) {}
    open fun onInteractPacketEntity(event: WrapperPlayClientInteractEntity) {}
    open fun onRightClickBlock(event: PlayerInteractEvent, block: Block) {}
    open fun onRightClickItem(event: PlayerInteractEvent, item: ItemStack) {}
    open fun onSendMessage(event: AsyncChatEvent) {}
}