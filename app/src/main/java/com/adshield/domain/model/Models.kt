package com.adshield.domain.model

/** Dominio de la lista blanca o de la lista negra del usuario. */
data class DomainRule(val id: Long, val domain: String, val enabled: Boolean, val createdAt: Long)

enum class ThemeMode(val label: String) {
    SYSTEM("Sistema"), LIGHT("Claro"), DARK("Oscuro");

    companion object {
        fun fromName(name: String?): ThemeMode = entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}

enum class UpdateFrequency(val label: String, val description: String) {
    AUTOMATIC("Automático", "Una vez al día, solo con Wi‑Fi y batería suficiente."),
    DAILY("Diario", "Una vez al día con cualquier conexión."),
    WEEKLY("Semanal", "Una vez por semana con cualquier conexión."),
    MANUAL("Manual", "Solo cuando pulses «Actualizar ahora».");

    companion object {
        fun fromName(name: String?): UpdateFrequency = entries.firstOrNull { it.name == name } ?: AUTOMATIC
    }
}

data class AppSettings(
    val mode: ProtectionMode = ProtectionMode.NORMAL,
    val startOnBoot: Boolean = false,
    val updateFrequency: UpdateFrequency = UpdateFrequency.AUTOMATIC,
    val notificationCounter: Boolean = true,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val saveStats: Boolean = true
)

data class TrafficEvent(
    val id: Long,
    val timestamp: Long,
    val domain: String,
    val blocked: Boolean,
    val category: BlockCategory?,
    val appPackage: String?
)

/** Contadores de una hora concreta. [hourStart] es el inicio de la hora en milisegundos. */
data class StatsBucket(
    val hourStart: Long,
    val allowed: Long = 0,
    val ads: Long = 0,
    val trackers: Long = 0,
    val threats: Long = 0,
    val custom: Long = 0
) {
    val blocked: Long get() = ads + trackers + threats + custom
}

data class StatsTotals(
    val allowed: Long = 0,
    val ads: Long = 0,
    val trackers: Long = 0,
    val threats: Long = 0,
    val custom: Long = 0
) {
    val blocked: Long get() = ads + trackers + threats + custom
    val total: Long get() = allowed + blocked

    companion object {
        fun of(buckets: List<StatsBucket>): StatsTotals = StatsTotals(
            allowed = buckets.sumOf { it.allowed },
            ads = buckets.sumOf { it.ads },
            trackers = buckets.sumOf { it.trackers },
            threats = buckets.sumOf { it.threats },
            custom = buckets.sumOf { it.custom }
        )
    }
}

/** Lista de bloqueo pública que la app puede descargar. */
data class BlocklistSource(
    val id: String,
    val name: String,
    val url: String,
    val category: BlockCategory,
    val level: Int,
    val license: String
)

data class BlocklistSourceStatus(
    val source: BlocklistSource,
    val lastSuccessAt: Long?,
    val lastAttemptAt: Long?,
    val domainCount: Int,
    val lastError: String?
)

data class UpdateSummary(val updated: Int, val failed: Int, val alreadyRunning: Boolean = false)

data class RuleCounts(val lists: Int = 0, val whitelist: Int = 0, val blacklist: Int = 0, val loaded: Boolean = false)

data class InstalledApp(val packageName: String, val label: String)

enum class DiagnosticStatus { OK, WARNING, ERROR }

data class DiagnosticItem(
    val title: String,
    val status: DiagnosticStatus,
    val detail: String,
    val fix: String? = null
)
