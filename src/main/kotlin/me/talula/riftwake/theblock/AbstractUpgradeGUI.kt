package me.talula.riftwake.theblock

import me.talula.riftwake.RiftwakePlayer
import me.talula.riftwake.utils.*
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack

abstract class AbstractUpgradeGUI(player: RiftwakePlayer, numRows: Int, title: Component): InventoryGUI(player, numRows, title) {
    protected val block = player.block.block ?:
        throw IllegalStateException("Attempted to create upgrade GUI when player ${player.name} (${player.uniqueId}) has no block")
    private var upgradeButtons = mutableListOf<UpgradeButton>()

    init {
        SimpleButton((numRows - 1) * 9, createIcon("Back".yellow(), Material.ARROW)) {
            UpgradeMenuGUI(player).open()
            player.playSound(Sound.ITEM_BOOK_PAGE_TURN, SoundCategory.UI, 1.0f, 1.0f)
        }
    }

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
                return createIcon(
                    upgrade.name,
                    upgrade.icon,
                    NamedTextColor.RED.joinLore("Requires ", unsatisfied.map { it.name }.andJoin(), " to unlock."),
                    glint=true)
            val disabled = block.isDisabled(upgrade)

            val name = if (disabled)
                Components.join(upgrade.name.strikethrough(), " (Disabled) ".red(), "(Level ${currentLevel + 1})".gray())
            else
                upgrade.name + " (Level ${currentLevel + 1})".gray()

            val lore = mutableListOf<Component>()
            lore += "<YELLOW|<>% → <>% chance>".parseLore(weight.maxPlaces(2), (weight + upgrade.weightPerLevel).maxPlaces(2))

            val cost = upgrade.getCost(currentLevel)
            val canAffordColor = if (player.inventory.contains(upgrade.upgradeItem, cost))
                NamedTextColor.GREEN else NamedTextColor.RED
            lore += canAffordColor.joinLoreLine("Cost: ", cost, " ", Component.translatable(upgrade.upgradeItem))
            lore.addAll(upgrade.description)

            if (currentLevel > 0) {
                lore += if (disabled)
                    "Right-click to re-enable".green().unitalic()
                else
                    "Right-click to disable".darkGray().unitalic()
            }

            return createIcon(name, upgrade.icon, currentLevel + 1, lore)
        }

        override fun onClick(event: InventoryClickEvent) {
            if (block.isLocked(upgrade)) {
                player.playSound(Sound.ENTITY_VILLAGER_NO, SoundCategory.UI, 1f, 1f)
                return
            }
            if (event.isRightClick && block.hasPurchased(upgrade.key)) {
                if (block.isDisabled(upgrade))
                    block.enable(upgrade)
                else
                    block.disable(upgrade)
                setItem(getIcon())
                player.playSound(Sound.UI_BUTTON_CLICK, SoundCategory.UI, 0.8f, 1f)
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