package me.talula.riftwake.dialogue

import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes
import com.github.retrooper.packetevents.resources.ResourceLocation
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity
import io.papermc.paper.event.player.AsyncChatEvent
import me.talula.riftwake.Riftwake
import me.talula.riftwake.RiftwakePlayer
import me.talula.riftwake.temporaries.BlockCoordDisplay
import me.talula.riftwake.utils.cursorLocation
import me.talula.riftwake.utils.parse
import me.talula.riftwake.utils.red
import net.kyori.adventure.text.TextComponent
import org.bukkit.Location
import org.bukkit.event.player.PlayerMoveEvent
import java.util.function.Consumer

class PlaceBlockStage(
    private val coordsName: String,
    private val onResult: Consumer<Location>
): DialogueStage() {
    private var player: RiftwakePlayer? = null
    private var cursorDisplay: BlockCoordDisplay? = null
    private var isOnCooldown = true

    override fun start(player: RiftwakePlayer) {
        this.player = player
        player.sendMessage(
            ("<YELLOW|Right-click while holding an item to set <>, or type it in chat " +
            "<GRAY|(e.g., -100 200 -300)>. Left-click to cancel.>").parse(coordsName)
        )

        cursorDisplay = BlockCoordDisplay(
            player,
            blockMaterial=StateTypes.getByName(ResourceLocation(player.block.pull().name.lowercase()))!!,
            player.cursorLocation,
            true)

        Riftwake.runTaskTimer(10, 10) {
            var material = StateTypes.getByName(ResourceLocation(player.block.pull().name.lowercase()))!!
            while (cursorDisplay?.material == material)
                material = StateTypes.getByName(ResourceLocation(player.block.pull().name.lowercase()))!!

            cursorDisplay?.material = material
        }

        Riftwake.runTaskLater(10) { isOnCooldown = false }
    }

    override fun cleanUp() {
        cursorDisplay?.delete()
    }

    override fun onMove(event: PlayerMoveEvent) {
        cursorDisplay?.location = event.cursorLocation
    }

    override fun onSendMessage(event: AsyncChatEvent) {
        val player = player ?: return
        val cursorDisplay = cursorDisplay ?: return
        val message = event.message() as? TextComponent ?: return
        event.isCancelled = true
        Riftwake.runTask {
            val content = message.content().trim().split(" ")
            if (content.size != 3) {
                player.sendMessage("Invalid coordinates (format should be 'x y z').".red())
                return@runTask
            }
            val x: Int
            val y: Int
            val z: Int
            try {
                x = content[0].toInt()
                y = content[1].toInt()
                z = content[2].toInt()
            } catch (_: NumberFormatException) {
                player.sendMessage("Invalid coordinates (x, y, and z must be integers).".red())
                return@runTask
            }
            cursorDisplay.location = Location(player.world, x.toDouble(), y.toDouble(), z.toDouble())
            onResult.accept(cursorDisplay.location)
            player.dialogue.advance()
        }
    }

    override fun onRightClickPacketEntity(event: WrapperPlayClientInteractEntity) {
        if (isOnCooldown)
            return
        val cursorDisplay = cursorDisplay ?: return
        val player = player ?: return

        onResult.accept(cursorDisplay.location)
        player.dialogue.advance()
    }
//
//    override fun onRightClickObject(event: PlayerInteractEvent) {
//        if (isOnCooldown) return
//        event.setCancelled(true)
//        onResult!!.accept(cursorDisplay.getBlockLocation())
//        state.advance()
//    }
//
//    override fun onLeftClickEntity(event: PrePlayerAttackEntityEvent) {
//        event.setCancelled(true)
//        state.cancel()
//    }
//
//    override fun onLeftClickObject(event: PlayerInteractEvent) {
//        event.setCancelled(true)
//        state.cancel()
//    }
}