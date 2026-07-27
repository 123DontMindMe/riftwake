package me.talula.riftwake.utils

import net.kyori.adventure.text.Component

class ConfigurationException(override val message: String): Exception(message)
class CommandFail(val error: Component): Exception() {
    constructor(message: String): this(message.red)
}