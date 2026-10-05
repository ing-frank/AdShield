package com.adshield.domain.usecase

import com.adshield.domain.model.DomainRule
import com.adshield.domain.repository.DomainRuleRepository
import com.adshield.filter.DomainMatcher

sealed interface SaveDomainResult {
    data class Saved(val domain: String) : SaveDomainResult
    data object Invalid : SaveDomainResult
    data object Duplicate : SaveDomainResult
}

/** Normaliza y valida un dominio antes de añadirlo o editarlo en una lista del usuario. */
class SaveDomainRuleUseCase(private val repository: DomainRuleRepository) {

    suspend operator fun invoke(input: String, editing: DomainRule? = null): SaveDomainResult {
        val domain = DomainMatcher.normalize(input)
        if (!DomainMatcher.isValid(domain)) return SaveDomainResult.Invalid
        if (editing != null && editing.domain == domain) return SaveDomainResult.Saved(domain)
        if (repository.exists(domain)) return SaveDomainResult.Duplicate
        if (editing == null) repository.add(domain) else repository.rename(editing.id, domain)
        return SaveDomainResult.Saved(domain)
    }
}
