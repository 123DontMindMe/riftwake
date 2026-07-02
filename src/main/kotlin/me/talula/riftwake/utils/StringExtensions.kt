package me.talula.riftwake.utils

import java.text.DecimalFormat

fun Double.maxPlaces(decimals: Int): String {
    return DecimalFormat("0." + "#".repeat(decimals)).format(this)
}

fun Double.toFixed(decimals: Int): String {
    return String.format("%.${decimals}f", this)
}
