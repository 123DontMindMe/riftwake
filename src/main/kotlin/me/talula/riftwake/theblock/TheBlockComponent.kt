package me.talula.riftwake.theblock

import me.talula.riftwake.Riftwake
import me.talula.riftwake.RiftwakePlayer
import me.talula.riftwake.utils.RandomTable
import me.talula.riftwake.utils.getData
import me.talula.riftwake.utils.pow
import me.talula.riftwake.utils.setData
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.persistence.PersistentDataType
import org.bukkit.util.Vector
import java.util.*

class TheBlockComponent(val player: RiftwakePlayer) {
    private val upgrades: MutableMap<Material, UpgradeInfo> = EnumMap(Material::class.java)
    private val blockTable = RandomTable<Material>()

    var blockLocation: Location? = null
        set(location) {
            val previousLocation = field
            if (previousLocation == null) {
                if (location != null)
                    player.world.setType(location, blockTable.pull())
            } else {
                if (location != null)
                    player.world.setType(location, player.world.getType(previousLocation))
                player.world.setType(previousLocation, Material.AIR)
            }
            field = location
        }

    inner class UpgradeInfo(
        key: String,
        val weightPerLevel: Double,
        val startCost: Int,
        val costPower: Double,
    ) {
        val dataKey = "block-upgrades." + key
        var level = player.getData(dataKey, PersistentDataType.INTEGER) ?: 0
        val currentWeight: Double
            get() = weightPerLevel * level
    }

    init {
        blockTable.add(Material.DIRT, 70.0)
        blockTable.add(Material.OAK_LOG, 30.0)

        upgrades[Material.STONE] = UpgradeInfo("stone",
            weightPerLevel = 2.5,
            startCost = 1,
            costPower = 2.55)
        upgrades[Material.COAL_ORE] = UpgradeInfo("coal",
            weightPerLevel = 1.0,
            startCost = 3,
            costPower = 1.5)
        upgrades[Material.IRON_ORE] = UpgradeInfo("iron",
            weightPerLevel = 1.0,
            startCost = 3,
            costPower = 1.5)
        upgrades[Material.GOLD_ORE] = UpgradeInfo("gold",
            weightPerLevel = 0.25,
            startCost = 5,
            costPower = 1.3)
        upgrades[Material.DIAMOND_ORE] = UpgradeInfo("diamond",
            weightPerLevel = 0.1,
            startCost = 10,
            costPower = 1.15)
        upgrades[Material.ANCIENT_DEBRIS] = UpgradeInfo("netherite",
            weightPerLevel = 0.01,
            startCost = 10,
            costPower = 1.25)

        upgrades[Material.COPPER_ORE] = UpgradeInfo("copper",
            weightPerLevel = 0.3,
            startCost = 4,
            costPower = 1.3)
        upgrades[Material.REDSTONE_ORE] = UpgradeInfo("redstone",
            weightPerLevel = 0.2,
            startCost = 8,
            costPower = 1.2)
        upgrades[Material.NETHER_QUARTZ_ORE] = UpgradeInfo("quartz",
            weightPerLevel = 0.1,
            startCost = 16,
            costPower = 1.05)

        upgrades[Material.AMETHYST_BLOCK] = UpgradeInfo("amethyst",
            weightPerLevel = 0.3,
            startCost = 4,
            costPower = 1.3)
        upgrades[Material.LAPIS_ORE] = UpgradeInfo("lapis",
            weightPerLevel = 0.2,
            startCost = 8,
            costPower = 1.2)
        upgrades[Material.EMERALD_ORE] = UpgradeInfo("emerald",
            weightPerLevel = 0.1,
            startCost = 16,
            costPower = 1.05)

        player.onBreakBlock.addListener { event ->
            val blockLocation = blockLocation ?: return@addListener
            if (event.block.location != blockLocation)
                return@addListener
            event.isDropItems = false
            // even though RiftwakePlayer delegates to Player, some Paper methods like this one explicitly
            // cast the Player to a CraftPlayer (which a RiftwakePlayer obviously isn't), hence passing in
            // the craftPlayer being required
            for (drop in event.getBlock().getDrops(player.inventory.itemInMainHand, player.craftPlayer)) {
                val item = player.world.dropItem(blockLocation.toCenterLocation().add(0.0, 0.5, 0.0), drop)
                item.velocity = Vector(
                    Math.random() * 0.2 - 0.1,
                    Math.random() * 0.02 + 0.1,
                    Math.random() * 0.2 - 0.1
                )
            }
            Riftwake.runTask { player.world.setType(blockLocation, blockTable.pull()) }
        }
    }

    fun getLevel(block: Material) = upgrades[block]!!.level
    fun hasPurchased(block: Material) = upgrades[block]!!.level > 0
    fun getWeightPerLevel(block: Material) = upgrades[block]!!.weightPerLevel
    fun pull() = blockTable.pull()

    fun getUpgradeCost(block: Material): Int {
        val upgradeInfo = upgrades[block]!!
        return (upgradeInfo.startCost * (upgradeInfo.level + 1).pow(upgradeInfo.costPower)).toInt()
    }

    fun upgrade(block: Material) {
        val upgradeInfo = upgrades[block]!!
        upgradeInfo.level++
        blockTable.remove(block)
        blockTable.add(block, upgradeInfo.currentWeight)
        player.setData(upgradeInfo.dataKey, PersistentDataType.INTEGER, upgradeInfo.level)
    }
}