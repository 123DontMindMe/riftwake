package me.talula.riftwake.theblock

import me.talula.riftwake.RiftwakePlayer
import me.talula.riftwake.utils.InventoryGUI
import me.talula.riftwake.utils.andJoin
import me.talula.riftwake.utils.join
import me.talula.riftwake.utils.joinLore
import me.talula.riftwake.utils.joinLoreLine
import me.talula.riftwake.utils.maxPlaces
import me.talula.riftwake.utils.parseLore
import me.talula.riftwake.utils.playSound
import me.talula.riftwake.utils.subtractItem
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.inventory.ItemStack

abstract class AbstractUpgradeGUI(player: RiftwakePlayer, numRows: Int, title: Component): InventoryGUI(player, numRows, title) {
    protected val block = player.block.block ?:
        throw IllegalStateException("Attempted to create upgrade GUI when player ${player.name} (${player.uniqueId}) has no block")
    private var upgradeButtons = mutableListOf<UpgradeButton>()

    fun updateIcons() {
        for (button in upgradeButtons)
            button.setItem(button.getIcon())
    }

    protected inner class UpgradeButton(val upgrade: Upgrade) : Button((5 - upgrade.slotY) * 9 + upgrade.slotX + 4, null) {
        init {
            upgradeButtons.add(this)
            setItem(getIcon())
        }

        fun getIcon(): ItemStack {
            if (upgrade.dependencies.any { block.isLocked(it) })
                return emptyIcon

            val currentLevel = block.getLevel(upgrade.key)
            val weight = upgrade.weightPerLevel * currentLevel

            val unsatisfied = upgrade.dependencies.filter { !block.hasPurchased(it.key) }
            if (unsatisfied.isNotEmpty())
                return createIcon(upgrade.name, upgrade.icon, NamedTextColor.RED.joinLore(
                    "Requires ", unsatisfied.map { it.name }.andJoin(), " to unlock."))

            val cost = upgrade.getCost(currentLevel)
            val canAfford = player.inventory.contains(upgrade.upgradeItem, cost)
            return createIcon(
                name=NamedTextColor.GRAY.join(upgrade.name, " (Level ", currentLevel + 1, ")"),
                material=upgrade.icon,
                amount=currentLevel + 1,
                "<YELLOW|<>% → <>% chance>".parseLore(weight.maxPlaces(2), (weight + upgrade.weightPerLevel).maxPlaces(2)),
                (if (canAfford) NamedTextColor.GREEN else NamedTextColor.RED).joinLoreLine(
                    "Cost: ", cost, " ", Component.translatable(upgrade.upgradeItem)
                ),
                *upgrade.description
            )
        }

        override fun onClick() {
            if (upgrade.dependencies.any { !block.hasPurchased(it.key) }) {
                player.playSound(Sound.ENTITY_VILLAGER_NO, SoundCategory.UI, 1f, 1f)
                return
            }
            if (!player.subtractItem(upgrade.upgradeItem, upgrade.getCost(block.getLevel(upgrade.key)))) {
                player.playSound(Sound.ENTITY_VILLAGER_NO, SoundCategory.UI, 1f, 1f)
                return
            }
            block.upgrade(upgrade.key)
            updateIcons()
            player.playSound(Sound.BLOCK_NOTE_BLOCK_HARP, SoundCategory.UI, 1f, 2f)
        }
    }
}