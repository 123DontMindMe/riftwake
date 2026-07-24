package me.talula.riftwake.constants

class IntConstant(name: String): Constant<Int>(name, "integer") {
    override fun formatted(): String {
        return value.toString()
    }

    override fun serialize() = value

    override fun deserialize(value: String): Int? {
        return try { value.toInt() } catch (_: NumberFormatException) { null }
    }
}