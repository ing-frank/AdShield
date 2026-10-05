package com.adshield.filter

import com.adshield.domain.model.BlockCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleManagerTest {

    @Test
    fun packRoundTripsEveryCategoryAndLevel() {
        for (category in BlockCategory.entries) {
            for (level in 0..3) {
                val packed = RuleManager.pack(category, level)
                assertEquals(category, RuleManager.categoryOf(packed))
                assertEquals(level, RuleManager.levelOf(packed))
            }
        }
    }

    @Test
    fun mergeKeepsLowerLevel() {
        val map = HashMap<String, Byte>()
        RuleManager.merge(map, "x.com", BlockCategory.ADVERTISING, 3)
        RuleManager.merge(map, "x.com", BlockCategory.TRACKING, 1)
        assertEquals(1, RuleManager.levelOf(map.getValue("x.com")))
        assertEquals(BlockCategory.TRACKING, RuleManager.categoryOf(map.getValue("x.com")))
    }

    @Test
    fun mergePrefersSecurityCategoryAtSameLevel() {
        val map = HashMap<String, Byte>()
        RuleManager.merge(map, "x.com", BlockCategory.ADVERTISING, 1)
        RuleManager.merge(map, "x.com", BlockCategory.MALWARE, 1)
        RuleManager.merge(map, "x.com", BlockCategory.TRACKING, 1)
        assertEquals(BlockCategory.MALWARE, RuleManager.categoryOf(map.getValue("x.com")))
    }

    @Test
    fun snapshotsAreReplacedIndependently() {
        val manager = RuleManager()
        assertFalse(manager.loaded)
        manager.setUserLists(setOf("ok.com"), setOf("bad.com"))
        manager.setBlocked(mapOf("ads.com" to RuleManager.pack(BlockCategory.ADVERTISING, 1)))
        assertTrue(manager.loaded)
        assertEquals(1, manager.whitelistCount)
        assertEquals(1, manager.blacklistCount)
        assertEquals(1, manager.blockedCount)
        manager.setUserLists(emptySet(), emptySet())
        assertEquals(1, manager.blockedCount)
    }
}
