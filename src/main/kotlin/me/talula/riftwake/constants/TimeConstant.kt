package me.talula.riftwake.constants

class TimeConstant(name: String): Constant<Int>(name, "time") {
    override fun serialize(): String {
        var totalTicks = value
        val days = totalTicks / (60 * 60 * 20 * 24)
        totalTicks %= 60 * 60 * 20 * 24
        val hours = totalTicks / (60 * 60 * 20)
		totalTicks %= 60 * 60 * 20
		val minutes = totalTicks / (60 * 20)
		totalTicks %= 60 * 20
		val seconds = totalTicks / 20
		totalTicks %= 20
		val ticks = totalTicks
        val result = StringBuilder()
        if (days > 0)
            result.append(days).append("w ")
        if (hours > 0)
            result.append(hours).append("d ")
		if (hours > 0)
            result.append(hours).append("h ")
		if (minutes > 0)
			result.append(minutes).append("m ")
		if (seconds > 0)
			result.append(seconds).append("s ")
		if (ticks > 0)
			result.append(ticks).append("t ")

		if (result.isEmpty())
			result.append("0t")
		else
			result.deleteCharAt(result.length - 1)

		return result.toString()
    }

    override fun deserialize(value: String): Int? {
        var total = 0.0
        // whole thing must consist of the same pattern repeated with optional spaces between
        if (!Regex("([0-9]*\\.?[0-9]+\\s*[(A-z]+\\s*)+").matches(value.trim()))
            return null
        // (decimal number)any amount of spaces(word)
        for (amount in Regex("([0-9]*\\.?[0-9]+)\\s*([(A-z]+)").findAll(value)) {
            val number = try { amount.groupValues[1].toDouble() } catch (_: NumberFormatException) {
                return null
            }
            val ticks = when (amount.groupValues[2].lowercase()) {
                "t", "tick", "ticks" -> 1
                "s", "sec", "secs", "second", "seconds" -> 20
                "m", "min", "mins", "minute", "minutes" -> 20 * 60
                "h", "hr", "hrs", "hour", "hours" -> 20 * 60 * 60
                "d", "day", "days" -> 20 * 60 * 60 * 24
                "w", "week", "weeks" -> 20 * 60 * 60 * 24 * 7
                else -> return null
            }
            total += ticks * number
        }
        return total.toInt()
    }
}