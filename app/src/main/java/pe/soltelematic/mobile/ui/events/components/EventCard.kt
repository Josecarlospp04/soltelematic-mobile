package pe.soltelematic.mobile.ui.events.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import pe.soltelematic.mobile.R
import pe.soltelematic.mobile.domain.model.AlertEvent
import pe.soltelematic.mobile.domain.model.AlertEventType
import pe.soltelematic.mobile.ui.events.AddressResolution
import java.time.Duration
import java.time.Instant

/**
 * Tarjeta de un evento de la bandeja de alertas (Sprint 3A, Bloque 2). unseen se calcula en
 * EventsScreen contra seenBaselineId (ver EventsUiState), no acá -- esta tarjeta solo pinta lo
 * que le llega.
 */
@Composable
fun EventCard(
    event: AlertEvent,
    unseen: Boolean,
    addressResolution: AddressResolution?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = event.deviceId != null, onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            EventTypeIcon(event.type)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.deviceName ?: stringResource(R.string.asset_unnamed),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = event.name ?: stringResource(R.string.events_unnamed_event),
                    style = MaterialTheme.typography.bodyMedium
                )
                // detail es el umbral configurado ("5 kph") -- sin él no hay con qué comparar
                // speedText, aunque speedText no sea null (ej. ignición con la unidad detenida:
                // speed.value=0, así que speedText="0 kph", pero detail viene vacío por no tener
                // umbral). La señal es la presencia de detail, no el tipo de evento: si mañana
                // otro tipo trae umbral, esto lo muestra solo, sin tocar este código.
                if (!event.detail.isNullOrBlank() && event.speedText != null) {
                    Text(
                        text = stringResource(R.string.events_speed_vs_limit, event.speedText, event.detail),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = relativeEventTimeText(event.occurredAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AddressLine(addressResolution)
            }
            if (unseen) UnseenDot()
        }
    }
}

@Composable
private fun EventTypeIcon(type: AlertEventType) {
    val icon = type.toIcon()
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .background(MaterialTheme.colorScheme.surface, CircleShape)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
    }
}

// Mismos íconos que AssetBottomSheet.IgnitionRow para ON/OFF -- mismo concepto, misma app.
private fun AlertEventType.toIcon(): ImageVector = when (this) {
    AlertEventType.OVERSPEED -> Icons.Filled.Speed
    AlertEventType.IGNITION_ON -> Icons.Filled.Bolt
    AlertEventType.IGNITION_OFF -> Icons.Filled.PowerOff
    AlertEventType.GEOFENCE_IN -> Icons.AutoMirrored.Filled.Login
    AlertEventType.GEOFENCE_OUT -> Icons.AutoMirrored.Filled.Logout
    AlertEventType.UNKNOWN -> Icons.AutoMirrored.Filled.HelpOutline
}

@Composable
private fun UnseenDot() {
    Box(
        modifier = Modifier
            .padding(top = 4.dp)
            .size(10.dp)
            .background(MaterialTheme.colorScheme.primary, CircleShape)
    )
}

// Reutiliza los strings de dirección de la ficha (Sprint 2A) -- mismo patrón que
// HistoryTimeline.AddressLine: resolution null = todavía no entró en pantalla, se trata igual
// que "cargando" porque para cuando el usuario alcanza a leerlo ya debería haber corrido.
@Composable
private fun AddressLine(resolution: AddressResolution?) {
    val address = (resolution as? AddressResolution.Resolved)?.address
    val text = address ?: stringResource(
        if (resolution == null || resolution is AddressResolution.Loading) {
            R.string.asset_detail_address_loading
        } else {
            R.string.asset_detail_address_unavailable
        }
    )
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        fontStyle = if (address != null) FontStyle.Normal else FontStyle.Italic,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

// Duplicado a propósito de ui.map.relativeLastSeenText: mismo cálculo, pero es un concepto propio
// de "cuándo ocurrió el evento", no de "última posición conocida de una unidad" -- tres líneas no
// ameritan compartir un archivo util entre dos pantallas (mismo criterio que el resto de la app).
private fun relativeEventTimeText(occurredAt: Instant?, now: Instant = Instant.now()): String {
    if (occurredAt == null) return "-"
    val elapsed = Duration.between(occurredAt, now)
    return when {
        elapsed.toMinutes() < 1 -> "hace instantes"
        elapsed.toHours() < 1 -> "hace ${elapsed.toMinutes()} min"
        elapsed.toDays() < 1 -> "hace ${elapsed.toHours()} h"
        else -> "hace ${elapsed.toDays()} d"
    }
}
