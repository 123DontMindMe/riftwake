package me.talula.riftwake.utils

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.wrapper.PacketWrapper
import me.talula.riftwake.Riftwake
import me.talula.riftwake.RiftwakePlayer
import net.luckperms.api.model.user.User
import net.luckperms.api.node.NodeType
import net.luckperms.api.node.types.MetaNode
import org.bukkit.*
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataHolder
import org.bukkit.persistence.PersistentDataType
import java.util.concurrent.CompletableFuture


val OfflinePlayer.riftwake get() = Riftwake.playerRegistry[this]
val RiftwakePlayer.riftwake get() = this
val CommandSender.riftwake get() = (this as OfflinePlayer?)?.riftwake

// should use when passing in a potential RiftwakePlayer into a Paper method, since it usually
// attempts to cast it to a CraftPlayer
val Player.craft: Player get() = if (this is RiftwakePlayer) craftPlayer else this

val Player.luckPermsUser: User get() = Riftwake.luckPerms.getPlayerAdapter(Player::class.java).getUser(craft)

fun OfflinePlayer.getBalance(): CompletableFuture<Long> {
    return Riftwake.luckPerms.userManager.loadUser(uniqueId).thenApplyAsync { user ->
        user.cachedData.metaData.getMetaValue("balance", String::toLong).orElse(0)
    }
}

fun OfflinePlayer.setBalance(balance: Long): CompletableFuture<Void> {
    return Riftwake.luckPerms.userManager.modifyUser(uniqueId) { user ->
        user.data().clear(NodeType.META.predicate { it.metaKey == "balance" })
        user.data().add(MetaNode.builder("balance", balance.toString()).build())
    }
}

fun <P: Any, C: Any> PersistentDataHolder.hasData(key: String, type: PersistentDataType<P, C>): Boolean {
    return persistentDataContainer.has(NamespacedKey("riftwake", key), type)
}

fun <P: Any, C: Any> PersistentDataHolder.getData(key: String, type: PersistentDataType<P, C>): C? {
    return persistentDataContainer.get(NamespacedKey("riftwake", key), type)
}

fun <P: Any, C: Any> PersistentDataHolder.setData(key: String, type: PersistentDataType<P, C>, value: C?): C? {
    if (value == null)
        persistentDataContainer.remove(NamespacedKey("riftwake", key))
    else
        persistentDataContainer.set(NamespacedKey("riftwake", key), type, value)
    return value
}

fun <P: Any, C: Any> PersistentDataHolder.setDataFromString(key: String, type: PersistentDataType<P, C>, string: String): C? {
    return setData(key, type, type.parse(string))
}

fun <P: Any, C: Any> PersistentDataHolder.setDataIfPresent(key: String, type: PersistentDataType<P, C>, value: C?): PersistentDataType<P, C> {
    val dataKey = NamespacedKey("riftwake", key)
    if (value == null)
        persistentDataContainer.remove(dataKey)
    else if (persistentDataContainer.has(dataKey))
        persistentDataContainer.set(dataKey, type, value)
    return type
}

fun Player.sendPacket(packet: PacketWrapper<*>) = PacketEvents.getAPI().playerManager.sendPacket(craft, packet)

fun Player.lookLocation(distance: Double): Location = eyeLocation.add(eyeLocation.direction.multiply(distance))

val Player.cursorLocation: Location
    get() {
        val targetBlock = getTargetBlockExact(5)
        if (targetBlock != null) {
            val face = getTargetBlockFace(5)
            return if (face == null)
                targetBlock.location
            else
                targetBlock.location.add(face.direction)
        }

        val cursorLocation = eyeLocation
            .add(eyeLocation.direction.multiply(5))
            .toBlockLocation()
        cursorLocation.pitch = 0f
        cursorLocation.yaw = 0f
        return cursorLocation
    }

val PlayerMoveEvent.cursorLocation: Location
    get() {
        val targetBlock = player.getTargetBlockExact(5)
        if (targetBlock != null) {
            val face = player.getTargetBlockFace(5)
            return if (face == null)
                targetBlock.location
            else
                targetBlock.location.add(face.direction)
        }

        val cursorLocation = to.plus(0.0, 1.62, 0.0)
            .add(to.direction.multiply(5))
            .toBlockLocation()
        cursorLocation.pitch = 0f
        cursorLocation.yaw = 0f
        return cursorLocation
    }

fun Player.subtractItem(material: Material, amount: Int): Boolean {
    var total = 0
    val stacks = ArrayList<ItemStack>()

    for (item in inventory.contents)
        if (item != null && item.type == material) {
            total += item.amount
            stacks.add(item)
        }

    if (total < amount)
        return false

    var remainingCost = amount
    for (stack in stacks) {
        val stackAmount = stack.amount
        if (remainingCost < stackAmount) {
            stack.subtract(remainingCost)
            break
        }
        stack.amount = 0
        remainingCost -= stackAmount
    }

    return true
}

fun Player.playSound(sound: Sound, category: SoundCategory, volume: Float, pitch: Float) {
    playSound(craft, sound, category, volume, pitch)
}
