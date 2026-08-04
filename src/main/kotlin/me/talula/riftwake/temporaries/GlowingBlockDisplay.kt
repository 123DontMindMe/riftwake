package me.talula.riftwake.temporaries

import com.github.retrooper.packetevents.protocol.entity.data.EntityData
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams.TeamMode
import io.github.retrooper.packetevents.util.SpigotReflectionUtil
import me.talula.riftwake.utils.forPacket
import me.talula.riftwake.utils.plus
import me.talula.riftwake.utils.sendPacket
import org.bukkit.Location
import org.bukkit.entity.Player
import java.util.*
import kotlin.experimental.or

class GlowingBlockDisplay(
    val player: Player,
    private var blockLocation: Location
) {
    private val displayEntityId = SpigotReflectionUtil.generateEntityId()

    init {
        val uuid = UUID.randomUUID()
        player.sendPacket(WrapperPlayServerSpawnEntity(
            displayEntityId, uuid, EntityTypes.SLIME,
            blockLocation.plus(0.5, 0.0, 0.5).forPacket(), 0f, 0, null
        ))
        player.sendPacket(WrapperPlayServerEntityMetadata(
            displayEntityId, listOf(
                EntityData(0, EntityDataTypes.BYTE, 0x20.toByte() or 0x40.toByte()),  // invisible and glowing
                EntityData(16, EntityDataTypes.INT, 2)  // size
            )
        ))
        player.sendPacket(WrapperPlayServerTeams(
            "in-spawn",
            TeamMode.ADD_ENTITIES,
            null as WrapperPlayServerTeams.ScoreBoardTeamInfo?,
            uuid.toString()
        ))
    }

    var location: Location
        get() = blockLocation
        set(location) {
            blockLocation = location
            player.sendPacket(WrapperPlayServerEntityTeleport(displayEntityId, location.plus(0.5, 0.0, 0.5).forPacket(), false))
        }

    fun delete() {
        player.sendPacket(WrapperPlayServerDestroyEntities(displayEntityId))
    }
}