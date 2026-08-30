package pe.soltelematic.mobile.ui.assetdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import pe.soltelematic.mobile.domain.model.AssetDetail
import pe.soltelematic.mobile.domain.model.AssetStatus
import pe.soltelematic.mobile.domain.model.AssetStatusType
import pe.soltelematic.mobile.domain.model.Ignition
import pe.soltelematic.mobile.ui.theme.SoltelematicTheme
import java.time.Instant

/**
 * Verificación visual del fix de layout de SpeedStatusRow (dentro de SummaryTab, primer bloque):
 * el caso "status largo" simula un AssetStatusType.UNKNOWN con title crudo del servidor (el único
 * caso donde ese texto no está acotado por las etiquetas propias de la app, ver
 * StatusPill/AssetStatus.label()) -- antes del fix, esto era justo lo que dejaba "Último reporte"
 * sin espacio y lo partía letra por letra. SpeedStatusRow es privado a SummaryTab.kt, así que se
 * previsualiza a través de SummaryTab (público) en vez de cambiarle la visibilidad solo para esto.
 */
@Composable
private fun SpeedStatusRowPreviewContent() {
    Column(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.background)
            .height(700.dp)
    ) {
        SummaryTab(
            detail = AssetDetailPreviewSample(name = "Caso normal"),
            address = null,
            isAddressLoading = false,
            todayStats = emptyList(),
            isTodayStatsLoading = false
        )
    }
}

@Composable
private fun SpeedStatusRowLongStatusPreviewContent() {
    Column(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.background)
            .height(700.dp)
    ) {
        SummaryTab(
            detail = AssetDetailPreviewSample(
                name = "Caso status largo",
                statusTitle = "Bloqueado por incumplimiento de mantenimiento programado"
            ),
            address = null,
            isAddressLoading = false,
            todayStats = emptyList(),
            isTodayStatsLoading = false
        )
    }
}

private fun AssetDetailPreviewSample(name: String, statusTitle: String? = null): AssetDetail = AssetDetail(
    id = 1,
    name = name,
    status = AssetStatus(type = AssetStatusType.UNKNOWN, title = statusTitle, colorHex = "#9E9E9E"),
    speedText = "0 kph",
    ignition = Ignition.ON,
    position = null,
    lastSeenAt = Instant.parse("2026-08-29T18:59:05Z"),
    lastSeenFormatted = "29-08-2026 13:59:05",
    sensors = emptyList(),
    services = emptyList(),
    driver = null
)

@Preview(name = "SpeedStatusRow - Claro", showBackground = true)
@Composable
private fun SpeedStatusRowPreviewLight() {
    SoltelematicTheme(darkTheme = false) { SpeedStatusRowPreviewContent() }
}

@Preview(name = "SpeedStatusRow - Oscuro", showBackground = true)
@Composable
private fun SpeedStatusRowPreviewDark() {
    SoltelematicTheme(darkTheme = true) { SpeedStatusRowPreviewContent() }
}

@Preview(name = "SpeedStatusRow - Status largo - Claro", showBackground = true)
@Composable
private fun SpeedStatusRowLongStatusPreviewLight() {
    SoltelematicTheme(darkTheme = false) { SpeedStatusRowLongStatusPreviewContent() }
}

@Preview(name = "SpeedStatusRow - Status largo - Oscuro", showBackground = true)
@Composable
private fun SpeedStatusRowLongStatusPreviewDark() {
    SoltelematicTheme(darkTheme = true) { SpeedStatusRowLongStatusPreviewContent() }
}
