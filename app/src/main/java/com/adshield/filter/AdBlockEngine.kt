package com.adshield.filter

import com.adshield.domain.model.BlockCategory
import com.adshield.domain.model.FilterDecision
import com.adshield.domain.model.FilterResult
import com.adshield.domain.model.ProtectionMode

/**
 * Decide si un dominio se permite o se bloquea.
 *
 * Orden de prioridad:
 * 1. Lista blanca del usuario        → ALLOW (gana siempre, incluso a las listas de seguridad)
 * 2. Lista negra del usuario         → BLOCK (CUSTOM)
 * 3. Reglas de seguridad             → BLOCK (MALWARE / PHISHING)
 * 4. Publicidad                      → BLOCK (ADVERTISING)
 * 5. Rastreadores y telemetría       → BLOCK (TRACKING / TELEMETRY)
 * 6. Regla predeterminada            → UNKNOWN (se deja pasar)
 *
 * Una regla sobre "example.com" también cubre sus subdominios.
 */
class AdBlockEngine(private val rules: RuleManager) {

    @Volatile
    var mode: ProtectionMode = ProtectionMode.NORMAL

    fun check(rawDomain: String): FilterResult {
        val domain = rawDomain.trimEnd('.').lowercase()
        if (domain.isEmpty()) return FilterResult.UNKNOWN

        val snapshot = rules.snapshot

        if (snapshot.whitelist.isNotEmpty() &&
            DomainMatcher.anySuffix(domain) { it in snapshot.whitelist }
        ) {
            return FilterResult.ALLOWED
        }

        if (snapshot.blacklist.isNotEmpty() &&
            DomainMatcher.anySuffix(domain) { it in snapshot.blacklist }
        ) {
            return FilterResult(FilterDecision.BLOCK, BlockCategory.CUSTOM)
        }

        if (snapshot.blocked.isEmpty()) return FilterResult.UNKNOWN

        val maxLevel = mode.level
        var best: BlockCategory? = null
        DomainMatcher.anySuffix(domain) { candidate ->
            val packed = snapshot.blocked[candidate]
            if (packed != null && RuleManager.levelOf(packed) <= maxLevel) {
                val category = RuleManager.categoryOf(packed)
                val current = best
                if (current == null || category.priority < current.priority) best = category
            }
            false
        }

        val category = best ?: return FilterResult.UNKNOWN
        return FilterResult(FilterDecision.BLOCK, category)
    }
}
