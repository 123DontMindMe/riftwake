package me.talula.riftwake.utils

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.wrapper.PacketWrapper
import me.talula.riftwake.Riftwake
import me.talula.riftwake.RiftwakePlayer
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

fun Player.riftwake(): RiftwakePlayer? {
    return Riftwake.instance.playerRegistry[this]
}

fun CommandSender.riftwake() : RiftwakePlayer? {
    return (this as Player?)?.riftwake()
}

// should use when passing in a potential RiftwakePlayer into a Paper method, since it usually
// attempts to cast it to a CraftPlayer
fun Player.craft(): Player {
    return if (this is RiftwakePlayer) craftPlayer else this
}

fun <P: Any, C: Any> Player.getData(key: String, type: PersistentDataType<P, C>): C? {
    return persistentDataContainer.get(NamespacedKey("riftwake", key), type)
}

fun <P: Any, C: Any> Player.setData(key: String, type: PersistentDataType<P, C>, value: C) {
    persistentDataContainer.set(NamespacedKey("riftwake", key), type, value)
}

fun Player.sendPacket(packet: PacketWrapper<*>) {
    PacketEvents.getAPI().playerManager.sendPacket(craft(), packet)
}

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
    this.world.playSound(craft(), sound, category, volume, pitch)
}
