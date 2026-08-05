package me.talula.riftwake.utils

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import io.papermc.paper.command.brigadier.CommandSourceStack
import me.talula.riftwake.RiftwakePlayer
import net.kyori.adventure.text.Component
import org.bukkit.command.CommandSender

object Command {
    fun fail(message: Component? = null): Nothing = throw CommandFail(message)
}

inline fun LiteralArgumentBuilder<CommandSourceStack>.replySender(
    crossinline command: (CommandSender) -> Component
): LiteralArgumentBuilder<CommandSourceStack> {
    return executes { ctx ->
        try {
            ctx.source.sender.sendMessage(command(ctx.source.sender))
            return@executes 1
        } catch (error: CommandFail) {
            if (error.error != null)
                ctx.source.sender.sendMessage(error.error.red)
            return@executes 0
        }
    }
}

inline fun LiteralArgumentBuilder<CommandSourceStack>.runPlayer(
    crossinline command: (RiftwakePlayer) -> Unit
): LiteralArgumentBuilder<CommandSourceStack> {
    return executes { ctx ->
        val player = ctx.source.sender.riftwake ?: return@executes 0
        try {
            command(player)
            return@executes 1
        } catch (error: CommandFail) {
            if (error.error != null)
                player.sendMessage(error.error.red)
            return@executes 0
        }
    }
}

inline fun LiteralArgumentBuilder<CommandSourceStack>.replyPlayer(
    crossinline command: (RiftwakePlayer) -> Component?
): LiteralArgumentBuilder<CommandSourceStack> {
    return executes { ctx ->
        val player = ctx.source.sender.riftwake ?: return@executes 0
        try {
            command(player)?.let { player.sendMessage(it) }
            return@executes 1
        } catch (error: CommandFail) {
            if (error.error != null)
                player.sendMessage(error.error.red)
            return@executes 0
        }
    }
}

inline fun <T> RequiredArgumentBuilder<CommandSourceStack, T>.runPlayer(
    crossinline command: (CommandContext<CommandSourceStack>, RiftwakePlayer) -> Unit
): RequiredArgumentBuilder<CommandSourceStack, T> {
    return executes { ctx ->
        val player = ctx.source.sender.riftwake ?: return@executes 0
        try {
            command(ctx, player)
            return@executes 1
        } catch (error: CommandFail) {
            if (error.error != null)
                player.sendMessage(error.error.red)
            return@executes 0
        }
    }
}

inline fun <T> RequiredArgumentBuilder<CommandSourceStack, T>.replyPlayer(
    crossinline command: (CommandContext<CommandSourceStack>, RiftwakePlayer) -> Component?
): RequiredArgumentBuilder<CommandSourceStack, T> {
    return executes { ctx ->
        val player = ctx.source.sender.riftwake ?: return@executes 0
        try {
            command(ctx, player)?.let { player.sendMessage(it) }
            return@executes 1
        } catch (error: CommandFail) {
            if (error.error != null)
                player.sendMessage(error.error.red)
            return@executes 0
        }
    }
}