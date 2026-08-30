package pe.soltelematic.mobile.ui.units

import java.text.Collator
import java.util.Locale
import pe.soltelematic.mobile.domain.model.Asset
import pe.soltelematic.mobile.domain.model.AssetFilter

// Collator "es": compara ignorando mayúsculas y tratando los acentos correctamente ("Álvarez"
// antes que "Beto", no después por el punto de código de 'Á') -- un sortedBy{ it.name } crudo
// ordena por punto de código UTF-16 y deja mayúsculas/acentos en un orden que no es el
// alfabético real. Una sola instancia reutilizada (no es thread-safe para uso concurrente, pero
// esto corre siempre en el hilo principal de Compose).
private val spanishNameComparator = Comparator<Asset> { a, b ->
    Collator.getInstance(Locale("es")).compare(a.name.orEmpty(), b.name.orEmpty())
}

data class UnitsUiState(
    val assets: List<Asset> = emptyList(),
    val searchQuery: String = "",
    val activeFilter: AssetFilter = AssetFilter.ALL,
    val hasBlockedAssets: Boolean = false,
    // true hasta la primera emisión de AssetRepository.observeAssets(), sin importar si esa
    // primera lista viene vacía o con datos -- así el esqueleto no se confunde con "flota vacía".
    val isLoading: Boolean = true
) {
    // Mismo filtro que MapUiState.visibleAssets (AssetFilter.matches + búsqueda por nombre),
    // duplicado a propósito (mismo criterio del proyecto: unas líneas no justifican compartir un
    // archivo) -- acá además se ordena alfabéticamente, que el mapa no necesita.
    val visibleAssets: List<Asset>
        get() = assets
            .filter(activeFilter::matches)
            .filter { asset -> searchQuery.isBlank() || asset.name?.contains(searchQuery, ignoreCase = true) == true }
            .sortedWith(spanishNameComparator)
}
