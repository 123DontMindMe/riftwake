package me.talula.riftwake

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.PacketEventsAPI
import com.github.retrooper.packetevents.event.PacketListener
import com.github.retrooper.packetevents.event.PacketListenerPriority
import com.github.retrooper.packetevents.event.PacketReceiveEvent
import com.github.retrooper.packetevents.protocol.packettype.PacketType
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.event.player.AsyncChatEvent
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import me.talula.riftwake.dialogue.PlaceBlockStage
import me.talula.riftwake.theblock.TheBlockUpgradeGUI
import me.talula.riftwake.utils.InventoryGUI
import me.talula.riftwake.utils.green
import me.talula.riftwake.utils.red
import me.talula.riftwake.utils.riftwake
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerToggleSneakEvent
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask


class Riftwake : JavaPlugin(), Listener, PacketListener {
    companion object {
        lateinit var instance: Riftwake
            private set

        fun runTask(task: (BukkitTask) -> Unit) {
            Bukkit.getScheduler().runTask(instance, task)
        }

        fun runTaskLater(delay: Long, task: (BukkitTask) -> Unit) {
            Bukkit.getScheduler().runTaskLater(instance, task, delay)
        }

        fun runTaskTimer(delay: Long, interval: Long, task: (BukkitTask) -> Unit) {
            Bukkit.getScheduler().runTaskTimer(instance, task, delay, interval)
        }
    }

    val playerRegistry: MutableMap<Player, RiftwakePlayer> = HashMap()

    override fun onEnable() {
        instance = this
        logger.info("Riftwake enabled")

        server.pluginManager.registerEvents(this, this)
        PacketEvents.getAPI().eventManager.registerListener(this, PacketListenerPriority.NORMAL)

        registerCommand(Commands.literal("createblock")
            .executes { ctx ->
                val player = ctx.source.sender.riftwake() ?: return@executes 0
                player.dialogue.start(
                    cancelMessage = "Block placement cancelled.".red(),
                    PlaceBlockStage("the location of the block") { location ->
                        runTask {  // must be done sync
                            player.block.blockLocation = location
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
                TheBlockUpgradeGUI(player).open()
                1
            }
        )
    }

    override fun onDisable() {

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
        playerRegistry[event.player]?.onMove?.invoke(event)
    }

    @EventHandler
    fun onPlayerInteractEntity(event: PlayerInteractEntityEvent) {
        componentLogger.info("{}", event.rightClicked)
        playerRegistry[event.player]?.onRightClickEntity?.invoke(event)
    }

    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val player = playerRegistry[event.player] ?: return
        if (!event.action.isRightClick)
            return

        val clickedBlock = event.clickedBlock
        val item = event.item

        if (item == null) {
            if (clickedBlock != null)
                player.onRightClickBlock.invoke(event, clickedBlock)
            return
        }
        if (clickedBlock != null)
            player.onRightClickBlock.invoke(event, clickedBlock)
        else
            player.onRightClickItem.invoke(event, item)
    }

    @EventHandler
    fun onPlayerSendMessage(event: AsyncChatEvent) {
        playerRegistry[event.player]?.onSendMessage?.invoke(event)
    }

    @EventHandler
    fun onPlayerToggleSneak(event: PlayerToggleSneakEvent) {
        playerRegistry[event.player]?.onToggleSneak?.invoke(event)
    }

    @EventHandler
    fun onPlayerBreakBlock(event: BlockBreakEvent) {
        playerRegistry[event.player]?.onBreakBlock?.invoke(event)
    }

    override fun onPacketReceive(event: PacketReceiveEvent) {
        if (event.packetType == PacketType.Play.Client.INTERACT_ENTITY) {
            val packet = WrapperPlayClientInteractEntity(event)
            componentLogger.info("{}", packet.action)
            playerRegistry[event.getPlayer()]?.onRightClickPacketEntity?.invoke(packet)
        }
    }

    fun registerCommand(command: LiteralArgumentBuilder<CommandSourceStack>) {
        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { commands ->
            commands.registrar().register(command.build())
        }
    }
}
