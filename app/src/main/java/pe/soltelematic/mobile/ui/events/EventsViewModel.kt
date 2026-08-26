package pe.soltelematic.mobile.ui.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.soltelematic.mobile.core.result.ApiResult
import pe.soltelematic.mobile.core.storage.SeenEventsStore
import pe.soltelematic.mobile.domain.model.GeoPoint
import pe.soltelematic.mobile.domain.repository.AssetDetailRepository
import pe.soltelematic.mobile.domain.repository.EventsRepository

private const val SEARCH_DEBOUNCE_MS = 300L

// Los eventos se muestran id DESC (más nuevo arriba). El primer LaunchedEffect en disparar al
// abrir la pantalla es casi siempre el de la fila más arriba -- el id MÁS ALTO de todos, no el
// más bajo -- porque es la primera fila que Compose compone. Si se llamara a
// SeenEventsStore.markSeen() con ese id de inmediato, se marcaría como visto TODO lo que hay
// debajo (el store solo puede subir el umbral, nunca bajarlo), aunque el usuario no haya bajado
// más allá de la primera fila -- exactamente el bug que se quiere evitar. Por eso acá se seguido
// el mínimo id visto en esta sesión (se actualiza fila a fila, sin importar el orden en que
// disparen los LaunchedEffect) y solo se persiste tras una pausa breve sin filas nuevas: para
// cuando esa pausa se cumple, todas las filas que entraron en el mismo "golpe" inicial ya
// actualizaron el mínimo, así que lo que se guarda es el id de la fila MÁS BAJA realmente
// mostrada, no el de la primera en dispararse.
private const val MARK_SEEN_DEBOUNCE_MS = 500L

/**
 * assetDetailRepository.getAddress() se reutiliza tal cual (Sprint 2A/2B): un solo endpoint de
 * geocodificación en toda la app, no se repite en EventsRepository.
 */
class EventsViewModel(
    private val eventsRepository: EventsRepository,
    private val seenEventsStore: SeenEventsStore,
    private val assetDetailRepository: AssetDetailRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(EventsUiState())
    val uiState: StateFlow<EventsUiState> = _uiState.asStateFlow()

    // Caché de direcciones por coordenada exacta, vive mientras viva esta pantalla -- mismo
    // patrón que HistoryViewModel.addressCache.
    private val addressCache = mutableMapOf<GeoPoint, String?>()

    private var inboxPage = 1
    private var searchPage = 1
    private var searchJob: Job? = null

    private var minVisibleEventId: Int? = null
    private var markSeenJob: Job? = null

    init {
        viewModelScope.launch {
            eventsRepository.observeEvents().collect { events ->
                _uiState.update { it.copy(cachedEvents = events) }
            }
        }
        viewModelScope.launch {
            // Una sola lectura, nunca más -- ver el comentario de EventsUiState.seenBaselineId.
            val baseline = seenEventsStore.lastSeenEventId.first()
            _uiState.update { it.copy(seenBaselineId = baseline) }
        }
        loadFirstPage()
    }

    fun onRetry() = loadFirstPage()

    fun onLoadMore() {
        val state = _uiState.value
        if (state.isLoadingMore || !state.hasMorePages) return
        if (state.isFiltering) loadMoreSearch() else loadMoreInbox()
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query, loadMoreError = null) }
        restartSearch()
    }

    /** Se llama desde el LaunchedEffect por fila de EventsScreen -- mismo mecanismo que geocodifica. */
    fun onEventRowVisible(eventId: Int, position: GeoPoint?) {
        minVisibleEventId = minVisibleEventId?.let { minOf(it, eventId) } ?: eventId
        scheduleMarkSeen()
        if (position != null) resolveAddress(eventId, position)
    }

    private fun scheduleMarkSeen() {
        markSeenJob?.cancel()
        markSeenJob = viewModelScope.launch {
            delay(MARK_SEEN_DEBOUNCE_MS)
            minVisibleEventId?.let { seenEventsStore.markSeen(it) }
        }
    }

    private fun loadFirstPage() {
        inboxPage = 1
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, isOffline = false) }
            when (val result = eventsRepository.loadPage(inboxPage)) {
                is ApiResult.Success -> _uiState.update { it.copy(isLoading = false, inboxHasMore = result.data) }
                is ApiResult.Error -> _uiState.update {
                    val hasCache = it.cachedEvents.isNotEmpty()
                    it.copy(isLoading = false, error = if (hasCache) null else result.error, isOffline = hasCache)
                }
            }
        }
    }

    private fun loadMoreInbox() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true, loadMoreError = null) }
            val nextPage = inboxPage + 1
            when (val result = eventsRepository.loadPage(nextPage)) {
                is ApiResult.Success -> {
                    inboxPage = nextPage
                    _uiState.update { it.copy(isLoadingMore = false, inboxHasMore = result.data) }
                }
                is ApiResult.Error -> _uiState.update { it.copy(isLoadingMore = false, loadMoreError = result.error) }
            }
        }
    }

    private fun restartSearch() {
        searchJob?.cancel()
        val query = _uiState.value.searchQuery
        if (query.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList(), searchError = null, isSearching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            runSearch(query)
        }
    }

    /** Reintento explícito (botón, ver EventsScreen): mismo camino que restartSearch, sin debounce. */
    fun onRetrySearch() {
        searchJob?.cancel()
        val query = _uiState.value.searchQuery
        if (query.isBlank()) return
        searchJob = viewModelScope.launch { runSearch(query) }
    }

    private suspend fun runSearch(query: String) {
        searchPage = 1
        _uiState.update { it.copy(isSearching = true, searchError = null) }
        when (val result = eventsRepository.searchEvents(page = searchPage, search = query)) {
            is ApiResult.Success -> _uiState.update {
                it.copy(isSearching = false, searchResults = result.data.items, searchHasMore = result.data.hasMore)
            }
            is ApiResult.Error -> _uiState.update {
                it.copy(isSearching = false, searchResults = emptyList(), searchError = result.error)
            }
        }
    }

    private fun loadMoreSearch() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true, loadMoreError = null) }
            val nextPage = searchPage + 1
            val query = _uiState.value.searchQuery
            when (val result = eventsRepository.searchEvents(page = nextPage, search = query)) {
                is ApiResult.Success -> {
                    searchPage = nextPage
                    _uiState.update {
                        it.copy(
                            isLoadingMore = false,
                            searchResults = it.searchResults + result.data.items,
                            searchHasMore = result.data.hasMore
                        )
                    }
                }
                is ApiResult.Error -> _uiState.update { it.copy(isLoadingMore = false, loadMoreError = result.error) }
            }
        }
    }

    private fun resolveAddress(eventId: Int, point: GeoPoint) {
        if (_uiState.value.addresses.containsKey(eventId)) return // ya pedida o resuelta para este id

        if (addressCache.containsKey(point)) {
            val cached = addressCache.getValue(point)
            _uiState.update { it.copy(addresses = it.addresses + (eventId to AddressResolution.Resolved(cached))) }
            return
        }

        _uiState.update { it.copy(addresses = it.addresses + (eventId to AddressResolution.Loading)) }
        viewModelScope.launch {
            val resolved = when (val result = assetDetailRepository.getAddress(point.lat, point.lng)) {
                is ApiResult.Success -> result.data
                is ApiResult.Error -> null
            }
            addressCache[point] = resolved
            _uiState.update { it.copy(addresses = it.addresses + (eventId to AddressResolution.Resolved(resolved))) }
        }
    }
}
