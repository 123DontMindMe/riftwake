package me.talula.riftwake.theblock

import me.talula.riftwake.Riftwake
import me.talula.riftwake.RiftwakePlayer
import me.talula.riftwake.constants.IntConstant
import me.talula.riftwake.constants.TimeConstant
import me.talula.riftwake.islands.Structures
import me.talula.riftwake.utils.blockCoords
import me.talula.riftwake.utils.green
import me.talula.riftwake.utils.parse
import me.talula.riftwake.utils.playSound
import me.talula.riftwake.utils.plus
import me.talula.riftwake.utils.red
import me.talula.riftwake.utils.until
import me.talula.riftwake.utils.xzDistance2
import me.talula.riftwake.utils.yellow
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.Vector
import java.util.concurrent.CompletableFuture
import kotlin.math.floor

class TheBlockComponent(val player: RiftwakePlayer) {
    companion object {
        val teleportCooldown = TimeConstant("rtp.cooldown")
        val minY = IntConstant("rtp.min-y")
        val maxY = IntConstant("rtp.max-y")
        val minFromSpawn = IntConstant("rtp.min-from-spawn")
        val minFromBorder = IntConstant("rtp.min-from-border")
    }

    val block get() = TheBlockRegistry.blocksByOwner[player.uniqueId]

    private var lastTeleportTick = -teleportCooldown()
    private var isTeleporting = false
    private var teleportTask: BukkitTask? = null

    init {
        player.onBreakBlock += blockBreak@{ event ->
            val block = TheBlockRegistry.blocksByLocation[event.block] ?: return@blockBreak
            event.isDropItems = false
            val drops = if (event.block.type == Material.AMETHYST_BLOCK)
                listOf(ItemStack.of(Material.AMETHYST_SHARD, 4))
            else
                // even though RiftwakePlayer implements Player, some Paper methods like this one explicitly
                // cast the Player to a CraftPlayer (which a RiftwakePlayer obviously isn't), hence passing in
                // the craftPlayer being required
                event.block.getDrops(player.inventory.itemInMainHand, player.craftPlayer)

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
            else TheBlockRegistry.blocksByLocation[block]?.let {
                player.sendMessage(
                    ("<yellow|This block is owned by <green|${Riftwake.server.getOfflinePlayer(it.owner).name}>. " +
                    "You can still mine it for resources!>").parse())
                player.playSound(Sound.ENTITY_VILLAGER_TRADE, SoundCategory.MASTER, 1f, 1f)
            }
        }

        player.onMove += onMove@{ event ->
            if (isTeleporting && event.hasChangedPosition()) {
                isTeleporting = false
                teleportTask?.cancel()
                teleportTask = null
                player.sendActionBar("Teleport cancelled.".red)
            }
        }
    }

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
        getRandomTeleportLocation().thenAccept {
            if (teleportTask == null)
                // teleport cancelled, don't do anything
                return@thenAccept
            if (secondsLeft > 0)
                // still waiting for count down, just set location
                location = it
            else
                // count down is already done, do the teleport
                finishTeleport(it)
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