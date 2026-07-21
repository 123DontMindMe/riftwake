package me.talula.riftwake.dialogue

import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity
import me.talula.riftwake.Riftwake
import me.talula.riftwake.RiftwakePlayer
import me.talula.riftwake.temporaries.BlockCoordDisplay
import me.talula.riftwake.utils.cursorLocation
import me.talula.riftwake.utils.green
import me.talula.riftwake.utils.parse
import me.talula.riftwake.utils.toStateType
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.scheduler.BukkitTask

class PlaceBlockStage: DialogueStage() {
    private var player: RiftwakePlayer? = null
    private var cursorDisplay: BlockCoordDisplay? = null
    private var isOnCooldown = true
    private var previewTask: BukkitTask? = null

    override fun start(player: RiftwakePlayer) {
        this.player = player
        player.sendMessage("<yellow|<green|Right-click> to place, or <red|left-click> to cancel.>".parse())

        cursorDisplay = BlockCoordDisplay(
            player,
            player.block.previewPull().toStateType(),
            player.cursorLocation,
            true)

        previewTask = Riftwake.runTaskTimer(10, 10) {
            var material = player.block.previewPull().toStateType()
            var i = 0
            while (cursorDisplay?.material == material && i++ < 20)
                material = player.block.previewPull().toStateType()

            cursorDisplay?.material = material
        }

        Riftwake.runTaskLater(10) { isOnCooldown = false }
    }

    override fun cleanUp() {
        previewTask?.cancel()
        cursorDisplay?.delete()
    }

    override fun onMove(event: PlayerMoveEvent) {
        cursorDisplay?.location = event.cursorLocation
    }

    override fun onInteractPacketEntity(event: WrapperPlayClientInteractEntity) {
        if (isOnCooldown)
            return
        val cursorDisplay = cursorDisplay ?: return
        val player = player ?: return

        if (event.action == WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
            player.dialogue.cancel()
            return
        }

        val location = cursorDisplay.location
        // must be done sync
        Riftwake.runTask {
            player.block.setBlockLocation(location)
            player.sendMessage("Block placed at (${location.x.toInt()}, ${location.y.toInt()}, ${location.z.toInt()}).".green)
        }
        player.dialogue.advance()
    }
}