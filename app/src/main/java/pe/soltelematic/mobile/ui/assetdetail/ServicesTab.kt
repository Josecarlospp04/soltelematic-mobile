package pe.soltelematic.mobile.ui.assetdetail

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import pe.soltelematic.mobile.R
import pe.soltelematic.mobile.domain.model.DeviceService
import pe.soltelematic.mobile.ui.theme.LocalSoltelematicColors
import pe.soltelematic.mobile.ui.theme.SoltelematicElevation
import pe.soltelematic.mobile.ui.theme.SoltelematicIconSpec
import pe.soltelematic.mobile.ui.theme.SoltelematicShapes
import pe.soltelematic.mobile.ui.theme.SoltelematicSpacing

/**
 * Reemplaza a GenericFieldsTab para la pestaña Servicios (ver AssetDetailScreen): antes se
 * pintaba el JSON crudo de device/{id}.services porque nunca se había visto un payload real (ver
 * AssetDetailDto.kt); ahora GET device/{id}/services (parche 6) trae una forma conocida y
 * estable, así que cada servicio se pinta con sus campos reales en vez de una lista clave/valor
 * genérica. id/device_id/email quedan fuera a propósito: son internos, no le sirven al usuario
 * (ver DeviceService).
 *
 * expires es el texto YA FORMATEADO por el servidor (ver DeviceServiceDto) -- se pinta tal cual,
 * es el dato que más importa acá, por eso va en labelLarge en vez de bodyMedium. expired == true
 * se resalta con el mismo color de alerta que usan los estados BLOCKED en SensorsTab/UnitsScreen
 * (statusAlert del design system), no un rojo a mano.
 *
 * onAddClick: botón en el ENCABEZADO de la pestaña, no un FAB -- se descartó un FAB de Scaffold
 * porque su alcance es toda la pantalla (SUMMARY/SENSORS/etc. lo heredarían también sin sentido, y
 * Scaffold no sabe qué pestaña está activa hoy sin subir ese estado); un botón acá es autocontenido
 * y solo existe donde tiene sentido. Siempre visible (cargando/lista/vacío) -- crear un servicio no
 * depende de que ya existan otros.
 */
@Composable
fun ServicesTab(services: List<DeviceService>, isLoading: Boolean, onAddClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            horizontalArrangement = Arrangement.End,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SoltelematicSpacing.lg, vertical = SoltelematicSpacing.sm)
        ) {
            TextButton(onClick = onAddClick) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(SoltelematicIconSpec.small))
                Text(
                    text = stringResource(R.string.asset_detail_service_add),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = SoltelematicSpacing.xs)
                )
            }
        }
        when {
            isLoading -> Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            services.isEmpty() -> Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                ServicesEmptyState()
            }
            else -> LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = SoltelematicSpacing.lg, vertical = SoltelematicSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(SoltelematicSpacing.md)
            ) {
                items(services, key = { it.id }) { service -> ServiceCard(service) }
            }
        }
    }
}

@Composable
private fun ServiceCard(service: DeviceService) {
    val colors = LocalSoltelematicColors.current
    val expiresColor = if (service.expired) colors.statusAlert else MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        shape = SoltelematicShapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = SoltelematicElevation.e1),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(SoltelematicSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(SoltelematicSpacing.xs)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SoltelematicSpacing.xs)) {
                Text(
                    text = service.name ?: stringResource(R.string.asset_detail_service_unnamed),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (service.expired) {
                    Icon(
                        Icons.Filled.WarningAmber,
                        contentDescription = stringResource(R.string.asset_detail_service_expired),
                        tint = colors.statusAlert,
                        modifier = Modifier.size(SoltelematicIconSpec.small)
                    )
                }
            }
            service.expiresText?.let { expiresText ->
                Text(text = expiresText, style = MaterialTheme.typography.labelLarge, color = expiresColor)
            }
            service.description?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Misma anatomía estándar (ícono + título + mensaje) que UnitsEmptyState/GeofenceDeleteEmptyState. */
@Composable
private fun ServicesEmptyState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SoltelematicSpacing.sm),
        modifier = Modifier.padding(SoltelematicSpacing.xl)
    ) {
        Icon(
            Icons.Filled.Build,
            contentDescription = null,
            tint = LocalSoltelematicColors.current.inkFaint,
            modifier = Modifier.size(SoltelematicIconSpec.large)
        )
        Text(
            text = stringResource(R.string.asset_detail_services_empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = stringResource(R.string.asset_detail_services_empty_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
