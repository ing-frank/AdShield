package com.adshield.data.blocklist

import com.adshield.filter.DomainMatcher

/**
 * Interpreta una línea de una lista de bloqueo. Formatos admitidos:
 *  - hosts:        "0.0.0.0 ads.example.com" / "127.0.0.1 ads.example.com"
 *  - dominios:     "ads.example.com"
 *  - estilo adblock simple: "||ads.example.com^"
 * Cualquier otra cosa (comentarios, excepciones, reglas con opciones) se ignora.
 */
object HostsParser {

    private val IGNORED = setOf(
        "localhost", "localhost.localdomain", "local", "broadcasthost",
        "ip6-localhost", "ip6-loopback", "ip6-localnet", "ip6-mcastprefix",
        "ip6-allnodes", "ip6-allrouters", "ip6-allhosts", "0.0.0.0"
    )
    private val SINK_ADDRESSES = setOf("0.0.0.0", "127.0.0.1", "::", "::1", "0")

    fun parseLine(rawLine: String): String? {
        var line = rawLine.trim()
        if (line.isEmpty()) return null
        val first = line[0]
        if (first == '#' || first == '!' || first == '[' || first == '@') return null

        val candidate: String
        if (line.startsWith("||")) {
            if (!line.endsWith("^")) return null
            candidate = line.substring(2, line.length - 1)
        } else {
            val hash = line.indexOf('#')
            if (hash >= 0) line = line.substring(0, hash).trim()
            if (line.isEmpty()) return null
            val parts = line.split(' ', '\t').filter { it.isNotEmpty() }
            candidate = when (parts.size) {
                1 -> parts[0]
                2 -> if (parts[0] in SINK_ADDRESSES) parts[1] else return null
                else -> return null
            }
        }

        val domain = candidate.lowercase().trim('.')
        if (domain in IGNORED) return null
        return if (DomainMatcher.isValid(domain)) domain else null
    }
}
