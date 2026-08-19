package pe.soltelematic.mobile.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * Última posición conocida de cada unidad, ya con la URL del icono reescrita (ver
 * IconUrlResolver) y la estela serializada a JSON (ver Converters) -- pocos puntos por unidad,
 * no justifica una tabla aparte. Todo nullable salvo id: alimenta el modo offline (abrir la
 * app sin red y ver la última posición conocida), y GPSWOX omite campos según cada unidad.
 */
@Entity(tableName = "assets")
data class AssetEntity(
    @PrimaryKey val id: Int,
    val groupId: Int?,
    val name: String?,
    val active: Boolean?,
    val lat: Double?,
    val lng: Double?,
    val speedValue: Double?,
    val speedUnit: String?,
    val speedHuman: String?,
    val engineStatus: Boolean?, // null = sin sensor, false = apagado, true = encendido
    val statusType: String?,
    val statusTitle: String?,
    val statusColor: String?,
    val iconUrl: String?,
    val iconColor: String?,
    val iconCourse: Float?,
    val tail: List<TailPoint>, // JSON en la columna, vía Converters
    val tailColor: String?,
    val lastSeenAt: Instant?, // desde time.timestamp, para calcular "hace X"
    val lastSeenFormatted: String? // desde time.formatted, ya con la zona horaria del usuario
)

@Serializable
data class TailPoint(val lat: Double, val lng: Double)
