package com.adshield.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adshield.domain.model.DomainRule
import com.adshield.domain.repository.DomainRuleRepository
import com.adshield.domain.usecase.SaveDomainResult
import com.adshield.domain.usecase.SaveDomainRuleUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DomainListUiState(
    val query: String = "",
    val rules: List<DomainRule> = emptyList(),
    val total: Int = 0
)

/** ViewModel compartido por Lista blanca y Lista negra; solo cambia el repositorio. */
class DomainListViewModel(
    private val repository: DomainRuleRepository,
    private val saveRule: SaveDomainRuleUseCase
) : ViewModel() {

    private val query = MutableStateFlow("")

    private val _formError = MutableStateFlow<String?>(null)
    val formError: StateFlow<String?> = _formError.asStateFlow()

    val uiState: StateFlow<DomainListUiState> = combine(repository.observe(), query) { rules, text ->
        val needle = text.trim().lowercase()
        DomainListUiState(
            query = text,
            rules = if (needle.isEmpty()) rules else rules.filter { it.domain.contains(needle) },
            total = rules.size
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DomainListUiState())

    fun onQueryChange(text: String) {
        query.value = text
    }

    fun clearFormError() {
        _formError.value = null
    }

    /** Guarda un dominio nuevo o editado. [onSaved] solo se llama si se guardó. */
    fun save(input: String, editing: DomainRule?, onSaved: () -> Unit) {
        viewModelScope.launch {
            when (saveRule(input, editing)) {
                is SaveDomainResult.Saved -> {
                    _formError.value = null
                    onSaved()
                }
                SaveDomainResult.Invalid ->
                    _formError.value = "Dominio no válido. Ejemplo: ads.example.com"
                SaveDomainResult.Duplicate ->
                    _formError.value = "Ese dominio ya está en la lista."
            }
        }
    }

    fun setEnabled(rule: DomainRule, enabled: Boolean) {
        viewModelScope.launch { repository.setEnabled(rule.id, enabled) }
    }

    fun delete(rule: DomainRule) {
        viewModelScope.launch { repository.delete(rule.id) }
    }
}
