package me.talula.riftwake.utils

import me.talula.riftwake.Riftwake
import org.bukkit.event.Listener

abstract class EventListener: Listener {
    init {
        Riftwake.listeners += this
    }

    open fun onDisable() {}
}