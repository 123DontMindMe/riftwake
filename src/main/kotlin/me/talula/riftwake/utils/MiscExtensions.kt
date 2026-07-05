package me.talula.riftwake.utils

import com.github.retrooper.packetevents.protocol.world.states.type.StateType
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes
import com.github.retrooper.packetevents.resources.ResourceLocation
import io.github.retrooper.packetevents.util.SpigotConversionUtil
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.data.BlockData
import org.bukkit.util.Vector
import kotlin.math.pow

fun Material.toStateType(): StateType {
    return StateTypes.getByName(ResourceLocation(name.lowercase()))!!
}

fun Int.pow(power: Double): Double {
    return this.toDouble().pow(power)
}

fun Location.setType(type: Material) {
    world.setType(this, type)
}

fun Location.setBlock(type: Material, dataConsumer: (BlockData) -> Unit) {
    world.setType(this, type)
    world.setBlockData(this, type.createBlockData(dataConsumer))
}

fun Location.forPacket(): com.github.retrooper.packetevents.protocol.world.Location {
    return SpigotConversionUtil.fromBukkitLocation(this)
}

fun Location.plus(x: Double, y: Double, z: Double): Location {
    return this.clone().add(x, y, z)
}

fun Location.plus(x: Int, y: Int, z: Int): Location {
    return this.clone().add(x.toDouble(), y.toDouble(), z.toDouble())
}

fun Vector.plus(x: Double, y: Double, z: Double): Vector {
    val result = this.clone()
    result.x += x
    result.y += y
    result.z += z
    return result
}