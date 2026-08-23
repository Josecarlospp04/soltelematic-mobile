package pe.soltelematic.mobile.ui.assetdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import pe.soltelematic.mobile.domain.model.AssetDetailField

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
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        sections.forEach { fields -> GenericFieldsCard(fields = fields) }
    }
}

@Composable
private fun GenericFieldsCard(fields: List<AssetDetailField>) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(text = field.value ?: "-", style = MaterialTheme.typography.bodyLarge)
    }
}
