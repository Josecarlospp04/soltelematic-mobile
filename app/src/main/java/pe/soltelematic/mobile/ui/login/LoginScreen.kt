package pe.soltelematic.mobile.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import pe.soltelematic.mobile.R

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
private fun LoginContent(
    uiState: LoginUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onSubmit: () -> Unit,
    onNavigateToForgotPassword: () -> Unit
) {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.login_title),
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = uiState.email,
                onValueChange = onEmailChange,
                label = { Text(stringResource(R.string.login_email_label)) },
                singleLine = true,
                isError = uiState.fieldErrors.containsKey("username") || uiState.isEmailRequiredError,
                supportingText = {
                    val serverMessage = uiState.fieldErrors["username"]?.firstOrNull()
                    when {
                        serverMessage != null -> Text(serverMessage)
                        uiState.isEmailRequiredError -> Text(stringResource(R.string.login_error_required_email))
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = uiState.password,
                onValueChange = onPasswordChange,
                label = { Text(stringResource(R.string.login_password_label)) },
                singleLine = true,
                isError = uiState.fieldErrors.containsKey("password") || uiState.isPasswordRequiredError,
                supportingText = {
                    val serverMessage = uiState.fieldErrors["password"]?.firstOrNull()
                    when {
                        serverMessage != null -> Text(serverMessage)
                        uiState.isPasswordRequiredError -> Text(stringResource(R.string.login_error_required_password))
                    }
                },
                visualTransformation = if (uiState.isPasswordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                trailingIcon = {
                    TextButton(onClick = onTogglePasswordVisibility) {
                        Text(
                            stringResource(
                                if (uiState.isPasswordVisible) {
                                    R.string.login_hide_password
                                } else {
                                    R.string.login_show_password
                                }
                            )
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            uiState.generalError?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorMessage(error),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(onClick = onNavigateToForgotPassword) {
                Text(stringResource(R.string.login_forgot_password))
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onSubmit,
                enabled = !uiState.isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(stringResource(R.string.login_submit))
                }
            }
        }
    }
}

@Composable
private fun errorMessage(error: LoginError): String = when (error) {
    LoginError.InvalidCredentials -> stringResource(R.string.login_error_invalid_credentials)
    LoginError.NoConnection -> stringResource(R.string.login_error_no_connection)
    LoginError.Timeout -> stringResource(R.string.login_error_timeout)
    is LoginError.ServerError -> stringResource(R.string.login_error_server)
}
