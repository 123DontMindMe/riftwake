package me.talula.riftwake.constants

import me.talula.riftwake.utils.maxPlaces

class NumConstant(name: String): Constant<Double>(name, "double") {
    override fun serialize(): String {
        return value.maxPlaces(8)
    }

    override fun deserialize(value: String): Double? {
        return try { value.toDouble() } catch (_: NumberFormatException) { null }
    }
}