package com.ajthom90.kwikfinder.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ajthom90.kwikfinder.KwikFinderApplication
import com.ajthom90.kwikfinder.ui.theme.KwikFinderTheme

@Composable
fun KwikFinderApp() {
    val context = LocalContext.current
    val app = context.applicationContext as KwikFinderApplication
    val viewModel: StoresViewModel = viewModel(
        factory = StoresViewModel.Factory(app.container),
    )
    MainScreen(
        viewModel = viewModel,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
fun KwikFinderAppPreviewHost() {
    KwikFinderTheme {
        // Preview without Application DI — not used for runtime.
    }
}
