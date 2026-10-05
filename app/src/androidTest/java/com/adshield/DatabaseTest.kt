package com.adshield

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.adshield.data.database.AppDatabase
import com.adshield.data.database.BlockedDomainEntity
import com.adshield.data.database.TrafficEventEntity
import com.adshield.data.repository.BlacklistRepositoryImpl
import com.adshield.data.repository.RuleLoader
import com.adshield.data.repository.WhitelistRepositoryImpl
import com.adshield.domain.model.FilterDecision
import com.adshield.domain.model.ProtectionMode
import com.adshield.filter.AdBlockEngine
import com.adshield.filter.RuleManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Pruebas instrumentadas (necesitan emulador o teléfono): Room + repositorios + motor. */
@RunWith(AndroidJUnit4::class)
class DatabaseTest {

    private lateinit var database: AppDatabase
    private lateinit var engine: AdBlockEngine
    private lateinit var loader: RuleLoader

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val rules = RuleManager()
        engine = AdBlockEngine(rules)
        loader = RuleLoader(database.blockedDomainDao(), database.allowedDomainDao(), rules, engine)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun statisticsAccumulateInTheSameHour() = runBlocking {
        val dao = database.statisticsDao()
        dao.add(3_600_000, allowed = 5, ads = 1, trackers = 0, threats = 0, custom = 0)
        dao.add(3_600_000, allowed = 2, ads = 3, trackers = 1, threats = 0, custom = 0)
        val row = dao.observeSince(0).first().single()
        assertEquals(7L, row.allowed)
        assertEquals(4L, row.ads)
        assertEquals(1L, row.trackers)
    }

    @Test
    fun eventsArePrunedToTheNewest() = runBlocking {
        val dao = database.trafficEventDao()
        dao.insertAll((1..50).map { TrafficEventEntity(timestamp = it.toLong(), domain = "d$it.com", blocked = false, category = null, appPackage = null) })
        dao.prune(10)
        val rows = dao.observeRecent(100).first()
        assertEquals(10, rows.size)
        assertEquals("d50.com", rows.first().domain)
    }

    @Test
    fun builtinListIsSeededAndBlocks() = runBlocking {
        loader.reloadAll(ProtectionMode.NORMAL)
        assertEquals(FilterDecision.BLOCK, engine.check("stats.g.doubleclick.net").decision)
        assertEquals(FilterDecision.UNKNOWN, engine.check("example.com").decision)
    }

    @Test
    fun userListsApplyImmediatelyAndWhitelistWins() = runBlocking {
        loader.reloadAll(ProtectionMode.NORMAL)
        val blacklist = BlacklistRepositoryImpl(database.blockedDomainDao()) { loader.reloadUserLists() }
        val whitelist = WhitelistRepositoryImpl(database.allowedDomainDao()) { loader.reloadUserLists() }

        blacklist.add("bad.example.com")
        assertTrue(blacklist.exists("bad.example.com"))
        assertEquals(FilterDecision.BLOCK, engine.check("bad.example.com").decision)

        whitelist.add("doubleclick.net")
        assertEquals(FilterDecision.ALLOW, engine.check("doubleclick.net").decision)

        val rule = blacklist.observe().first().single()
        blacklist.setEnabled(rule.id, false)
        assertEquals(FilterDecision.UNKNOWN, engine.check("bad.example.com").decision)
    }

    @Test
    fun modeLimitsWhichListRulesAreLoaded() = runBlocking {
        database.blockedDomainDao().insertAll(
            listOf(
                BlockedDomainEntity(domain = "strict.example.com", category = "TELEMETRY", source = "test", createdAt = 0, level = 2),
                BlockedDomainEntity(domain = "max.example.com", category = "ADVERTISING", source = "test", createdAt = 0, level = 3)
            )
        )
        loader.reloadAll(ProtectionMode.NORMAL)
        assertEquals(FilterDecision.UNKNOWN, engine.check("strict.example.com").decision)
        loader.reloadBlocklists(ProtectionMode.STRICT)
        assertEquals(FilterDecision.BLOCK, engine.check("strict.example.com").decision)
        assertEquals(FilterDecision.UNKNOWN, engine.check("max.example.com").decision)
        loader.reloadBlocklists(ProtectionMode.MAXIMUM)
        assertEquals(FilterDecision.BLOCK, engine.check("max.example.com").decision)
    }
}
