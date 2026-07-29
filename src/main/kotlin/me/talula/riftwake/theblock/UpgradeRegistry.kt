package me.talula.riftwake.theblock

import me.talula.riftwake.Riftwake
import me.talula.riftwake.utils.*
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.TreeType
import org.bukkit.block.BlockFace
import org.bukkit.block.data.Ageable
import org.bukkit.block.data.BlockData
import org.bukkit.block.data.Directional
import org.bukkit.block.data.type.CaveVinesPlant
import org.bukkit.configuration.ConfigurationSection
import java.util.*

object UpgradeRegistry {
    val miningFile = Riftwake.Config("mining_upgrades.yml")
    val farmingFile = Riftwake.Config("farming_upgrades.yml")
    val buildingFile = Riftwake.Config("building_upgrades.yml")

    val tiers: Map<String, Int> field = mutableMapOf()
    val miningUpgrades: Map<String, Upgrade> field = mutableMapOf()
    val farmingUpgrades: Map<String, Upgrade> field = mutableMapOf()
    val buildingUpgrades: Map<String, Upgrade> field = mutableMapOf()
    val upgrades: Map<String, Upgrade> field = mutableMapOf()
    var maxUpgradeLevels = 0
        private set

    init {
        for ((index, key) in Riftwake.getFile("tiers.txt").readLines().withIndex())
            tiers[key] = index + 2

        for ((key, section) in miningFile.sections) {
            val upgrade = try {
                readUpgrade(key, section)
            } catch (error: ConfigurationException) {
                Riftwake.broadcastToOperators(("config error in upgrade '$key': " + error.message).red)
                continue
            }
            upgrades[key] = upgrade
            miningUpgrades[key] = upgrade
            maxUpgradeLevels += upgrade.maxLevel
        }
        for ((key, section) in farmingFile.sections) {
            val upgrade = try {
                readUpgrade(key, section)
            } catch (error: ConfigurationException) {
                Riftwake.broadcastToOperators(("config error in upgrade $key: " + error.message).red)
                continue
            }
            upgrades[key] = upgrade
            farmingUpgrades[key] = upgrade
            maxUpgradeLevels += upgrade.maxLevel
        }
        for ((key, section) in buildingFile.sections) {
            val upgrade = try {
                readUpgrade(key, section)
            } catch (error: ConfigurationException) {
                Riftwake.broadcastToOperators(("config error in upgrade $key: " + error.message).red)
                continue
            }
            upgrades[key] = upgrade
            buildingUpgrades[key] = upgrade
            maxUpgradeLevels += upgrade.maxLevel
        }
    }

    fun readUpgrade(key: String, data: ConfigurationSection): Upgrade {
        return when (data.getString("type")) {
            "GROWTH_CHANCE" -> GrowthChanceUpgrade(key, data)
            "BLOCK" -> BlockUpgrade(key, data)
            "CROP" -> CropUpgrade(key, data)
            "TREE" -> TreeUpgrade(key, data)
            else -> throw ConfigurationException("Unknown upgrade type $data")
        }
    }
}

abstract class Upgrade {
    val key: String
    val tier: Int
    val dependencies: List<Upgrade>
    val upgradeItems: List<Material>
    val maxLevel: Int
    val weightPerLevel: Double
    val startCost: Int
    val costPower: Double
    val name: Component
    val description: Array<Component>
    val icon: Material
    val slotX: Int
    val slotY: Int

    constructor(key: String, data: ConfigurationSection) {
        this.key = key
        tier = UpgradeRegistry.tiers[key] ?:
            throw ConfigurationException("tier not found")
        dependencies = data.getString("needs")?.split(',')?.map{ s ->
            val k = s.trim()
            UpgradeRegistry.upgrades[k] ?:
                throw ConfigurationException("'needs' contains non-existent key '$k'")
        } ?: listOf()

        upgradeItems = data.getString("upgrade-with")?.split(',')?.map { s -> Material.valueOf(s.trim()) } ?:
            throw ConfigurationException("missing 'upgrade-with'")

        maxLevel = data.getInt("max-level")
        if (maxLevel <= 0)
            throw ConfigurationException("missing 'max-level' (or is ≤0)")

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

        description = data.getString("description")?.split('\n')?.parseLore().orEmpty().toTypedArray()

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

class GrowthChanceUpgrade: Upgrade {
    constructor(key: String, data: ConfigurationSection) : super(key, data)

    override fun onUpgrade(theBlock: TheBlock, newLevel: Int) {
        theBlock.growthChance = weightPerLevel * newLevel / 100
    }
}

interface Spawnable {
    fun spawn(theBlock: TheBlock)
}

open class BlockUpgrade: Upgrade, Spawnable {
    val block: Material

    constructor(key: String, data: ConfigurationSection) : super(key, data) {
        block = data.getString("block")?.let(Material::valueOf) ?:
            throw ConfigurationException("missing 'block'")
    }

    override fun onUpgrade(theBlock: TheBlock, newLevel: Int) {
        val chance = weightPerLevel * newLevel
        theBlock.previewTable.set(tier, chance, block)
        theBlock.spawnTable.set(tier, chance, this)
    }

    override fun spawn(theBlock: TheBlock) {
        theBlock.location.setType(block)
        if (Math.random() < theBlock.growthChance) {
            when (block) {
                Material.SCULK -> {
                    val location = theBlock.location.plus(0, 1, 0)
                    if (location.block.isEmpty)
                        location.setType(listOf(
                            Material.SCULK_VEIN, Material.SCULK_SENSOR,
                            Material.SCULK_CATALYST, Material.SCULK_SHRIEKER
                        ).random())
                }
                Material.AMETHYST_BLOCK -> {
                    theBlock.location.setType(Material.BUDDING_AMETHYST)
                    for (direction in listOf(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN)) {
                        val location = theBlock.location.plus(direction.direction)
                        if (location.block.isEmpty) {
                            location.setBlock(Material.SMALL_AMETHYST_BUD.createBlockData { data ->
                                (data as Directional).facing = direction
                            })
                        }
                    }
                }
                else -> {}
            }
        }
    }
}

class CropUpgrade: BlockUpgrade {
    val crops: List<BlockData>

    companion object {
        val random = Random()
    }

    constructor(key: String, data: ConfigurationSection) : super(key, data) {
        crops = data.getString("crop")?.split(',')?.map{ c ->
            Material.valueOf(c.trim()).createBlockData { data ->
                if (data is Ageable)
                    data.age = data.maximumAge
                if (data is CaveVinesPlant)
                    data.isBerries = true
            }
        } ?: throw ConfigurationException("field 'crop' missing from upgrade '$key'")
    }

    override fun spawn(theBlock: TheBlock) {
        if (Math.random() < theBlock.growthChance) {
            val crop = crops.random()
            var didGrow = true
            when (crop.material) {
                Material.RED_MUSHROOM -> {
                    theBlock.location.setType(Material.PODZOL)
                    Riftwake.world.generateTree(theBlock.location.plus(0, 1, 0), random, TreeType.RED_MUSHROOM)
                }
                Material.BROWN_MUSHROOM -> {
                    theBlock.location.setType(Material.PODZOL)
                    Riftwake.world.generateTree(theBlock.location.plus(0, 1, 0), random, TreeType.BROWN_MUSHROOM)
                }
                Material.CHORUS_FLOWER -> {
                    theBlock.location.setType(Material.END_STONE)
                    Riftwake.world.generateTree(theBlock.location.plus(0, 1, 0), random, TreeType.CHORUS_PLANT)
                }
                Material.SUGAR_CANE -> {
                    theBlock.location.setType(Material.SAND)
                    for (y in 1..random.nextInt(3, 5)) {
                        val location = theBlock.location.plus(0, y, 0)
                        if (location.block.isEmpty)
                            location.setType(Material.SUGAR_CANE)
                        else break
                    }
                }
                Material.BAMBOO -> {
                    theBlock.location.setType(Material.PODZOL)
                    for (y in 1..random.nextInt(5, 10)) {
                        val location = theBlock.location.plus(0, y, 0)
                        if (location.block.isEmpty)
                            location.setType(Material.BAMBOO)
                        else break
                    }
                }
                Material.GLOW_BERRIES -> {
                    theBlock.location.setType(Material.STONE)
                    for (y in 1..random.nextInt(3, 5)) {
                        val location = theBlock.location.plus(0, -y, 0)
                        if (location.block.isEmpty)
                            location.setBlock(crop)
                        else break
                    }
                }
                Material.CACTUS -> {
                    theBlock.location.setType(Material.SAND)
                    for (y in 1..random.nextInt(3, 5)) {
                        val location = theBlock.location.plus(0, y, 0)
                        if (location.block.isEmpty)
                            location.setType(Material.CACTUS)
                        else break
                    }
                }
                else -> didGrow = false
            }
            if (didGrow)
                return
        }

        theBlock.location.setType(block)
        for (entity in theBlock.location.toCenterLocation().getNearbyLivingEntities(0.5))
            entity.location.y = theBlock.block.boundingBox.maxY

        val cropLocation = theBlock.location.plus(0, 1, 0)
        if (cropLocation.block.type == Material.AIR)
            cropLocation.setBlock(crops.random())
    }
}

class TreeUpgrade: BlockUpgrade {
    val treeBlock: Material
    val treeType: TreeType

    companion object {
        val random = Random()
    }

    constructor(key: String, data: ConfigurationSection) : super(key, data) {
        treeBlock = data.getString("tree-block")?.let(Material::valueOf) ?:
            throw ConfigurationException("field 'tree-block' missing from upgrade '$key'")
        treeType = data.getString("tree-type")?.let(TreeType::valueOf) ?:
            throw ConfigurationException("field 'tree-type' missing from upgrade '$key'")
    }

    override fun spawn(theBlock: TheBlock) {
        if (Math.random() > theBlock.growthChance) {
            theBlock.location.setType(block)
            return
        }
        theBlock.location.setType(treeBlock)
        val treeLocation = theBlock.location.plus(0, 1, 0)
        val type = when (treeType) {
            TreeType.SMALL_JUNGLE -> if (Math.random() < 0.1) TreeType.JUNGLE else TreeType.SMALL_JUNGLE
            TreeType.REDWOOD -> if (Math.random() < 0.1) TreeType.MEGA_REDWOOD else TreeType.REDWOOD
            TreeType.PALE_OAK -> if (Math.random() < 0.25) TreeType.PALE_OAK_CREAKING else TreeType.PALE_OAK
            else -> treeType
        }
        Riftwake.world.generateTree(treeLocation, random, type)
    }
}