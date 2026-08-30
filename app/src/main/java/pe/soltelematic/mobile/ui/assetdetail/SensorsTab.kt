package pe.soltelematic.mobile.ui.assetdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import coil.ImageLoader
import coil.compose.AsyncImage
import org.koin.compose.koinInject
import pe.soltelematic.mobile.R
import pe.soltelematic.mobile.domain.model.AssetSensor
import pe.soltelematic.mobile.ui.theme.SoltelematicElevation
import pe.soltelematic.mobile.ui.theme.SoltelematicIconSpec
import pe.soltelematic.mobile.ui.theme.SoltelematicShapes
import pe.soltelematic.mobile.ui.theme.SoltelematicSpacing

/**
 * Cada sensor tal como llega: sin agrupar por type todavía (el contrato del sprint solo pide
 * icono + nombre + valor + un contador al final, no tratamiento visual por tipo).
 */
@Composable
fun SensorsTab(sensors: List<AssetSensor>, modifier: Modifier = Modifier) {
    // Mismo ImageLoader que usa el mapa para los iconos de marcador (ver MapModule): un solo
    // caché de Coil para toda la app, no uno nuevo por pantalla.
    val imageLoader = koinInject<ImageLoader>()

    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = SoltelematicSpacing.sm, horizontal = SoltelematicSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(SoltelematicSpacing.xs)
        ) {
            items(sensors, key = { it.id }) { sensor ->
                SensorRow(sensor = sensor, imageLoader = imageLoader)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            text = pluralStringResource(R.plurals.asset_detail_sensors_count, sensors.size, sensors.size),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(SoltelematicSpacing.lg)
        )
    }
}

@Composable
private fun SensorRow(sensor: AssetSensor, imageLoader: ImageLoader) {
    Card(
        shape = SoltelematicShapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = SoltelematicElevation.e1),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SoltelematicSpacing.lg),
            modifier = Modifier
                .fillMaxWidth()
                .padding(SoltelematicSpacing.sm)
        ) {
            if (sensor.iconUrl != null) {
                AsyncImage(
                    model = sensor.iconUrl,
                    imageLoader = imageLoader,
                    contentDescription = null,
                    modifier = Modifier.size(SoltelematicIconSpec.large)
                )
            } else {
                // Sin icono propio (no debería pasar según el contrato, pero sensor.icon es nullable
                // en el DTO): un ícono genérico en vez de dejar el hueco vacío.
                Icon(
                    Icons.Filled.Sensors,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(SoltelematicIconSpec.large)
                )
            }
            Text(
                text = sensor.name ?: sensor.type ?: "-",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = sensor.value ?: "-",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
