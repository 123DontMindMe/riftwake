package me.talula.riftwake.theblock

import me.talula.riftwake.Riftwake
import me.talula.riftwake.theblock.TreeUpgrade.Companion.random
import me.talula.riftwake.utils.ConfigurationException
import me.talula.riftwake.utils.TieredTable
import me.talula.riftwake.utils.plus
import me.talula.riftwake.utils.red
import me.talula.riftwake.utils.setType
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.TreeType
import org.bukkit.block.Block
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Player
import java.util.UUID

object TheBlockRegistry {
    val file = Riftwake.getConfig("blocks.yml")
    val blocksByOwner: MutableMap<UUID, TheBlock> = HashMap()
    val blocksByLocation: MutableMap<Block, TheBlock> = HashMap()

    init {
        for (key in file.getKeys(false)) {
            val uuid = UUID.fromString(key)
            try {
                register(TheBlock(uuid, file.getConfigurationSection(key)!!))
            } catch (error: ConfigurationException) {
                val owner = Riftwake.instance.server.getOfflinePlayer(uuid)
                Riftwake.broadcastToOperators("error in ${owner.name}'s data (uuid=${owner.uniqueId}) in blocks.yml: ${error.message}".red())
                continue
            }
        }
    }

    operator fun contains(block: Block): Boolean {
        return block in blocksByLocation
    }

    fun register(block: TheBlock, isFromFile: Boolean = false) {
        if (block.owner in blocksByOwner)
            throw IllegalArgumentException("Attempted to register block whose owner already has a block")
        blocksByOwner[block.owner] = block
        blocksByLocation[block.block] = block
        if (!isFromFile)
            file.set(block.owner.toString(), block.serialize())
    }

    fun save() {
        Riftwake.saveConfig(file, "blocks.yml")
        Riftwake.logger.info("Player block data saved to blocks.yml")
    }
}

class TheBlock {
    var block: Block private set
    val owner: UUID
    val upgradeLevels: MutableMap<String, Int> = HashMap()
    private val disabledUpgrades = HashSet<String>()
    val previewTable = TieredTable<Material>()
    val spawnTable = TieredTable<Spawnable>()
    var growthChance = 0.0

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

    fun previewPull() = previewTable.pull()
    fun spawn() = spawnTable.pull().spawn(this)
    fun getLevel(key: String) = upgradeLevels[key] ?: 0
    fun hasPurchased(key: String) = getLevel(key) > 0
    fun isLocked(upgrade: Upgrade) = upgrade.dependencies.any { !hasPurchased(it.key) }
    fun isDisabled(upgrade: Upgrade) = upgrade.key in disabledUpgrades
    fun disable(upgrade: Upgrade) {
        disabledUpgrades.add(upgrade.key)
        upgrade.onUpgrade(this, 0)
    }
    fun enable(upgrade: Upgrade) {
        disabledUpgrades.remove(upgrade.key)
        upgrade.onUpgrade(this, getLevel(upgrade.key))
    }

    fun serialize(): Map<String, Any> = linkedMapOf(
        "location" to "${location.x.toInt()}, ${location.y.toInt()}, ${location.z.toInt()}",
        "upgrades" to upgradeLevels
    )

    fun clearUpgrades() {
        for (key in upgradeLevels.keys)
            UpgradeRegistry.upgrades[key]?.onUpgrade(this, 0)
        upgradeLevels.clear()
    }

    fun upgrade(key: String) {
        val upgrade = UpgradeRegistry.upgrades[key]!!
        val newLevel = getLevel(key) + 1
        upgradeLevels[key] = newLevel
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

    fun getNumFarmingAffordable(player: Player): Int {
        var num = 0
        for ((key, upgrade) in UpgradeRegistry.farmingUpgrades)
            if (player.inventory.contains(upgrade.upgradeItem, upgrade.getCost(getLevel(key))))
                num++
        return num
    }

    fun getNumMiningAffordable(player: Player): Int {
        var num = 0
        for ((key, upgrade) in UpgradeRegistry.miningUpgrades)
            if (player.inventory.contains(upgrade.upgradeItem, upgrade.getCost(getLevel(key))))
                num++
        return num
    }

    fun getNumBuildingAffordable(player: Player): Int {
        var num = 0
        for ((key, upgrade) in UpgradeRegistry.buildingUpgrades)
            if (player.inventory.contains(upgrade.upgradeItem, upgrade.getCost(getLevel(key))))
                num++
        return num
    }
}