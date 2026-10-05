package com.adshield.domain

import com.adshield.domain.model.DomainRule
import com.adshield.domain.repository.DomainRuleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Repositorio en memoria para pruebas. */
class FakeDomainRuleRepository : DomainRuleRepository {
    private val items = MutableStateFlow<List<DomainRule>>(emptyList())
    private var nextId = 1L

    val current: List<DomainRule> get() = items.value

    override fun observe(): Flow<List<DomainRule>> = items
    override suspend fun exists(domain: String): Boolean = items.value.any { it.domain == domain }
    override suspend fun add(domain: String) {
        items.value = items.value + DomainRule(nextId++, domain, true, 0L)
    }
    override suspend fun rename(id: Long, domain: String) {
        items.value = items.value.map { if (it.id == id) it.copy(domain = domain) else it }
    }
    override suspend fun setEnabled(id: Long, enabled: Boolean) {
        items.value = items.value.map { if (it.id == id) it.copy(enabled = enabled) else it }
    }
    override suspend fun delete(id: Long) {
        items.value = items.value.filter { it.id != id }
    }
}
