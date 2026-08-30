package pe.soltelematic.mobile.ui.units

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.soltelematic.mobile.domain.model.AssetFilter
import pe.soltelematic.mobile.domain.model.AssetStatusType
import pe.soltelematic.mobile.domain.repository.AssetRepository

/**
 * Vista alterna al mapa: misma flota, mismo AssetRepository, sin duplicar nada de red. El
 * polling real (RealtimePoller, un solo Job vivo en el ViewModelScope de MapViewModel) y el
 * refresco inicial ya corren desde que se entra a la app -- Destination.Map es siempre el punto
 * de entrada con sesión activa (ver SoltelematicNavHost), así que para cuando el usuario puede
 * llegar a esta pestaña esa maquinaria ya está viva. Este ViewModel solo LEE
 * AssetRepository.observeAssets() (Flow respaldado por Room): no llama refresh() ni
 * realtimePoller.start() -- hacerlo aquí correría un segundo poll loop en paralelo sobre el mismo
 * singleton, doblando la frecuencia de red sin necesidad.
 */
class UnitsViewModel(private val assetRepository: AssetRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(UnitsUiState())
    val uiState: StateFlow<UnitsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            assetRepository.observeAssets().collect { assets ->
                _uiState.update {
                    it.copy(
                        assets = assets,
                        hasBlockedAssets = assets.any { asset -> asset.status.type == AssetStatusType.BLOCKED },
                        isLoading = false
                    )
                }
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun onFilterSelected(filter: AssetFilter) {
        _uiState.update { it.copy(activeFilter = filter) }
    }
}
