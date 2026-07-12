package me.talula.riftwake.utils

import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes
import com.github.retrooper.packetevents.resources.ResourceLocation
import com.sk89q.worldedit.EditSession
import com.sk89q.worldedit.WorldEdit
import com.sk89q.worldedit.bukkit.BukkitAdapter
import io.github.retrooper.packetevents.util.SpigotConversionUtil
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.data.BlockData
import org.bukkit.util.Vector
import kotlin.math.pow

fun World.edit(consumer: (EditSession) -> Unit) {
    WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(this)).use(consumer)
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