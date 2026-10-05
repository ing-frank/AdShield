package com.adshield.filter

/** Utilidades puras para normalizar, validar y comparar nombres de dominio. */
object DomainMatcher {

    private val LABEL = Regex("^[a-z0-9_]([a-z0-9_-]{0,61}[a-z0-9_])?$")

    /**
     * Convierte lo que escriba el usuario ("https://Ads.Example.com/x", "*.example.com")
     * en un dominio limpio ("ads.example.com", "example.com").
     */
    fun normalize(input: String): String {
        var s = input.trim().lowercase()
        val scheme = s.indexOf("://")
        if (scheme >= 0) s = s.substring(scheme + 3)
        val cut = s.indexOfFirst { it == '/' || it == '?' || it == '#' }
        if (cut >= 0) s = s.substring(0, cut)
        val at = s.lastIndexOf('@')
        if (at >= 0) s = s.substring(at + 1)
        val colon = s.indexOf(':')
        if (colon >= 0) s = s.substring(0, colon)
        if (s.startsWith("*.")) s = s.substring(2)
        return s.trim('.')
    }

    /** Valida un dominio ya normalizado. Rechaza IPs, etiquetas vacías y nombres de una sola etiqueta. */
    fun isValid(domain: String): Boolean {
        if (domain.length < 3 || domain.length > 253) return false
        val labels = domain.split('.')
        if (labels.size < 2) return false
        if (labels.any { !LABEL.matches(it) }) return false
        return labels.last().any { it in 'a'..'z' }
    }

    /**
     * Evalúa [predicate] sobre el dominio y sobre cada dominio padre
     * (a.b.example.com → b.example.com → example.com), sin llegar al TLD.
     * Devuelve true en cuanto el predicado devuelve true.
     */
    inline fun anySuffix(domain: String, predicate: (String) -> Boolean): Boolean {
        var current = domain
        while (true) {
            if (predicate(current)) return true
            val dot = current.indexOf('.')
            if (dot < 0) return false
            current = current.substring(dot + 1)
            if (current.indexOf('.') < 0) return false
        }
    }
}
