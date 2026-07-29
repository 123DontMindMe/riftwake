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
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.event.player.AsyncChatEvent
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import me.talula.riftwake.constants.Constant
import me.talula.riftwake.crates.CratePreviewGUI
import me.talula.riftwake.crates.CrateRegistry
import me.talula.riftwake.dialogue.PlaceBlockStage
import me.talula.riftwake.economy.AuctionRegistry
import me.talula.riftwake.islands.Structures
import me.talula.riftwake.items.Items
import me.talula.riftwake.theblock.PlayerPlacedRegistry
import me.talula.riftwake.theblock.TheBlockRegistry
import me.talula.riftwake.theblock.UpgradeMenuGUI
import me.talula.riftwake.utils.*
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
import org.bukkit.event.entity.*
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.player.*
import org.bukkit.event.world.ChunkLoadEvent
import org.bukkit.event.world.ChunkUnloadEvent
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import org.bukkit.scoreboard.Team
import java.io.File


class Riftwake : JavaPlugin(), Listener, PacketListener {
    class Config(pathInDataFolder: String) {
        val file = File(instance.dataFolder, pathInDataFolder)
        val yaml = YamlConfiguration.loadConfiguration(file)
        fun save() = yaml.save(file)
        fun getSection(path: String) = yaml.getConfigurationSection(path)
        fun getInt(path: String) = yaml.get(path) as? Int
        fun getDouble(path: String) = yaml.get(path) as? Double
        fun getBoolean(path: String) = yaml.get(path) as? Boolean
        fun getString(path: String) = yaml.getString(path)
        fun getMapList(path: String) = yaml.getMapList(path)
        val sections get() = yaml.getKeys(false).map { Pair(it, yaml.getConfigurationSection(it)!!) }
        operator fun set(path: String, value: Any?) = yaml.set(path, value)
    }

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

        fun broadcastToOperators(message: Component) {
            logger.info(message)
            for (player in Bukkit.getOnlinePlayers())
                if (player.isOp)
                    player.sendMessage(message)
        }

        fun getFile(pathInDataFolder: String): File {
            return File(instance.dataFolder, pathInDataFolder)
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

        runTask {
            val scoreboard = server.scoreboardManager.mainScoreboard
            if (scoreboard.getTeam("in-spawn") == null) {
                val team = scoreboard.registerNewTeam("in-spawn")
                team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER)
            }
        }

        registerCommand(Commands.literal("pdc")
            .requires { ctx -> ctx.sender.isOp }
            .then(Commands.literal("clear")
                .replyPlayer { player ->
                    for (key in player.persistentDataContainer.keys)
                        if (key.namespace == "riftwake")
                            player.persistentDataContainer.remove(key)
                    "Riftwake player data cleared.".green
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
                        .replyPlayer { ctx, player ->
                            val key = ctx.getArgument("key", String::class.java)
                            val type = ctx.getArgument("type", String::class.java).toPersistentDataType() ?:
                                throw CommandFail("Not a valid type.")
                            val oldValue = player.getData(key, type) ?: throw CommandFail("That key doesn't exist.")

                            val newValue = try {
                                player.setDataFromString(key, type, ctx.getArgument("value", String::class.java))
                            } catch (error: IllegalArgumentException) {
                                throw CommandFail("Invalid value: ${error.message}")
                            }

                            val oldString = if (oldValue is Array<*>) oldValue.contentToString() else oldValue.toString()
                            val newString = if (newValue is Array<*>) newValue.contentToString() else newValue.toString()
                            ("$key=$oldString → $newString").green
                        }
                    )
                )
            )
        )

        registerCommand(Commands.literal("createblock")
            .runPlayer { player ->
                if (player.spawn.isInSpawn)
                    throw CommandFail("You can't use this command in spawn.".red)
                player.dialogue.start(
                    cancelMessage = "Cancelled block placement.".red,
                    PlaceBlockStage()
                )
            }
        )

        registerCommand(Commands.literal("blockmenu")
            .runPlayer { player ->
                if (player.block.block == null)
                    throw CommandFail("You don't currently have a block. Place one with ".red + "/createblock".yellow + ".".red)
                UpgradeMenuGUI(player).open()
            }
        )

        registerCommand(Commands.literal("clearupgrades")
            .requires { ctx -> ctx.sender.isOp }
            .replyPlayer { player ->
                val block = player.block.block ?: throw CommandFail("You don't currently have a block.")
                block.clearUpgrades()
                "Upgrades cleared.".green
            }
        )

        registerCommand(Commands.literal("trash")
            .runPlayer { player ->
                player.openInventory(server.createInventory(null, InventoryType.CHEST, "Trash".comp()))
                player.playSound(Sound.BLOCK_CHEST_OPEN, SoundCategory.UI, 0.4f, 1f)
            }
        )

        registerCommand(Commands.literal("egg").runPlayer { player -> player.give(Items.createBridgeEgg()) })

        registerCommand(Commands.literal("spawn")
            .runPlayer { player ->
                player.craft.teleportAsync(Location(world, 0.0, 100.0, 0.0)).thenAccept { success ->
                    if (success)
                        player.sendMessage("Teleported to spawn.".green)
                    else
                        player.sendMessage("Could not teleport to spawn right now.".red)
                }
            }
        )

        registerCommand(Commands.literal("balance")
            .replyPlayer { player -> "Your current balance is ${player.balance}.".green }
            .then(Commands.literal("add")
                .requires { it.sender.isOp }
                .then(Commands.argument("amount", LongArgumentType.longArg())
                    .replyPlayer { ctx, player ->
                        val amount = ctx.getArgument("amount", Long::class.java)
                        val oldBalance = player.balance
                        player.balance += amount
                        "Balance changed from $oldBalance to ${oldBalance + amount}".green
                    }
                )
            )
        )

        registerCommand(Commands.literal("rtp")
            .runPlayer { player ->
                val cooldownRemaining = player.block.randomTeleportCooldownRemaining
                if (cooldownRemaining > 0)
                    throw CommandFail("You must wait ${cooldownRemaining.toTimeString()} to random teleport again.")
                else
                    player.block.startRandomTeleport()
            }
        )

        Constant.init()
        AuctionRegistry.init()
        Structures.init()
        CrateRegistry.init()
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
    fun onInventoryClose(event: InventoryCloseEvent) {
        val gui = event.inventory.holder
        if (gui is InventoryGUI) {
            gui.onClose(event)
            return
        }
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
        if (clickedBlock != null && clickedBlock.type.takesInteractPriority)
            player.onRightClickBlock(event, clickedBlock)
        else
            player.onRightClickItem(event, item)
    }

    @EventHandler
    fun onPlayerSendMessage(event: AsyncChatEvent) = playerRegistry[event.player]?.onSendMessage(event)

    @EventHandler
    fun onPlayerToggleSneak(event: PlayerToggleSneakEvent) = playerRegistry[event.player]?.onToggleSneak(event)

    @EventHandler
    fun onPlayerBreakBlock(event: BlockBreakEvent) {
        val crate = CrateRegistry[event.block.location]
        if (crate == null)
            playerRegistry[event.player]?.onBreakBlock(event)
        else playerRegistry[event.player]?.let {
            event.isCancelled = true
            CratePreviewGUI(it, crate).open()
            it.playSound(Sound.BLOCK_CHEST_OPEN, SoundCategory.UI, 1f, 1f)
        }
    }

    @EventHandler
    fun onPlayerBlockDropItems(event: BlockDropItemEvent) {
        playerRegistry[event.player]?.onBlockDropItems(event)
    }

    @EventHandler
    fun onPlayerPlaceBlock(event: BlockPlaceEvent) = playerRegistry[event.player]?.onPlaceBlock(event)

    @EventHandler
    fun onPlayerPlaceEntity(event: EntityPlaceEvent) = playerRegistry[event.player]?.onPlaceEntity(event)

    @EventHandler
    fun onPlayerReceiveDamage(event: EntityDamageEvent) = playerRegistry[event.entity]?.onReceiveDamage(event)

    @EventHandler
    fun onPlayerDamageEntity(event: EntityDamageByEntityEvent) {
        val attacker = event.damageSource.causingEntity ?: event.damageSource.directEntity ?: return
        playerRegistry[attacker]?.onDamageEntity(event)
        playerRegistry[event.entity]?.onReceiveEntityDamage(event, attacker)
    }

    @EventHandler
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val attacker = event.damageSource.causingEntity ?: event.damageSource.directEntity ?: return
        val victim = playerRegistry[attacker] ?: return
        playerRegistry[attacker]?.onKillPlayer(event, victim)
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

    @EventHandler
    fun onChunkLoad(event: ChunkLoadEvent) = PlayerPlacedRegistry.registerChunk(event.chunk)
    @EventHandler
    fun onChunkUnload(event: ChunkUnloadEvent) = PlayerPlacedRegistry.unregisterChunk(event.chunk)

    override fun onPacketReceive(event: PacketReceiveEvent) {
        if (event.packetType == PacketType.Play.Client.INTERACT_ENTITY) {
            val packet = WrapperPlayClientInteractEntity(event)
            componentLogger.info("{}", packet.action)
            playerRegistry[event.getPlayer()]?.onInteractPacketEntity(packet)
        }
    }

    val Material.takesInteractPriority: Boolean get() {
        return when (this) {
            Material.FURNACE, Material.BLAST_FURNACE, Material.SMOKER, Material.ANVIL, Material.CHIPPED_ANVIL, Material.DAMAGED_ANVIL, Material.BARREL, Material.BEACON, Material.RED_BED, Material.BROWN_BED, Material.ORANGE_BED, Material.YELLOW_BED, Material.LIME_BED, Material.GREEN_BED, Material.CYAN_BED, Material.LIGHT_BLUE_BED, Material.BLUE_BED, Material.PURPLE_BED, Material.MAGENTA_BED, Material.PINK_BED, Material.BLACK_BED, Material.GRAY_BED, Material.LIGHT_GRAY_BED, Material.WHITE_BED, Material.BELL, Material.BREWING_STAND, Material.ACACIA_BUTTON, Material.BAMBOO_BUTTON, Material.BIRCH_BUTTON, Material.CHERRY_BUTTON, Material.CRIMSON_BUTTON, Material.DARK_OAK_BUTTON, Material.JUNGLE_BUTTON, Material.MANGROVE_BUTTON, Material.OAK_BUTTON, Material.POLISHED_BLACKSTONE_BUTTON, Material.SPRUCE_BUTTON, Material.STONE_BUTTON, Material.WARPED_BUTTON, Material.CAKE, Material.BLACK_CANDLE_CAKE, Material.CANDLE_CAKE, Material.BLUE_CANDLE_CAKE, Material.CYAN_CANDLE_CAKE, Material.BROWN_CANDLE_CAKE, Material.GRAY_CANDLE_CAKE, Material.GREEN_CANDLE_CAKE, Material.LIGHT_BLUE_CANDLE_CAKE, Material.LIGHT_GRAY_CANDLE_CAKE, Material.LIME_CANDLE_CAKE, Material.MAGENTA_CANDLE_CAKE, Material.ORANGE_CANDLE_CAKE, Material.PINK_CANDLE_CAKE, Material.PURPLE_CANDLE_CAKE, Material.RED_CANDLE_CAKE, Material.WHITE_CANDLE_CAKE, Material.YELLOW_CANDLE_CAKE, Material.CARTOGRAPHY_TABLE, Material.CHEST, Material.TRAPPED_CHEST, Material.CHISELED_BOOKSHELF, Material.COMMAND_BLOCK, Material.COMPARATOR, Material.CRAFTER, Material.CRAFTING_TABLE, Material.DAYLIGHT_DETECTOR, Material.DISPENSER, Material.ACACIA_DOOR, Material.BAMBOO_DOOR, Material.BIRCH_DOOR, Material.CHERRY_DOOR, Material.COPPER_DOOR, Material.CRIMSON_DOOR, Material.DARK_OAK_DOOR, Material.EXPOSED_COPPER_DOOR, Material.IRON_DOOR, Material.JUNGLE_DOOR, Material.MANGROVE_DOOR, Material.OAK_DOOR, Material.OXIDIZED_COPPER_DOOR, Material.SPRUCE_DOOR, Material.WARPED_DOOR, Material.WAXED_COPPER_DOOR, Material.WAXED_EXPOSED_COPPER_DOOR, Material.WAXED_OXIDIZED_COPPER_DOOR, Material.WAXED_WEATHERED_COPPER_DOOR, Material.WEATHERED_COPPER_DOOR, Material.ENCHANTING_TABLE, Material.ENDER_CHEST, Material.ACACIA_FENCE_GATE, Material.BAMBOO_FENCE, Material.BIRCH_FENCE_GATE, Material.CHERRY_FENCE_GATE, Material.CRIMSON_FENCE_GATE, Material.DARK_OAK_FENCE_GATE, Material.JUNGLE_FENCE_GATE, Material.MANGROVE_FENCE_GATE, Material.SPRUCE_FENCE_GATE, Material.WARPED_FENCE_GATE, Material.OAK_FENCE_GATE, Material.FLETCHING_TABLE, Material.GRINDSTONE, Material.HOPPER, Material.JIGSAW, Material.LECTERN, Material.LEVER, Material.LOOM, Material.NOTE_BLOCK, Material.REPEATER, Material.SHULKER_BOX, Material.WHITE_SHULKER_BOX, Material.LIGHT_GRAY_SHULKER_BOX, Material.GRAY_SHULKER_BOX, Material.BLACK_SHULKER_BOX, Material.BROWN_SHULKER_BOX, Material.RED_SHULKER_BOX, Material.ORANGE_SHULKER_BOX, Material.YELLOW_SHULKER_BOX, Material.LIME_SHULKER_BOX, Material.GREEN_SHULKER_BOX, Material.CYAN_SHULKER_BOX, Material.LIGHT_BLUE_SHULKER_BOX, Material.BLUE_SHULKER_BOX, Material.PURPLE_SHULKER_BOX, Material.MAGENTA_SHULKER_BOX, Material.PINK_SHULKER_BOX, Material.SMITHING_TABLE, Material.STONECUTTER, Material.STRUCTURE_BLOCK, Material.ACACIA_TRAPDOOR, Material.BAMBOO_TRAPDOOR, Material.BIRCH_TRAPDOOR, Material.CHERRY_TRAPDOOR, Material.COPPER_TRAPDOOR, Material.CRIMSON_TRAPDOOR, Material.DARK_OAK_TRAPDOOR, Material.EXPOSED_COPPER_TRAPDOOR, Material.IRON_TRAPDOOR, Material.JUNGLE_TRAPDOOR, Material.MANGROVE_TRAPDOOR, Material.OAK_TRAPDOOR, Material.OXIDIZED_COPPER_TRAPDOOR, Material.SPRUCE_TRAPDOOR, Material.WARPED_TRAPDOOR, Material.WAXED_COPPER_TRAPDOOR, Material.WAXED_EXPOSED_COPPER_TRAPDOOR, Material.WAXED_OXIDIZED_COPPER_TRAPDOOR, Material.WAXED_WEATHERED_COPPER_TRAPDOOR, Material.WEATHERED_COPPER_TRAPDOOR -> true
            else -> false
        }
    }
}
