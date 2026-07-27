package me.talula.riftwake.theblock

import me.talula.riftwake.RiftwakePlayer
import me.talula.riftwake.utils.comp


class MiningUpgradeGUI(player: RiftwakePlayer): AbstractUpgradeGUI(player, 6, "Upgrades » Mining".comp()) {
    init {
        for (upgrade in UpgradeRegistry.miningUpgrades.values)
            UpgradeButton(upgrade)
        fillEmpty()
    }
}
