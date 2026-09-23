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
import androidx.compose.material.icons.filled.Battery4Bar
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.SensorDoor
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import java.text.Normalizer
import java.util.Locale
import pe.soltelematic.mobile.R
import pe.soltelematic.mobile.core.format.convertVolumeForDisplay
import pe.soltelematic.mobile.domain.model.AssetSensor
import pe.soltelematic.mobile.domain.model.VolumeUnit
import pe.soltelematic.mobile.ui.theme.SoltelematicElevation
import pe.soltelematic.mobile.ui.theme.SoltelematicIconSpec
import pe.soltelematic.mobile.ui.theme.SoltelematicShapes
import pe.soltelematic.mobile.ui.theme.SoltelematicSpacing

/**
 * Cada sensor tal como llega: sin agrupar por type todavía (el contrato del sprint solo pide
 * icono + nombre + valor + un contador al final, no tratamiento visual por tipo). volumeUnit solo
 * afecta sensores de volumen (ver convertVolumeForDisplay) -- cualquier otro sensor ("13.06 vts",
 * "2165 km", "OFF") se pinta exactamente como llegó del servidor.
 */
@Composable
fun SensorsTab(sensors: List<AssetSensor>, volumeUnit: VolumeUnit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = SoltelematicSpacing.sm, horizontal = SoltelematicSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(SoltelematicSpacing.xs)
        ) {
            items(sensors, key = { it.id }) { sensor ->
                SensorRow(sensor = sensor, volumeUnit = volumeUnit)
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
private fun SensorRow(sensor: AssetSensor, volumeUnit: VolumeUnit) {
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
            // DeviceSensor::getIconAsset() en el servidor SIEMPRE arma una URL
            // ("assets/icons/sensors_{tipo}_l.svg"), nunca null -- pero ese directorio no existe
            // en /public del servidor, así que sensor.iconUrl da 404 sin excepción. Por eso el
            // ícono es siempre este vectorial local mapeado por type, nunca el remoto.
            Icon(
                sensor.toFallbackIcon(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(SoltelematicIconSpec.large)
            )
            Text(
                text = sensor.name ?: sensor.type ?: "-",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = sensor.value?.let { convertVolumeForDisplay(it, volumeUnit) } ?: "-",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * El type solo no alcanza para tres sensores reales de la flota: "BLOQUEO" es acc/ignition igual
 * que IGNICION pero es un inmovilizador (candado, no llave), y "Bateria"/"RESPALDO" son ambos
 * type battery pese a ser cosas distintas (voltaje externo del vehículo vs. batería interna del
 * GPS) -- mismo problema que resuelve AlertEventType.fromAlertName para alertas custom, así que
 * acá también el nombre gana cuando dice algo más concreto que el tipo.
 *
 * Tipos confirmados en la base de datos del servidor (battery..fuel_tank) más un segundo grupo
 * (temperature..door) que existe en GPSWOX pero no se ha visto todavía en la flota actual -- se
 * mapean igual para no repetir este trabajo cuando aparezcan en otro cliente. lowercase(Locale.ROOT)
 * explícito: sin Locale, lowercase() usa el del dispositivo y en turco la I mayúscula no baja a
 * "i" sino a "ı", con lo que un type en mayúsculas no matchearía.
 */
private fun AssetSensor.toFallbackIcon(): ImageVector {
    val normalizedName = name?.normalizeForMatch()
    when {
        normalizedName == null -> Unit
        normalizedName.containsAny("bloque", "inmovil") -> return Icons.Filled.Lock
        normalizedName.containsAny("respaldo", "interna") -> return Icons.Filled.Battery4Bar
        normalizedName.containsAny("externa", "externo") -> return Icons.Filled.BatteryChargingFull
    }

    return when (type?.lowercase(Locale.ROOT)) {
        // "Bateria" en esta flota es el voltaje externo del vehículo, no la batería interna del
        // equipo -- BatteryChargingFull en vez de BatteryFull para no confundirla con RESPALDO.
        "battery" -> Icons.Filled.BatteryChargingFull
        "gsm" -> Icons.Filled.SignalCellularAlt
        "satellites" -> Icons.Filled.SatelliteAlt
        "acc", "ignition" -> Icons.Filled.Key
        "odometer" -> Icons.Filled.Speed
        "engine" -> Icons.Filled.Settings
        "engine_hours" -> Icons.Filled.Timer
        "fuel_tank" -> Icons.Filled.LocalGasStation
        "temperature" -> Icons.Filled.Thermostat
        "voltage", "power", "external_power" -> Icons.Filled.ElectricBolt
        "rpm" -> Icons.Filled.Speed
        "gps" -> Icons.Filled.GpsFixed
        "door" -> Icons.Filled.SensorDoor
        else -> Icons.Filled.Sensors
    }
}

// Mismo criterio que AlertEventType.normalizeForMatch/containsAny (domain/model/AlertEventType.kt):
// sin un helper compartido en el proyecto para esto, se duplica en vez de acoplar dos features
// por una función de 3 líneas.
private fun String.normalizeForMatch(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase(Locale.ROOT)
        .trim()

private fun String.containsAny(vararg needles: String): Boolean =
    needles.any { this.contains(it) }
