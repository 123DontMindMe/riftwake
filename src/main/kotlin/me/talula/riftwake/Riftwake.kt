package me.talula.riftwake

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.event.PacketListener
import com.github.retrooper.packetevents.event.PacketListenerPriority
import com.github.retrooper.packetevents.event.PacketReceiveEvent
import com.github.retrooper.packetevents.protocol.packettype.PacketType
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.event.player.AsyncChatEvent
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import me.talula.riftwake.constants.Constant
import me.talula.riftwake.dialogue.PlaceBlockStage
import me.talula.riftwake.theblock.TheBlockRegistry
import me.talula.riftwake.theblock.UpgradeMenuGUI
import me.talula.riftwake.utils.*
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPistonExtendEvent
import org.bukkit.event.block.BlockPistonRetractEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.*
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.io.File


class Riftwake : JavaPlugin(), Listener, PacketListener {
    companion object {
        lateinit var instance: Riftwake private set
        val world get() = instance.server.getWorld("world") ?: throw RuntimeException("world not found")
        val logger get() = instance.componentLogger
        val server get() = instance.server

        val playerRegistry: MutableMap<Player, RiftwakePlayer> = HashMap()

        fun runTask(task: (BukkitTask) -> Unit) {
            Bukkit.getScheduler().runTask(instance, task)
        }

        fun runTaskLater(delay: Long, task: (BukkitTask) -> Unit) {
            Bukkit.getScheduler().runTaskLater(instance, task, delay)
        }

        fun runTaskTimer(delay: Long, interval: Long, task: (BukkitTask) -> Unit) {
            Bukkit.getScheduler().runTaskTimer(instance, task, delay, interval)
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

        fun saveConfig(file: YamlConfiguration, pathInDataFolder: String) {
            file.save(File(instance.dataFolder, pathInDataFolder))
        }
    }

    override fun onEnable() {
        instance = this
        logger.info("Riftwake enabled")

        server.pluginManager.registerEvents(this, this)
        PacketEvents.getAPI().eventManager.registerListener(this, PacketListenerPriority.NORMAL)

        registerCommand(Commands.literal("pdc")
            .requires { ctx -> ctx.sender.isOp }
            .then(Commands.literal("clear")
                .executes { ctx ->
                    val player = ctx.source.sender as? Player ?: return@executes 0
                    for (key in player.persistentDataContainer.keys)
                        if (key.namespace == "riftwake")
                            player.persistentDataContainer.remove(key)
                    player.sendMessage("Riftwake player data cleared.".green())
                    1
                }
            )
            .then(Commands.literal("get")
                .then(Commands.argument("key", StringArgumentType.string())
                    .suggests { ctx, builder ->
                        val player = ctx.source.sender as? Player ?: return@suggests builder.buildFuture()
                        for (key in player.persistentDataContainer.keys)
                            if (key.namespace == "riftwake")
                                builder.suggest(key.key)
                        builder.buildFuture()
                    }
                    .then(Commands.argument("type", StringArgumentType.greedyString())
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
                            val type = when (ctx.getArgument("type", String::class.java)) {
                                "byte" -> PersistentDataType.BYTE
                                "short" -> PersistentDataType.SHORT
                                "int" -> PersistentDataType.INTEGER
                                "long" -> PersistentDataType.LONG
                                "float" -> PersistentDataType.FLOAT
                                "double" -> PersistentDataType.DOUBLE
                                "bool" -> PersistentDataType.BOOLEAN
                                "string" -> PersistentDataType.STRING
                                "byte[]" -> PersistentDataType.BYTE_ARRAY
                                "int[]" -> PersistentDataType.INTEGER_ARRAY
                                "long[]" -> PersistentDataType.LONG_ARRAY
                                else -> {
                                    player.sendMessage("Not a valid type.".red())
                                    return@executes 0
                                }
                            }
                            val value = player.getData(key, type)
                            if (value == null) {
                                player.sendMessage("No data found".red())
                                return@executes 0
                            }
                            if (value is Array<*>)
                                player.sendMessage((key + "=" + value.contentToString()).green())
                            else
                                player.sendMessage((key + "=" + value.toString()).green())
                            1
                        }
                    )
                )
            )
        )

        registerCommand(Commands.literal("createblock")
            .executes { ctx ->
                val player = ctx.source.sender.riftwake() ?: return@executes 0
                player.dialogue.start(
                    cancelMessage = "Block placement cancelled.".red(),
                    PlaceBlockStage("the location of the block") { location ->
                        runTask {  // must be done sync
                            player.block.setBlockLocation(location)
                            player.sendMessage("Block placed at (${location.x.toInt()}, ${location.y.toInt()}, ${location.z.toInt()}).".green())
                        }
                    }
                )
                1
            }
        )

        registerCommand(Commands.literal("blockmenu")
            .executes { ctx ->
                val player = ctx.source.sender.riftwake() ?: return@executes 0
                UpgradeMenuGUI(player).open()
                1
            }
        )

        Constant.init()
    }

    override fun onDisable() {
        TheBlockRegistry.save()
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        logger.info("${event.player.name} joined the game")
        playerRegistry[event.player] = RiftwakePlayer(event.player)
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
    fun onPistonMove(event: BlockPistonExtendEvent) {
        for (block in event.blocks)
            if (block in TheBlockRegistry) {
                event.isCancelled = true
                return
            }
    }

    @EventHandler
    fun onPistonRetract(event: BlockPistonRetractEvent) {
        for (block in event.blocks)
            if (block in TheBlockRegistry) {
                event.isCancelled = true
                return
            }
    }

    override fun onPacketReceive(event: PacketReceiveEvent) {
        if (event.packetType == PacketType.Play.Client.INTERACT_ENTITY) {
            val packet = WrapperPlayClientInteractEntity(event)
            componentLogger.info("{}", packet.action)
            playerRegistry[event.getPlayer()]?.onRightClickPacketEntity(packet)
        }
    }

    fun registerCommand(command: LiteralArgumentBuilder<CommandSourceStack>) {
        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { commands ->
            commands.registrar().register(command.build())
        }
    }
}
