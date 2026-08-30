package pe.soltelematic.mobile.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import pe.soltelematic.mobile.R
import pe.soltelematic.mobile.core.format.formatDurationCompact
import pe.soltelematic.mobile.core.format.normalizeSpeedUnit
import pe.soltelematic.mobile.core.format.normalizeSpeedUnitSuffix
import pe.soltelematic.mobile.domain.model.Asset
import pe.soltelematic.mobile.domain.model.AssetStatus
import pe.soltelematic.mobile.domain.model.AssetStatusType
import pe.soltelematic.mobile.domain.model.Ignition
import pe.soltelematic.mobile.domain.model.UnitStat
import pe.soltelematic.mobile.ui.theme.LocalSoltelematicColors
import pe.soltelematic.mobile.ui.theme.SoltelematicBottomSheetShape
import pe.soltelematic.mobile.ui.theme.SoltelematicIconSpec
import pe.soltelematic.mobile.ui.theme.SoltelematicMetricTypography
import pe.soltelematic.mobile.ui.theme.SoltelematicMinTouchTarget
import pe.soltelematic.mobile.ui.theme.SoltelematicPillShape
import pe.soltelematic.mobile.ui.theme.SoltelematicShapes
import pe.soltelematic.mobile.ui.theme.SoltelematicSpacing
import kotlin.math.roundToInt

/**
 * Resumen de unidad al tocar un marcador. Nombre/estado/velocidad/chips vienen de [Asset]
 * (devices/map, ya disponible al instante). distancia/conducción/detenido y la dirección NO están
 * en ese modelo -- llegan por separado vía MapViewModel (device/{id}+history, mismo camino que
 * AssetDetailViewModel para "HOY", más la geocodificación ya existente), así que la hoja se abre
 * de inmediato con lo que ya tiene y esas dos secciones muestran su propio placeholder de carga
 * hasta que resuelven -- nunca bloquean la apertura ni bloquean con un error si fallan.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetBottomSheet(
    asset: Asset,
    stats: List<UnitStat>,
    isStatsLoading: Boolean,
    address: String?,
    isAddressLoading: Boolean,
    onDismiss: () -> Unit,
    onOpenDetail: () -> Unit,
    onOpenHistory: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = SoltelematicBottomSheetShape,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outlineVariant) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SoltelematicSpacing.lg)
                .padding(bottom = SoltelematicSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(SoltelematicSpacing.md)
        ) {
            HeaderRow(asset)
            SpeedRow(asset)
            val chips = attributeChips(asset)
            if (chips.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(SoltelematicSpacing.sm)) {
                    chips.forEach { chip -> AttributeChip(chip) }
                }
            }
            RouteStatsRow(stats = stats, isLoading = isStatsLoading)
            LocationRow(address = address, isLoading = isAddressLoading)
            ActionsRow(onOpenDetail = onOpenDetail, onOpenHistory = onOpenHistory)
        }
    }
}

/** Distancia hoy / conducción / detenido: dinámico (ver HistoryStatDto), se busca por key, nunca por posición fija. */
@Composable
private fun RouteStatsRow(stats: List<UnitStat>, isLoading: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(SoltelematicSpacing.sm)) {
        RouteStatTile(
            value = stats.valueFor("distance"),
            label = stringResource(R.string.asset_stat_distance_today),
            isLoading = isLoading,
            modifier = Modifier.weight(1f)
        )
        RouteStatTile(
            // Solo horas y minutos, redondeado -- el segundo crudo del servidor parte el texto
            // en dos líneas (ver core/format/DurationFormat.kt).
            value = formatDurationCompact(stats.valueFor("drive_duration")),
            label = stringResource(R.string.asset_stat_drive_duration),
            isLoading = isLoading,
            modifier = Modifier.weight(1f)
        )
        RouteStatTile(
            // stop_duration (tiempo sin movimiento) -- distinto de "en ralentí" (motor encendido
            // sin moverse, ver AssetStatusType.ENGINE): no se mezclan, son dos métricas separadas
            // en la respuesta real del servidor.
            value = formatDurationCompact(stats.valueFor("stop_duration")),
            label = stringResource(R.string.asset_stat_stop_duration),
            isLoading = isLoading,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun RouteStatTile(value: String?, label: String, isLoading: Boolean, modifier: Modifier = Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Text(
            text = if (isLoading) "…" else value ?: "-",
            style = SoltelematicMetricTypography.medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun List<UnitStat>.valueFor(key: String): String? = firstOrNull { it.key == key }?.value

/** Reutiliza los strings de dirección de la ficha (Sprint 2A) -- mismo significado ahí y acá. */
@Composable
private fun LocationRow(address: String?, isLoading: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SoltelematicSpacing.sm),
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            Icons.Filled.LocationOn,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(SoltelematicIconSpec.small)
        )
        Text(
            text = when {
                isLoading -> stringResource(R.string.asset_detail_address_loading)
                address != null -> address
                else -> stringResource(R.string.asset_detail_address_unavailable)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun HeaderRow(asset: Asset) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SoltelematicSpacing.sm),
            modifier = Modifier.weight(1f, fill = false)
        ) {
            Text(
                text = asset.name ?: stringResource(R.string.asset_unnamed),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            StatusPill(asset.status)
        }
        Text(
            text = relativeLastSeenText(asset.lastSeenAt),
            style = MaterialTheme.typography.labelLarge,
            color = LocalSoltelematicColors.current.inkFaint
        )
    }
}

@Composable
private fun StatusPill(status: AssetStatus) {
    // Color viene del servidor y se muestra tal cual: no se recolorea (ver toComposeColorOrDefault
    // más abajo). El texto sí se traduce -- status.label().
    val color = status.colorHex.toComposeColorOrDefault()
    AssistChip(
        onClick = {},
        enabled = false,
        shape = SoltelematicPillShape,
        label = { Text(status.label().uppercase(), style = MaterialTheme.typography.labelLarge) },
        colors = AssistChipDefaults.assistChipColors(
            disabledContainerColor = color.copy(alpha = 0.15f),
            disabledLabelColor = color
        )
    )
}

/**
 * Etiqueta propia por status.type en vez del status.title crudo del servidor (jerga de
 * protocolo, p. ej. "ACK"). Si el tipo no es ninguno de los cinco conocidos, cae de vuelta al
 * title del servidor -- mostrar la jerga de un estado nuevo que la plataforma agregue mañana es
 * más útil que "Desconocido" a secas -- y solo si ni eso hay, a asset_status_unknown.
 *
 * Duplicada en AssetDetailScreen.kt (mismo criterio que toComposeColorOrDefault más abajo):
 * seis líneas de mapeo no justifican compartir una función entre las dos pantallas.
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

/** Velocidad destacada en Metric L (mono, cifras tabulares) + unidad en Micro caps al lado. */
@Composable
private fun SpeedRow(asset: Asset) {
    val kph = asset.speedKph
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(SoltelematicSpacing.xs)) {
        Text(
            // Si no hay speedKph (speed.value) se cae al speedText crudo del servidor -- ese sí
            // trae su propia unidad pegada ("0 kph"), normalizada acá también para no dejar un
            // "kph" suelto en el único caso donde no se muestra el Text de unidad de abajo.
            text = kph?.roundToInt()?.toString() ?: asset.speedText?.let(::normalizeSpeedUnitSuffix) ?: "-",
            style = SoltelematicMetricTypography.large,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (kph != null) {
            Text(
                text = normalizeSpeedUnit(asset.speedUnit),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = SoltelematicSpacing.xs)
            )
        }
    }
}

/**
 * Hasta 3 chips de atributos variables -- lista, no slots fijos, porque no toda unidad reporta lo
 * mismo. Voltaje aparecía en el mockup pero no existe en el modelo [Asset] (ver comentario de
 * cabecera), así que no está entre las opciones de abajo.
 */
@Composable
private fun attributeChips(asset: Asset): List<AttributeChipData> = buildList {
    if (asset.ignition != Ignition.NO_SENSOR) {
        val (icon, textRes) = when (asset.ignition) {
            Ignition.ON -> Icons.Filled.Bolt to R.string.asset_ignition_on
            Ignition.OFF -> Icons.Filled.PowerOff to R.string.asset_ignition_off
            Ignition.NO_SENSOR -> Icons.AutoMirrored.Filled.HelpOutline to R.string.asset_ignition_no_sensor
        }
        add(AttributeChipData(icon, stringResource(textRes).uppercase()))
    }
    // El rumbo solo es significativo mientras la unidad se mueve.
    if (asset.status.type == AssetStatusType.ONLINE) {
        add(AttributeChipData(icon = null, text = stringResource(R.string.map_course_format, asset.icon.courseDegrees)))
    }
}

private data class AttributeChipData(val icon: ImageVector?, val text: String)

@Composable
private fun AttributeChip(data: AttributeChipData) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SoltelematicSpacing.xs),
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, SoltelematicPillShape)
            .padding(horizontal = SoltelematicSpacing.sm, vertical = SoltelematicSpacing.xs)
    ) {
        data.icon?.let {
            Icon(
                it,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(SoltelematicIconSpec.small)
            )
        }
        Text(data.text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ActionsRow(onOpenDetail: () -> Unit, onOpenHistory: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(SoltelematicSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Button(
            onClick = onOpenDetail,
            shape = SoltelematicShapes.small,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            modifier = Modifier
                .weight(1f)
                .heightIn(min = SoltelematicMinTouchTarget)
        ) {
            Text(stringResource(R.string.asset_open_detail), style = MaterialTheme.typography.labelLarge)
        }
        // Historial es la única acción secundaria que ya existe (ver onOpenHistory); el mockup
        // muestra "reintentar" y "compartir" en su lugar, pero ninguna de las dos tiene lógica
        // implementada todavía -- no se inventan aquí.
        IconButton(
            onClick = onOpenHistory,
            colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
            modifier = Modifier.size(SoltelematicMinTouchTarget)
        ) {
            Icon(Icons.Outlined.History, contentDescription = stringResource(R.string.asset_open_history))
        }
    }
}

private fun String?.toComposeColorOrDefault(): Color =
    runCatching { Color(android.graphics.Color.parseColor(this)) }.getOrDefault(Color.Gray)
