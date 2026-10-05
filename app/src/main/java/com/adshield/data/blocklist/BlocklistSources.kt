package com.adshield.data.blocklist

import com.adshield.domain.model.BlockCategory
import com.adshield.domain.model.BlocklistSource

/**
 * Listas públicas que descarga AD Shield. Para añadir, quitar o cambiar una lista
 * basta con editar este archivo.
 *
 * level: 1 = modo Normal, 2 = Estricto, 3 = Máximo.
 * category: categoría con la que se contabilizan los bloqueos de esa lista. Algunas
 * listas mezclan tipos (por ejemplo publicidad y rastreo); se usa la predominante.
 */
object BlocklistSources {

    val ALL: List<BlocklistSource> = listOf(
        BlocklistSource(
            id = "peter-lowe",
            name = "Peter Lowe's Ad and tracking server list",
            url = "https://pgl.yoyo.org/adservers/serverlist.php?hostformat=hosts&showintro=0&mimetype=plaintext",
            category = BlockCategory.ADVERTISING, level = 1, license = "McRae GPL"
        ),
        BlocklistSource(
            id = "firebog-easylist",
            name = "EasyList (dominios, vía Firebog)",
            url = "https://v.firebog.net/hosts/Easylist.txt",
            category = BlockCategory.ADVERTISING, level = 1, license = "GPL-3.0 / CC BY-SA 3.0"
        ),
        BlocklistSource(
            id = "firebog-easyprivacy",
            name = "EasyPrivacy (dominios, vía Firebog)",
            url = "https://v.firebog.net/hosts/Easyprivacy.txt",
            category = BlockCategory.TRACKING, level = 1, license = "GPL-3.0 / CC BY-SA 3.0"
        ),
        BlocklistSource(
            id = "urlhaus",
            name = "URLhaus (abuse.ch)",
            url = "https://urlhaus.abuse.ch/downloads/hostfile/",
            category = BlockCategory.MALWARE, level = 1, license = "CC0"
        ),
        BlocklistSource(
            id = "phishing-army",
            name = "Phishing Army",
            url = "https://phishing.army/download/phishing_army_blocklist.txt",
            category = BlockCategory.PHISHING, level = 1, license = "CC BY-NC 4.0 (uso no comercial)"
        ),
        BlocklistSource(
            id = "stevenblack",
            name = "StevenBlack Unified hosts",
            url = "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts",
            category = BlockCategory.ADVERTISING, level = 2, license = "MIT"
        ),
        BlocklistSource(
            id = "firebog-adguard-dns",
            name = "AdGuard DNS filter (dominios, vía Firebog)",
            url = "https://v.firebog.net/hosts/AdguardDNS.txt",
            category = BlockCategory.ADVERTISING, level = 2, license = "GPL-3.0"
        ),
        BlocklistSource(
            id = "windows-spy-blocker",
            name = "WindowsSpyBlocker (telemetría)",
            url = "https://raw.githubusercontent.com/crazy-max/WindowsSpyBlocker/master/data/hosts/spy.txt",
            category = BlockCategory.TELEMETRY, level = 2, license = "MIT"
        ),
        BlocklistSource(
            id = "hagezi-pro",
            name = "HaGeZi Multi PRO",
            url = "https://cdn.jsdelivr.net/gh/hagezi/dns-blocklists@latest/wildcard/pro-onlydomains.txt",
            category = BlockCategory.ADVERTISING, level = 3, license = "GPL-3.0"
        )
    )

    /**
     * Lista mínima incluida en la app para que el filtrado funcione antes de la primera
     * descarga. Son dominios publicitarios y de analítica muy conocidos.
     */
    val BUILTIN: List<Pair<String, BlockCategory>> = listOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com", "adservice.google.com",
        "googletagservices.com", "adnxs.com", "moatads.com", "taboola.com", "outbrain.com",
        "criteo.com", "criteo.net", "pubmatic.com", "rubiconproject.com", "openx.net",
        "adcolony.com", "applovin.com", "unityads.unity3d.com", "inmobi.com", "mopub.com",
        "amazon-adsystem.com", "ads-twitter.com", "adsrvr.org", "smaato.net", "vungle.com",
        "chartboost.com"
    ).map { it to BlockCategory.ADVERTISING } + listOf(
        "google-analytics.com", "scorecardresearch.com", "hotjar.com", "mixpanel.com",
        "app-measurement.com", "appsflyer.com", "quantserve.com", "flurry.com", "kochava.com"
    ).map { it to BlockCategory.TRACKING }
}
