package me.talula.riftwake.crates

import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import io.papermc.paper.command.brigadier.Commands
import me.talula.riftwake.Riftwake
import me.talula.riftwake.dialogue.ConfirmStage
import me.talula.riftwake.items.Items
import me.talula.riftwake.utils.*
import net.kyori.adventure.text.TextComponent
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

object CrateRegistry {
    val cratesByLocation = mutableMapOf<Location, Crate>()
    val cratesById = mutableMapOf<String, Crate>()

    val file = Riftwake.Config("crates.yml")

    fun init() {
        for ((crateId, data) in file.sections) {
            val name = data.getString("name")?.parse()
            if (name == null) {
                Riftwake.broadcastToOperators("crate '$crateId' missing field 'name'".red)
                continue
            }
            val location = data.getLocation("location")
            if (location == null) {
                Riftwake.broadcastToOperators("crate '$crateId' missing field 'location'".red)
                continue
            }
            val keyItemId = data.getString("key-item-id")
            val keyName = data.getString("key-name")?.parse()

            val lootTable = RandomTable<ItemStack>()
            val crateLoot = data.getMapList("loot")
            for (entry in crateLoot) {
                val item = entry["item"] as ItemStack
                val weight = entry["weight"] as Double
                lootTable.add(item, weight)
            }
            val crate = Crate(crateId, name, location, keyItemId, keyName, lootTable).register()
            cratesByLocation[location] = crate
            cratesById[crateId] = crate
        }

        val dailyLootTable = RandomTable<ItemStack>()
        dailyLootTable.add(ItemStack(Material.GOLD_INGOT), 1.0)
        dailyLootTable.add(Items.createBridgeEgg(), 1.0)
        val dailyCrate = Crate("daily", "Daily".gold.bold, Location(null, 0.0, 0.0, 0.0), null, null, dailyLootTable)

        Riftwake.registerCommand(Commands.literal("money-reward")
            .requires { it.sender.isOp }
            .then(Commands.argument("amount", IntegerArgumentType.integer())
                .runPlayer { ctx, player ->
                    val amount = IntegerArgumentType.getInteger(ctx, "amount")
                    val item = ItemStack.of(Material.PAPER)
                    item.editMeta {
                        it.itemName("$$amount".gold)
                        it.setData("money-reward", PersistentDataType.INTEGER, amount)
                    }
                    player.give(item)
                }
            )
        )

        Riftwake.registerCommand(Commands.literal("daily")
            .replyPlayer { player ->
                val lastUse = player.getData("last-daily-crate-use", PersistentDataType.LONG) ?: 0
                val currentTime = System.currentTimeMillis()

                val timeRemaining = 1000 * 60 * 60 * 24 - (currentTime - lastUse)
                if (timeRemaining > 0)
                    throw CommandFail("Please wait ${timeRemaining.toTimeString()} to claim your next daily crate.")

                player.setData("last-daily-crate-use", PersistentDataType.LONG, currentTime)
                CratePullGUI(player, dailyCrate).open()
                null
            }
        )

        Riftwake.registerCommand(Commands.literal("crate")
            .requires { it.sender.isOp }
            .then(Commands.literal("delete")
                .then(Commands.argument("crate-id", StringArgumentType.word())
                    .replyPlayer { ctx, player ->
                        val id = ctx.getArgument("crate-id", String::class.java)
                        val crate = cratesById[id] ?: throw CommandFail("No crate with id '$id' exists.")
                        if (crate.numRewards > 10) {
                            player.dialogue.start("Crate deletion cancelled.".red,
                                ConfirmStage("Crate '$id' has ${crate.numRewards} entries in its loot table. Are you sure you want to delete it?".yellow) {
                                    cratesById.remove(id)
                                    cratesByLocation.remove(crate.location)
                                    file[id] = null
                                    file.save()
                                    player.sendMessage("Crate deleted.".green)
                                }
                            )
                            null
                        } else {
                            cratesById.remove(id)
                            cratesByLocation.remove(crate.location)
                            file[id] = null
                            file.save()
                            "Crate deleted.".green
                        }
                    }
                )
            )
            .then(Commands.literal("create")
                .then(Commands.argument("crate-id", StringArgumentType.word())
                    .replyPlayer { ctx, player ->
                        val block = player.getTargetBlockExact(6) ?:
                            throw CommandFail("You must be looking at a block to set it as a crate")
                        if (block.location in cratesByLocation)
                            throw CommandFail("That block is already a crate")

                        val id = ctx.getArgument("crate-id", String::class.java)
                        if (id in cratesById)
                            throw CommandFail("A crate with that id already exists!")

                        Crate(id, "Unnamed".comp(), block.location, null, null, RandomTable()).register()
                        "Crate '$id' created.".green
                    }
                )
            )
            .then(Commands.literal("addreward")
                .then(Commands.argument("crate-id", StringArgumentType.word())
                    .then(Commands.argument("weight", DoubleArgumentType.doubleArg(0.0))
                        .replyPlayer { ctx, player ->
                            val crateId = StringArgumentType.getString(ctx, "crate-id")
                            val crate = cratesById[crateId] ?: throw CommandFail("No crate exists with that id.")
                            val weight = DoubleArgumentType.getDouble(ctx, "weight")
                            val item = player.itemHeld ?: throw CommandFail("You must be holding the item you want to add as a reward.")

                            crate.addReward(item.clone(), weight)
                            "Added ".green + item.hoverableStack
                        }
                        .then(Commands.argument("slot", IntegerArgumentType.integer(1))
                            .replyPlayer { ctx, player ->
                                val crateId = StringArgumentType.getString(ctx, "crate-id")
                                val crate = cratesById[crateId] ?: throw CommandFail("No crate exists with that id")
                                val item = player.itemHeld ?: throw CommandFail("You must be holding the item you want to add as a reward.")

                                val weight = DoubleArgumentType.getDouble(ctx, "weight")
                                val slot = IntegerArgumentType.getInteger(ctx, "slot") - 1
                                if (slot > crate.numRewards)
                                    throw CommandFail("There are only ${crate.numRewards} rewards in this crate.")
                                crate.addReward(item.clone(), weight, slot)
                                "Inserted ".green + item.hoverableStack + " into slot ${slot + 1}".green
                            }
                        )
                    )
                )
            )
            .then(Commands.literal("setkey")
                .then(Commands.argument("crate-id", StringArgumentType.word())
                    .replyPlayer { ctx, player ->
                        val crateId = StringArgumentType.getString(ctx, "crate-id")
                        val crate = cratesById[crateId] ?: throw CommandFail("No crate exists with that id")
                        val item = player.itemHeld ?: throw CommandFail("You must be holding the item you want to add as a reward.")

                        val keyItemId = item.getStringData("item-id") ?: throw CommandFail("You must be holding an item with an item id.")
                        val keyName = item.itemMeta.itemName() as? TextComponent ?: throw CommandFail("The key's name must be custom.")
                        crate.keyName = keyName
                        crate.keyItemId = keyItemId
                        "Set key to ".green + keyName + " (id=$keyItemId)".green
                    }
                )
            )
            .then(Commands.literal("setname")
                .then(Commands.argument("crate-id", StringArgumentType.word())
                    .then(Commands.argument("name", StringArgumentType.greedyString())
                        .replyPlayer { ctx, _ ->
                            val crateId = StringArgumentType.getString(ctx, "crate-id")
                            val crate = cratesById[crateId] ?: throw CommandFail("No crate exists with that id")
                            val name = StringArgumentType.getString(ctx, "name")
                            crate.name = name.parse()
                            "Set name to ".green + crate.name
                        }
                    )
                )
            )
            .then(Commands.literal("removereward")
                .then(Commands.argument("crate-id", StringArgumentType.word())
                    .then(Commands.argument("slot", IntegerArgumentType.integer(1))
                        .replyPlayer { ctx, _ ->
                            val crateId = StringArgumentType.getString(ctx, "crate-id")
                            val crate = cratesById[crateId] ?: throw CommandFail("No crate exists with that id")

                            val slot = IntegerArgumentType.getInteger(ctx, "slot")
                            if (slot > crate.numRewards)
                                throw CommandFail("There are only ${crate.numRewards} rewards in this crate.")

                            val item = crate.removeReward(slot - 1)
                            "Removed reward ".green + item.hoverableStack + " from slot $slot".green
                        }
                    )
                )
            )
        )
    }

    operator fun get(location: Location) = cratesByLocation[location]
    operator fun contains(location: Location) = cratesByLocation.containsKey(location)
}

class Crate(
    val id: String,
    name: TextComponent = "Unnamed".comp(),
    location: Location,
    keyItemId: String?,
    keyName: TextComponent?,
    private val lootTable: RandomTable<ItemStack>
) {
    var isRegistered: Boolean = false

    var name: TextComponent = name
        set(value) {
            field = value
            if (!isRegistered)
                return
            CrateRegistry.file.getSection(id)?.set("name", value.serialize()) ?:
                Riftwake.broadcastToOperators("crate '$id' not found in crates.yml when setting name".red)
            CrateRegistry.file.save()
        }
    var location: Location = location
        set(value) {
            if (!isRegistered) {
                field = value
                return
            }
            CrateRegistry.cratesByLocation.remove(field)
            CrateRegistry.cratesByLocation[value] = this
            field = value
            CrateRegistry.file.getSection(id)?.set("location", value) ?:
                Riftwake.broadcastToOperators("crate '$id' not found in crates.yml when setting location".red)
            CrateRegistry.file.save()
        }
    var keyItemId: String? = keyItemId
        set(value) {
            if (!isRegistered)
                return
            field = value
            CrateRegistry.file.getSection(id)?.set("key-item-id", value) ?:
                Riftwake.broadcastToOperators("crate '$id' not found in crates.yml when setting key-item-id".red)
            CrateRegistry.file.save()
        }
    var keyName: TextComponent? = keyName
        set(value) {
            if (!isRegistered)
                return
            field = value
            CrateRegistry.file.getSection(id)?.set("key-name", value?.serialize()) ?:
                Riftwake.broadcastToOperators("crate '$id' not found in crates.yml when setting key-name".red)
            CrateRegistry.file.save()
        }

    fun addReward(item: ItemStack, weight: Double) {
        lootTable.add(item, weight)
        CrateRegistry.file.getSection(id)?.set("loot", lootTable.entries.map { mapOf("item" to it.value, "weight" to it.weight) }) ?:
            Riftwake.broadcastToOperators("crate '$id' not found in crates.yml when adding reward".red)
        CrateRegistry.file.save()
    }

    fun addReward(item: ItemStack, weight: Double, index: Int) {
        lootTable.add(item, weight, index)
        CrateRegistry.file.getSection(id)?.set("loot", lootTable.entries.map { mapOf("item" to it.value, "weight" to it.weight) }) ?:
            Riftwake.broadcastToOperators("crate '$id' not found in crates.yml when inserting reward".red)
        CrateRegistry.file.save()
    }

    fun removeReward(index: Int): ItemStack {
        val entry = lootTable.remove(index)
        CrateRegistry.file.getSection(id)?.set("loot", lootTable.entries.map { mapOf("item" to it.value, "weight" to it.weight) }) ?:
            Riftwake.broadcastToOperators("crate '$id' not found in crates.yml when removing reward".red)
        CrateRegistry.file.save()
        return entry.value
    }

    val numRewards get() = lootTable.entries.size
    fun pull() = lootTable.pull()
    val entries get() = lootTable.entries

    fun register(): Crate {
        CrateRegistry.file[id] = object : YamlConfiguration() {
            init {
                set("id", id)
                set("name", this@Crate.name.serialize())
                set("location", location)
                set("key-item-id", keyItemId)
                set("key-name", keyName?.serialize())
                set("loot", lootTable.entries.map { mapOf("item" to it.value, "weight" to it.weight) })
            }
        }
        CrateRegistry.file.save()
        CrateRegistry.cratesById[id] = this
        CrateRegistry.cratesByLocation[location] = this
        isRegistered = true
        return this
    }
}