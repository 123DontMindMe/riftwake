package me.talula.riftwake.constants

import me.talula.riftwake.utils.maxPlaces
import kotlin.math.pow

class NumConstant(name: String): Constant<Double>(name, "double") {
    override fun formatted(): String {
        return value.maxPlaces(8)
    }

    override fun serialize() = value

    override fun deserialize(value: String): Double? {
        return try { value.toDouble() } catch (_: NumberFormatException) { null }
    }

    fun pow(n: Int): Double {
        return value.pow(n)
    }
}