package pe.soltelematic.mobile.ui.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import pe.soltelematic.mobile.R

/** Placeholder del Sprint 0. Mapas, unidades y comandos llegan en sprints posteriores. */
@Composable
fun MapPlaceholderScreen() {
    Scaffold { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.map_placeholder_title),
                style = MaterialTheme.typography.headlineMedium
            )
        }
    }
}
