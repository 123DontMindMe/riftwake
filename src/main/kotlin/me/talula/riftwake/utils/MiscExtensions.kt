package me.talula.riftwake.utils

import io.github.retrooper.packetevents.util.SpigotConversionUtil
import org.bukkit.Location
import kotlin.math.pow

fun Int.pow(power: Double): Double {
    return this.toDouble().pow(power)
}

fun Location.forPacket(): com.github.retrooper.packetevents.protocol.world.Location {
    return SpigotConversionUtil.fromBukkitLocation(this)
}

fun Location.plus(x: Double, y: Double, z: Double): Location {
    return this.clone().add(x, y, z)
}