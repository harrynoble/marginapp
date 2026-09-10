package com.margin.app.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * A ViewModel needs constructor arguments from [com.margin.app.di.AppContainer]. This is the
 * whole of the wiring: no framework, no code generation, no module declarations.
 */
@Composable
inline fun <reified VM : ViewModel> marginViewModel(
    key: String? = null,
    crossinline create: () -> VM,
): VM = viewModel(
    key = key,
    factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
    },
)
