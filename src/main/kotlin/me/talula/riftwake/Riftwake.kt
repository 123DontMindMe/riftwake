package me.talula.riftwake

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.event.PacketListener
import com.github.retrooper.packetevents.event.PacketListenerPriority
import com.github.retrooper.packetevents.event.PacketReceiveEvent
import com.github.retrooper.packetevents.protocol.packettype.PacketType
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity
import com.mojang.brigadier.arguments.LongArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats
import com.sk89q.worldedit.function.mask.BlockTypeMask
import com.sk89q.worldedit.function.operation.Operations
import com.sk89q.worldedit.math.BlockVector3
import com.sk89q.worldedit.regions.CuboidRegion
import com.sk89q.worldedit.session.ClipboardHolder
import com.sk89q.worldedit.util.SideEffect
import com.sk89q.worldedit.world.block.BlockTypes
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.event.player.AsyncChatEvent
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import me.talula.riftwake.constants.Constant
import me.talula.riftwake.dialogue.PlaceBlockStage
import me.talula.riftwake.economy.AuctionRegistry
import me.talula.riftwake.items.Items
import me.talula.riftwake.theblock.TheBlockRegistry
import me.talula.riftwake.theblock.UpgradeMenuGUI
import me.talula.riftwake.utils.*
import me.talula.riftwake.utils.LayerTable.Layer
import net.kyori.adventure.text.Component
import net.luckperms.api.LuckPerms
import net.luckperms.api.LuckPermsProvider
import org.apache.commons.lang3.mutable.MutableObject
import org.bukkit.*
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.*
import org.bukkit.event.entity.EntityChangeBlockEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityPlaceEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.player.*
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import org.bukkit.scoreboard.Team
import java.io.File
import java.io.FileInputStream


class Riftwake : JavaPlugin(), Listener, PacketListener {
    companion object {
        lateinit var instance: Riftwake private set
        lateinit var combatAllowedCommands: Set<String> private set
        lateinit var luckPerms: LuckPerms private set
        val world get() = instance.server.getWorld("world") ?: throw RuntimeException("world not found")
        val logger get() = instance.componentLogger
        val server get() = instance.server

        val playerRegistry: MutableMap<Player, RiftwakePlayer> = HashMap()

        fun runTask(task: (BukkitTask) -> Unit) {
            Bukkit.getScheduler().runTask(instance, task)
        }

        fun runTaskLater(delay: Long, task: (BukkitTask) -> Unit): BukkitTask {
            val reference = MutableObject<BukkitTask>()
            reference.value = Bukkit.getScheduler().runTaskLater(instance, Runnable {
                task(reference.value)
            }, delay)
            return reference.value
        }

        fun runTaskTimer(delay: Long, interval: Long, task: (BukkitTask) -> Unit): BukkitTask {
            val reference = MutableObject<BukkitTask>()
            reference.value = Bukkit.getScheduler().runTaskTimer(instance, Runnable {
                task(reference.value)
            }, delay, interval)
            return reference.value
        }

        fun broadcastToOperators(message: String) {
            for (player in Bukkit.getOnlinePlayers())
                if (player.isOp)
                    player.sendMessage(message)
        }

        fun broadcastToOperators(message: Component) {
            for (player in Bukkit.getOnlinePlayers())
                if (player.isOp)
                    player.sendMessage(message)
        }

        fun getConfig(pathInDataFolder: String): YamlConfiguration {
            return YamlConfiguration.loadConfiguration(File(instance.dataFolder, pathInDataFolder))
        }

        fun getFile(pathInDataFolder: String): File {
            return File(instance.dataFolder, pathInDataFolder)
        }

        fun saveConfig(file: YamlConfiguration, pathInDataFolder: String) {
            file.save(File(instance.dataFolder, pathInDataFolder))
        }

        fun registerCommand(command: LiteralArgumentBuilder<CommandSourceStack>) {
            instance.lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { commands ->
                commands.registrar().register(command.build())
            }
        }
    }

    override fun onEnable() {
        instance = this
        combatAllowedCommands = getFile("combat_commands.txt").readLines().filter { it.isNotBlank() }.toSet()
        luckPerms = LuckPermsProvider.get()

        server.pluginManager.registerEvents(this, this)
        PacketEvents.getAPI().eventManager.registerListener(this, PacketListenerPriority.NORMAL)

        val scoreboard = server.scoreboardManager.mainScoreboard
        if (scoreboard.getTeam("in-spawn") == null) {
            val team = scoreboard.registerNewTeam("in-spawn")
            team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER)
        }

        registerCommand(Commands.literal("pdc")
            .requires { ctx -> ctx.sender.isOp }
            .then(Commands.literal("clear")
                .executes { ctx ->
                    val player = ctx.source.sender as? Player ?: return@executes 0
                    for (key in player.persistentDataContainer.keys)
                        if (key.namespace == "riftwake")
                            player.persistentDataContainer.remove(key)
                    player.sendMessage("Riftwake player data cleared.".green)
                    1
                }
            )
            .then(Commands.argument("key", StringArgumentType.string())
                .suggests { ctx, builder ->
                    val player = ctx.source.sender as? Player ?: return@suggests builder.buildFuture()
                    for (key in player.persistentDataContainer.keys)
                        if (key.namespace == "riftwake")
                            builder.suggest(key.key)
                    builder.buildFuture()
                }
                .then(Commands.argument("type", StringArgumentType.string())
                    .suggests { _, builder ->
                        builder.suggest("byte")
                        builder.suggest("short")
                        builder.suggest("int")
                        builder.suggest("long")
                        builder.suggest("float")
                        builder.suggest("double")
                        builder.suggest("bool")
                        builder.suggest("string")
                        builder.suggest("byte[]")
                        builder.suggest("int[]")
                        builder.suggest("long[]")
                        builder.buildFuture()
                    }
                    .executes { ctx ->
                        val player = ctx.source.sender as? Player ?: return@executes 0
                        val key = ctx.getArgument("key", String::class.java)
                        val type = ctx.getArgument("type", String::class.java).toPersistentDataType()
                        if (type == null) {
                            player.sendMessage("Not a valid type.".red)
                            return@executes 0
                        }
                        val value = player.getData(key, type)
                        if (value == null) {
                            player.sendMessage("No data found".red)
                            return@executes 0
                        }
                        if (value is Array<*>)
                            player.sendMessage((key + "=" + value.contentToString()).green)
                        else
                            player.sendMessage((key + "=" + value.toString()).green)
                        1
                    }
                    .then(Commands.argument("value", StringArgumentType.greedyString())
                        .playerRun { ctx, player ->
                            val key = ctx.getArgument("key", String::class.java)
                            val type = ctx.getArgument("type", String::class.java).toPersistentDataType()
                            if (type == null) {
                                player.sendMessage("Not a valid type.".red)
                                return@playerRun false
                            }
                            val oldValue = player.getData(key, type)
                            if (oldValue == null) {
                                player.sendMessage("That key doesn't exist".red)
                                return@playerRun false
                            }

                            val newValue = try {
                                player.setDataFromString(key, type, ctx.getArgument("value", String::class.java))
                            } catch (error: IllegalArgumentException) {
                                player.sendMessage("Invalid value: ${error.message}".red)
                                return@playerRun false
                            }

                            val oldString = if (oldValue is Array<*>) oldValue.contentToString() else oldValue.toString()
                            val newString = if (newValue is Array<*>) newValue.contentToString() else newValue.toString()
                            player.sendMessage(("$key=$oldString → $newString").green)
                            true
                        }
                    )
                )
            )
        )

        registerCommand(Commands.literal("createblock")
            .executes { ctx ->
                val player = ctx.source.sender.riftwake ?: return@executes 0
                player.dialogue.start(
                    cancelMessage = "Cancelled block placement.".red,
                    PlaceBlockStage()
                )
                1
            }
        )

        registerCommand(Commands.literal("blockmenu")
            .executes { ctx ->
                val player = ctx.source.sender.riftwake ?: return@executes 0
                if (player.block.block == null) {
                    player.sendMessage(
                        "You don't currently have a block. Place one with ".red + "/createblock".yellow + ".".red
                    )
                }
                UpgradeMenuGUI(player).open()
                1
            }
        )

        registerCommand(Commands.literal("clearupgrades")
            .requires { ctx -> ctx.sender.isOp }
            .executes { ctx ->
                val player = ctx.source.sender.riftwake ?: return@executes 0
                val block = player.block.block
                if (block == null) {
                    player.sendMessage("You don't currently have a block.".red)
                    return@executes 0
                }
                block.clearUpgrades()
                player.sendMessage("Upgrades cleared.".green)
                1
            }
        )

        registerCommand(Commands.literal("trash")
            .executes { ctx ->
                val player = ctx.source.sender.riftwake ?: return@executes 0
                player.openInventory(server.createInventory(null, InventoryType.CHEST, "Trash".comp()))
                player.playSound(Sound.BLOCK_CHEST_OPEN, SoundCategory.UI, 0.4f, 1f)
                1
            }
        )

        registerCommand(Commands.literal("egg")
            .executes { ctx ->
                val player = ctx.source.sender.riftwake ?: return@executes 0
                player.give(Items.createBridgeEgg())
                1
            }
        )

        registerCommand(Commands.literal("spawn")
            .executes { ctx ->
                val player = ctx.source.sender.riftwake ?: return@executes 0
                player.craft.teleportAsync(Location(world, 0.0, 100.0, 0.0)).thenAccept { success ->
                    if (success)
                        player.sendMessage("Teleported to spawn.".green)
                    else
                        player.sendMessage("Could not teleport to spawn right now.".red)
                }
                1
            }
        )

        registerCommand(Commands.literal("balance")
            .playerRun { player ->
                player.sendMessage("Your current balance is ${player.balance}.".green)
                true
            }
            .then(Commands.literal("add")
                .requires { it.sender.isOp }
                .then(Commands.argument("amount", LongArgumentType.longArg())
                    .playerRun { ctx, player ->
                        val amount = ctx.getArgument("amount", Long::class.java)
                        val oldBalance = player.balance
                        player.balance += amount
                        player.sendMessage("Balance changed from $oldBalance to ${oldBalance + amount}".green)
                        true
                    }
                )
            )
        )

        val layerTable = LayerTable()
        layerTable.add(Layer.GRASS, 1.0, Material.GRASS_BLOCK)
        layerTable.add(Layer.GRASS, 1.0, Material.PODZOL)
        layerTable.add(Layer.GRASS, 1.0, Material.MYCELIUM)
        layerTable.add(Layer.GRASS, 1.0, Material.DIRT_PATH)
        layerTable.add(Layer.GRASS, 1.0, Material.FARMLAND)
        layerTable.add(Layer.GRASS, 1.0, Material.STONE)
        layerTable.add(Layer.GRASS, 1.0, Material.SCULK)
        layerTable.add(Layer.GRASS, 1.0, Material.WARPED_NYLIUM)
        layerTable.add(Layer.GRASS, 1.0, Material.CRIMSON_NYLIUM)
        layerTable.add(Layer.GRASS, 1.0, Material.MOSS_BLOCK)
        layerTable.add(Layer.GRASS, 1.0, Material.PALE_MOSS_BLOCK)
        layerTable.add(Layer.GRASS, 1.0, Layer.DIRT)
        layerTable.add(Layer.GRASS, 1.0, Layer.ALT_DIRT)
        layerTable.add(Layer.GRASS, 1.0, Layer.STONE)
        layerTable.add(Layer.GRASS, 1.0, Layer.BUILDING_BLOCK)
        layerTable.add(Layer.GRASS, 1.0, Layer.LIQUID)

        layerTable.add(Layer.DIRT, 1.0, Material.DIRT)
        layerTable.add(Layer.DIRT, 1.0, Material.SAND)
        layerTable.add(Layer.DIRT, 1.0, Material.RED_SAND)
        layerTable.add(Layer.DIRT, 1.0, Material.MUD)
        layerTable.add(Layer.DIRT, 1.0, Material.SNOW)
        layerTable.add(Layer.DIRT, 1.0, Material.SOUL_SOIL)
        layerTable.add(Layer.DIRT, 1.0, Material.WHITE_CONCRETE_POWDER)
        layerTable.add(Layer.DIRT, 1.0, Layer.STONE)
        layerTable.add(Layer.DIRT, 1.0, Layer.ALT_DIRT)
        layerTable.add(Layer.DIRT, 1.0, Layer.BUILDING_BLOCK)

        layerTable.add(Layer.STONE, 1.0, Material.STONE)
        layerTable.add(Layer.STONE, 1.0, Material.DEEPSLATE)
        layerTable.add(Layer.STONE, 1.0, Material.SANDSTONE)
        layerTable.add(Layer.STONE, 1.0, Material.RED_SANDSTONE)
        layerTable.add(Layer.STONE, 1.0, Material.NETHERRACK)
        layerTable.add(Layer.STONE, 1.0, Material.END_STONE)
        layerTable.add(Layer.STONE, 1.0, Material.WHITE_CONCRETE)
        layerTable.add(Layer.STONE, 1.0, Material.WHITE_TERRACOTTA)
        layerTable.add(Layer.STONE, 1.0, Material.BEDROCK)
        layerTable.add(Layer.STONE, 1.0, Layer.DIRT)
        layerTable.add(Layer.STONE, 1.0, Layer.ALT_STONE)
        layerTable.add(Layer.STONE, 1.0, Layer.BUILDING_BLOCK)

        layerTable.add(Layer.ORE, 1.0, Material.COAL_ORE)
        layerTable.add(Layer.ORE, 1.0, Material.COPPER_ORE)
        layerTable.add(Layer.ORE, 1.0, Material.IRON_ORE)
        layerTable.add(Layer.ORE, 1.0, Material.GOLD_ORE)
        layerTable.add(Layer.ORE, 1.0, Material.REDSTONE_ORE)
        layerTable.add(Layer.ORE, 1.0, Material.EMERALD_ORE)
        layerTable.add(Layer.ORE, 1.0, Material.LAPIS_ORE)
        layerTable.add(Layer.ORE, 1.0, Material.DIAMOND_ORE)
        layerTable.add(Layer.ORE, 1.0, Material.AMETHYST_BLOCK)
        layerTable.add(Layer.ORE, 1.0, Material.NETHER_GOLD_ORE)
        layerTable.add(Layer.ORE, 1.0, Material.NETHER_QUARTZ_ORE)
        layerTable.add(Layer.ORE, 1.0, Material.ANCIENT_DEBRIS)

        layerTable.add(Layer.LIQUID, 1.0, Material.WATER)
        layerTable.add(Layer.LIQUID, 1.0, Material.AIR)
        layerTable.add(Layer.LIQUID, 1.0, Material.LAVA)
        layerTable.add(Layer.LIQUID, 1.0, Material.POWDER_SNOW)

        layerTable.add(Layer.ORE, 1.0, Material.OAK_WOOD)
        layerTable.add(Layer.ORE, 1.0, Material.BIRCH_WOOD)
        layerTable.add(Layer.ORE, 1.0, Material.SPRUCE_WOOD)
        layerTable.add(Layer.ORE, 1.0, Material.JUNGLE_WOOD)
        layerTable.add(Layer.ORE, 1.0, Material.ACACIA_WOOD)
        layerTable.add(Layer.ORE, 1.0, Material.DARK_OAK_WOOD)
        layerTable.add(Layer.ORE, 1.0, Material.CHERRY_WOOD)
        layerTable.add(Layer.ORE, 1.0, Material.MANGROVE_WOOD)
        layerTable.add(Layer.ORE, 1.0, Material.CRIMSON_HYPHAE)
        layerTable.add(Layer.ORE, 1.0, Material.WARPED_HYPHAE)
        layerTable.add(Layer.ORE, 1.0, Material.PALE_OAK_WOOD)
        layerTable.add(Layer.ORE, 1.0, Material.BROWN_MUSHROOM_BLOCK)
        layerTable.add(Layer.ORE, 1.0, Material.RED_MUSHROOM_BLOCK)
        layerTable.add(Layer.ORE, 1.0, Layer.BUILDING_BLOCK)

        layerTable.add(Layer.FLORA, 1.0, Material.TUBE_CORAL)
        layerTable.add(Layer.FLORA, 1.0, Material.BRAIN_CORAL)
        layerTable.add(Layer.FLORA, 1.0, Material.SPRUCE_WOOD)
        layerTable.add(Layer.FLORA, 1.0, Material.JUNGLE_WOOD)
        layerTable.add(Layer.FLORA, 1.0, Material.ACACIA_WOOD)
        layerTable.add(Layer.FLORA, 1.0, Material.DARK_OAK_WOOD)
        layerTable.add(Layer.FLORA, 1.0, Material.CHERRY_WOOD)
        layerTable.add(Layer.FLORA, 1.0, Material.MANGROVE_WOOD)
        layerTable.add(Layer.FLORA, 1.0, Material.CRIMSON_HYPHAE)
        layerTable.add(Layer.FLORA, 1.0, Material.WARPED_HYPHAE)
        layerTable.add(Layer.FLORA, 1.0, Material.PALE_OAK_WOOD)
        layerTable.add(Layer.FLORA, 1.0, Material.BROWN_MUSHROOM_BLOCK)
        layerTable.add(Layer.FLORA, 1.0, Material.RED_MUSHROOM_BLOCK)
        layerTable.add(Layer.FLORA, 1.0, Layer.BUILDING_BLOCK)

        registerCommand(Commands.literal("placestructures")
            .executes { ctx ->
                SideEffect.UPDATE
                val player = ctx.source.sender.riftwake ?: return@executes 0
                val file = File(dataFolder, "structures/islandtemplate3.schem")
                val format = ClipboardFormats.findByPath(file.toPath())
                if (format == null) {
                    player.sendMessage("Schematic file not found.".red)
                    return@executes 0
                }
                val reader = format.getReader(FileInputStream(file))

                val worldRadiusInChunks = Math.floorDiv(Math.ceilDiv(500, 16), 16) * 16

                reader.use { reader ->
                    val clipboard = reader.read()
                    val halfWidthX = clipboard.dimensions.x() / 2
                    val halfWidthZ = clipboard.dimensions.z() / 2

                    var gridChunkX = -worldRadiusInChunks
                    var gridChunkZ = -worldRadiusInChunks
                    var i = 0
                    var created = 0

                    fun createIsland(): Boolean {
                        i++
                        if (gridChunkX in -1..1 || gridChunkZ in -1..1 || Math.random() > 0.9) {
                            gridChunkZ += 16
                            if (gridChunkZ > worldRadiusInChunks) {
                                gridChunkZ = 0
                                gridChunkX += 16
                            }
                            return false
                        }

                        
                        val chunkXOffset = (Math.random() * 10).toInt() - 5
                        val chunkZOffset = (Math.random() * 10).toInt() - 5
                        val actualChunkX = gridChunkX + chunkXOffset
                        val actualChunkZ = gridChunkZ + chunkZOffset
                        val centerX = actualChunkX * 16
                        val centerZ = actualChunkZ * 16
                        val y = (Math.random() * 100).toInt() - 63 + clipboard.dimensions.y()
                        val to = BlockVector3(centerX + halfWidthX, y, centerZ + halfWidthZ)

                        player.craft.sendActionBar("$i grid points, $created created, creating at chunk ($gridChunkX, $gridChunkZ), coords ($centerX, $y, $centerZ)...".yellow)

                        for (surroundingChunkX in (actualChunkX - 2)..(actualChunkX + 2))
                            for (surroundingChunkZ in (actualChunkZ - 2)..(actualChunkZ + 2))
                                world.loadChunk(surroundingChunkX, surroundingChunkZ)

                        world.edit { session ->
                            Operations.complete(ClipboardHolder(clipboard)
                                .createPaste(session)
                                .to(to)
                                .ignoreAirBlocks(true)
                                .build())
                        }
                        world.edit { session ->
                            session.replaceBlocks(
                                CuboidRegion(to, to.subtract(clipboard.dimensions)),
                                BlockTypeMask(session, BlockTypes.RED_WOOL),
                                BlockTypes.OAK_LEAVES!!.getState(mapOf(
                                    BlockTypes.OAK_LEAVES!!.getProperty<Boolean>("persistent") to true
                                ))
                            )
                        }
                        world.edit { session ->
                            session.replaceBlocks(
                                CuboidRegion(to, to.subtract(clipboard.dimensions)),
                                BlockTypeMask(session, BlockTypes.ORANGE_WOOL),
                                BlockTypes.OAK_WOOD!!.defaultState
                            )
                        }
                        world.edit { session ->
                            session.replaceBlocks(
                                CuboidRegion(to, to.subtract(clipboard.dimensions)),
                                BlockTypeMask(session, BlockTypes.YELLOW_GLAZED_TERRACOTTA),
                                BlockTypes.FARMLAND!!.defaultState
                            )
                        }
                        world.edit { session ->
                            session.replaceBlocks(
                                CuboidRegion(to, to.subtract(clipboard.dimensions)),
                                BlockTypeMask(session, BlockTypes.YELLOW_WOOL),
                                BlockTypes.WHEAT!!.getState(mapOf(
                                    BlockTypes.WHEAT!!.getProperty<Int>("age") to 7
                                ))
                            )
                        }
                        world.edit { session ->
                            session.replaceBlocks(
                                CuboidRegion(to, to.subtract(clipboard.dimensions)),
                                BlockTypeMask(session, BlockTypes.GREEN_WOOL),
                                BlockTypes.GRASS_BLOCK!!.defaultState
                            )
                        }
                        world.edit { session ->
                            session.replaceBlocks(
                                CuboidRegion(to, to.subtract(clipboard.dimensions)),
                                BlockTypeMask(session, BlockTypes.LIME_WOOL),
                                BlockTypes.SHORT_GRASS!!.defaultState
                            )
                        }
                        world.edit { session ->
                            session.replaceBlocks(
                                CuboidRegion(to, to.subtract(clipboard.dimensions)),
                                BlockTypeMask(session, BlockTypes.BROWN_WOOL),
                                BlockTypes.DIRT!!.defaultState
                            )
                        }
                        world.edit { session ->
                            session.replaceBlocks(
                                CuboidRegion(to, to.subtract(clipboard.dimensions)),
                                BlockTypeMask(session, BlockTypes.LIGHT_GRAY_WOOL),
                                BlockTypes.STONE!!.defaultState
                            )
                        }
                        world.edit { session ->
                            session.replaceBlocks(
                                CuboidRegion(to, to.subtract(clipboard.dimensions)),
                                BlockTypeMask(session, BlockTypes.GRAY_WOOL),
                                BlockTypes.ANDESITE!!.defaultState
                            )
                        }
                        world.edit { session ->
                            session.replaceBlocks(
                                CuboidRegion(to, to.subtract(clipboard.dimensions)),
                                BlockTypeMask(session, BlockTypes.WHITE_WOOL),
                                BlockTypes.COARSE_DIRT!!.defaultState
                            )
                        }
                        world.edit { session ->
                            session.replaceBlocks(
                                CuboidRegion(to, to.subtract(clipboard.dimensions)),
                                BlockTypeMask(session, BlockTypes.LIGHT_BLUE_WOOL),
                                BlockTypes.DIAMOND_ORE!!.defaultState
                            )
                        }
                        world.edit { session ->
                            session.replaceBlocks(
                                CuboidRegion(to, to.subtract(clipboard.dimensions)),
                                BlockTypeMask(session, BlockTypes.PURPLE_WOOL),
                                BlockTypes.STONE_BRICKS!!.defaultState
                            )
                        }

                        for (surroundingChunkX in (actualChunkX - 2)..(actualChunkX + 2))
                            for (surroundingChunkZ in (actualChunkZ - 2)..(actualChunkZ + 2))
                                world.unloadChunk(surroundingChunkX, surroundingChunkZ)

                        created++
                        println("$i grid points, $created created, at chunk ($actualChunkX, $actualChunkZ), coords ($centerX, $y, $centerZ)")
                        player.craft.sendActionBar("$i grid points, $created created, at chunk ($actualChunkX, $actualChunkZ), coords ($centerX, $y, $centerZ)".green)
                        gridChunkZ += 16
                        if (gridChunkZ > worldRadiusInChunks) {
                            gridChunkZ = -worldRadiusInChunks
                            gridChunkX += 16
                        }
                        return true
                    }

                    fun step() {
                        while (gridChunkX <= worldRadiusInChunks && !createIsland()) {}
                        if (gridChunkX > worldRadiusInChunks) {
                            player.sendMessage("DONE: $i grid points, $created created".green)
                            return
                        }
                        runTaskLater(5) { step() }
                    }

                    step()
                }
                player.sendMessage("Placed structure.".green)
                1
            }
        )

        Constant.init()
        AuctionRegistry.init()
    }

    override fun onDisable() {
        TheBlockRegistry.save()
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        playerRegistry[event.player] = RiftwakePlayer(event.player)
        if (!event.player.hasPlayedBefore())
            server.broadcast("Welcome ${event.player.name} to Riftwake!".lightPurple)
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        logger.info("${event.player.name} quit the game")
        playerRegistry.remove(event.player)
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val inventory = event.clickedInventory ?: return

        var gui = inventory.holder
        if (gui is InventoryGUI) {
            gui.handleClickEvent(event)
            return
        }

        gui = event.whoClicked.openInventory.topInventory.holder
        if (gui is InventoryGUI)
            gui.onPlayerInventoryClick(event)
    }

    @EventHandler
    fun onPlayerMove(event: PlayerMoveEvent) {
        playerRegistry[event.player]?.onMove(event)
    }

    @EventHandler
    fun onPlayerTeleport(event: PlayerTeleportEvent) {
        playerRegistry[event.player]?.onTeleport(event)
    }

    @EventHandler
    fun onPlayerInteractEntity(event: PlayerInteractEntityEvent) {
        componentLogger.info("{}", event.rightClicked)
        playerRegistry[event.player]?.onRightClickEntity(event)
    }

    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val player = playerRegistry[event.player] ?: return
        if (event.action == Action.PHYSICAL) {
            player.onPhysicalInteract(event)
            return
        }
        if (!event.action.isRightClick)
            return

        val clickedBlock = event.clickedBlock
        val item = event.item

        if (item == null) {
            if (clickedBlock != null)
                player.onRightClickBlock(event, clickedBlock)
            return
        }
        if (clickedBlock != null)
            player.onRightClickBlock(event, clickedBlock)
        else
            player.onRightClickItem(event, item)
    }

    @EventHandler
    fun onPlayerSendMessage(event: AsyncChatEvent) = playerRegistry[event.player]?.onSendMessage(event)

    @EventHandler
    fun onPlayerToggleSneak(event: PlayerToggleSneakEvent) = playerRegistry[event.player]?.onToggleSneak(event)

    @EventHandler
    fun onPlayerBreakBlock(event: BlockBreakEvent) = playerRegistry[event.player]?.onBreakBlock(event)

    @EventHandler
    fun onPlayerPlaceBlock(event: BlockPlaceEvent) = playerRegistry[event.player]?.onPlaceBlock(event)

    @EventHandler
    fun onPlayerPlaceEntity(event: EntityPlaceEvent) = playerRegistry[event.player]?.onPlaceEntity(event)

    @EventHandler
    fun onPlayerReceiveDamage(event: EntityDamageEvent) = playerRegistry[event.entity]?.onReceiveDamage(event)

    @EventHandler
    fun onPlayerDamageEntity(event: EntityDamageByEntityEvent) {
        val attacker = event.damageSource.causingEntity ?: event.damageSource.directEntity
        if (attacker == null)
            return
        playerRegistry[attacker]?.onDamageEntity(event)
        playerRegistry[event.entity]?.onReceiveEntityDamage(event, attacker)
    }

    @EventHandler
    fun onPlayerDropItem(event: PlayerDropItemEvent) = playerRegistry[event.player]?.onDropItem(event)

    @EventHandler(ignoreCancelled=true)
    fun onPistonMove(event: BlockPistonExtendEvent) {
        for (block in event.blocks)
            if (block in TheBlockRegistry) {
                event.isCancelled = true
                return
            }
    }

    @EventHandler(ignoreCancelled=true)
    fun onPistonRetract(event: BlockPistonRetractEvent) {
        for (block in event.blocks)
            if (block in TheBlockRegistry) {
                event.isCancelled = true
                return
            }
    }

    // https://www.spigotmc.org/threads/prevent-sand-from-falling-upon-placing-sand.133386/
    @EventHandler(ignoreCancelled=true)
    fun onBlockFall(event: EntityChangeBlockEvent) {
        if (event.block !in TheBlockRegistry)
            return
        if (event.entityType == EntityType.FALLING_BLOCK && event.to == Material.AIR) {
            event.isCancelled = true
            // Update the block to fix a visual client bug, but don't apply physics
            event.block.state.update(false, false)
        }
    }

    @EventHandler
    fun onPlayerCommandSend(event: PlayerCommandPreprocessEvent) {
        val player = playerRegistry[event.player] ?: return
        if (player.combat.isInCombat && event.message.split(" ")[0] !in combatAllowedCommands) {
            if (player.isOp) {
                player.combat.endTimer()
                player.sendMessage("Combat timer bypassed as server operator.".green)
                return
            }
            event.isCancelled = true
            player.sendMessage("You can't use commands in combat.".darkRed)
        }
    }

    override fun onPacketReceive(event: PacketReceiveEvent) {
        if (event.packetType == PacketType.Play.Client.INTERACT_ENTITY) {
            val packet = WrapperPlayClientInteractEntity(event)
            componentLogger.info("{}", packet.action)
            playerRegistry[event.getPlayer()]?.onInteractPacketEntity(packet)
        }
    }
}
