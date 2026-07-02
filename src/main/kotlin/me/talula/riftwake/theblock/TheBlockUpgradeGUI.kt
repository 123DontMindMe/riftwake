package me.talula.riftwake.theblock

import me.talula.riftwake.RiftwakePlayer
import me.talula.riftwake.utils.InventoryGUI
import me.talula.riftwake.utils.parse
import me.talula.riftwake.utils.join
import me.talula.riftwake.utils.joinLore
import me.talula.riftwake.utils.joinLoreLine
import me.talula.riftwake.utils.maxPlaces
import me.talula.riftwake.utils.parseLore
import me.talula.riftwake.utils.playSound
import me.talula.riftwake.utils.subtractItem
import me.talula.riftwake.utils.toFixed
import net.kyori.adventure.text.Component

import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.inventory.ItemStack
import org.bukkit.event.inventory.InventoryClickEvent


class TheBlockUpgradeGUI(player: RiftwakePlayer) : InventoryGUI(player, 6, Component.text("Upgrades")) {
    init {
        val stone = UpgradeButton(
            index=49,
            name="<BOLD,GRAY|Stone>".parse(),
            icon=Material.STONE,
            block=Material.STONE,
            upgradeMaterial=Material.DIRT)
        val coal = UpgradeButton(
            index=40,
            name="<BOLD,HEX(#505252)|Coal>".parse(),
            icon=Material.COAL,
            block=Material.COAL_ORE,
            upgradeMaterial=Material.COBBLESTONE,
            stone)
        val iron = UpgradeButton(
            index=31,
            name="<BOLD,WHITE|Iron>".parse(),
            icon=Material.IRON_INGOT,
            block=Material.IRON_ORE,
            upgradeMaterial=Material.COAL,
            coal)
        val gold = UpgradeButton(
            index=22,
            name="<BOLD,HEX(#FFDE4D)|Gold>".parse(),
            icon=Material.GOLD_INGOT,
            block=Material.GOLD_ORE,
            upgradeMaterial=Material.IRON_INGOT,
            iron)
        val diamond = UpgradeButton(
            index=13,
            name="<BOLD,AQUA|Diamond>".parse(),
            icon=Material.DIAMOND,
            block=Material.DIAMOND_ORE,
            upgradeMaterial=Material.GOLD_INGOT,
            gold)
        UpgradeButton(
            index=4,
            name="<BOLD,HEX(#574749)|Netherite>".parse(),
            icon=Material.NETHERITE_INGOT,
            block=Material.ANCIENT_DEBRIS,
            upgradeMaterial=Material.DIAMOND,
            diamond)

        val copper = UpgradeButton(
            index=30,
            name="<BOLD,HEX(#DB652A)|Copper>".parse(),
            icon=Material.COPPER_INGOT,
            block=Material.COPPER_ORE,
            upgradeMaterial=Material.IRON_INGOT,
            iron)
        val redstone = UpgradeButton(
            index=29,
            name="<BOLD,HEX(#8C2E2E)|Redstone>".parse(),
            icon=Material.REDSTONE,
            block=Material.REDSTONE_ORE,
            upgradeMaterial=Material.COPPER_INGOT,
            copper)
        UpgradeButton(
            index=20,
            name="<BOLD,HEX(#FFCFCF)|Quartz>".parse(),
            icon=Material.QUARTZ,
            block=Material.NETHER_QUARTZ_ORE,
            upgradeMaterial=Material.REDSTONE,
            redstone)

        val amethyst = UpgradeButton(
            index=32,
            name="<BOLD,LIGHT_PURPLE|Amethyst>".parse(),
            icon=Material.AMETHYST_SHARD,
            block=Material.AMETHYST_BLOCK,
            upgradeMaterial=Material.IRON_INGOT,
            iron)
        val lapis = UpgradeButton(
            index=33,
            name="<BOLD,BLUE|Lapis>".parse(),
            icon=Material.LAPIS_LAZULI,
            block=Material.LAPIS_ORE,
            upgradeMaterial=Material.AMETHYST_SHARD,
            amethyst)
        UpgradeButton(
            index=24,
            name="<BOLD,GREEN|Emerald>".parse(),
            icon=Material.EMERALD,
            block=Material.EMERALD_ORE,
            upgradeMaterial=Material.LAPIS_LAZULI,
            lapis)

        fillEmpty()
    }

    override fun onClick(event: InventoryClickEvent) {}

    private inner class UpgradeButton(
        index: Int,
        val name: Component,
        val icon: Material,
        val block: Material,
        val upgradeMaterial: Material,
        val dependency: UpgradeButton? = null
    ) : Button(index, null) {
        val unlocks: MutableList<UpgradeButton> = ArrayList()

        init {
            dependency?.unlocks?.add(this)
            setItem(getIcon())
        }

        fun getIcon(): ItemStack {
            val weightPerLevel = player.block.getWeightPerLevel(block)
            val level = player.block.getLevel(block)
            val weight = weightPerLevel * level
            if (dependency != null && !player.block.hasPurchased(dependency.block))
                return createIcon(name, icon, NamedTextColor.RED.joinLore("Requires ", dependency.name, " to unlock."))
            
            val upgradeCost = player.block.getUpgradeCost(block)
            val canAfford = player.inventory.contains(upgradeMaterial, upgradeCost)
            return createIcon(
                name=NamedTextColor.GRAY.join(name, " (Level ", level + 1, ")"),
                material=icon,
                amount=player.block.getLevel(block) + 1,
                "<YELLOW|<>% → <>% chance>".parseLore(weight.maxPlaces(2), (weight + weightPerLevel).maxPlaces(2)),
                (if (canAfford) NamedTextColor.GREEN else NamedTextColor.RED).joinLoreLine(
                    "Cost: ", player.block.getUpgradeCost(block), " ", Component.translatable(upgradeMaterial)
                )
            )
        }

        override fun onClick() {
            if (dependency != null && player.block.getLevel(dependency.block) == 0) {
                player.playSound(Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1f, 1f)
                return
            }
            if (!player.subtractItem(upgradeMaterial, player.block.getUpgradeCost(block))) {
                player.playSound(Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1f, 1f)
                return
            }
            player.block.upgrade(block)
            setItem(getIcon())
            for (unlock in unlocks)
                unlock.setItem(unlock.getIcon())
            player.playSound(Sound.BLOCK_NOTE_BLOCK_HARP, SoundCategory.MASTER, 1f, 2f)
        }
    }
}
