package pe.soltelematic.mobile.ui.assetdetail

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import pe.soltelematic.mobile.R
import pe.soltelematic.mobile.core.time.LastSeenFreshness
import pe.soltelematic.mobile.core.time.lastSeenFreshness
import pe.soltelematic.mobile.domain.model.AssetDetail
import pe.soltelematic.mobile.domain.model.AssetStatus
import pe.soltelematic.mobile.domain.model.AssetStatusType
import pe.soltelematic.mobile.domain.model.GeoPoint
import pe.soltelematic.mobile.domain.model.Ignition
import pe.soltelematic.mobile.domain.model.UnitStat
import pe.soltelematic.mobile.ui.map.relativeLastSeenText
import pe.soltelematic.mobile.ui.theme.LocalSoltelematicColors
import pe.soltelematic.mobile.ui.theme.SoltelematicColors
import pe.soltelematic.mobile.ui.theme.SoltelematicElevation
import pe.soltelematic.mobile.ui.theme.SoltelematicIconSpec
import pe.soltelematic.mobile.ui.theme.SoltelematicMetricTypography
import pe.soltelematic.mobile.ui.theme.SoltelematicPillShape
import pe.soltelematic.mobile.ui.theme.SoltelematicShapes
import pe.soltelematic.mobile.ui.theme.SoltelematicSpacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val LAST_REPORT_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

/** Pestaña "Resumen": métricas superiores, bloque de ubicación y "HOY". Siempre visible. */
@Composable
fun SummaryTab(
    detail: AssetDetail,
    address: String?,
    isAddressLoading: Boolean,
    todayStats: List<UnitStat>,
    isTodayStatsLoading: Boolean,
    modifier: Modifier = Modifier
) {
    // Una sola vez acá: alimenta tanto el color de LastSeenLine como el atenuado de "HOY" --
    // una unidad sin reportar hace días no debe verse con las mismas métricas "frescas" que una
    // que acaba de reportar, aunque el número en sí no haya cambiado.
    val freshness = lastSeenFreshness(detail.lastSeenAt)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(SoltelematicSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(SoltelematicSpacing.xl)
    ) {
        SpeedStatusRow(detail)
        LocationBlock(detail = detail, address = address, isAddressLoading = isAddressLoading, freshness = freshness)
        TodayStatsBlock(
            stats = todayStats,
            isLoading = isTodayStatsLoading,
            isStale = freshness == LastSeenFreshness.VERY_STALE
        )
    }
}

/**
 * Bloque de velocidad/estado + último reporte (solo hora), como Column de dos filas -- NO una
 * sola Row. El intento anterior le daba weight(1f) al bloque de velocidad para "proteger" el
 * texto de último reporte, pero eso apretaba TODO lo que hay dentro de ese bloque (número Metric
 * L + badge + ícono) contra el ancho ya reducido por el peso -- el badge, sin protección propia,
 * terminaba con un ancho de una letra y Compose lo partía verticalmente ("DETENIDA" en 8 líneas).
 * Ahora cada elemento vive en su propia fila y ninguno compite por espacio con el otro:
 * - Fila 1: velocidad (tamaño de fuente fijo, no crece) a la izquierda, badge+ignición a la
 *   derecha, SpaceBetween sin weights -- cada lado mide su ancho natural.
 * - Fila 2, aparte: "Último reporte HH:mm", ancho completo, ya no compite por espacio con nada.
 */
@Composable
private fun SpeedStatusRow(detail: AssetDetail) {
    Column(verticalArrangement = Arrangement.spacedBy(SoltelematicSpacing.xs)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(SoltelematicSpacing.xs)) {
                Text(
                    text = detail.speedText ?: "-",
                    style = SoltelematicMetricTypography.large,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.map_speed_unit),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = SoltelematicSpacing.xs)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SoltelematicSpacing.sm)) {
                StatusPill(detail.status)
                IgnitionIcon(detail.ignition)
            }
        }
        // Solo la hora, no lastSeenFormatted completo (fecha+hora, ver AssetDetailMapper.kt) --
        // lastSeenAt ya es el mismo instante resuelto (parseado de time.formatted, evitando el
        // desfase conocido de time.timestamp), reformateado acá a HH:mm. maxLines/ellipsis quedan
        // como salvaguarda, no porque haga falta con un string de 5 caracteres.
        Text(
            text = stringResource(R.string.asset_detail_last_report, detail.lastSeenAt.toLastReportTimeText()),
            style = MaterialTheme.typography.labelLarge,
            color = LocalSoltelematicColors.current.inkFaint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun Instant?.toLastReportTimeText(): String = this?.let { LAST_REPORT_TIME_FORMAT.format(it) } ?: "-"

/**
 * A diferencia de AssetBottomSheet.kt (mapa), acá el pill usa los 4 tokens fijos de estado
 * (statusMoving/Idle/Alert/Offline) en vez del colorHex crudo del servidor -- así lo pide esta
 * pantalla específicamente (ver tarea de re-tematizado de la ficha).
 */
@Composable
private fun StatusPill(status: AssetStatus) {
    val colors = LocalSoltelematicColors.current
    val (color, wash) = statusPillColors(status.type, colors)
    AssistChip(
        onClick = {},
        enabled = false,
        shape = SoltelematicPillShape,
        label = {
            Text(
                status.label().uppercase(),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            disabledContainerColor = wash,
            disabledLabelColor = color
        )
    )
}

private fun statusPillColors(type: AssetStatusType, colors: SoltelematicColors) = when (type) {
    AssetStatusType.ONLINE -> colors.statusMoving to colors.statusMovingWash
    AssetStatusType.ENGINE, AssetStatusType.ACK -> colors.statusIdle to colors.statusIdleWash
    AssetStatusType.BLOCKED -> colors.statusAlert to colors.statusAlertWash
    AssetStatusType.OFFLINE, AssetStatusType.UNKNOWN -> colors.statusOffline to colors.statusOfflineWash
}

/**
 * Etiqueta propia por status.type en vez del status.title crudo del servidor (jerga de
 * protocolo, p. ej. "ACK"). Duplicada a propósito (mismo criterio que toComposeColorOrDefault en
 * AssetBottomSheet.kt): seis líneas de mapeo no justifican compartir una función entre pantallas.
 */
@Composable
private fun AssetStatus.label(): String = when (type) {
    AssetStatusType.ACK -> stringResource(R.string.asset_status_ack)
    AssetStatusType.OFFLINE -> stringResource(R.string.asset_status_offline)
    AssetStatusType.ONLINE -> stringResource(R.string.asset_status_online)
    AssetStatusType.ENGINE -> stringResource(R.string.asset_status_engine)
    AssetStatusType.BLOCKED -> stringResource(R.string.asset_status_blocked)
    AssetStatusType.UNKNOWN -> title ?: stringResource(R.string.asset_status_unknown)
}

// El mockup de la ficha no reserva una ranura propia para ignición (a diferencia de la versión
// anterior de esta pantalla, que la mostraba como segunda métrica) -- se conserva como ícono
// pequeño junto al badge de estado para no perder el dato, en vez de quitarlo sin que la tarea lo
// pidiera explícitamente.
@Composable
private fun IgnitionIcon(ignition: Ignition) {
    val (icon, textRes) = when (ignition) {
        Ignition.ON -> Icons.Filled.Bolt to R.string.asset_ignition_on
        Ignition.OFF -> Icons.Filled.PowerOff to R.string.asset_ignition_off
        Ignition.NO_SENSOR -> Icons.AutoMirrored.Filled.HelpOutline to R.string.asset_ignition_no_sensor
    }
    Icon(
        icon,
        contentDescription = stringResource(textRes),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(SoltelematicIconSpec.small)
    )
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun LocationBlock(detail: AssetDetail, address: String?, isAddressLoading: Boolean, freshness: LastSeenFreshness) {
    Column(verticalArrangement = Arrangement.spacedBy(SoltelematicSpacing.sm)) {
        SectionHeader(stringResource(R.string.asset_detail_location_title))
        Card(
            shape = SoltelematicShapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = SoltelematicElevation.e1),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(SoltelematicSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(SoltelematicSpacing.md)
            ) {
                val position = detail.position
                if (position == null) {
                    // La unidad nunca reportó posición -- nada de mapa en 0,0, un vacío explícito.
                    EmptyLocationState()
                } else {
                    AddressLine(address = address, isLoading = isAddressLoading)
                    CoordinatesLine(position = position)
                    LastSeenLine(detail = detail, freshness = freshness)
                }
            }
        }
    }
}

@Composable
private fun EmptyLocationState() {
    Column(verticalArrangement = Arrangement.spacedBy(SoltelematicSpacing.xs)) {
        Text(
            stringResource(R.string.asset_detail_no_location_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            stringResource(R.string.asset_detail_no_location_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AddressLine(address: String?, isLoading: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            when {
                isLoading -> Text(
                    stringResource(R.string.asset_detail_address_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                address != null -> Text(
                    address,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                else -> Text(
                    stringResource(R.string.asset_detail_address_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (!address.isNullOrBlank()) {
            CopyButton(value = address)
        }
    }
}

@Composable
private fun CoordinatesLine(position: GeoPoint) {
    // Locale.US a propósito: son coordenadas, notación internacional con punto decimal, no debe
    // depender de que el dispositivo tenga un locale que use coma.
    val text = String.format(Locale.US, "%.6f, %.6f", position.lat, position.lng)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = text,
            style = SoltelematicMetricTypography.small,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        CopyButton(value = text)
    }
}

@Composable
private fun LastSeenLine(detail: AssetDetail, freshness: LastSeenFreshness) {
    Column {
        Text(
            stringResource(R.string.asset_detail_last_seen_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "${detail.lastSeenFormatted ?: "-"} · ${relativeLastSeenText(detail.lastSeenAt)}",
            style = MaterialTheme.typography.bodyMedium,
            color = freshness.toColor()
        )
    }
}

@Composable
private fun CopyButton(value: String) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val copiedMessage = stringResource(R.string.asset_detail_copied)
    val label = stringResource(R.string.asset_detail_copy)
    TextButton(
        onClick = {
            clipboardManager.setText(AnnotatedString(value))
            Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
        },
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
    ) {
        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(SoltelematicIconSpec.small))
        Spacer(Modifier.width(SoltelematicSpacing.xs))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun TodayStatsBlock(stats: List<UnitStat>, isLoading: Boolean, isStale: Boolean) {
    // Unidad sin reportar hace días: las métricas de "hoy" ya no significan "ahora mismo" --
    // se atenúan en vez de mostrarse con la misma fuerza visual que datos realmente frescos.
    Column(
        verticalArrangement = Arrangement.spacedBy(SoltelematicSpacing.sm),
        modifier = if (isStale) Modifier.alpha(0.5f) else Modifier
    ) {
        SectionHeader(stringResource(R.string.asset_detail_today_title))
        when {
            isLoading -> CircularProgressIndicator(modifier = Modifier.size(SoltelematicIconSpec.large))
            stats.isEmpty() -> Text(
                stringResource(R.string.asset_detail_today_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // No se asume qué métricas vienen ni en qué orden (las de combustible son dinámicas,
            // una por sensor) -- se pinta la lista tal cual llega, en filas de hasta 3 columnas
            // iguales. El color de la barra es una heurística sobre key/title (contiene "mov" /
            // "idle" o "ralent"), no una posición fija: así nunca se colorea "conducción" o
            // "ralentí" un dato que en realidad sea otra cosa (p. ej. distancia).
            else -> stats.chunked(3).forEach { rowStats ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(SoltelematicSpacing.sm),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val maxValue = rowStats.mapNotNull { it.numericValue() }.maxOrNull()
                    rowStats.forEach { stat -> StatTile(stat = stat, maxValue = maxValue, modifier = Modifier.weight(1f)) }
                    repeat(3 - rowStats.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun StatTile(stat: UnitStat, maxValue: Double?, modifier: Modifier = Modifier) {
    val colors = LocalSoltelematicColors.current
    val barColor = statTileColor(stat, colors)
    val fraction = stat.numericValue()?.let { value -> maxValue?.takeIf { it > 0 }?.let { (value / it).toFloat() } } ?: 0f

    Card(
        shape = SoltelematicShapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = SoltelematicElevation.e1),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(SoltelematicSpacing.md),
            verticalArrangement = Arrangement.spacedBy(SoltelematicSpacing.xs)
        ) {
            Text(
                text = stat.value ?: "-",
                style = SoltelematicMetricTypography.medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stat.title ?: stat.key ?: "-",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            LinearProgressIndicator(
                progress = { fraction.coerceIn(0f, 1f) },
                color = barColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(SoltelematicSpacing.xs)
            )
        }
    }
}

private fun statTileColor(stat: UnitStat, colors: SoltelematicColors): Color {
    val haystack = "${stat.key.orEmpty()} ${stat.title.orEmpty()}".lowercase()
    return when {
        haystack.contains("idle") || haystack.contains("ralent") -> colors.statusIdle
        haystack.contains("mov") || haystack.contains("conduc") || haystack.contains("drive") -> colors.statusMoving
        else -> colors.inkFaint
    }
}

private fun UnitStat.numericValue(): Double? =
    value?.let { Regex("""\d+(\.\d+)?""").find(it)?.value?.toDoubleOrNull() }

// Duplicado deliberado del helper homónimo privado en AssetBottomSheet.kt/AssetDetailScreen.kt --
// mismo criterio que las otras duplicaciones de este sprint: tres líneas no ameritan un archivo
// util compartido. Los 3 estados ahora leen de los tokens fijos de estado en vez de hex propios.
@Composable
private fun LastSeenFreshness.toColor(): Color {
    val colors = LocalSoltelematicColors.current
    return when (this) {
        LastSeenFreshness.RECENT -> colors.statusMoving
        LastSeenFreshness.STALE -> colors.statusIdle
        LastSeenFreshness.VERY_STALE -> colors.statusAlert
    }
}
