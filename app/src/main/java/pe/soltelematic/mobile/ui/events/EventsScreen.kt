package pe.soltelematic.mobile.ui.events

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import pe.soltelematic.mobile.R
import pe.soltelematic.mobile.core.result.ApiError
import pe.soltelematic.mobile.domain.model.GeoPoint
import pe.soltelematic.mobile.ui.events.components.EventCard

/**
 * TopAppBar con volver, buscador (que también sirve como filtro por unidad -- el servidor busca
 * en "message" y en "device.name", ver EventsRepository.searchEvents), lista con scroll infinito
 * y banner de caché cuando la bandeja no pudo refrescarse pero ya había datos guardados.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsScreen(
    onBack: () -> Unit,
    onOpenAssetDetail: (Int) -> Unit,
    viewModel: EventsViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(R.string.events_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.asset_detail_back)
                            )
                        }
                    }
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
                uiState.isLoading -> CircularProgressIndicator()
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
private fun EventsSearchBar(query: String, onQueryChange: (String) -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text(stringResource(R.string.events_search_placeholder)) },
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.map_search_clear))
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun EventsErrorState(error: ApiError, onRetry: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(24.dp)
    ) {
        Text(text = errorMessage(error), style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onRetry) {
            Text(stringResource(R.string.asset_detail_retry))
        }
    }
}

@Composable
private fun EventsEmptyState(isFiltering: Boolean) {
    Text(
        text = stringResource(if (isFiltering) R.string.events_empty_search else R.string.events_empty_title),
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(24.dp)
    )
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
            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
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
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(12.dp)
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
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        if (error != null) {
            Text(
                text = stringResource(R.string.events_load_more_error),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = onRetry) {
                Text(stringResource(R.string.asset_detail_retry))
            }
        } else {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
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
