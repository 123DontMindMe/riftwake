package me.talula.riftwake.utils

import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes
import com.github.retrooper.packetevents.resources.ResourceLocation
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.sk89q.worldedit.EditSession
import com.sk89q.worldedit.WorldEdit
import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldedit.util.SideEffectSet
import io.github.retrooper.packetevents.util.SpigotConversionUtil
import io.papermc.paper.command.brigadier.CommandSourceStack
import me.talula.riftwake.RiftwakePlayer
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.data.BlockData
import org.bukkit.persistence.PersistentDataType
import org.bukkit.util.Vector
import kotlin.math.pow

fun String.toPersistentDataType(): PersistentDataType<*,*>? {
    return when (this) {
        "byte" -> PersistentDataType.BYTE
        "short" -> PersistentDataType.SHORT
        "int" -> PersistentDataType.INTEGER
        "long" -> PersistentDataType.LONG
        "float" -> PersistentDataType.FLOAT
        "double" -> PersistentDataType.DOUBLE
        "bool" -> PersistentDataType.BOOLEAN
        "string" -> PersistentDataType.STRING
        "byte[]" -> PersistentDataType.BYTE_ARRAY
        "int[]" -> PersistentDataType.INTEGER_ARRAY
        "long[]" -> PersistentDataType.LONG_ARRAY
        else -> null
    }
}

fun <P,C> PersistentDataType<P,C>.parse(string: String): C {
    @Suppress("UNCHECKED_CAST")
    return when (this) {
        PersistentDataType.BYTE -> string.toByte()
        PersistentDataType.SHORT -> string.toShort()
        PersistentDataType.INTEGER -> string.toInt()
        PersistentDataType.LONG -> string.toLong()
        PersistentDataType.FLOAT -> string.toFloat()
        PersistentDataType.DOUBLE -> string.toDouble()
        PersistentDataType.BOOLEAN -> string.toBoolean()
        PersistentDataType.STRING -> string
        PersistentDataType.BYTE_ARRAY -> string.split(",").map { it.trim().toByte() }.toByteArray()
        PersistentDataType.INTEGER_ARRAY -> string.split(",").map { it.trim().toInt() }.toIntArray()
        PersistentDataType.LONG_ARRAY -> string.split(",").map { it.trim().toLong() }.toLongArray()
        else -> null
    } as C
}

fun LiteralArgumentBuilder<CommandSourceStack>.playerRun(command: (RiftwakePlayer) -> Boolean): LiteralArgumentBuilder<CommandSourceStack> {
    return executes { ctx ->
        val player = ctx.source.sender.riftwake ?: return@executes 0
        return@executes if (command(player)) 1 else 0
    }
}

fun <T> RequiredArgumentBuilder<CommandSourceStack, T>.playerRun(
    command: (CommandContext<CommandSourceStack>, RiftwakePlayer) -> Boolean
): RequiredArgumentBuilder<CommandSourceStack, T> {
    return executes { ctx ->
        val player = ctx.source.sender.riftwake ?: return@executes 0
        return@executes if (command(ctx, player)) 1 else 0
    }
}

fun World.edit(consumer: (EditSession) -> Unit) {
    val session = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(this))
    session.sideEffectApplier = SideEffectSet.none()
    session.use(consumer)
}

fun Material.toStateType() = StateTypes.getByName(ResourceLocation(name.lowercase()))!!

fun Int.pow(power: Double) = toDouble().pow(power)

fun Location.xzDistance2(other: Location): Double {
    val xDiff = x - other.x
    val zDiff = z - other.z
    return xDiff * xDiff + zDiff * zDiff
}

fun Location.setType(type: Material) = world.setType(this, type)

fun Location.setBlock(type: Material, dataConsumer: (BlockData) -> Unit) {
    world.setBlockData(this, type.createBlockData(dataConsumer))
}

fun Location.setBlock(data: BlockData) {
    world.setBlockData(this, data)
}

fun Location.forPacket(): com.github.retrooper.packetevents.protocol.world.Location {
    return SpigotConversionUtil.fromBukkitLocation(this)
}

fun Location.plus(x: Double, y: Double, z: Double) = clone().add(x, y, z)
fun Location.plus(x: Int, y: Int, z: Int): Location = clone().add(x.toDouble(), y.toDouble(), z.toDouble())

fun Vector.plus(x: Double, y: Double, z: Double): Vector {
    val result = this.clone()
    result.x += x
    result.y += y
    result.z += z
    return result
}