package me.talula.riftwake.theblock

import me.talula.riftwake.RiftwakePlayer
import me.talula.riftwake.utils.comp
import org.bukkit.event.inventory.InventoryClickEvent


class FarmingUpgradeGUI(player: RiftwakePlayer): AbstractUpgradeGUI(player, 6, "Upgrades » Farming".comp()) {
    init {
        for (upgrade in UpgradeRegistry.farmingUpgrades.values)
            UpgradeButton(upgrade)
        fillEmpty()
    }

    override fun onClick(event: InventoryClickEvent) {}
}
