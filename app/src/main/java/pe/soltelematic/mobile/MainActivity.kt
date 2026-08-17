package pe.soltelematic.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import pe.soltelematic.mobile.ui.navigation.SoltelematicNavHost
import pe.soltelematic.mobile.ui.theme.SOLTELEMATICTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SOLTELEMATICTheme {
                SoltelematicNavHost()
            }
        }
    }
}
