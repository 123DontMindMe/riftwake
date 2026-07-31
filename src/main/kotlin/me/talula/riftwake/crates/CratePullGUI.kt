package me.talula.riftwake.crates

import me.talula.riftwake.Riftwake
import me.talula.riftwake.RiftwakePlayer
import me.talula.riftwake.constants.IntConstant
import me.talula.riftwake.constants.NumConstant
import me.talula.riftwake.constants.TimeConstant
import me.talula.riftwake.utils.*
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.persistence.PersistentDataType
import org.bukkit.scheduler.BukkitTask
import kotlin.math.pow

class CratePullGUI(player: RiftwakePlayer, crate: Crate): InventoryGUI(player, 3, crate.name) {
    companion object {
        val numSteps = IntConstant("crate-animation.num-steps")
        val easingBase = NumConstant("crate-animation.easing-base")
        val maxInterval = NumConstant("crate-animation.max-interval")
        val linger = TimeConstant("crate-animation.linger")
    }

    val reward = crate.pull()
    val animationTask: BukkitTask

    init {
        for (i in 9..<18)
            inventory.setItem(i, crate.pull().withRandomUUID())

        fillEmpty()

        val duration = numSteps()
        val base = easingBase()
        var steps = 0
        var ticksUntilNextStep = 0
        animationTask = Riftwake.runTaskTimer(0, 1) { task ->
            if (ticksUntilNextStep-- > 0)
                return@runTaskTimer

            steps++
            player.playSound(Sound.BLOCK_NOTE_BLOCK_HAT, SoundCategory.UI, 1f, 1f)

            if (steps < duration) {
                val progress = steps.toDouble() / duration
                ticksUntilNextStep = (base.pow(progress) / (base - 1) * maxInterval()).toInt()

                for (i in 9..16)
                    inventory.setItem(i, inventory.getItem(i + 1))

                inventory.setItem(17,
                    if (steps == duration - 5)
                        reward.withRandomUUID()
                    else
                        crate.pull().withRandomUUID()
                )
            }
            else {
                task.cancel()
                for (i in 9..12)
                    EmptyButton(i)
                for (i in 14..17)
                    EmptyButton(i)
                Riftwake.runTaskLater(linger().toLong()) { close() }
            }
        }
    }

    override fun onClose(event: InventoryCloseEvent) {
        animationTask.cancel()
        val money = reward.getData("money-reward", PersistentDataType.INTEGER)
        if (money == null) {
            player.give(reward)
            player.sendMessage("You received ".gray + reward.hoverableStack + "!".gray)
        } else {
            player.balance += money
            player.sendMessage("You received ".gray + "$$money".gold + "!".gray)
        }
        player.playSound(Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.UI, 1f, 1f)
    }

    override fun onClick(event: InventoryClickEvent) {
        event.isCancelled = true
    }
}