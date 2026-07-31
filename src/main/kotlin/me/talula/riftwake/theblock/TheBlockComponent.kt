package me.talula.riftwake.theblock

import com.destroystokyo.paper.ParticleBuilder
import me.talula.riftwake.Riftwake
import me.talula.riftwake.RiftwakePlayer
import me.talula.riftwake.constants.IntConstant
import me.talula.riftwake.constants.TimeConstant
import me.talula.riftwake.islands.Structures
import me.talula.riftwake.spawn.SpawnComponent
import me.talula.riftwake.utils.*
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.Vector
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.math.floor

class TheBlockComponent(val player: RiftwakePlayer) {
    companion object {
        val teleportCooldown = TimeConstant("rtp.cooldown")
        val minY = IntConstant("rtp.min-y")
        val maxY = IntConstant("rtp.max-y")
        val minFromSpawn = IntConstant("rtp.min-from-spawn")
        val minFromBorder = IntConstant("rtp.min-from-border")

        val lastRtpTick = mutableMapOf<UUID, Int>()
    }

    val block get() = TheBlockRegistry.blocksByOwner[player.uniqueId]

    private var lastTeleportTick = lastRtpTick[player.uniqueId] ?: -teleportCooldown()
        set(value) {
            field = value
            lastRtpTick[player.uniqueId] = value
        }
    private var isTeleporting = false
    private var teleportTask: BukkitTask? = null

    init {
        player.onPlaceBlock += blockPlace@{ event ->
            if (SpawnComponent.isInSpawn(event.block.location))
                return@blockPlace
            if (!event.isCancelled)
                PlayerPlacedRegistry.registerBlock(event.block)
        }

        player.onMultiPlaceBlock += blockPlace@{ event ->
            if (SpawnComponent.isInSpawn(event.block.location))
                return@blockPlace
            for (state in event.replacedBlockStates)
                PlayerPlacedRegistry.registerBlock(state.block)
        }

        player.onBlockDropItems += blockDrops@{ event ->
            if (SpawnComponent.isInSpawn(event.block.location))
                return@blockDrops
            val wasPlayerPlaced = PlayerPlacedRegistry.unregisterBlock(event.block)
            if (!wasPlayerPlaced) {
                if (event.block.type == Material.CHEST)
                    return@blockDrops

                val block = block ?: return@blockDrops
                if (Math.random() < block.doubleDropsChance) {
                    val iterator = event.items.listIterator()
                    while (iterator.hasNext()) {
                        val item = iterator.next()
                        val amount = item.itemStack.amount * 2
                        if (amount > item.itemStack.maxStackSize)
                            iterator.add(item)  // inserts before the current element so it doesn't go forever
                        else
                            item.itemStack.amount = amount
                    }
                    player.playSound(Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.BLOCKS, 1f, 2f)
                    ParticleBuilder(Particle.COMPOSTER)
                        .location(block.location.toCenterLocation())
                        .count(10)
                        .offset(0.5, 0.5, 0.5)
                        .spawn()
                }
            }
        }

        player.onBreakBlock += blockBreak@{ event ->
            val block = TheBlockRegistry[event.block] ?: return@blockBreak
            event.isDropItems = false
            var drops = if (event.block.type == Material.AMETHYST_BLOCK)
                listOf(ItemStack.of(Material.AMETHYST_SHARD, 4))
            else
                // even though RiftwakePlayer implements Player, some Paper methods like this one explicitly
                // cast the Player to a CraftPlayer (which a RiftwakePlayer obviously isn't), hence passing in
                // the craftPlayer being required
                event.block.getDrops(player.inventory.itemInMainHand, player.craftPlayer)

            val wasPlayerPlaced = PlayerPlacedRegistry.unregisterBlock(event.block)
            println("unregistered: " + !wasPlayerPlaced)
            val ourBlock = this.block
            if (!wasPlayerPlaced && ourBlock != null) {
                println("roll double drop")
                if (Math.random() < block.doubleDropsChance) {
                    println("success")
                    val newDrops = mutableListOf<ItemStack>()
                    for (drop in drops) {
                        val amount = drop.amount * 2
                        if (amount > drop.maxStackSize) {
                            newDrops += drop
                            newDrops += drop
                        }
                        else
                            newDrops += drop.asQuantity(amount)
                    }
                    drops = newDrops
                    player.playSound(Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.BLOCKS, 1f, 2f)
                    ParticleBuilder(Particle.COMPOSTER)
                        .location(block.location.toCenterLocation())
                        .count(10)
                        .offset(0.5, 0.5, 0.5)
                        .spawn()
                }
            }

            for (drop in drops) {
                val item = player.world.dropItem(block.location.toCenterLocation().add(0.0, 0.5, 0.0), drop)
                item.velocity = Vector(
                    Math.random() * 0.2 - 0.1,
                    Math.random() * 0.02 + 0.1,
                    Math.random() * 0.2 - 0.1
                )
            }
            Riftwake.runTask { block.spawn() }
        }
        player.onRightClickBlock += rightClick@{ event, block ->
            if (player.isSneaking)
                return@rightClick
            if (block.location == this.block?.location) {
                UpgradeMenuGUI(player).open()
                event.isCancelled = true
            }
            else TheBlockRegistry[block]?.let {
                player.sendMessage(
                    ("<yellow|This block is owned by <green|${Riftwake.server.getOfflinePlayer(it.owner).name}>. " +
                    "You can still mine it for resources!>").parse())
                player.playSound(Sound.ENTITY_VILLAGER_TRADE, SoundCategory.MASTER, 1f, 1f)
            }
        }

        player.onMove += onMove@{ event ->
            if (teleportTask != null && event.hasChangedPosition()) {
                isTeleporting = false
                teleportTask?.cancel()
                teleportTask = null
                player.sendActionBar("Teleport cancelled.".red)
            }
        }
    }

    fun canAfford(upgrade: Upgrade): Boolean {
        val block = block ?: throw IllegalStateException("Player doesn't have a block yet")
        val cost = upgrade.getCost(block.getLevel(upgrade))
        return upgrade.upgradeItems.all { player.inventory.contains(it, cost) }
    }
    val numFarmingAffordable get() = UpgradeRegistry.farmingUpgrades.values.count { canAfford(it) }
    val numMiningAffordable get() = UpgradeRegistry.miningUpgrades.values.count { canAfford(it) }
    val numBuildingAffordable get() = UpgradeRegistry.buildingUpgrades.values.count { canAfford(it) }

    fun setBlockLocation(location: Location) {
        val currentBlock = block
        if (currentBlock != null) {
            currentBlock.location = location
            return
        }
        val block = TheBlock(player.uniqueId, location)
        TheBlockRegistry.register(block)
    }

    // null case is for when there's no block yet
    fun previewPull() = block?.previewTable?.pull() ?: (if (Math.random() < 0.7) Material.DIRT else Material.OAK_LOG)

    val randomTeleportCooldownRemaining get() = teleportCooldown() - (Riftwake.server.currentTick - lastTeleportTick)

    fun randomTeleportNow() {
        if (isTeleporting)
            return
        isTeleporting = true
        player.sendActionBar("Teleporting you to a random location...".yellow)
        getRandomTeleportLocation().thenAccept { location ->
            Riftwake.runTask {
                if (block == null)
                    setBlockLocation(location)
                else
                    Riftwake.world.setType(location, Material.GLASS)
                player.teleport(location.plus(0.5, 1.0, 0.5))
                player.sendActionBar("You have been teleported to (${location.blockCoords}).".green)
                lastTeleportTick = Riftwake.server.currentTick
                isTeleporting = false
            }
        }
    }

    fun startRandomTeleport() {
        if (isTeleporting)
            return
        isTeleporting = true

        fun finishTeleport(location: Location) {
            if (block == null)
                setBlockLocation(location)
            else
                Riftwake.world.setType(location, Material.GLASS)
            player.teleport(location.plus(0.5, 1.0, 0.5))
            player.sendActionBar("You have been teleported to (${location.blockCoords}).".green)
            lastTeleportTick = Riftwake.server.currentTick
            isTeleporting = false
        }

        var location: Location? = null
        var secondsLeft = 5
        getRandomTeleportLocation().thenAccept { l ->
            if (teleportTask == null)
                // teleport cancelled, don't do anything
                return@thenAccept
            if (secondsLeft > 0)
                // still waiting for count down, just set location
                location = l
            else
                // count down is already done, do the teleport
                Riftwake.runTask { finishTeleport(l) }
        }

        teleportTask = Riftwake.runTaskTimer(0, 20) { task ->
            if (secondsLeft > 0) {
                player.sendActionBar("Teleporting you to a random location in $secondsLeft...".yellow)
                secondsLeft--
                return@runTaskTimer
            }
            if (location == null)
                // count down done but teleport location not found yet, keep sending action bar
                player.sendActionBar("Teleporting now...".yellow)
            else if (isTeleporting) {
                // count down done and teleport location found beforehand, so do the teleport
                task.cancel()
                finishTeleport(location)
            } else
                // count down done and teleport location found afterward, so it already teleported
                task.cancel()
        }
    }

    private fun getRandomTeleportLocation(): CompletableFuture<Location> {
        val r = Structures.worldRadius() - minFromBorder()
        val location = {
            Location(Riftwake.world, randomBetween(-r, r), randomBetween(minY(), maxY()), randomBetween(-r, r))
        } until { it.xzDistance2() > minFromSpawn() }

        return Riftwake.world.getChunkAtAsync(location).thenComposeAsync {
            if (!isTeleporting)
                // return early if teleport was cancelled
                return@thenComposeAsync CompletableFuture.completedFuture(null)

            for (y in -10..10)
                if (!location.block.getRelative(0, y, 0).isEmpty)
                    return@thenComposeAsync getRandomTeleportLocation()
            CompletableFuture.completedFuture(location)
        }
    }

    private fun randomBetween(min: Int, max: Int) = floor(Math.random() * (max - min) + min)
}