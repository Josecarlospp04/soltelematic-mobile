package pe.soltelematic.mobile.ui.assetdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import pe.soltelematic.mobile.domain.model.AssetDetailField
import pe.soltelematic.mobile.ui.theme.SoltelematicElevation
import pe.soltelematic.mobile.ui.theme.SoltelematicShapes
import pe.soltelematic.mobile.ui.theme.SoltelematicSpacing

/**
 * Reutilizada por Servicios (una tarjeta por entrada de la lista) y Conductor (una sola tarjeta
 * con sus campos) -- ambos son clave/valor genérico sin forma conocida (ver AssetDetailMapper),
 * así que comparten el mismo render en vez de duplicar dos tabs casi idénticas.
 */
@Composable
fun GenericFieldsTab(sections: List<List<AssetDetailField>>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(SoltelematicSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(SoltelematicSpacing.lg)
    ) {
        sections.forEach { fields -> GenericFieldsCard(fields = fields) }
    }
}

@Composable
private fun GenericFieldsCard(fields: List<AssetDetailField>) {
    Card(
        shape = SoltelematicShapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = SoltelematicElevation.e1),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(SoltelematicSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(SoltelematicSpacing.md)
        ) {
            fields.forEach { field -> GenericFieldRow(field) }
        }
    }
}

@Composable
private fun GenericFieldRow(field: AssetDetailField) {
    Column {
        field.title?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = field.value ?: "-",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
