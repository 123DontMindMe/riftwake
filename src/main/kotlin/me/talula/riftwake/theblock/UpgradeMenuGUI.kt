package me.talula.riftwake.theblock

import me.talula.riftwake.RiftwakePlayer
import me.talula.riftwake.utils.*
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.SoundCategory

class UpgradeMenuGUI(player: RiftwakePlayer): InventoryGUI(player, 5, "Upgrades".comp()) {
    init {
        val block = player.block.block ?:
            throw IllegalStateException("Attempted to create upgrade menu GUI when player ${player.name} (${player.uniqueId}) has no block")

        SimpleButton(11, createIcon(
            "Farming".gold + " (${block.numFarmingPurchased}/${UpgradeRegistry.farmingUpgrades.size})".yellow,
            Material.MELON_SLICE,
            getLore(player.block.numFarmingAffordable, block.numFarmingDisabled))
        ) {
            player.playSound(Sound.ITEM_BOOK_PAGE_TURN, SoundCategory.UI, 1f, 1f)
            FarmingUpgradeGUI(player).open()
        }


        SimpleButton(13, createIcon(
            "Mining".lightPurple + " (${ block.numMiningPurchased}/${UpgradeRegistry.miningUpgrades.size})".yellow,
            Material.STONE_PICKAXE,
            getLore(player.block.numMiningAffordable, block.numMiningDisabled))
        ) {
            player.playSound(Sound.ITEM_BOOK_PAGE_TURN, SoundCategory.UI, 1f, 1f)
            MiningUpgradeGUI(player).open()
        }

        SimpleButton(15, createIcon(
            "Building".green + " (${block.numBuildingPurchased}/${UpgradeRegistry.buildingUpgrades.size})".yellow,
            Material.ANDESITE,
            getLore(player.block.numBuildingAffordable, block.numBuildingDisabled))
        ) {
            player.playSound(Sound.ITEM_BOOK_PAGE_TURN, SoundCategory.UI, 1f, 1f)
            BuildingUpgradeGUI(player).open()
        }

        SimpleButton(31, createIcon(
            "Block Milestones".aqua,
            Material.NETHER_STAR,
            listOf(
                "Purchase block upgrades to gain a chance ".gray.unitalic,
                "for double drops when mining any block.".gray.unitalic,
                "".comp(),
                "Your block milestone: ".gray.unitalic + "Milestone ${block.milestoneLevel.roman}".aqua.unitalic,
                ("+${(block.doubleDropsChance * 100).toInt()}% double drop chance".gold).unitalic,
                "".comp(),
                "Click to view milestones!".yellow.unitalic,
            )
        )) {
            player.playSound(Sound.ITEM_BOOK_PAGE_TURN, SoundCategory.UI, 1f, 1f)
            MilestonesGUI(player).open()
        }

        fillEmpty()
    }

    private fun getLore(numUpgrades: Int, numDisabled: Int): List<Component> {
        val lore = mutableListOf<Component>()
        lore += if (numUpgrades == 0)
            "You have 0 upgrades you can afford.".lore()
        else
            "You have <GREEN|<>> you can afford.".parseLore(numUpgrades.plural("upgrade"))

        if (numDisabled > 0)
            lore += "You have <RED|<>> disabled.".parseLore(numDisabled.plural("upgrade"))
        return lore
    }
}

class FarmingUpgradeGUI(player: RiftwakePlayer): AbstractUpgradeGUI(player, 6, "Upgrades » Farming".comp()) {
    init {
        for (upgrade in UpgradeRegistry.farmingUpgrades.values)
            UpgradeButton(upgrade)
        fillEmpty()
    }
}

class BuildingUpgradeGUI(player: RiftwakePlayer): AbstractUpgradeGUI(player, 6, "Upgrades » Building".comp()) {
    init {
        for (upgrade in UpgradeRegistry.buildingUpgrades.values)
            UpgradeButton(upgrade)
        fillEmpty()
    }
}

class MiningUpgradeGUI(player: RiftwakePlayer): AbstractUpgradeGUI(player, 6, "Upgrades » Mining".comp()) {
    init {
        for (upgrade in UpgradeRegistry.miningUpgrades.values)
            UpgradeButton(upgrade)
        fillEmpty()
    }
}
