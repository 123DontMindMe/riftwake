package me.talula.riftwake.theblock

import me.talula.riftwake.RiftwakePlayer
import me.talula.riftwake.utils.InventoryGUI
import me.talula.riftwake.utils.aqua
import me.talula.riftwake.utils.comp
import me.talula.riftwake.utils.gold
import me.talula.riftwake.utils.gray
import me.talula.riftwake.utils.green
import me.talula.riftwake.utils.ordinal
import me.talula.riftwake.utils.playSound
import me.talula.riftwake.utils.plus
import me.talula.riftwake.utils.roman
import me.talula.riftwake.utils.unitalic
import me.talula.riftwake.utils.yellow
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.SoundCategory

class MilestonesGUI(player: RiftwakePlayer): InventoryGUI(player, 6, "Upgrades » Milestones".comp()) {
    val block = player.block.block ?:
        throw IllegalStateException("Attempted to create a MilestonesGUI when player doesn't have a block")
    val numLevels = block.totalUpgradeLevels
    val currentMilestone = block.milestoneLevel

    init {
        createMilestone(1, 36)
        createMilestone(2, 27)
        createMilestone(3, 18)
        createMilestone(4, 9)
        createMilestone(5, 0)
        createMilestone(6, 1)
        createMilestone(7, 2)
        createMilestone(8, 11)
        createMilestone(9, 20)
        createMilestone(10, 29)
        createMilestone(11, 38)
        createMilestone(12, 39)
        createMilestone(13, 40)
        createMilestone(14, 31)
        createMilestone(15, 22)
        createMilestone(16, 13)
        createMilestone(17, 4)
        createMilestone(18, 5)
        createMilestone(19, 6)
        createMilestone(20, 15)
        createMilestone(21, 24)
        createMilestone(22, 33)
        createMilestone(23, 42)
        createMilestone(24, 43)
        createMilestone(25, 44)
        createMilestone(26, 35)
        createMilestone(27, 26)
        createMilestone(28, 17)
        createMilestone(29, 8)

        SimpleButton(49, createIcon("Back".yellow, Material.ARROW)) {
            UpgradeMenuGUI(player).open()
            player.playSound(Sound.ITEM_BOOK_PAGE_TURN, SoundCategory.UI, 1.0f, 1.0f)
        }
    }

    fun createMilestone(milestone: Int, index: Int) {
        val levelsNeeded = milestone * 10
        if (milestone <= currentMilestone) {
            StaticButton(index, createIcon(
                "${milestone.ordinal} Milestone".gold,
                Material.LIME_STAINED_GLASS_PANE,
                ("Purchase ".gray + "$levelsNeeded upgrades".gold + " for a ".gray + "$milestone% chance".gold).unitalic,
                ("to receive ".gray + "double drops".green + " when mining any block.".gray).unitalic,
                "".comp(),
                ("Progress to Milestone ${milestone.roman}: ".gray + "UNLOCKED".green).unitalic,
                ("${"-".repeat(30)} $numLevels/$levelsNeeded").green.unitalic,
            ))
            return
        }
        val progress = numLevels.toDouble() / (milestone * 10)
        val numFilled = (progress * 30).toInt()
        val numEmpty = 30 - numFilled
        StaticButton(index, createIcon(
            "${milestone.ordinal} Milestone".gold,
            if (milestone == currentMilestone + 1) Material.YELLOW_STAINED_GLASS_PANE else Material.GRAY_STAINED_GLASS_PANE,
            ("Purchase ".gray + "$levelsNeeded upgrades".gold + " for a ".gray + "$milestone% chance".gold).unitalic,
            ("to receive ".gray + "double drops".green + " when mining any block.".gray).unitalic,
            "".comp(),
            ("Progress to Milestone ${milestone.roman}: ".gray + "${(progress * 100).toInt()}%".aqua).unitalic,
            ("-".repeat(numFilled).aqua + "-".repeat(numEmpty).gray + " $numLevels/$levelsNeeded".aqua).unitalic,
        ))
    }
}