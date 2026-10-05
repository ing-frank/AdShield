package com.adshield.presentation.screens.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.adshield.BuildConfig
import com.adshield.presentation.components.AdShieldLogo
import com.adshield.presentation.components.BackTopBar

@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(topBar = { BackTopBar(title = "Acerca de", onBack = onBack) }) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AdShieldLogo(
                color = MaterialTheme.colorScheme.primary,
                innerColor = MaterialTheme.colorScheme.background,
                size = 88.dp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "AD SHIELD", style = MaterialTheme.typography.headlineMedium)
            Text(
                text = "Versión ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
            AboutParagraph(
                "Cómo funciona",
                "AD Shield crea una VPN local en el teléfono (VpnService de Android). Solo las consultas DNS " +
                    "pasan por ella: cuando una app pregunta por un dominio de publicidad, rastreo o malware " +
                    "conocido, AD Shield responde que no existe. El resto del tráfico sigue por tu conexión normal."
            )
            AboutParagraph(
                "Privacidad",
                "No hay servidor externo propio, no se descifra HTTPS ni se instalan certificados. Solo se " +
                    "guardan en el teléfono el dominio consultado, la hora, la decisión y la aplicación. " +
                    "Las consultas permitidas se reenvían al DNS de tu red (o a 1.1.1.1 / 9.9.9.9 si la red no informa de ninguno)."
            )
            AboutParagraph(
                "Limitaciones",
                "No elimina todos los anuncios. YouTube, Facebook, Instagram o TikTok sirven su publicidad " +
                    "desde los mismos servidores que el contenido, y bloquearlos rompería la aplicación. " +
                    "Tampoco filtra apps que usan su propio DNS cifrado, ni funciona con el «DNS privado» " +
                    "de Android en modo estricto. Android solo permite una VPN activa a la vez."
            )
            AboutParagraph(
                "Sin root",
                "No modifica otras aplicaciones y usa únicamente APIs oficiales de Android."
            )
        }
    }
}

@Composable
private fun AboutParagraph(title: String, body: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
