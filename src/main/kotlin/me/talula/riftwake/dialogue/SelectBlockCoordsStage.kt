package me.talula.riftwake.dialogue

import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity
import io.papermc.paper.event.player.AsyncChatEvent
import me.talula.riftwake.Riftwake
import me.talula.riftwake.RiftwakePlayer
import me.talula.riftwake.temporaries.BlockCoordDisplay
import me.talula.riftwake.utils.cursorLocation
import me.talula.riftwake.utils.parse
import me.talula.riftwake.utils.red
import me.talula.riftwake.utils.toStateType
import net.kyori.adventure.text.TextComponent
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.event.player.PlayerMoveEvent
import java.util.function.Consumer

class SelectBlockCoordsStage(
    private val coordsName: String,
    val material: Material,
    private val onResult: Consumer<Location>
): DialogueStage() {
    private var player: RiftwakePlayer? = null
    private var cursorDisplay: BlockCoordDisplay? = null
    private var isOnCooldown = true

    override fun start(player: RiftwakePlayer) {
        this.player = player
        player.sendMessage(
            ("<yellow|Right-click to set <>, or type it in chat " +
            "<gray|(e.g., -100 200 -300)>. Left-click to cancel.>").parse(coordsName)
        )

        cursorDisplay = BlockCoordDisplay(player, material.toStateType(), player.cursorLocation, true)

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
                player.sendMessage("Invalid coordinates (format should be 'x y z').".red)
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
                player.sendMessage("Invalid coordinates (x, y, and z must be integers).".red)
                return@runTask
            }
            cursorDisplay.location = Location(player.world, x.toDouble(), y.toDouble(), z.toDouble())
            onResult.accept(cursorDisplay.location)
            player.dialogue.advance()
        }
    }

    override fun onInteractPacketEntity(event: WrapperPlayClientInteractEntity) {
        if (isOnCooldown)
            return
        val cursorDisplay = cursorDisplay ?: return
        val player = player ?: return

        onResult.accept(cursorDisplay.location)
        player.dialogue.advance()
    }
}