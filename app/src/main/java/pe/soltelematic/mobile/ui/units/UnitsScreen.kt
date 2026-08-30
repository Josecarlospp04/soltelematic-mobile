package pe.soltelematic.mobile.ui.units

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt
import org.koin.androidx.compose.koinViewModel
import pe.soltelematic.mobile.R
import pe.soltelematic.mobile.core.format.normalizeSpeedUnit
import pe.soltelematic.mobile.core.format.normalizeSpeedUnitSuffix
import pe.soltelematic.mobile.domain.model.Asset
import pe.soltelematic.mobile.domain.model.AssetFilter
import pe.soltelematic.mobile.domain.model.AssetStatus
import pe.soltelematic.mobile.domain.model.AssetStatusType
import pe.soltelematic.mobile.ui.components.AssetFilterChipsRow
import pe.soltelematic.mobile.ui.components.AssetSearchBar
import pe.soltelematic.mobile.ui.theme.LocalSoltelematicColors
import pe.soltelematic.mobile.ui.theme.SoltelematicColors
import pe.soltelematic.mobile.ui.theme.SoltelematicMetricTypography
import pe.soltelematic.mobile.ui.theme.SoltelematicMinTouchTarget
import pe.soltelematic.mobile.ui.theme.SoltelematicPillShape
import pe.soltelematic.mobile.ui.theme.SoltelematicShapes
import pe.soltelematic.mobile.ui.theme.SoltelematicSpacing

private val EmptyStateIconSize = 40.dp
private val StatusRailWidth = 3.dp
private val SkeletonRowCount = 6
private val SkeletonLineHeight = 16.dp
private val SkeletonLineHeightSmall = 12.dp
private val SkeletonSpeedWidth = 40.dp

/**
 * Vista alterna al mapa (Sprint 5, Bloque 3): misma flota (AssetRepository vía UnitsViewModel),
 * mismo AssetFilter, mismo buscador -- ver ui/components/AssetListControls.kt, compartido con
 * MapScreen. Orden alfabético de nombre (ver UnitsUiState.visibleAssets) es lo único que este
 * ViewModel agrega sobre lo que ya hace MapUiState.
 *
 * Sin dirección por fila: Asset (devices/map) no la trae, y pedirla por unidad al armar la lista
 * sería una llamada por fila -- inaceptable (ver tarea). La línea secundaria muestra solo el
 * tiempo relativo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnitsScreen(
    onOpenAssetDetail: (Int) -> Unit,
    viewModel: UnitsViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Mismo criterio que MapScreen.visibleFilters/filterCounts: "Bloqueadas" solo aparece si hay
    // al menos una unidad bloqueada, y los conteos son sobre TODA la flota (uiState.assets), no
    // sobre lo ya filtrado por búsqueda.
    val visibleFilters = remember(uiState.hasBlockedAssets) {
        if (uiState.hasBlockedAssets) AssetFilter.entries.toList() else AssetFilter.entries - AssetFilter.BLOCKED
    }
    val filterCounts = remember(uiState.assets) {
        AssetFilter.entries.associateWith { filter -> uiState.assets.count(filter::matches) }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(SoltelematicSpacing.xs)
                        ) {
                            Text(stringResource(R.string.units_title), style = MaterialTheme.typography.titleLarge)
                            Text(
                                uiState.assets.size.toString(),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(SoltelematicSpacing.sm),
                    modifier = Modifier.padding(horizontal = SoltelematicSpacing.lg, vertical = SoltelematicSpacing.sm)
                ) {
                    AssetSearchBar(
                        query = uiState.searchQuery,
                        onQueryChange = viewModel::onSearchQueryChange,
                        modifier = Modifier.fillMaxWidth()
                    )
                    AssetFilterChipsRow(
                        filters = visibleFilters,
                        activeFilter = uiState.activeFilter,
                        counts = filterCounts,
                        onFilterSelected = viewModel::onFilterSelected
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            when {
                uiState.isLoading -> UnitsSkeletonList()
                uiState.visibleAssets.isEmpty() -> UnitsEmptyState(
                    searchQuery = uiState.searchQuery,
                    activeFilter = uiState.activeFilter,
                    onResetFilter = { viewModel.onFilterSelected(AssetFilter.ALL) }
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(horizontal = SoltelematicSpacing.lg, vertical = SoltelematicSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(SoltelematicSpacing.sm),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(uiState.visibleAssets, key = { it.id }) { asset ->
                        UnitRow(asset = asset, onClick = { onOpenAssetDetail(asset.id) })
                    }
                }
            }
        }
    }
}

/**
 * Riel de color de estado a la izquierda (StatusRailWidth) + nombre en Heading (titleMedium, ver
 * Type.kt) + badge de estado + tiempo relativo debajo + velocidad en Metric M a la derecha.
 * Row(IntrinsicSize.Min) para que el riel llene el alto real de la fila, no una altura fija.
 */
@Composable
private fun UnitRow(asset: Asset, onClick: () -> Unit) {
    val colors = LocalSoltelematicColors.current
    val (statusColor, _) = statusPillColors(asset.status.type, colors)
    Surface(
        shape = SoltelematicShapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SoltelematicMinTouchTarget)
            .clickable(onClick = onClick)
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(StatusRailWidth)
                    .fillMaxHeight()
                    .background(statusColor)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SoltelematicSpacing.md),
                modifier = Modifier
                    .weight(1f)
                    .padding(SoltelematicSpacing.md)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(SoltelematicSpacing.xs),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(SoltelematicSpacing.sm)
                    ) {
                        Text(
                            text = asset.name ?: stringResource(R.string.asset_unnamed),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        UnitStatusBadge(asset.status)
                    }
                    // Sin dirección (ver comentario de cabecera): solo el tiempo relativo.
                    Text(
                        text = relativeLastSeenText(asset.lastSeenAt),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                UnitSpeedText(asset)
            }
        }
    }
}

@Composable
private fun UnitStatusBadge(status: AssetStatus) {
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
        colors = AssistChipDefaults.assistChipColors(disabledContainerColor = wash, disabledLabelColor = color)
    )
}

/** Velocidad en Metric M (ver SpeedRow en AssetBottomSheet.kt, misma lógica a escala L). */
@Composable
private fun UnitSpeedText(asset: Asset) {
    val kph = asset.speedKph
    Column(horizontalAlignment = Alignment.End) {
        Text(
            text = kph?.roundToInt()?.toString() ?: asset.speedText?.let(::normalizeSpeedUnitSuffix) ?: "-",
            style = SoltelematicMetricTypography.medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (kph != null) {
            Text(
                text = normalizeSpeedUnit(asset.speedUnit),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// Mismo mapeo de 4 colores que SummaryTab.statusPillColors/MapScreen.toMarkerStatusColor --
// duplicado a propósito (convención del proyecto, ver esos archivos).
private fun statusPillColors(type: AssetStatusType, colors: SoltelematicColors) = when (type) {
    AssetStatusType.ONLINE -> colors.statusMoving to colors.statusMovingWash
    AssetStatusType.ENGINE, AssetStatusType.ACK -> colors.statusIdle to colors.statusIdleWash
    AssetStatusType.BLOCKED -> colors.statusAlert to colors.statusAlertWash
    AssetStatusType.OFFLINE, AssetStatusType.UNKNOWN -> colors.statusOffline to colors.statusOfflineWash
}

/** Duplicada a propósito (mismo criterio que AssetBottomSheet.kt/SummaryTab.kt). */
@Composable
private fun AssetStatus.label(): String = when (type) {
    AssetStatusType.ACK -> stringResource(R.string.asset_status_ack)
    AssetStatusType.OFFLINE -> stringResource(R.string.asset_status_offline)
    AssetStatusType.ONLINE -> stringResource(R.string.asset_status_online)
    AssetStatusType.ENGINE -> stringResource(R.string.asset_status_engine)
    AssetStatusType.BLOCKED -> stringResource(R.string.asset_status_blocked)
    AssetStatusType.UNKNOWN -> title ?: stringResource(R.string.asset_status_unknown)
}

// Mismo texto que relativeLastSeenText en ui/map/LastSeenText.kt -- duplicado a propósito
// (convención del proyecto) en vez de importar entre paquetes de features distintos.
private fun relativeLastSeenText(lastSeenAt: Instant?, now: Instant = Instant.now()): String {
    if (lastSeenAt == null) return "sin datos"
    val elapsed = Duration.between(lastSeenAt, now)
    return when {
        elapsed.toMinutes() < 1 -> "hace instantes"
        elapsed.toHours() < 1 -> "hace ${elapsed.toMinutes()} min"
        elapsed.toDays() < 1 -> "hace ${elapsed.toHours()} h"
        else -> "hace ${elapsed.toDays()} d"
    }
}

@Composable
private fun UnitsSkeletonList() {
    Column(
        verticalArrangement = Arrangement.spacedBy(SoltelematicSpacing.sm),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = SoltelematicSpacing.lg, vertical = SoltelematicSpacing.sm)
    ) {
        repeat(SkeletonRowCount) { UnitRowSkeleton() }
    }
}

@Composable
private fun UnitRowSkeleton() {
    val placeholderColor = MaterialTheme.colorScheme.outlineVariant
    Surface(
        shape = SoltelematicShapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SoltelematicMinTouchTarget)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SoltelematicSpacing.md),
            modifier = Modifier
                .fillMaxWidth()
                .padding(SoltelematicSpacing.md)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(SoltelematicSpacing.xs),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(SkeletonLineHeight)
                        .background(placeholderColor, SoltelematicShapes.extraSmall)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.3f)
                        .height(SkeletonLineHeightSmall)
                        .background(placeholderColor, SoltelematicShapes.extraSmall)
                )
            }
            Box(
                modifier = Modifier
                    .width(SkeletonSpeedWidth)
                    .height(SkeletonLineHeight)
                    .background(placeholderColor, SoltelematicShapes.extraSmall)
            )
        }
    }
}

/**
 * Búsqueda sin resultados: una línea, sin ícono ni botón (ver EventsEmptyState, mismo criterio).
 * Filtro sin resultados (ej. "Bloqueadas" con 0): anatomía estándar con botón que resetea a ALL.
 * Flota realmente vacía (ALL, sin búsqueda): misma anatomía sin botón -- no hay filtro que resetear.
 */
@Composable
private fun UnitsEmptyState(searchQuery: String, activeFilter: AssetFilter, onResetFilter: () -> Unit) {
    if (searchQuery.isNotBlank()) {
        Text(
            text = stringResource(R.string.units_empty_search),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(SoltelematicSpacing.xl)
        )
        return
    }
    if (activeFilter == AssetFilter.ALL) {
        EmptyStateBody(
            titleRes = R.string.units_empty_title,
            messageRes = R.string.units_empty_message,
            actionRes = null,
            onAction = {}
        )
        return
    }
    val titleRes = when (activeFilter) {
        AssetFilter.ON_ROUTE -> R.string.units_empty_on_route_title
        AssetFilter.STOPPED -> R.string.units_empty_stopped_title
        AssetFilter.BLOCKED -> R.string.units_empty_blocked_title
        AssetFilter.OFFLINE -> R.string.units_empty_offline_title
        AssetFilter.ALL -> R.string.units_empty_title // inalcanzable, ver return de arriba
    }
    EmptyStateBody(
        titleRes = titleRes,
        messageRes = R.string.units_empty_filter_message,
        actionRes = R.string.units_empty_filter_action,
        onAction = onResetFilter
    )
}

@Composable
private fun EmptyStateBody(titleRes: Int, messageRes: Int, actionRes: Int?, onAction: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SoltelematicSpacing.sm),
        modifier = Modifier.padding(SoltelematicSpacing.xl)
    ) {
        Icon(
            Icons.Filled.DirectionsCar,
            contentDescription = null,
            tint = LocalSoltelematicColors.current.inkFaint,
            modifier = Modifier.size(EmptyStateIconSize)
        )
        Text(stringResource(titleRes), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(
            text = stringResource(messageRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (actionRes != null) {
            OutlinedButton(
                onClick = onAction,
                shape = SoltelematicShapes.small,
                modifier = Modifier.padding(top = SoltelematicSpacing.sm)
            ) {
                Text(stringResource(actionRes), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
