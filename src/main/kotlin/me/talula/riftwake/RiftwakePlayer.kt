package me.talula.riftwake

import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity
import io.papermc.paper.event.player.AsyncChatEvent
import me.talula.riftwake.combat.CombatComponent
import me.talula.riftwake.dialogue.DialogueComponent
import me.talula.riftwake.events.BiEvent
import me.talula.riftwake.events.Event
import me.talula.riftwake.events.RemoveReason
import me.talula.riftwake.items.ItemComponent
import me.talula.riftwake.spawn.SpawnComponent
import me.talula.riftwake.theblock.TheBlockComponent
import me.talula.riftwake.utils.luckPermsUser
import me.talula.riftwake.utils.setBalance
import org.bukkit.block.Block
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityPlaceEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.event.player.PlayerToggleSneakEvent
import org.bukkit.inventory.ItemStack
import java.util.concurrent.CompletableFuture
import kotlin.jvm.optionals.getOrDefault

class RiftwakePlayer(val craftPlayer: Player): Player by craftPlayer {
    val onRemove = Event<RemoveReason>()
    val onMove = Event<PlayerMoveEvent>()
    val onTeleport = Event<PlayerTeleportEvent>()
    val onPhysicalInteract = Event<PlayerInteractEvent>()
    val onRightClickEntity = Event<PlayerInteractEntityEvent>()
    val onInteractPacketEntity = Event<WrapperPlayClientInteractEntity>()
    val onRightClickBlock = BiEvent<PlayerInteractEvent, Block>()
    val onRightClickItem = BiEvent<PlayerInteractEvent, ItemStack>()
    val onSendMessage = Event<AsyncChatEvent>()
    val onToggleSneak = Event<PlayerToggleSneakEvent>()
    val onBreakBlock = Event<BlockBreakEvent>()
    val onPlaceBlock = Event<BlockPlaceEvent>()
    val onPlaceEntity = Event<EntityPlaceEvent>()
    val onReceiveDamage = Event<EntityDamageEvent>()
    val onDamageEntity = Event<EntityDamageByEntityEvent>()
    val onReceiveEntityDamage = BiEvent<EntityDamageByEntityEvent, Entity>()
    val onDropItem = Event<PlayerDropItemEvent>()

    val dialogue = DialogueComponent(this)
    val block = TheBlockComponent(this)
    val spawn = SpawnComponent(this)
    val items = ItemComponent(this)
    val combat = CombatComponent(this)

    var balance: Long = luckPermsUser.cachedData.metaData.getMetaValue("balance", String::toLong).getOrDefault(0L)
        set(value) {
            field = value
            craftPlayer.setBalance(value)
        }

    fun getBalance(): CompletableFuture<Long> {
        return CompletableFuture.completedFuture(balance)
    }

    fun setBalance(balance: Long): CompletableFuture<Void> {
        this.balance = balance
        return craftPlayer.setBalance(balance)
    }
}