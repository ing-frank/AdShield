package com.adshield.data

import com.adshield.data.blocklist.HostsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HostsParserTest {

    @Test
    fun parsesHostsFormat() {
        assertEquals("ads.example.com", HostsParser.parseLine("0.0.0.0 ads.example.com"))
        assertEquals("ads.example.com", HostsParser.parseLine("127.0.0.1\tAds.Example.com  # comentario"))
    }

    @Test
    fun parsesPlainDomainAndAdblockStyle() {
        assertEquals("tracker.net", HostsParser.parseLine("tracker.net"))
        assertEquals("ads.example.com", HostsParser.parseLine("||ads.example.com^"))
    }

    @Test
    fun ignoresCommentsBlanksAndLocalEntries() {
        assertNull(HostsParser.parseLine(""))
        assertNull(HostsParser.parseLine("# comentario"))
        assertNull(HostsParser.parseLine("! comentario adblock"))
        assertNull(HostsParser.parseLine("[Adblock Plus]"))
        assertNull(HostsParser.parseLine("127.0.0.1 localhost"))
        assertNull(HostsParser.parseLine("0.0.0.0 0.0.0.0"))
        assertNull(HostsParser.parseLine("::1 ip6-localhost"))
    }

    @Test
    fun ignoresUnsupportedRules() {
        assertNull(HostsParser.parseLine("@@||allowed.example.com^"))
        assertNull(HostsParser.parseLine("||ads.example.com^\$third-party"))
        assertNull(HostsParser.parseLine("||example.com/ads/*"))
        assertNull(HostsParser.parseLine("192.168.1.10 printer.lan.example.com"))
        assertNull(HostsParser.parseLine("<html><body>error</body></html>"))
        assertNull(HostsParser.parseLine("uno dos tres"))
    }
}
