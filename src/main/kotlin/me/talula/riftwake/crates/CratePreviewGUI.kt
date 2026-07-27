package me.talula.riftwake.crates

import me.talula.riftwake.RiftwakePlayer
import me.talula.riftwake.utils.*
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent

class CratePreviewGUI(player: RiftwakePlayer, crate: Crate): InventoryGUI(player, 6, "Previewing ".comp() + crate.name) {
    init {
        for ((i, entry) in crate.entries.withIndex()) {
            val item = entry.value.withRandomUUID()
            val lore = item.lore() ?: mutableListOf()
            lore += "Win chance: ${(entry.currentChance * 100).sigFigs(3)}%".yellow.unitalic
            item.lore(lore)
            StaticButton(i, item)
        }
        fillClear()
    }

    override fun onClick(event: InventoryClickEvent) {
        event.isCancelled = true
    }

    override fun onDrag(event: InventoryDragEvent) {
        for (slot in event.rawSlots) if (slot < 9 * 6) {
            event.isCancelled = true
            break
        }
    }

    override fun onPlayerInventoryClick(event: InventoryClickEvent) {
        if (event.isShiftClick)
            event.isCancelled = true
    }
}