package pe.soltelematic.mobile.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * Última posición conocida de cada unidad. Todo nullable salvo id: GPSWOX omite campos
 * según la configuración de cada unidad, y coordinates/time pueden faltar si nunca reportó.
 * Es la tabla que alimenta el modo offline (abrir la app sin red y ver la última posición).
 */
@Entity(tableName = "assets")
data class AssetEntity(
    @PrimaryKey val id: Int,
    val groupId: Int?,
    val name: String?,
    val active: Boolean?,
    val speed: String?, // "42 km/h", tal como llega del servidor
    val engineStatus: String?,
    val statusType: String?,
    val statusTitle: String?,
    val statusColor: String?,
    val lat: Double?,
    val lng: Double?,
    val lastSeenAt: Instant?, // desde time.timestamp, para calcular "hace X"
    val lastSeenFormatted: String? // desde time.formatted, ya con la zona horaria del usuario aplicada
)
