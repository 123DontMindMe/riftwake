package me.talula.riftwake.theblock

import me.talula.riftwake.Riftwake
import me.talula.riftwake.utils.ConfigurationException
import me.talula.riftwake.utils.RandomTable
import me.talula.riftwake.utils.red
import me.talula.riftwake.utils.setType
import org.bukkit.Location
import org.bukkit.Material
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
    val previewTable = RandomTable<Material>()
    val spawnTable = RandomTable<Spawnable>()

    init {
        previewTable.add(Material.DIRT, 70.0)
        previewTable.add(Material.OAK_LOG, 30.0)

        spawnTable.add(object : Spawnable {
            override fun spawn(location: Location) = location.setType(Material.DIRT)
        }, 70.0)
        spawnTable.add(object : Spawnable {
            override fun spawn(location: Location) = location.setType(Material.OAK_LOG)
        }, 30.0)
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
            spawnTable.pull().spawn(location)
        }

    fun previewPull() = previewTable.pull()
    fun spawn() = spawnTable.pull().spawn(location)
    fun getLevel(key: String) = upgradeLevels[key] ?: 0
    fun hasPurchased(key: String): Boolean = getLevel(key) > 0

    fun serialize(): Map<String, Any> = linkedMapOf(
        "location" to "${location.x.toInt()}, ${location.y.toInt()}, ${location.z.toInt()}",
        "upgrades" to upgradeLevels
    )

    fun upgrade(key: String) {
        val upgrade = UpgradeRegistry.upgrades[key]!!
        val newLevel = getLevel(key) + 1
        upgradeLevels[key] = newLevel
        upgrade.onUpgrade(this, newLevel)
    }

    fun getNumMiningAffordable(player: Player): Int {
        var num = 0
        for ((key, upgrade) in UpgradeRegistry.miningUpgrades)
            if (player.inventory.contains(upgrade.upgradeItem, upgrade.getCost(getLevel(key))))
                num++
        return num
    }

    fun getNumFarmingAffordable(player: Player): Int {
        var num = 0
        for ((key, upgrade) in UpgradeRegistry.farmingUpgrades)
            if (player.inventory.contains(upgrade.upgradeItem, upgrade.getCost(getLevel(key))))
                num++
        return num
    }
}