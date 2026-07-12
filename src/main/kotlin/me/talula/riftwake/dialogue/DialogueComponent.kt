package me.talula.riftwake.dialogue

import me.talula.riftwake.RiftwakePlayer
import net.kyori.adventure.text.Component

class DialogueComponent(val player: RiftwakePlayer) {
    private var stages: Array<out DialogueStage>? = null
    private var stageIndex = 0
    private var cancelMessage: Component? = null

    private val currentStage: DialogueStage?
        get() = stages?.get(stageIndex)

    init {
        player.onRemove += { stages?.forEach { it.cleanUp() } }
        player.onMove += { event -> currentStage?.onMove(event) }
        player.onRightClickEntity += { event -> currentStage?.onRightClickEntity(event) }
        player.onInteractPacketEntity += { event -> currentStage?.onInteractPacketEntity(event) }
        player.onRightClickBlock += { event, block -> currentStage?.onRightClickBlock(event, block) }
        player.onRightClickItem += { event, item -> currentStage?.onRightClickItem(event, item) }
        player.onSendMessage += { event -> currentStage?.onSendMessage(event) }

//        player.onRightClickEntity.addListener({ event -> if (state != null) state.onRightClickEntity(event) })
//        player.onMissAttack.addListener(({ event, item -> if (state != null) state.onLeftClickObject(event) }))
//        player.onLeftClickEntity.addListener({ event -> if (state != null) state.onLeftClickEntity(event) })
//        player.onSendMessage.addListener({ event -> if (state != null) state.onChat(event) })
//        player.onToggleSneak.addListener({ event -> if (state != null) state.onToggleSneak(event) })
//
//        player.onRemove.addListener({ reason ->
//            if (state != null) for (temporary in state.temporaries) temporary.delete()
//        })
    }

    fun start(cancelMessage: Component? = null, vararg stages: DialogueStage) {
        cancel()
        this.cancelMessage = cancelMessage
        this.stages = stages
        stageIndex = 0
        if (stages.isNotEmpty())
            stages[0].start(player)
    }

    fun advance() {
        stages?.let {
            stageIndex++
            if (stageIndex >= it.size)
                finish()
            else
                it[stageIndex].start(player)
        }
    }

    fun cancel() {
        stages?.let {
            for (i in 0..stageIndex)
                it[i].cleanUp()
            stages = null
        }
        cancelMessage?.let { player.sendMessage(it) }
        cancelMessage = null
    }

    fun finish() {
        stages?.forEach { it.cleanUp() }
        stages = null
        cancelMessage = null
    }
}