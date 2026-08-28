package pe.soltelematic.mobile.ui.forgot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import pe.soltelematic.mobile.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForgotPasswordScreen(
    onBack: () -> Unit,
    viewModel: ForgotPasswordViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.forgot_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.forgot_back_content_description)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            if (uiState.isSubmitted) {
                ForgotPasswordConfirmation(onBackToLogin = onBack)
            } else {
                ForgotPasswordForm(
                    uiState = uiState,
                    onEmailChange = viewModel::onEmailChange,
                    onSubmit = viewModel::onSubmit
                )
            }
        }
    }
}

@Composable
private fun ForgotPasswordForm(
    uiState: ForgotPasswordUiState,
    onEmailChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    OutlinedTextField(
        value = uiState.email,
        onValueChange = onEmailChange,
        label = { Text(stringResource(R.string.forgot_email_label)) },
        singleLine = true,
        isError = uiState.isEmailFormatError,
        supportingText = {
            if (uiState.isEmailFormatError) {
                Text(stringResource(R.string.forgot_error_invalid_email))
            }
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Done
        ),
        modifier = Modifier.fillMaxWidth()
    )

    if (uiState.isSubmitError) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.forgot_error_network),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium
        )
    }

    Spacer(modifier = Modifier.height(24.dp))

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
            Text(stringResource(R.string.forgot_submit))
        }
    }
}

@Composable
private fun ForgotPasswordConfirmation(onBackToLogin: () -> Unit) {
    Text(
        text = stringResource(R.string.forgot_confirmation_message),
        style = MaterialTheme.typography.bodyLarge
    )

    Spacer(modifier = Modifier.height(24.dp))

    Button(
        onClick = onBackToLogin,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(R.string.forgot_back_to_login))
    }
}
