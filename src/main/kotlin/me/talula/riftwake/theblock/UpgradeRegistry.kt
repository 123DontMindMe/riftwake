package me.talula.riftwake.theblock

import me.talula.riftwake.Riftwake
import me.talula.riftwake.utils.ConfigurationException
import me.talula.riftwake.utils.parse
import me.talula.riftwake.utils.plus
import me.talula.riftwake.utils.pow
import me.talula.riftwake.utils.red
import me.talula.riftwake.utils.setBlock
import me.talula.riftwake.utils.setType
import net.kyori.adventure.text.Component
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.data.Ageable
import org.bukkit.configuration.ConfigurationSection

object UpgradeRegistry {
    val miningFile = Riftwake.getConfig("mining_upgrades.yml")
    val farmingFile = Riftwake.getConfig("farming_upgrades.yml")

    val miningUpgrades: Map<String, MiningUpgrade> field = mutableMapOf()
    val farmingUpgrades: Map<String, FarmingUpgrade> field = mutableMapOf()
    val upgrades: Map<String, Upgrade> field = mutableMapOf()

    init {
        for (key in miningFile.getKeys(false)) {
            val upgrade = try {
                MiningUpgrade(key, miningFile.getConfigurationSection(key)!!)
            } catch (error: ConfigurationException) {
                Riftwake.broadcastToOperators(("config error in upgrade $key: " + error.message).red())
                continue
            }
            upgrades[key] = upgrade
            miningUpgrades[key] = upgrade
        }
        for (key in farmingFile.getKeys(false)) {
            val upgrade = try {
                FarmingUpgrade(key, farmingFile.getConfigurationSection(key)!!)
            } catch (error: ConfigurationException) {
                Riftwake.broadcastToOperators(error.message.red())
                continue
            }
            upgrades[key] = upgrade
            farmingUpgrades[key] = upgrade
        }
    }
}

abstract class Upgrade {
    val key: String
    val dataKey: String
    val dependencies: List<Upgrade>
    val upgradeItem: Material
    val maxLevel: Int
    val weightPerLevel: Double
    val startCost: Int
    val costPower: Double
    val name: Component
    val icon: Material
    val slotX: Int
    val slotY: Int

    constructor(key: String, data: ConfigurationSection) {
        this.key = key
        dataKey = "block-upgrades." + key
        dependencies =
            data.getString("needs")?.split(',')?.map{ s ->
                val k = s.trim()
                if (k in UpgradeRegistry.upgrades)
                    UpgradeRegistry.upgrades[k]!!
                else
                    throw ConfigurationException("'needs' contains non-existent key '$k'")
            } ?: listOf()

        upgradeItem = data.getString("upgrade-with")?.let(Material::valueOf) ?:
            throw ConfigurationException("missing 'upgrade-with'")

        maxLevel = data.getInt("max-level")
        if (maxLevel <= 0)
            throw ConfigurationException("mssing 'max-level' (or is ≤0)")

        weightPerLevel = data.getDouble("chance-per-level")
        if (weightPerLevel <= 0)
            throw ConfigurationException("missing 'chance-per-level' (or is ≤0)")

        startCost = data.getInt("cost-start")
        if (startCost <= 0)
            throw ConfigurationException("missing 'cost-start' (or is ≤0)")

        costPower = data.getDouble("cost-power")
        if (costPower <= 0)
            throw ConfigurationException("missing 'cost-power' (or is ≤0)")

        name = data.getString("name")?.parse() ?:
            throw ConfigurationException("missing 'name'")

        icon = data.getString("icon")?.let(Material::valueOf) ?:
            throw ConfigurationException("missing 'icon'")

        val slotString = data.getString("slot")?.replace(" ", "") ?:
            throw ConfigurationException("missing 'slot'")

        val match = Regex("(-?[0-9]),(-?[0-9])").matchEntire(slotString) ?:
            throw ConfigurationException("'slot' not formatted correctly")

        slotX = match.groupValues[1].toInt()
        slotY = match.groupValues[2].toInt()
    }

    fun getCost(currentLevel: Int) = (startCost * (currentLevel + 1).pow(costPower)).toInt()

    abstract fun onUpgrade(theBlock: TheBlock, newLevel: Int)
}

interface Spawnable {
    fun spawn(location: Location)
}

class MiningUpgrade: Upgrade, Spawnable {
    val block: Material

    constructor(key: String, data: ConfigurationSection) : super(key, data) {
        block = data.getString("block")?.let(Material::valueOf) ?:
            throw ConfigurationException("field 'block' missing from upgrade '$key'")
    }

    override fun onUpgrade(theBlock: TheBlock, newLevel: Int) {
        theBlock.previewTable[block] = weightPerLevel * newLevel
        theBlock.spawnTable[this] = weightPerLevel * newLevel
    }

    override fun spawn(location: Location) {
        location.setType(block)
    }
}

class FarmingUpgrade: Upgrade, Spawnable {
    val block: Material
    val crop: Material

    constructor(key: String, data: ConfigurationSection) : super(key, data) {
        block = data.getString("block")?.let(Material::valueOf) ?:
            throw ConfigurationException("field 'block' missing from upgrade '$key'")
        crop = data.getString("crop")?.let(Material::valueOf) ?:
            throw ConfigurationException("field 'crop' missing from upgrade '$key'")
    }

    override fun onUpgrade(theBlock: TheBlock, newLevel: Int) {
        theBlock.previewTable[block] = weightPerLevel * newLevel
        theBlock.spawnTable[this] = weightPerLevel * newLevel
    }

    override fun spawn(location: Location) {
        location.setType(block)
        val cropLocation = location.plus(0, 1, 0)
        if (cropLocation.block.type == Material.AIR) {
            cropLocation.setBlock(crop) { data ->
                if (data is Ageable)
                    data.age = data.maximumAge
            }
        }
    }
}