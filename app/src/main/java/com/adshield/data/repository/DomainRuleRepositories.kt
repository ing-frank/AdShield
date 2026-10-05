package com.adshield.data.repository

import com.adshield.data.database.AllowedDomainDao
import com.adshield.data.database.AllowedDomainEntity
import com.adshield.data.database.BlockedDomainDao
import com.adshield.data.database.BlockedDomainEntity
import com.adshield.data.database.SOURCE_CUSTOM
import com.adshield.domain.model.BlockCategory
import com.adshield.domain.model.DomainRule
import com.adshield.domain.repository.DomainRuleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Lista blanca. Cada cambio recarga las reglas en memoria para que se aplique al instante. */
class WhitelistRepositoryImpl(
    private val dao: AllowedDomainDao,
    private val onChanged: suspend () -> Unit
) : DomainRuleRepository {

    override fun observe(): Flow<List<DomainRule>> = dao.observeAll().map { list ->
        list.map { DomainRule(it.id, it.domain, it.enabled, it.createdAt) }
    }

    override suspend fun exists(domain: String): Boolean = dao.count(domain) > 0

    override suspend fun add(domain: String) {
        dao.insert(AllowedDomainEntity(domain = domain, createdAt = System.currentTimeMillis()))
        onChanged()
    }

    override suspend fun rename(id: Long, domain: String) {
        dao.rename(id, domain)
        onChanged()
    }

    override suspend fun setEnabled(id: Long, enabled: Boolean) {
        dao.setEnabled(id, enabled)
        onChanged()
    }

    override suspend fun delete(id: Long) {
        dao.deleteById(id)
        onChanged()
    }
}

/** Lista negra personalizada (categoría CUSTOM). */
class BlacklistRepositoryImpl(
    private val dao: BlockedDomainDao,
    private val onChanged: suspend () -> Unit
) : DomainRuleRepository {

    override fun observe(): Flow<List<DomainRule>> = dao.observeCustom().map { list ->
        list.map { DomainRule(it.id, it.domain, it.enabled, it.createdAt) }
    }

    override suspend fun exists(domain: String): Boolean = dao.countCustom(domain) > 0

    override suspend fun add(domain: String) {
        dao.insert(
            BlockedDomainEntity(
                domain = domain, category = BlockCategory.CUSTOM.name, source = SOURCE_CUSTOM,
                createdAt = System.currentTimeMillis(), level = 0
            )
        )
        onChanged()
    }

    override suspend fun rename(id: Long, domain: String) {
        dao.rename(id, domain)
        onChanged()
    }

    override suspend fun setEnabled(id: Long, enabled: Boolean) {
        dao.setEnabled(id, enabled)
        onChanged()
    }

    override suspend fun delete(id: Long) {
        dao.deleteById(id)
        onChanged()
    }
}
