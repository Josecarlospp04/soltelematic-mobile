package pe.soltelematic.mobile.ui.history

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import pe.soltelematic.mobile.domain.model.GeoPoint
import pe.soltelematic.mobile.domain.model.HistoryDriveLeg
import pe.soltelematic.mobile.domain.model.HistoryEndpoint
import pe.soltelematic.mobile.domain.model.HistoryStopLeg
import pe.soltelematic.mobile.domain.model.UnitStat
import pe.soltelematic.mobile.ui.theme.SOLTELEMATICTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// A diferencia de HistoryRouteMapPreview.kt, este sí renderiza contenido real en el preview de
// Android Studio: HistoryTimeline es Compose puro, sin el SDK de Google Maps de por medio.
@Preview(showBackground = true, heightDp = 500)
@Composable
private fun HistoryTimelinePreview() {
    SOLTELEMATICTheme {
        HistoryTimeline(
            legs = sampleLegs(),
            periodStats = listOf(
                UnitStat("distance", "Longitud de ruta", "2.44 Km"),
                UnitStat("speed_max", "Velocidad máxima", "29 kph")
            ),
            selectedLegIndex = 1,
            onLegClick = {},
            addresses = mapOf(
                0 to AddressResolution.Resolved("Av. Los Próceres 123, Lima"),
                2 to AddressResolution.Loading
            ),
            onStopRowVisible = { _, _ -> },
            modifier = Modifier.fillMaxSize()
        )
    }
}

private fun time(text: String): Instant =
    DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss")
        .withZone(ZoneId.systemDefault())
        .parse(text, Instant::from)

private fun sampleLegs() = listOf(
    HistoryStopLeg(
        title = "Detener",
        start = HistoryEndpoint(GeoPoint(-12.06362, -76.981367), time("22-08-2026 00:34:58")),
        end = HistoryEndpoint(GeoPoint(-12.063652, -76.981733), time("22-08-2026 08:04:41")),
        stats = listOf(UnitStat("duration", "Duración", "07:29:43"))
    ),
    HistoryDriveLeg(
        title = "Conducir",
        start = HistoryEndpoint(GeoPoint(-12.063652, -76.981733), time("22-08-2026 08:04:41")),
        end = HistoryEndpoint(GeoPoint(-12.067388, -76.986733), time("22-08-2026 08:15:14")),
        stats = listOf(
            UnitStat("distance", "Longitud de ruta", "1.12 Km"),
            UnitStat("duration", "Duración", "00:10:33")
        ),
        positions = emptyList()
    ),
    HistoryStopLeg(
        title = "Detener",
        start = HistoryEndpoint(GeoPoint(-12.067388, -76.986733), time("22-08-2026 08:15:14")),
        end = HistoryEndpoint(GeoPoint(-12.067388, -76.986733), time("22-08-2026 23:59:59")),
        stats = listOf(UnitStat("duration", "Duración", "15:44:45"))
    )
)
