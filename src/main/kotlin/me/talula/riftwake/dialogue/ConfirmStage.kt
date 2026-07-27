package me.talula.riftwake.dialogue

import io.papermc.paper.event.player.AsyncChatEvent
import me.talula.riftwake.Riftwake
import me.talula.riftwake.RiftwakePlayer
import me.talula.riftwake.utils.*
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.event.ClickEvent

class ConfirmStage(val prompt: Component, val onConfirm: () -> Unit): DialogueStage() {
    private var didRespond = false
    private var player: RiftwakePlayer? = null

    override fun start(player: RiftwakePlayer) {
        this.player = player
        player.sendMessage(
            prompt + " (".yellow + "Y".green.bold.hoverEvent("Confirm".green).clickEvent(ClickEvent.callback {
                if (didRespond)
                    return@callback
                didRespond = true
                Riftwake.runTask {
                    onConfirm()
                    player.dialogue.advance()
                }
            })
            + "/".yellow + "N".red.bold.hoverEvent("Cancel".red).clickEvent(ClickEvent.callback {
                if (didRespond)
                    return@callback
                didRespond = true
                Riftwake.runTask { player.dialogue.cancel() }
            })
            + ")".yellow
        )
    }

    override fun cleanUp() {}

    override fun onSendMessage(event: AsyncChatEvent) {
        val message = event.message()
        if (message !is TextComponent)
            return
        val content = message.content().trim()
        when (content.lowercase()) {
            "y", "yes", "confirm" -> {
                event.isCancelled = true
                Riftwake.runTask {
                    didRespond = true
                    onConfirm()
                    player!!.dialogue.advance()
                }
            }

            "n", "no", "cancel" -> {
                didRespond = true
                event.isCancelled = true
                Riftwake.runTask { player!!.dialogue.cancel() }
            }
        }
    }
}