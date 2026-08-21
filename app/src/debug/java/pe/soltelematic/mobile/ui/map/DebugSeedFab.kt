package pe.soltelematic.mobile.ui.map

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import pe.soltelematic.mobile.R
import pe.soltelematic.mobile.debug.SyntheticAssetSeeder

/**
 * Bloque 7 (prueba de carga sintética). Implementación real: solo se compila en el variant
 * debug (este archivo vive en src/debug, no en src/main) -- en release ni siquiera existe el
 * bytecode, no depende de que nadie recuerde envolverlo en BuildConfig.DEBUG. La contraparte
 * no-op está en src/release/.../DebugSeedFab.kt con la misma firma.
 */
@Composable
fun DebugSeedFab() {
    val seeder = koinInject<SyntheticAssetSeeder>()
    val scope = rememberCoroutineScope()
    Spacer(modifier = Modifier.height(12.dp))
    FloatingActionButton(onClick = { scope.launch { seeder.seed() } }) {
        Icon(Icons.Filled.Science, contentDescription = stringResource(R.string.map_debug_seed_load))
    }
}
