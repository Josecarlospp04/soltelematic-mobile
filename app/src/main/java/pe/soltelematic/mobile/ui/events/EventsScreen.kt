package pe.soltelematic.mobile.ui.events

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import pe.soltelematic.mobile.R
import pe.soltelematic.mobile.core.network.UnseenEventsPoller
import pe.soltelematic.mobile.core.result.ApiError
import pe.soltelematic.mobile.domain.model.GeoPoint
import pe.soltelematic.mobile.ui.events.components.EventCard
import pe.soltelematic.mobile.ui.theme.LocalSoltelematicColors
import pe.soltelematic.mobile.ui.theme.SoltelematicMinTouchTarget
import pe.soltelematic.mobile.ui.theme.SoltelematicPillShape
import pe.soltelematic.mobile.ui.theme.SoltelematicShapes
import pe.soltelematic.mobile.ui.theme.SoltelematicSpacing

private val BadgeMinSize = 20.dp
private val EmptyStateIconSize = 40.dp
private val LoadMoreSpinnerSize = 24.dp

/**
 * TopAppBar con volver, badge de no vistos (leído directo de UnseenEventsPoller vía Koin -- no
 * pasa por el ViewModel, mismo criterio que routeMapEngine en HistoryScreen: una dependencia que
 * el Composable consume tal cual, sin que el ViewModel medie), buscador (que también sirve como
 * filtro por unidad -- el servidor busca en "message" y en "device.name", ver
 * EventsRepository.searchEvents), lista plana con scroll infinito y banner de caché cuando la
 * bandeja no pudo refrescarse pero ya había datos guardados.
 *
 * Sin chips de "No leídas/Todas" ni agrupación por fecha, sin "Marcar todas" y sin acción de
 * swipe: ninguno de los cuatro existía antes de esta ronda de retematizado y no se agregan acá
 * (ver resumen de la tarea).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsScreen(
    onBack: () -> Unit,
    onOpenAssetDetail: (Int) -> Unit,
    viewModel: EventsViewModel = koinViewModel(),
    unseenEventsPoller: UnseenEventsPoller = koinInject()
) {
    val uiState by viewModel.uiState.collectAsState()
    val unseenCount by unseenEventsPoller.unseenCount.collectAsState()

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(SoltelematicSpacing.sm)
                        ) {
                            Text(stringResource(R.string.events_title), style = MaterialTheme.typography.titleLarge)
                            UnreadCountBadge(count = unseenCount)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.asset_detail_back)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                EventsSearchBar(query = uiState.searchQuery, onQueryChange = viewModel::onSearchQueryChange)
                if (uiState.isSearching) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
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
                uiState.isLoading -> CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                uiState.error != null -> EventsErrorState(error = uiState.error!!, onRetry = viewModel::onRetry)
                // searchError con la búsqueda vacía es un fallo de red, no "sin resultados" -- sin
                // esto se veía indistinguible de una búsqueda que de verdad no encontró nada.
                uiState.isFiltering && uiState.searchError != null && uiState.searchResults.isEmpty() ->
                    EventsErrorState(error = uiState.searchError!!, onRetry = viewModel::onRetrySearch)
                uiState.visibleEvents.isEmpty() && !uiState.isSearching -> EventsEmptyState(isFiltering = uiState.isFiltering)
                else -> EventsListContent(
                    uiState = uiState,
                    onLoadMore = viewModel::onLoadMore,
                    onRowVisible = viewModel::onEventRowVisible,
                    onOpenAssetDetail = onOpenAssetDetail
                )
            }
        }
    }
}

@Composable
private fun UnreadCountBadge(count: Int) {
    if (count <= 0) return
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .defaultMinSize(minWidth = BadgeMinSize, minHeight = BadgeMinSize)
            .background(MaterialTheme.colorScheme.error, SoltelematicPillShape)
            .padding(horizontal = SoltelematicSpacing.xs)
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onError
        )
    }
}

@Composable
private fun EventsSearchBar(query: String, onQueryChange: (String) -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Surface(
        shape = SoltelematicShapes.small,
        color = if (isFocused) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SoltelematicSpacing.lg, vertical = SoltelematicSpacing.sm)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text(stringResource(R.string.events_search_placeholder), style = MaterialTheme.typography.bodyMedium) },
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.map_search_clear))
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent
            ),
            interactionSource = interactionSource,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = SoltelematicMinTouchTarget)
        )
    }
}

@Composable
private fun EventsErrorState(error: ApiError, onRetry: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SoltelematicSpacing.lg),
        modifier = Modifier.padding(SoltelematicSpacing.xl)
    ) {
        Text(
            text = errorMessage(error),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Button(
            onClick = onRetry,
            shape = SoltelematicShapes.small,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Text(stringResource(R.string.asset_detail_retry), style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * Solo la bandeja realmente vacía (sin filtrar) usa la anatomía completa del mockup (ícono +
 * título + una línea + máximo una acción) -- "sin resultados de búsqueda" es un estado transitorio
 * que el usuario mismo puede deshacer borrando el texto, no amerita la misma puesta en escena.
 */
@Composable
private fun EventsEmptyState(isFiltering: Boolean) {
    if (isFiltering) {
        Text(
            text = stringResource(R.string.events_empty_search),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(SoltelematicSpacing.xl)
        )
        return
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SoltelematicSpacing.sm),
        modifier = Modifier.padding(SoltelematicSpacing.xl)
    ) {
        Icon(
            Icons.Filled.NotificationsOff,
            contentDescription = null,
            tint = LocalSoltelematicColors.current.inkFaint,
            modifier = Modifier.size(EmptyStateIconSize)
        )
        Text(
            text = stringResource(R.string.events_empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = stringResource(R.string.events_empty_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        // Sin destino de "ajustes de notificaciones" en la navegación todavía (ver
        // Destinations.kt) -- el botón queda sin acción a propósito, no se simula (ver
        // resumen de la tarea).
        OutlinedButton(onClick = {}, shape = SoltelematicShapes.small, modifier = Modifier.padding(top = SoltelematicSpacing.sm)) {
            Text(stringResource(R.string.events_empty_action), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun EventsListContent(
    uiState: EventsUiState,
    onLoadMore: () -> Unit,
    onRowVisible: (Int, GeoPoint?) -> Unit,
    onOpenAssetDetail: (Int) -> Unit
) {
    val listState = rememberLazyListState()
    val events = uiState.visibleEvents

    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            layoutInfo.totalItemsCount > 0 && lastVisible >= layoutInfo.totalItemsCount - 3
        }
    }
    LaunchedEffect(shouldLoadMore, events.size) {
        if (shouldLoadMore) onLoadMore()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (uiState.isOffline) OfflineBanner()

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(vertical = SoltelematicSpacing.sm, horizontal = SoltelematicSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(SoltelematicSpacing.sm),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(events, key = { _, event -> event.id }) { _, event ->
                LaunchedEffect(event.id) { onRowVisible(event.id, event.position) }
                EventCard(
                    event = event,
                    unseen = uiState.seenBaselineId?.let { event.id > it } ?: true,
                    addressResolution = uiState.addresses[event.id],
                    onClick = { event.deviceId?.let(onOpenAssetDetail) }
                )
            }
            if (uiState.isLoadingMore || uiState.loadMoreError != null) {
                item { LoadMoreFooter(error = uiState.loadMoreError, onRetry = onLoadMore) }
            }
        }
    }
}

@Composable
private fun OfflineBanner() {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = SoltelematicShapes.small,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SoltelematicSpacing.lg, vertical = SoltelematicSpacing.sm)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SoltelematicSpacing.sm),
            modifier = Modifier.padding(SoltelematicSpacing.md)
        ) {
            Icon(Icons.Filled.WifiOff, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Text(
                text = stringResource(R.string.events_offline_banner),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun LoadMoreFooter(error: ApiError?, onRetry: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SoltelematicSpacing.sm),
        modifier = Modifier
            .fillMaxWidth()
            .padding(SoltelematicSpacing.lg)
    ) {
        if (error != null) {
            Text(
                text = stringResource(R.string.events_load_more_error),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = onRetry, shape = SoltelematicShapes.small) {
                Text(stringResource(R.string.asset_detail_retry), style = MaterialTheme.typography.labelLarge)
            }
        } else {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(LoadMoreSpinnerSize))
        }
    }
}

// Mismos mensajes genéricos que AssetDetailScreen.errorMessage/HistoryScreen.errorMessage --
// duplicado deliberado, igual criterio que el resto de la app: tres líneas no ameritan compartir
// un archivo util entre pantallas.
@Composable
private fun errorMessage(error: ApiError): String = when (error) {
    ApiError.NoConnection -> stringResource(R.string.asset_detail_error_no_connection)
    ApiError.Timeout -> stringResource(R.string.asset_detail_error_timeout)
    ApiError.Unauthorized,
    is ApiError.ValidationError,
    is ApiError.Http,
    is ApiError.Unknown -> stringResource(R.string.asset_detail_error_generic)
}
