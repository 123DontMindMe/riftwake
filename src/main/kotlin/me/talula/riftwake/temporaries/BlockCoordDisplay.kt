package me.talula.riftwake.temporaries

import com.github.retrooper.packetevents.protocol.entity.data.EntityData
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes
import com.github.retrooper.packetevents.protocol.world.states.type.StateType
import com.github.retrooper.packetevents.util.Vector3f
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity
import io.github.retrooper.packetevents.util.SpigotReflectionUtil
import me.talula.riftwake.utils.forPacket
import me.talula.riftwake.utils.parse
import me.talula.riftwake.utils.plus
import me.talula.riftwake.utils.sendPacket
import org.bukkit.Location
import org.bukkit.entity.Player
import java.util.*

class BlockCoordDisplay(
    val player: Player,
    private var blockMaterial: StateType,
    private var blockLocation: Location,
    val isInteractable: Boolean = false,
) : Temporary {
    private val displayEntityId = SpigotReflectionUtil.generateEntityId()
    private val textEntityId = SpigotReflectionUtil.generateEntityId()
    private val interactEntityId = if (isInteractable) SpigotReflectionUtil.generateEntityId() else -1

    init {
        player.sendPacket(WrapperPlayServerSpawnEntity(
            displayEntityId, UUID.randomUUID(), EntityTypes.BLOCK_DISPLAY,
            blockLocation.forPacket(), 0f, 0, null
        ))
        player.sendPacket(WrapperPlayServerEntityMetadata(
            displayEntityId, listOf(
                EntityData(0, EntityDataTypes.BYTE, 0x40.toByte()),  // glowing
                EntityData(23, EntityDataTypes.BLOCK_STATE, blockMaterial.createBlockState().globalId),
            )
        ))

        player.sendPacket(WrapperPlayServerSpawnEntity(
            textEntityId, UUID.randomUUID(), EntityTypes.TEXT_DISPLAY,
            blockLocation.toCenterLocation().forPacket(), 0f, 0, null
        ))
        player.sendPacket(WrapperPlayServerEntityMetadata(
            textEntityId, listOf(
                EntityData(0, EntityDataTypes.BYTE, 0x40.toByte()),  // glowing
                EntityData(11, EntityDataTypes.VECTOR3F, Vector3f(0f, 0.8f, 0f)),  // translation
                EntityData(12, EntityDataTypes.VECTOR3F, Vector3f(1.2f, 1.2f, 1.2f)),  // scale
                EntityData(15, EntityDataTypes.BYTE, 3)  // billboard center
            )
        ))
        updateText()

        if (isInteractable) {
            player.sendPacket(WrapperPlayServerSpawnEntity(
                interactEntityId, UUID.randomUUID(), EntityTypes.INTERACTION,
                blockLocation.plus(0.5, 0.0, 0.5).forPacket(), 0f, 0, null
            ))
            player.sendPacket(WrapperPlayServerEntityMetadata(
                interactEntityId, listOf(
                    EntityData(8, EntityDataTypes.FLOAT, 1f),  // width
                    EntityData(9, EntityDataTypes.FLOAT, 1f),  // height
                    EntityData(10, EntityDataTypes.BOOLEAN, true),  // responsive
                )
            ))
        }
    }

    var location: Location
        get() = blockLocation
        set(location) {
            blockLocation = location
            player.sendPacket(WrapperPlayServerEntityTeleport(displayEntityId, location.forPacket(), false))
            player.sendPacket(WrapperPlayServerEntityTeleport(textEntityId, location.toCenterLocation().forPacket(), false))
            if (isInteractable)
                player.sendPacket(WrapperPlayServerEntityTeleport(interactEntityId, location.plus(0.5, 0.0, 0.5).forPacket(), false))
            updateText()
        }

    var material: StateType
        get() = blockMaterial
        set(material) {
            blockMaterial = material
            player.sendPacket(WrapperPlayServerEntityMetadata(
                displayEntityId, listOf(EntityData(23, EntityDataTypes.BLOCK_STATE, material.createBlockState().globalId))
            ))
        }

    override fun delete() {
        if (isInteractable)
            player.sendPacket(WrapperPlayServerDestroyEntities(displayEntityId, textEntityId, interactEntityId))
        else
            player.sendPacket(WrapperPlayServerDestroyEntities(displayEntityId, textEntityId))
    }

    private fun updateText() {
        player.sendPacket(WrapperPlayServerEntityMetadata(
            textEntityId, listOf(
                EntityData(23, EntityDataTypes.ADV_COMPONENT,
                    "<RED|${blockLocation.x.toInt()}> <GREEN|${blockLocation.y.toInt()}> <BLUE|${blockLocation.z.toInt()}>".parse()),
            )
        ))
    }
}