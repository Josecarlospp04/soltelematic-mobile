package pe.soltelematic.mobile.ui.map

import androidx.compose.runtime.Composable

/**
 * Contraparte no-op de src/debug/.../DebugSeedFab.kt: en release no hay botón ni
 * SyntheticAssetSeeder en el classpath -- MapScreen.kt (src/main) llama DebugSeedFab() sin
 * condicional, y cada variant resuelve a su propio archivo.
 */
@Composable
fun DebugSeedFab() = Unit
