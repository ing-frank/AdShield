package com.adshield.domain.model

/**
 * Categorías de bloqueo. [priority] decide qué categoría se informa cuando un dominio
 * coincide con varias reglas: menor número = mayor prioridad (seguridad primero).
 */
enum class BlockCategory(val priority: Int, val label: String) {
    ADVERTISING(2, "Publicidad"),
    TRACKING(3, "Rastreo"),
    TELEMETRY(4, "Telemetría"),
    MALWARE(0, "Malware"),
    PHISHING(1, "Phishing"),
    CUSTOM(5, "Personalizado");

    companion object {
        fun fromName(name: String?): BlockCategory? = entries.firstOrNull { it.name == name }
    }
}

enum class FilterDecision { ALLOW, BLOCK, UNKNOWN }

data class FilterResult(val decision: FilterDecision, val category: BlockCategory? = null) {
    val isBlocked: Boolean get() = decision == FilterDecision.BLOCK

    companion object {
        val ALLOWED = FilterResult(FilterDecision.ALLOW)
        val UNKNOWN = FilterResult(FilterDecision.UNKNOWN)
    }
}

/** Niveles de protección. Una regla de nivel N solo se aplica si el modo actual es >= N. */
enum class ProtectionMode(val level: Int, val label: String, val description: String) {
    NORMAL(1, "Normal", "Bloqueo básico de publicidad, rastreadores y amenazas conocidas."),
    STRICT(2, "Estricto", "Añade más reglas de publicidad, rastreo y telemetría."),
    MAXIMUM(3, "Máximo", "Filtrado agresivo. Puede romper algunas aplicaciones o sitios.");

    companion object {
        fun fromName(name: String?): ProtectionMode = entries.firstOrNull { it.name == name } ?: NORMAL
    }
}
