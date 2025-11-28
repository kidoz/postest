package su.kidoz.postest

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import org.koin.compose.koinInject
import su.kidoz.postest.ui.screens.MainScreen
import su.kidoz.postest.ui.theme.AppTheme
import su.kidoz.postest.viewmodel.AppSideEffect
import su.kidoz.postest.viewmodel.MainViewModel

@Composable
fun App() {
    val viewModel: MainViewModel = koinInject()
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Clean up ViewModel resources when the app is closed
    DisposableEffect(viewModel) {
        onDispose {
            viewModel.cleanup()
        }
    }

    // Handle side effects
    LaunchedEffect(viewModel) {
        viewModel.sideEffect.collect { sideEffect ->
            when (sideEffect) {
                is AppSideEffect.ShowToast -> {
                    snackbarHostState.showSnackbar(sideEffect.message)
                }
                is AppSideEffect.ShowError -> {
                    snackbarHostState.showSnackbar("Error: ${sideEffect.message}")
                }
                is AppSideEffect.NavigateToSettings -> {
                    // Handle navigation if needed
                }
            }
        }
    }

    AppTheme(darkTheme = state.isDarkTheme) {
        MainScreen(
            viewModel = viewModel,
            snackbarHostState = snackbarHostState,
        )
    }
}
