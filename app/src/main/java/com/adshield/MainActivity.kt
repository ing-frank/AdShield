package com.adshield

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adshield.domain.model.AppSettings
import com.adshield.presentation.navigation.AdShieldNavHost
import com.adshield.presentation.theme.AdShieldTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as AdShieldApp).container
        setContent {
            val settings by container.settingsRepository.settings
                .collectAsStateWithLifecycle(initialValue = AppSettings())
            AdShieldTheme(themeMode = settings.theme) {
                AdShieldNavHost()
            }
        }
    }
}
