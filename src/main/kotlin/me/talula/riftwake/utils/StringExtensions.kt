package me.talula.riftwake.utils

import java.math.BigDecimal
import java.math.MathContext
import java.text.DecimalFormat

val Int.roman: String get() {
    return "I".repeat(this)
        .replace("IIIII", "V")
        .replace("IIII", "IV")
        .replace("VV", "X")
        .replace("VIV", "IX")
        .replace("XXXXX", "L")
        .replace("XXXX", "XL")
        .replace("LL", "C")
        .replace("LXL", "XC")
        .replace("CCCCC", "D")
        .replace("CCCC", "CD")
        .replace("DD", "M")
        .replace("DCD", "CM")
}

val Int.ordinal: String get() {
    if (this <= 0)
        throw IllegalArgumentException("Must be positive, got $this")
    if (this in 11..13)
        return "${this}th"
    return when (this % 10) {
        1 -> "${this}st"
        2 -> "${this}nd"
        3 -> "${this}rd"
        else -> "${this}th"
    }
}

val Long.withCommas: String get() = DecimalFormat("#,##0").format(this)

fun Double.sigFigs(precision: Int): String = BigDecimal(this).round(MathContext(precision)).toPlainString()

fun Double.maxPlaces(decimals: Int): String {
    return DecimalFormat("0." + "#".repeat(decimals)).format(this)
}

fun Double.toFixed(decimals: Int): String {
    return String.format("%.${decimals}f", this)
}

fun Int.plural(singular: String, plural: String? = null): String {
    return "$this ${(if (this == 1) singular else (plural ?: "${singular}s"))}"
}

fun Int.toTimeString(): String = toLong().toTimeString()
fun Long.toTimeString(): String {
    if (this < 0)
        throw IllegalArgumentException("must be non-negative, got $this")
    var totalTicks = this + 20
    val weeks = totalTicks / (60 * 60 * 20 * 24 * 7)
    totalTicks %= 60 * 60 * 20 * 24 * 7
    val days = totalTicks / (60 * 60 * 20 * 24)
    totalTicks %= 60 * 60 * 20 * 24
    val hours = totalTicks / (60 * 60 * 20)
    totalTicks %= 60 * 60 * 20
    val minutes = totalTicks / (60 * 20)
    totalTicks %= 60 * 20
    val seconds = totalTicks / 20

    val result = StringBuilder()
    if (weeks > 0) result.append(weeks).append("w ")
    if (days > 0) result.append(days).append("d ")
    if (hours > 0) result.append(hours).append("h ")
    if (minutes > 0) result.append(minutes).append("m ")
    if (seconds > 0) result.append(seconds).append("s ")

    if (result.isEmpty()) result.append("0s")
    else result.deleteCharAt(result.length - 1)

    return result.toString()
}