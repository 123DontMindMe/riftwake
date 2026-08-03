package me.talula.riftwake.utils

import com.github.retrooper.packetevents.protocol.component.ComponentTypes
import com.github.retrooper.packetevents.protocol.component.builtin.item.ItemRarity
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes
import com.github.retrooper.packetevents.resources.ResourceLocation
import com.sk89q.worldedit.EditSession
import com.sk89q.worldedit.WorldEdit
import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldedit.math.BlockVector3
import com.sk89q.worldedit.util.SideEffectSet
import io.github.retrooper.packetevents.util.SpigotConversionUtil
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.data.BlockData
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.util.Vector
import java.util.*
import kotlin.jvm.optionals.getOrDefault
import kotlin.math.pow

inline infix fun <T> (() -> T).until(predicate: (T) -> Boolean): T {
    var result = this()
    while (!predicate(result))
        result = this()
    return result
}

val ItemStack.nameWithAmount: Component get() {
    val name = if (itemMeta.hasItemName())
        itemMeta.itemName()
    else
        Component.translatable(this)
    return if (maxStackSize == 1)
        name
    else
        name + " x$amount".comp()
}

val ItemStack.hoverableStack: Component get() {
    val meta = itemMeta
    val name: Component
    val color: TextColor
    if (meta.hasItemName()) {
        name = meta.itemName()
        color = meta.itemName().firstColor
    } else {
        name = Component.translatable(this)
        val rarity = forPacket  // meta.rarity STILL doesn't work
            .getComponent(ComponentTypes.RARITY)
            .getOrDefault(ItemRarity.COMMON)
            .name
        color = when (rarity) {
            "UNCOMMON" -> NamedTextColor.YELLOW
            "RARE" -> NamedTextColor.AQUA
            "EPIC" -> NamedTextColor.LIGHT_PURPLE
            else -> NamedTextColor.WHITE
        }
    }

    val formattedName = if (maxStackSize == 1)
        "[".color(color) + name + "]".color(color)
    else
        "[".color(color) + name + " x$amount]".color(color)

    return if (meta.hasLore())
        formattedName.hoverEvent(asHoverEvent())
    else
        formattedName
}

val ItemStack.forPacket: com.github.retrooper.packetevents.protocol.item.ItemStack get() =
    SpigotConversionUtil.fromBukkitItemStack(this)

fun ItemStack.withRandomUUID(): ItemStack {
    val item = clone()
    item.editMeta { it.setData("random-uuid", PersistentDataType.STRING, UUID.randomUUID().toString()) }
    return item
}

fun String.toPersistentDataType(): PersistentDataType<*,*>? {
    return when (this) {
        "byte" -> PersistentDataType.BYTE
        "short" -> PersistentDataType.SHORT
        "int" -> PersistentDataType.INTEGER
        "long" -> PersistentDataType.LONG
        "float" -> PersistentDataType.FLOAT
        "double" -> PersistentDataType.DOUBLE
        "bool" -> PersistentDataType.BOOLEAN
        "string" -> PersistentDataType.STRING
        "byte[]" -> PersistentDataType.BYTE_ARRAY
        "int[]" -> PersistentDataType.INTEGER_ARRAY
        "long[]" -> PersistentDataType.LONG_ARRAY
        else -> null
    }
}

fun <P,C> PersistentDataType<P,C>.parse(string: String): C {
    @Suppress("UNCHECKED_CAST")
    return when (this) {
        PersistentDataType.BYTE -> string.toByte()
        PersistentDataType.SHORT -> string.toShort()
        PersistentDataType.INTEGER -> string.toInt()
        PersistentDataType.LONG -> string.toLong()
        PersistentDataType.FLOAT -> string.toFloat()
        PersistentDataType.DOUBLE -> string.toDouble()
        PersistentDataType.BOOLEAN -> string.toBoolean()
        PersistentDataType.STRING -> string
        PersistentDataType.BYTE_ARRAY -> string.split(",").map { it.trim().toByte() }.toByteArray()
        PersistentDataType.INTEGER_ARRAY -> string.split(",").map { it.trim().toInt() }.toIntArray()
        PersistentDataType.LONG_ARRAY -> string.split(",").map { it.trim().toLong() }.toLongArray()
        else -> null
    } as C
}

fun World.edit(consumer: (EditSession) -> Unit) {
    val session = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(this))
    session.sideEffectApplier = SideEffectSet.none()
    session.use(consumer)
}

fun Material.toStateType() = StateTypes.getByName(ResourceLocation(name.lowercase()))!!

fun Int.pow(power: Double) = toDouble().pow(power)

fun BlockVector3.toLocation(world: World) = Location(world, x().toDouble(), y().toDouble(), z().toDouble())
fun BlockVector3.toVector() = Vector(x(), y(), z())

val Location.blockCoords get() = "$blockX, $blockY, $blockZ"

fun Location.xzDistance2(other: Location): Double {
    val xDiff = x - other.x
    val zDiff = z - other.z
    return xDiff * xDiff + zDiff * zDiff
}

fun Location.xzDistance2() = x * x + z * z

fun Location.setType(type: Material) = world.setType(this, type)

fun Location.setBlock(type: Material, dataConsumer: (BlockData) -> Unit) {
    world.setBlockData(this, type.createBlockData(dataConsumer))
}

fun Location.setBlock(data: BlockData) {
    world.setBlockData(this, data)
}

fun Location.forPacket(): com.github.retrooper.packetevents.protocol.world.Location {
    return SpigotConversionUtil.fromBukkitLocation(this)
}

fun Location.plus(vector: Vector) = clone().add(vector)
fun Location.plus(x: Double, y: Double, z: Double) = clone().add(x, y, z)
fun Location.plus(x: Int, y: Int, z: Int): Location = clone().add(x.toDouble(), y.toDouble(), z.toDouble())

fun Vector.plus(x: Double, y: Double, z: Double): Vector {
    val result = this.clone()
    result.x += x
    result.y += y
    result.z += z
    return result
}