package pe.soltelematic.mobile.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * Placeholder. El contrato de alertas no está definido todavía (fuera de alcance de este
 * sprint) — estos campos son un supuesto razonable, no un contrato verificado. Se ajustan
 * cuando se defina la API de alertas.
 */
@Entity(tableName = "alerts")
data class AlertEntity(
    @PrimaryKey val id: Int,
    val assetId: Int?,
    val message: String?,
    val occurredAt: Instant?,
    val read: Boolean = false
)
