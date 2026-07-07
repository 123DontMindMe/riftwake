package me.talula.riftwake.theblock

import me.talula.riftwake.RiftwakePlayer
import me.talula.riftwake.utils.InventoryGUI
import me.talula.riftwake.utils.comp
import me.talula.riftwake.utils.gold
import me.talula.riftwake.utils.green
import me.talula.riftwake.utils.lightPurple
import me.talula.riftwake.utils.lore
import me.talula.riftwake.utils.parseLore
import me.talula.riftwake.utils.playSound
import me.talula.riftwake.utils.plural
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.event.inventory.InventoryClickEvent

class UpgradeMenuGUI(player: RiftwakePlayer): InventoryGUI(player, 3, "Upgrades".comp()) {
    init {
        val block = player.block.block ?:
            throw IllegalStateException("Attempted to create upgrade menu GUI when player ${player.name} (${player.uniqueId}) has no block")
        val numFarming = block.getNumFarmingAffordable(player)
        SimpleButton(11, createIcon("Farming".gold(), Material.MELON_SLICE, getUpgradeText(numFarming))) {
            player.playSound(Sound.ITEM_BOOK_PAGE_TURN, SoundCategory.UI, 1f, 1f)
            FarmingUpgradeGUI(player).open()
        }

        val numMining = block.getNumMiningAffordable(player)
        SimpleButton(13, createIcon("Mining".lightPurple(), Material.STONE_PICKAXE, getUpgradeText(numMining))) {
            player.playSound(Sound.ITEM_BOOK_PAGE_TURN, SoundCategory.UI, 1f, 1f)
            MiningUpgradeGUI(player).open()
        }

        val numBuilding = block.getNumBuildingAffordable(player)
        SimpleButton(15, createIcon("Building".green(), Material.ANDESITE, getUpgradeText(numBuilding))) {
            player.playSound(Sound.ITEM_BOOK_PAGE_TURN, SoundCategory.UI, 1f, 1f)
            BuildingUpgradeGUI(player).open()
        }

        fillEmpty()
    }

    override fun onClick(event: InventoryClickEvent) {}

    private fun getUpgradeText(numUpgrades: Int): Component {
        return if (numUpgrades == 0)
            "You have 0 upgrades you can afford.".lore()
        else
            "You have <GREEN|<>> you can afford.".parseLore(numUpgrades.plural("upgrade"))
    }
}