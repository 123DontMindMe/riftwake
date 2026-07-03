package me.talula.riftwake.constants

import com.mojang.brigadier.arguments.StringArgumentType
import io.papermc.paper.command.brigadier.Commands
import me.talula.riftwake.Riftwake
import me.talula.riftwake.utils.green
import me.talula.riftwake.utils.red
import me.talula.riftwake.utils.toMessage

abstract class Constant<T>(val name: String, val type: String) {
    class ConfigurationException(override val message: String): Exception(message)

    companion object {
        val constants = mutableMapOf<String, Constant<*>>()
        val file = Riftwake.getConfig("config.yml")

        fun init() {
            Riftwake.instance.registerCommand(Commands.literal("config")
                .requires { ctx -> ctx.sender.isOp }
                .then(Commands.literal("reload")
                    .executes { ctx ->
                        for ((name, constant) in constants) {
                            val string = file.getString(name)
                            if (string == null) {
                                ctx.source.sender.sendMessage("Config value $name was not set.".red())
                                continue
                            }
                            if (!constant.set(string, isFromFile=true)) {
                                ctx.source.sender.sendMessage("Config value $name was formatted incorrectly for type ${constant.type}.")
                                continue
                            }
                        }
                        ctx.source.sender.sendMessage("Config loaded from file successfully.".green())
                        1
                    }
                )
                .then(Commands.literal("val")
                    .then(Commands.argument("name", StringArgumentType.string())
                        .suggests { _, builder ->
                            for ((name, constant) in constants)
                                builder.suggest(name, ("Current value: " + constant.serialize()).toMessage())
                            builder.buildFuture()
                        }
                        .executes { ctx ->
                            val name = ctx.getArgument("name", String::class.java)
                            val constant = constants[name]
                            if (constant == null) {
                                ctx.source.sender.sendMessage(("Key '" + name + "' does not exist.").red())
                                return@executes 0
                            }
                            ctx.source.sender.sendMessage("Current value: " + constant.serialize())
                            1
                        }
                        .then(Commands.argument("value", StringArgumentType.greedyString())
                            .executes { ctx ->
                                val name = ctx.getArgument("name", String::class.java)
                                val value = ctx.getArgument("value", String::class.java)
                                val constant = constants[name]
                                if (constant == null) {
                                    ctx.source.sender.sendMessage(("Key '" + name + "' does not exist.").red())
                                    return@executes 0
                                }
                                if (constant.set(value)) {
                                    ctx.source.sender.sendMessage(
                                        "'${value}' is not a valid value for key of type ${constant.type}.".red())
                                    return@executes 0
                                }
                                ctx.source.sender.sendMessage("$name set to $value".green())
                                1
                            }
                        )
                    )
                )
            )
        }
    }

    protected var value: T

    init {
        try {
            val string = file.getString(name) ?: throw ConfigurationException("Config value $name was not set.")
            value = deserialize(string) ?: throw ConfigurationException("Config value $name was formatted incorrectly for type $type.")
        } catch (error: ConfigurationException) {
            Riftwake.broadcastToOperators(error.message)
            throw error
        }
        constants[name] = this
    }

    operator fun invoke(): T = value

    fun set(value: String, isFromFile: Boolean = false): Boolean {
        this.value = deserialize(value) ?: return false
        if (!isFromFile)
            file.set(name, value)
        return true
    }

    abstract fun serialize(): String
    abstract fun deserialize(value: String): T?
}