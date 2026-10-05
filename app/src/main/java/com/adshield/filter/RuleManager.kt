package com.adshield.filter

import com.adshield.domain.model.BlockCategory

/** Foto inmutable de todas las reglas. Se reemplaza entera, nunca se modifica. */
class RuleSnapshot(
    val whitelist: Set<String>,
    val blacklist: Set<String>,
    /** dominio → categoría y nivel empaquetados en un Byte (ver [RuleManager.pack]). */
    val blocked: Map<String, Byte>
) {
    companion object {
        val EMPTY = RuleSnapshot(emptySet(), emptySet(), emptyMap())
    }
}

/**
 * Mantiene en memoria las reglas que consulta el motor.
 *
 * Las búsquedas son O(1) por etiqueta del dominio (HashSet / HashMap); nunca se recorre
 * la lista completa. La carga desde la base de datos la hace RuleLoader (capa de datos).
 */
class RuleManager {

    @Volatile
    var snapshot: RuleSnapshot = RuleSnapshot.EMPTY
        private set

    @Volatile
    var loaded: Boolean = false
        private set

    val blockedCount: Int get() = snapshot.blocked.size
    val whitelistCount: Int get() = snapshot.whitelist.size
    val blacklistCount: Int get() = snapshot.blacklist.size

    @Synchronized
    fun setUserLists(whitelist: Set<String>, blacklist: Set<String>) {
        snapshot = RuleSnapshot(whitelist, blacklist, snapshot.blocked)
    }

    @Synchronized
    fun setBlocked(blocked: Map<String, Byte>) {
        snapshot = RuleSnapshot(snapshot.whitelist, snapshot.blacklist, blocked)
        loaded = true
    }

    companion object {
        private val CATEGORIES = BlockCategory.entries.toTypedArray()

        fun pack(category: BlockCategory, level: Int): Byte =
            (category.ordinal or (level.coerceIn(0, 7) shl 4)).toByte()

        fun categoryOf(packed: Byte): BlockCategory = CATEGORIES[packed.toInt() and 0x0F]

        fun levelOf(packed: Byte): Int = (packed.toInt() shr 4) and 0x07

        /**
         * Añade una regla al mapa. Si el dominio ya existe se conserva la regla de menor
         * nivel (se aplica en más modos) y, a igual nivel, la categoría de mayor prioridad.
         */
        fun merge(target: MutableMap<String, Byte>, domain: String, category: BlockCategory, level: Int) {
            val existing = target[domain]
            if (existing == null) {
                target[domain] = pack(category, level)
                return
            }
            val oldLevel = levelOf(existing)
            val better = level < oldLevel ||
                (level == oldLevel && category.priority < categoryOf(existing).priority)
            if (better) target[domain] = pack(category, level)
        }
    }
}
