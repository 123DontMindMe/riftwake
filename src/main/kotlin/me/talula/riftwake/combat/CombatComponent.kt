package me.talula.riftwake.combat

import me.talula.riftwake.Riftwake
import me.talula.riftwake.RiftwakePlayer
import me.talula.riftwake.constants.TimeConstant
import me.talula.riftwake.utils.comp
import me.talula.riftwake.utils.craft
import me.talula.riftwake.utils.gray
import me.talula.riftwake.utils.green
import me.talula.riftwake.utils.maxPlaces
import me.talula.riftwake.utils.plus
import me.talula.riftwake.utils.red
import me.talula.riftwake.utils.riftwake
import org.bukkit.scheduler.BukkitTask
import kotlin.math.ceil

class CombatComponent(val player: RiftwakePlayer) {
    companion object {
        private val combatTimerDuration = TimeConstant("combat-timer-duration")
    }

    private var actionBarTask: BukkitTask? = null
    val isInCombat: Boolean get() = actionBarTask != null

    init {
        player.onReceiveEntityDamage += damage@{ _, attacker ->
            val opponent = attacker.riftwake ?: return@damage
            refreshTimer(opponent.name)
        }
        player.onDamageEntity += damage@{ event ->
            val opponent = event.entity.riftwake ?: return@damage
            refreshTimer(opponent.name)
        }
    }

    fun endTimer() {
        actionBarTask?.cancel()
        actionBarTask = null
        player.craft.sendActionBar("You are no longer in combat.".green)
    }

    private fun refreshTimer(opponentName: String) {
        actionBarTask?.cancel()
        var t = 0
        actionBarTask = Riftwake.runTaskTimer(0, 1) { task ->
			val progress = (t.toDouble() / combatTimerDuration()).coerceAtMost(1.0)
			val numRed = ceil(progress * 30).toInt()
			val numGray = 30 - numRed
            val secondsRemaining = ((combatTimerDuration() - t) * 20.0).coerceAtLeast(0.0)

            if (t++ < combatTimerDuration()) {
                player.craft.sendActionBar(
                    "IN COMBAT ".red +
                    "|".repeat(numRed).red +
                    "|".repeat(numGray).gray +
                    " ".comp() +
                    secondsRemaining.maxPlaces(2).red +
                    "s (".red + opponentName.red + ")".red
                )
                return@runTaskTimer
            }

            player.craft.sendActionBar("You are no longer in combat.".green)
            task.cancel()
            actionBarTask = null
        }
    }
}