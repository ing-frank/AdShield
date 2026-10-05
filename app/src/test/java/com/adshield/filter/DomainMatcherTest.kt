package com.adshield.filter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainMatcherTest {

    @Test
    fun normalizeStripsSchemePathPortAndWildcard() {
        assertEquals("ads.example.com", DomainMatcher.normalize("  HTTPS://Ads.Example.com:443/path?q=1 "))
        assertEquals("example.com", DomainMatcher.normalize("*.example.com."))
        assertEquals("example.com", DomainMatcher.normalize("user@example.com"))
    }

    @Test
    fun validDomainsAreAccepted() {
        assertTrue(DomainMatcher.isValid("example.com"))
        assertTrue(DomainMatcher.isValid("a-b.sub.example.co.uk"))
        assertTrue(DomainMatcher.isValid("xn--80ak6aa92e.com"))
    }

    @Test
    fun invalidDomainsAreRejected() {
        assertFalse(DomainMatcher.isValid("localhost"))
        assertFalse(DomainMatcher.isValid("192.168.1.1"))
        assertFalse(DomainMatcher.isValid("exa mple.com"))
        assertFalse(DomainMatcher.isValid("-bad.com"))
        assertFalse(DomainMatcher.isValid("a..com"))
        assertFalse(DomainMatcher.isValid(""))
        assertFalse(DomainMatcher.isValid("a".repeat(64) + ".com"))
    }

    @Test
    fun anySuffixWalksParentsButNotTld() {
        val seen = ArrayList<String>()
        DomainMatcher.anySuffix("a.b.example.com") { seen.add(it); false }
        assertEquals(listOf("a.b.example.com", "b.example.com", "example.com"), seen)
    }

    @Test
    fun anySuffixStopsOnFirstMatch() {
        val seen = ArrayList<String>()
        val found = DomainMatcher.anySuffix("a.b.example.com") { seen.add(it); it == "b.example.com" }
        assertTrue(found)
        assertEquals(2, seen.size)
    }
}
