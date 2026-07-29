package me.talula.riftwake.theblock

import me.talula.riftwake.Riftwake
import me.talula.riftwake.theblock.TreeUpgrade.Companion.random
import me.talula.riftwake.utils.*
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.TreeType
import org.bukkit.block.Block
import org.bukkit.configuration.ConfigurationSection
import java.util.*

object TheBlockRegistry {
    val file = Riftwake.Config("blocks.yml")
    val blocksByOwner: MutableMap<UUID, TheBlock> = HashMap()
    val blocksByLocation: MutableMap<Block, TheBlock> = HashMap()

    init {
        for ((key, data) in file.sections) {
            val uuid = UUID.fromString(key)
            try {
                register(TheBlock(uuid, data))
            } catch (error: ConfigurationException) {
                val owner = Riftwake.instance.server.getOfflinePlayer(uuid)
                Riftwake.broadcastToOperators("error in ${owner.name}'s data (uuid=${owner.uniqueId}) in blocks.yml: ${error.message}".red)
                continue
            }
        }
    }

    operator fun contains(block: Block) = block in blocksByLocation
    operator fun contains(owner: UUID) = owner in blocksByOwner
    operator fun get(block: Block) = blocksByLocation[block]
    operator fun get(owner: UUID) = blocksByOwner[owner]

    fun register(block: TheBlock, isFromFile: Boolean = false) {
        if (block.owner in blocksByOwner)
            throw IllegalArgumentException("Attempted to register block whose owner already has a block")
        blocksByOwner[block.owner] = block
        blocksByLocation[block.block] = block
        if (!isFromFile)
            file[block.owner.toString()] = block.serialize()
    }

    fun save() {
        file.save()
        Riftwake.logger.info("Player block data saved to blocks.yml")
    }
}

class TheBlock {
    var block: Block private set
    val owner: UUID
    private val upgradeLevels = mutableMapOf<String, Int>()
    private val disabledUpgrades = mutableSetOf<String>()
    val previewTable = TieredTable<Material>()
    val spawnTable = TieredTable<Spawnable>()
    var growthChance = 0.0
    var totalUpgradeLevels = 0
        private set
    val milestoneLevel get() = totalUpgradeLevels.floorDiv(10)
    val doubleDropsChance get() = milestoneLevel * 0.01

    init {
        previewTable.set(tier=0, chance=70.0, Material.DIRT)
        previewTable.set(tier=1, chance=30.0, Material.OAK_LOG)

        spawnTable.set(tier=0, chance=70.0, object : Spawnable {
            override fun spawn(theBlock: TheBlock) = location.setType(Material.DIRT)
        })
        spawnTable.set(tier=1, chance=30.0, object : Spawnable {
            override fun spawn(theBlock: TheBlock) {
                if (Math.random() > growthChance) {
                    location.setType(Material.OAK_LOG)
                    return
                }
                location.setType(Material.DIRT)
                val treeLocation = theBlock.location.plus(0, 1, 0)
                Riftwake.world.generateTree(treeLocation, random, TreeType.TREE)
            }
        })
    }

    constructor(owner: UUID, data: ConfigurationSection) {
        this.owner = owner

        val locationString = data.getString("location")?.replace(" ", "") ?:
            throw ConfigurationException("missing 'location'")

        val match = Regex("(-?[0-9]+),(-?[0-9]+),(-?[0-9]+)").matchEntire(locationString) ?:
            throw ConfigurationException("'location' not formatted correctly")

        block = Riftwake.world.getBlockAt(
            match.groupValues[1].toInt(), match.groupValues[2].toInt(), match.groupValues[3].toInt())

        val upgrades = data.getConfigurationSection("upgrades") ?: return
        for (key in upgrades.getKeys(false)) {
            val upgrade = UpgradeRegistry.upgrades[key] ?: throw ConfigurationException("non-existent upgrade '$key'")
            val level = upgrades.getInt(key)
            upgradeLevels[key] = level
            totalUpgradeLevels += level
            upgrade.onUpgrade(this, level)
        }
    }

    constructor(owner: UUID, location: Location) {
        this.owner = owner
        block = location.block
        spawn()
    }

    var location: Location
        get() = block.location
        set(location) {
            block.location.setType(Material.AIR)
            TheBlockRegistry.blocksByLocation.remove(block)

            block = Riftwake.world.getBlockAt(location)
            TheBlockRegistry.blocksByLocation[block] = this
            spawnTable.pull().spawn(this)
        }

    fun spawn() = spawnTable.pull().spawn(this)
    fun getLevel(upgrade: Upgrade) = upgradeLevels[upgrade.key] ?: 0
    fun hasPurchased(upgrade: Upgrade) = getLevel(upgrade) > 0
    fun isLocked(upgrade: Upgrade) = upgrade.dependencies.any { !hasPurchased(it) }
    fun isDisabled(upgrade: Upgrade) = upgrade.key in disabledUpgrades
    fun disable(upgrade: Upgrade) {
        disabledUpgrades.add(upgrade.key)
        upgrade.onUpgrade(this, 0)
    }
    fun enable(upgrade: Upgrade) {
        disabledUpgrades.remove(upgrade.key)
        upgrade.onUpgrade(this, getLevel(upgrade))
    }

    fun serialize(): Map<String, Any> = linkedMapOf(
        "location" to "${location.x.toInt()}, ${location.y.toInt()}, ${location.z.toInt()}",
        "upgrades" to upgradeLevels
    )

    fun clearUpgrades() {
        for (key in upgradeLevels.keys)
            UpgradeRegistry.upgrades[key]?.onUpgrade(this, 0)
        upgradeLevels.clear()
        totalUpgradeLevels = 0
    }

    fun upgrade(upgrade: Upgrade) {
        val newLevel = getLevel(upgrade) + 1
        upgradeLevels[upgrade.key] = newLevel
        totalUpgradeLevels++
        if (!isDisabled(upgrade))
            upgrade.onUpgrade(this, newLevel)
    }

    val numMiningPurchased: Int get() = upgradeLevels.count { (key, level) ->
        key in UpgradeRegistry.miningUpgrades && level > 0
    }
    val numFarmingPurchased: Int get() = upgradeLevels.count { (key, level) ->
        key in UpgradeRegistry.farmingUpgrades && level > 0
    }
    val numBuildingPurchased: Int get() = upgradeLevels.count { (key, level) ->
        key in UpgradeRegistry.buildingUpgrades && level > 0
    }

    val numMiningDisabled: Int get() = upgradeLevels.keys.count { key ->
        key in UpgradeRegistry.miningUpgrades && key in disabledUpgrades
    }
    val numFarmingDisabled: Int get() = upgradeLevels.keys.count { key ->
        key in UpgradeRegistry.farmingUpgrades && key in disabledUpgrades
    }
    val numBuildingDisabled: Int get() = upgradeLevels.keys.count { key ->
        key in UpgradeRegistry.buildingUpgrades && key in disabledUpgrades
    }
}