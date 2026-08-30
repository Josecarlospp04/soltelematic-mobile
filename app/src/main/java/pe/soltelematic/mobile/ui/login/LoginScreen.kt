package pe.soltelematic.mobile.ui.login

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import pe.soltelematic.mobile.BuildConfig
import pe.soltelematic.mobile.R
import pe.soltelematic.mobile.ui.theme.BrandLogo
import pe.soltelematic.mobile.ui.theme.LocalSoltelematicColors
import pe.soltelematic.mobile.ui.theme.SoltelematicIconSpec
import pe.soltelematic.mobile.ui.theme.SoltelematicMinTouchTarget
import pe.soltelematic.mobile.ui.theme.SoltelematicShapes
import pe.soltelematic.mobile.ui.theme.SoltelematicSpacing
import org.koin.androidx.compose.koinViewModel

private val BrandLogoSize = 56.dp

@Composable
fun LoginScreen(
    onNavigateToMap: () -> Unit,
    onNavigateToForgotPassword: () -> Unit,
    viewModel: LoginViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.navigateToMap.collect { onNavigateToMap() }
    }

    LoginContent(
        uiState = uiState,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onTogglePasswordVisibility = viewModel::onTogglePasswordVisibility,
        onSubmit = viewModel::onSubmit,
        onNavigateToForgotPassword = onNavigateToForgotPassword
    )
}

@Composable
internal fun LoginContent(
    uiState: LoginUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onSubmit: () -> Unit,
    onNavigateToForgotPassword: () -> Unit
) {
    // Sin persistencia propia: es solo el estado visual del switch, tal como pide la tarea.
    // "Recordarme" en el sentido funcional ya lo cubre LoginViewModel al precargar lastEmail.
    var rememberMe by rememberSaveable { mutableStateOf(false) }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // weight(1f) + verticalScroll + Arrangement.Center: contenido corto queda centrado
            // en el espacio disponible. imePadding() es imprescindible acá: con
            // enableEdgeToEdge() (MainActivity) el árbol de Compose no se entera solo de que el
            // teclado tapa la parte de abajo -- sin esto, verificado en dispositivo real, el
            // teclado se abre y el botón queda tapado SIN que el scroll reaccione (Compose sigue
            // pensando que tiene toda la altura disponible). imePadding() sí encoge el espacio
            // real de esta Column, lo que activa el scroll para poder llegar al botón.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = SoltelematicSpacing.lg),
                verticalArrangement = Arrangement.Center
            ) {
                BrandBlock()

                Spacer(Modifier.height(SoltelematicSpacing.xl))

                LabeledField(
                    label = stringResource(R.string.login_email_label),
                    value = uiState.email,
                    onValueChange = onEmailChange,
                    placeholder = stringResource(R.string.login_email_placeholder),
                    isError = uiState.fieldErrors.containsKey("username") || uiState.isEmailRequiredError,
                    supportingText = uiState.fieldErrors["username"]?.firstOrNull()
                        ?: uiState.isEmailRequiredError.takeIf { it }
                            ?.let { stringResource(R.string.login_error_required_email) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    )
                )

                Spacer(Modifier.height(SoltelematicSpacing.lg))

                LabeledField(
                    label = stringResource(R.string.login_password_label),
                    value = uiState.password,
                    onValueChange = onPasswordChange,
                    placeholder = stringResource(R.string.login_password_placeholder),
                    isError = uiState.fieldErrors.containsKey("password") || uiState.isPasswordRequiredError,
                    supportingText = uiState.fieldErrors["password"]?.firstOrNull()
                        ?: uiState.isPasswordRequiredError.takeIf { it }
                            ?.let { stringResource(R.string.login_error_required_password) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    visualTransformation = if (uiState.isPasswordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = onTogglePasswordVisibility) {
                            Icon(
                                imageVector = if (uiState.isPasswordVisible) {
                                    Icons.Outlined.VisibilityOff
                                } else {
                                    Icons.Outlined.Visibility
                                },
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                contentDescription = stringResource(
                                    if (uiState.isPasswordVisible) {
                                        R.string.login_hide_password
                                    } else {
                                        R.string.login_show_password
                                    }
                                )
                            )
                        }
                    }
                )

                uiState.generalError?.let { error ->
                    Spacer(Modifier.height(SoltelematicSpacing.md))
                    ErrorBanner(text = errorMessage(error))
                }

                Spacer(Modifier.height(SoltelematicSpacing.md))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { rememberMe = !rememberMe }
                    ) {
                        Switch(
                            checked = rememberMe,
                            onCheckedChange = { rememberMe = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        Spacer(Modifier.width(SoltelematicSpacing.sm))
                        Text(
                            text = stringResource(R.string.login_remember_me),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    TextButton(
                        onClick = onNavigateToForgotPassword,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = LocalSoltelematicColors.current.accentText
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.login_forgot_password),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                Spacer(Modifier.height(SoltelematicSpacing.lg))

                Button(
                    onClick = onSubmit,
                    enabled = !uiState.isLoading,
                    shape = SoltelematicShapes.small,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.primary,
                        disabledContentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = SoltelematicMinTouchTarget)
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(SoltelematicIconSpec.large),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = SoltelematicIconSpec.strokeWidth
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.login_submit),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }

            Footer(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = SoltelematicSpacing.xl)
            )
        }
    }
}

/** En columna, no en fila (ver mockup): ícono arriba, nombre debajo, subtítulo debajo -- todo alineado a la izquierda. */
@Composable
private fun BrandBlock() {
    Column(horizontalAlignment = Alignment.Start) {
        BrandLogo(brand = null, size = BrandLogoSize)
        Spacer(Modifier.height(SoltelematicSpacing.sm))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(SoltelematicSpacing.xs))
        Text(
            text = stringResource(R.string.login_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LabeledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    isError: Boolean,
    supportingText: String?,
    keyboardOptions: KeyboardOptions,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    Column {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(SoltelematicSpacing.xs))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, style = MaterialTheme.typography.bodyMedium) },
            singleLine = true,
            isError = isError,
            supportingText = supportingText?.let { message ->
                { Text(message, style = MaterialTheme.typography.bodySmall) }
            },
            visualTransformation = visualTransformation,
            trailingIcon = trailingIcon,
            keyboardOptions = keyboardOptions,
            shape = SoltelematicShapes.extraSmall,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = SoltelematicMinTouchTarget)
        )
    }
}

@Composable
private fun ErrorBanner(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = SoltelematicShapes.extraSmall,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = SoltelematicSpacing.md, vertical = SoltelematicSpacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.width(SoltelematicSpacing.sm))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun Footer(modifier: Modifier = Modifier) {
    val inkFaint = LocalSoltelematicColors.current.inkFaint
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Text(
            text = stringResource(R.string.login_footer_network).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = inkFaint
        )
        Text(
            text = stringResource(R.string.login_footer_version, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.labelSmall,
            color = inkFaint
        )
    }
}

@Composable
private fun errorMessage(error: LoginError): String = when (error) {
    LoginError.InvalidCredentials -> stringResource(R.string.login_error_invalid_credentials)
    LoginError.NoConnection -> stringResource(R.string.login_error_no_connection)
    LoginError.Timeout -> stringResource(R.string.login_error_timeout)
    is LoginError.ServerError -> stringResource(R.string.login_error_server)
}
