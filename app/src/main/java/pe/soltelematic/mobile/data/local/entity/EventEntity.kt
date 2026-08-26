package pe.soltelematic.mobile.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * Caché de GET events, PK = id del servidor (nunca autogenerado) -- deja abierta la puerta a
 * sincronización real si GPSWOX expone alguna vez events/update. type guarda el serverKey de
 * AlertEventType (String plano, no el enum: Room/Converters no necesitan saber de este enum,
 * el remapeo hacia/desde dominio vive en EventMapper).
 */
@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey val id: Int,
    val type: String,
    val alertId: Int?,
    val alertName: String?,
    val deviceId: Int?,
    val deviceName: String?,
    val name: String?,
    val detail: String?,
    val speedValue: Double?,
    val speedUnit: String?,
    val speedHuman: String?,
    val lat: Double?,
    val lng: Double?,
    val occurredAt: Instant?, // desde time.timestamp (epoch UTC correcto en este endpoint)
    val occurredFormatted: String? // desde time.formatted
)
