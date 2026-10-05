package com.adshield.data.repository

import com.adshield.data.blocklist.BlocklistSources
import com.adshield.data.database.AllowedDomainDao
import com.adshield.data.database.BlockedDomainDao
import com.adshield.data.database.BlockedDomainEntity
import com.adshield.data.database.SOURCE_BUILTIN
import com.adshield.domain.model.BlockCategory
import com.adshield.domain.model.ProtectionMode
import com.adshield.domain.model.RuleCounts
import com.adshield.filter.AdBlockEngine
import com.adshield.filter.RuleManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Carga las reglas de Room a memoria (RuleManager) y mantiene el modo del motor. */
class RuleLoader(
    private val blockedDao: BlockedDomainDao,
    private val allowedDao: AllowedDomainDao,
    private val ruleManager: RuleManager,
    private val engine: AdBlockEngine
) {
    private val mutex = Mutex()

    @Volatile
    private var loadedOnce = false

    private val _counts = MutableStateFlow(RuleCounts())
    val counts: StateFlow<RuleCounts> = _counts.asStateFlow()

    suspend fun ensureLoaded(mode: ProtectionMode) {
        if (!loadedOnce) reloadAll(mode)
    }

    suspend fun reloadAll(mode: ProtectionMode) = withContext(Dispatchers.IO) {
        mutex.withLock {
            seedBuiltinIfEmpty()
            loadUserLists()
            loadBlocklists(mode)
            loadedOnce = true
        }
    }

    suspend fun reloadUserLists() = withContext(Dispatchers.IO) {
        mutex.withLock { loadUserLists() }
    }

    suspend fun reloadBlocklists(mode: ProtectionMode) = withContext(Dispatchers.IO) {
        mutex.withLock {
            loadBlocklists(mode)
            loadedOnce = true
        }
    }

    private suspend fun seedBuiltinIfEmpty() {
        if (blockedDao.listRuleCount() > 0) return
        val now = System.currentTimeMillis()
        blockedDao.insertAll(
            BlocklistSources.BUILTIN.map { (domain, category) ->
                BlockedDomainEntity(
                    domain = domain, category = category.name, source = SOURCE_BUILTIN,
                    createdAt = now, level = 1
                )
            }
        )
    }

    private suspend fun loadUserLists() {
        ruleManager.setUserLists(
            whitelist = allowedDao.enabledDomains().toHashSet(),
            blacklist = blockedDao.customEnabledDomains().toHashSet()
        )
        publishCounts()
    }

    private suspend fun loadBlocklists(mode: ProtectionMode) {
        val map = HashMap<String, Byte>()
        var afterId = 0L
        while (true) {
            val page = blockedDao.rulesPage(mode.level, afterId, PAGE_SIZE)
            if (page.isEmpty()) break
            for (row in page) {
                val category = BlockCategory.fromName(row.category) ?: continue
                RuleManager.merge(map, row.domain, category, row.level)
            }
            afterId = page.last().id
            if (page.size < PAGE_SIZE) break
        }
        ruleManager.setBlocked(map)
        engine.mode = mode
        publishCounts()
    }

    private fun publishCounts() {
        _counts.value = RuleCounts(
            lists = ruleManager.blockedCount,
            whitelist = ruleManager.whitelistCount,
            blacklist = ruleManager.blacklistCount,
            loaded = ruleManager.loaded
        )
    }

    private companion object {
        const val PAGE_SIZE = 5000
    }
}
