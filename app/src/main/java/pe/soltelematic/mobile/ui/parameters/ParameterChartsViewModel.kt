package pe.soltelematic.mobile.ui.parameters

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.soltelematic.mobile.core.result.ApiResult
import pe.soltelematic.mobile.domain.repository.AssetDetailRepository
import java.time.LocalDate

/** assetId/from/to por parámetro de Koin, igual que HistoryViewModel -- ver ViewModelModule. */
class ParameterChartsViewModel(
    private val assetId: Int,
    from: LocalDate,
    to: LocalDate,
    private val assetDetailRepository: AssetDetailRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ParameterChartsUiState(from = from, to = to))
    val uiState: StateFlow<ParameterChartsUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        load()
    }

    fun onRetry() = load()

    private fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val range = _uiState.value
            val result = assetDetailRepository.getParameters(
                id = assetId,
                from = range.from.atStartOfDay(),
                to = range.to.atTime(23, 59, 59)
            )
            when (result) {
                is ApiResult.Success -> _uiState.update { it.copy(isLoading = false, series = result.data) }
                is ApiResult.Error -> _uiState.update { it.copy(isLoading = false, error = result.error) }
            }
        }
    }
}
