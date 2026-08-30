package pe.soltelematic.mobile.ui.events.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import pe.soltelematic.mobile.R
import pe.soltelematic.mobile.core.format.normalizeSpeedUnitSuffix
import pe.soltelematic.mobile.domain.model.AlertEvent
import pe.soltelematic.mobile.domain.model.AlertEventType
import pe.soltelematic.mobile.ui.events.AddressResolution
import pe.soltelematic.mobile.ui.theme.LocalSoltelematicColors
import pe.soltelematic.mobile.ui.theme.SoltelematicMetricTypography
import pe.soltelematic.mobile.ui.theme.SoltelematicMinTouchTarget
import pe.soltelematic.mobile.ui.theme.SoltelematicShapes
import pe.soltelematic.mobile.ui.theme.SoltelematicSpacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val IconCircleSize = 40.dp
private val UnseenDotSize = 10.dp
private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

/**
 * Tarjeta de un evento de la bandeja de alertas (Sprint 3A, Bloque 2). unseen se calcula en
 * EventsScreen contra seenBaselineId (ver EventsUiState), no acá -- esta tarjeta solo pinta lo
 * que le llega. Toda la fila es tocable (no solo el ícono), con 48dp mínimo táctil.
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
        shape = SoltelematicShapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = SoltelematicMinTouchTarget)
            .clickable(enabled = event.deviceId != null, onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(SoltelematicSpacing.md),
            modifier = Modifier
                .fillMaxWidth()
                .padding(SoltelematicSpacing.md)
        ) {
            EventTypeIcon(event.type)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${event.deviceName ?: stringResource(R.string.asset_unnamed)} · " +
                        (event.name ?: stringResource(R.string.events_unnamed_event)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                // detail es el umbral configurado ("5 kph") -- sin él no hay con qué comparar
                // speedText, aunque speedText no sea null (ej. ignición con la unidad detenida:
                // speed.value=0, así que speedText="0 kph", pero detail viene vacío por no tener
                // umbral). La señal es la presencia de detail, no el tipo de evento: si mañana
                // otro tipo trae umbral, esto lo muestra solo, sin tocar este código.
                if (!event.detail.isNullOrBlank() && event.speedText != null) {
                    Text(
                        text = stringResource(
                            R.string.events_speed_vs_limit,
                            normalizeSpeedUnitSuffix(event.speedText),
                            normalizeSpeedUnitSuffix(event.detail)
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                AddressLine(addressResolution)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = event.occurredAt.toTimeText(),
                    style = SoltelematicMetricTypography.small,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (unseen) UnseenDot()
            }
        }
    }
}

@Composable
private fun EventTypeIcon(type: AlertEventType) {
    val icon = type.toIcon()
    val (tint, wash) = type.toColors()
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(IconCircleSize)
            .background(wash, CircleShape)
    ) {
        Icon(icon, contentDescription = null, tint = tint)
    }
}

// Mismos íconos que AssetBottomSheet.attributeChips para ON/OFF -- mismo concepto, misma app.
private fun AlertEventType.toIcon(): ImageVector = when (this) {
    AlertEventType.OVERSPEED -> Icons.Filled.Speed
    AlertEventType.IGNITION_ON -> Icons.Filled.Bolt
    AlertEventType.IGNITION_OFF -> Icons.Filled.PowerOff
    AlertEventType.GEOFENCE_IN -> Icons.AutoMirrored.Filled.Login
    AlertEventType.GEOFENCE_OUT -> Icons.AutoMirrored.Filled.Logout
    AlertEventType.UNKNOWN -> Icons.AutoMirrored.Filled.HelpOutline
}

/**
 * Wash del círculo del ícono según el tipo, con los 4 tokens fijos de estado (nunca se
 * re-tematizan por marca, ver Color.kt): OVERSPEED/GEOFENCE_OUT en statusAlert (algo que requiere
 * atención), IGNITION_ON en statusIdle (ámbar, "motor encendido"), IGNITION_OFF/UNKNOWN en
 * statusOffline (neutro), GEOFENCE_IN en statusMoving (verde, "entró" es la dirección esperada).
 */
@Composable
private fun AlertEventType.toColors(): Pair<Color, Color> {
    val colors = LocalSoltelematicColors.current
    return when (this) {
        AlertEventType.OVERSPEED -> colors.statusAlert to colors.statusAlertWash
        AlertEventType.IGNITION_ON -> colors.statusIdle to colors.statusIdleWash
        AlertEventType.IGNITION_OFF -> colors.statusOffline to colors.statusOfflineWash
        AlertEventType.GEOFENCE_IN -> colors.statusMoving to colors.statusMovingWash
        AlertEventType.GEOFENCE_OUT -> colors.statusAlert to colors.statusAlertWash
        AlertEventType.UNKNOWN -> colors.statusOffline to colors.statusOfflineWash
    }
}

/** Punto discreto, no un fondo distinto para toda la fila (ver mockup). Mismo rojo que el badge del header. */
@Composable
private fun UnseenDot() {
    Box(
        modifier = Modifier
            .padding(top = SoltelematicSpacing.xs)
            .size(UnseenDotSize)
            .background(MaterialTheme.colorScheme.error, CircleShape)
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
        style = MaterialTheme.typography.labelLarge,
        fontStyle = if (address != null) FontStyle.Normal else FontStyle.Italic,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private fun Instant?.toTimeText(): String = this?.let { TIME_FORMAT.format(it) } ?: "--:--"
