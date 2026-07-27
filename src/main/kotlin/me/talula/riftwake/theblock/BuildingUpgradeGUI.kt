package me.talula.riftwake.theblock

import me.talula.riftwake.RiftwakePlayer
import me.talula.riftwake.utils.comp


class BuildingUpgradeGUI(player: RiftwakePlayer): AbstractUpgradeGUI(player, 6, "Upgrades » Building".comp()) {
    init {
        for (upgrade in UpgradeRegistry.buildingUpgrades.values)
            UpgradeButton(upgrade)
        fillEmpty()
    }
}
