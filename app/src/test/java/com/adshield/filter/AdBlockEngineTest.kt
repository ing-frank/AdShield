package com.adshield.filter

import com.adshield.domain.model.BlockCategory
import com.adshield.domain.model.FilterDecision
import com.adshield.domain.model.ProtectionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AdBlockEngineTest {

    private fun engine(
        whitelist: Set<String> = emptySet(),
        blacklist: Set<String> = emptySet(),
        mode: ProtectionMode = ProtectionMode.NORMAL
    ): AdBlockEngine {
        val blocked = HashMap<String, Byte>()
        RuleManager.merge(blocked, "ads.example.com", BlockCategory.ADVERTISING, 1)
        RuleManager.merge(blocked, "tracker.net", BlockCategory.TRACKING, 1)
        RuleManager.merge(blocked, "evil.org", BlockCategory.MALWARE, 1)
        RuleManager.merge(blocked, "telemetry.vendor.com", BlockCategory.TELEMETRY, 2)
        RuleManager.merge(blocked, "aggressive.io", BlockCategory.ADVERTISING, 3)
        RuleManager.merge(blocked, "shared.cdn.com", BlockCategory.TRACKING, 1)
        RuleManager.merge(blocked, "x.shared.cdn.com", BlockCategory.PHISHING, 1)
        val rules = RuleManager()
        rules.setUserLists(whitelist, blacklist)
        rules.setBlocked(blocked)
        return AdBlockEngine(rules).also { it.mode = mode }
    }

    @Test
    fun unknownDomainIsNotBlocked() {
        val result = engine().check("example.com")
        assertEquals(FilterDecision.UNKNOWN, result.decision)
        assertNull(result.category)
    }

    @Test
    fun blockedDomainReturnsItsCategory() {
        assertEquals(BlockCategory.ADVERTISING, engine().check("ads.example.com").category)
        assertEquals(BlockCategory.TRACKING, engine().check("tracker.net").category)
        assertEquals(BlockCategory.MALWARE, engine().check("evil.org").category)
    }

    @Test
    fun subdomainsOfBlockedDomainAreBlocked() {
        assertEquals(FilterDecision.BLOCK, engine().check("a.b.tracker.net").decision)
    }

    @Test
    fun parentOfBlockedDomainIsNotBlocked() {
        assertEquals(FilterDecision.UNKNOWN, engine().check("example.com").decision)
    }

    @Test
    fun matchingIsCaseInsensitiveAndIgnoresTrailingDot() {
        assertEquals(FilterDecision.BLOCK, engine().check("ADS.Example.COM.").decision)
    }

    @Test
    fun whitelistWinsOverEverything() {
        val e = engine(whitelist = setOf("evil.org", "example.com"), blacklist = setOf("evil.org"))
        assertEquals(FilterDecision.ALLOW, e.check("evil.org").decision)
        assertEquals(FilterDecision.ALLOW, e.check("ads.example.com").decision)
    }

    @Test
    fun blacklistBlocksAsCustomIncludingSubdomains() {
        val e = engine(blacklist = setOf("mysite.com"))
        assertEquals(BlockCategory.CUSTOM, e.check("mysite.com").category)
        assertEquals(BlockCategory.CUSTOM, e.check("cdn.mysite.com").category)
    }

    @Test
    fun securityCategoryWinsWhenSeveralRulesMatch() {
        assertEquals(BlockCategory.PHISHING, engine().check("x.shared.cdn.com").category)
        assertEquals(BlockCategory.TRACKING, engine().check("y.shared.cdn.com").category)
    }

    @Test
    fun modeControlsWhichLevelsApply() {
        assertEquals(FilterDecision.UNKNOWN, engine(mode = ProtectionMode.NORMAL).check("telemetry.vendor.com").decision)
        assertEquals(FilterDecision.BLOCK, engine(mode = ProtectionMode.STRICT).check("telemetry.vendor.com").decision)
        assertEquals(FilterDecision.UNKNOWN, engine(mode = ProtectionMode.STRICT).check("aggressive.io").decision)
        assertEquals(FilterDecision.BLOCK, engine(mode = ProtectionMode.MAXIMUM).check("aggressive.io").decision)
    }

    @Test
    fun changingModeTakesEffectImmediately() {
        val e = engine()
        assertEquals(FilterDecision.UNKNOWN, e.check("aggressive.io").decision)
        e.mode = ProtectionMode.MAXIMUM
        assertEquals(FilterDecision.BLOCK, e.check("aggressive.io").decision)
    }

    @Test
    fun emptyInputIsUnknown() {
        assertEquals(FilterDecision.UNKNOWN, engine().check("").decision)
        assertEquals(FilterDecision.UNKNOWN, engine().check(".").decision)
    }
}
