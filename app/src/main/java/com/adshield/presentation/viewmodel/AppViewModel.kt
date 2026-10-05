package com.adshield.presentation.viewmodel

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.adshield.AdShieldApp
import com.adshield.AppContainer

/** Crea un ViewModel pasándole las dependencias del [AppContainer]. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(
    key: String? = null,
    crossinline create: (AppContainer) -> VM
): VM {
    val container = (LocalContext.current.applicationContext as AdShieldApp).container
    return viewModel(
        key = key,
        factory = viewModelFactory { initializer { create(container) } }
    )
}
