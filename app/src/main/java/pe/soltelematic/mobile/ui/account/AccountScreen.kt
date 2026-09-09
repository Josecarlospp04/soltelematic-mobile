package pe.soltelematic.mobile.ui.account

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import pe.soltelematic.mobile.BuildConfig
import pe.soltelematic.mobile.R
import pe.soltelematic.mobile.core.network.serverRootUrl
import pe.soltelematic.mobile.ui.theme.BrandLogo
import pe.soltelematic.mobile.ui.theme.DefaultBrandSupportWhatsAppNumber
import pe.soltelematic.mobile.ui.theme.LocalSoltelematicColors
import pe.soltelematic.mobile.ui.theme.SoltelematicMinTouchTarget
import pe.soltelematic.mobile.ui.theme.SoltelematicShapes
import pe.soltelematic.mobile.ui.theme.SoltelematicSpacing

private val AvatarSize = 56.dp
private const val WhatsAppUrl = "https://wa.me/$DefaultBrandSupportWhatsAppNumber"

/**
 * Sprint 5, retematizado: perfil + "Acerca de" + salir. La sección NOTIFICACIONES del mockup no
 * se agrega -- hoy no existe NINGUNA preferencia de notificación en UserPreferencesDataStore (solo
 * lastEmail y showGeofences, que es del mapa, no de alertas), así que no hay nada que restylear
 * sin inventar (ver resumen de la tarea). Tampoco hay BrandConfig real que fetchear en ningún
 * lado de la app todavía -- "Acerca de" usa BrandLogo(brand = null), el mismo default de siempre.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    onBack: () -> Unit,
    onLoggedOut: () -> Unit,
    viewModel: AccountViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val whatsAppUnavailableMessage = stringResource(R.string.account_support_whatsapp_unavailable)

    LaunchedEffect(Unit) {
        viewModel.loggedOut.collect { onLoggedOut() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.account_title), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.account_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(SoltelematicSpacing.lg)
        ) {
            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                ProfileCard(uiState)
                Spacer(modifier = Modifier.height(SoltelematicSpacing.xl))
                SupportSection(
                    onWhatsAppUnavailable = {
                        coroutineScope.launch { snackbarHostState.showSnackbar(whatsAppUnavailableMessage) }
                    }
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            AboutSection()
            Spacer(modifier = Modifier.height(SoltelematicSpacing.lg))
            LogoutButton(onClick = viewModel::onLogout)
        }
    }
}

/**
 * name viene sin campo propio en /user (ver User.kt) -- el correo ocupa el lugar de "identidad
 * principal" en Heading, y lo que iba a ser la línea secundaria de correo pasa a ser el servidor
 * (dato que la pantalla ya mostraba antes de este retematizado, no se descarta).
 */
@Composable
private fun ProfileCard(uiState: AccountUiState) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        AccountAvatar(email = uiState.email)
        Spacer(modifier = Modifier.width(SoltelematicSpacing.lg))
        Column {
            Text(
                text = uiState.email ?: "-",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            uiState.serverName?.let { serverName ->
                Text(
                    text = stringResource(R.string.account_server_line, serverName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            uiState.unitCount?.let { count ->
                Text(
                    text = pluralStringResource(R.plurals.account_units_assigned, count, count),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Mismo patrón que BrandMonogram (Theme.kt): círculo accent + inicial -- duplicado a propósito, es marca vs. usuario. */
@Composable
private fun AccountAvatar(email: String?) {
    Box(
        modifier = Modifier
            .size(AvatarSize)
            .background(MaterialTheme.colorScheme.primary, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = email?.trim()?.firstOrNull()?.uppercase() ?: "?",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onPrimary
        )
    }
}

/**
 * Espacio que antes quedaba vacío entre la tarjeta de perfil y "Acerca de" -- dos enlaces útiles,
 * anclados arriba de ese espacio (no centrados ni pegados abajo, ver AccountScreen). La URL de la
 * plataforma web sale de BASE_URL vía serverRootUrl (no hardcodeada); si no fuera válida, esa fila
 * simplemente no se dibuja en vez de mostrar un enlace roto. El número de WhatsApp es la única
 * constante blanco-etiquetable de esta pantalla (ver DefaultBrandSupportWhatsAppNumber en Theme.kt).
 */
@Composable
private fun SupportSection(onWhatsAppUnavailable: () -> Unit) {
    val context = LocalContext.current
    val webUrl = remember { serverRootUrl(BuildConfig.BASE_URL) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.account_support_title).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = LocalSoltelematicColors.current.inkFaint,
            modifier = Modifier.padding(bottom = SoltelematicSpacing.sm)
        )
        if (webUrl != null) {
            SupportRow(
                icon = Icons.AutoMirrored.Filled.OpenInNew,
                title = stringResource(R.string.account_support_web_title),
                subtitle = webUrl,
                onClick = { openExternalUrl(context, webUrl) }
            )
            Spacer(modifier = Modifier.height(SoltelematicSpacing.sm))
        }
        SupportRow(
            icon = Icons.AutoMirrored.Filled.Chat,
            title = stringResource(R.string.account_support_whatsapp_title),
            subtitle = formatWhatsAppDisplayNumber(DefaultBrandSupportWhatsAppNumber),
            onClick = {
                if (!openExternalUrl(context, WhatsAppUrl)) onWhatsAppUnavailable()
            }
        )
    }
}

/** Fila título + subtítulo + chevron, con el mínimo táctil de 48dp que exige el sistema de diseño. */
@Composable
private fun SupportRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SoltelematicSpacing.md),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SoltelematicMinTouchTarget)
            .clickable(onClick = onClick)
            .padding(vertical = SoltelematicSpacing.sm)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(text = subtitle, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = LocalSoltelematicColors.current.inkFaint)
    }
}

/**
 * [rawNumber] llega en el formato que espera wa.me (país + número, sin '+' ni espacios). El
 * subtítulo agrupa esos dígitos como "+<país> ddd ddd ddd" -- correcto para el número peruano por
 * defecto; un número blanco-etiquetable de otro país tomaría su propio formato cuando BrandConfig
 * lo traiga del servidor (ver comentario junto a DefaultBrandSupportWhatsAppNumber en Theme.kt).
 */
private fun formatWhatsAppDisplayNumber(rawNumber: String): String {
    val country = rawNumber.take(2)
    val groups = rawNumber.drop(2).chunked(3)
    return "+$country ${groups.joinToString(" ")}"
}

/** Intent externo (navegador o WhatsApp). Devuelve false si no hay app que lo resuelva. */
private fun openExternalUrl(context: Context, url: String): Boolean =
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        true
    } catch (e: ActivityNotFoundException) {
        false
    }

/**
 * Único bloque de la app (junto con la app bar) que se re-tematiza con el BrandConfig real -- pero
 * no existe ninguna llamada de red en la app que lo obtenga todavía (ver comentario de BrandConfig
 * en Theme.kt), así que brand = null cae al mismo default de siempre. La versión sí es real:
 * BuildConfig.VERSION_NAME/VERSION_CODE, no el texto del mockup.
 */
@Composable
private fun AboutSection() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.account_about_title).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = LocalSoltelematicColors.current.inkFaint,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = SoltelematicSpacing.sm)
        )
        BrandLogo(brand = null)
        Spacer(modifier = Modifier.height(SoltelematicSpacing.sm))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = stringResource(R.string.account_version_format, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
            style = MaterialTheme.typography.labelLarge,
            color = LocalSoltelematicColors.current.inkFaint
        )
    }
}

@Composable
private fun LogoutButton(onClick: () -> Unit) {
    // Mismo tratamiento que ErrorBanner en LoginScreen (errorContainer/error), pero como botón.
    Button(
        onClick = onClick,
        shape = SoltelematicShapes.small,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.error
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SoltelematicMinTouchTarget)
    ) {
        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
        Spacer(modifier = Modifier.width(SoltelematicSpacing.sm))
        Text(stringResource(R.string.account_logout), style = MaterialTheme.typography.labelLarge)
    }
}
