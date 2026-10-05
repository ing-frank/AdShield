package com.adshield.presentation

import com.adshield.domain.FakeDomainRuleRepository
import com.adshield.domain.usecase.SaveDomainRuleUseCase
import com.adshield.presentation.viewmodel.DomainListViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DomainListViewModelTest {

    private val repository = FakeDomainRuleRepository()
    private lateinit var viewModel: DomainListViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = DomainListViewModel(repository, SaveDomainRuleUseCase(repository))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun savingAValidDomainAddsItAndCallsBack() = runTest {
        var saved = false
        viewModel.save("ads.example.com", null) { saved = true }
        assertTrue(saved)
        assertNull(viewModel.formError.value)
        assertEquals(listOf("ads.example.com"), repository.current.map { it.domain })
    }

    @Test
    fun savingAnInvalidDomainShowsAnErrorAndDoesNotCallBack() = runTest {
        var saved = false
        viewModel.save("dominio inválido", null) { saved = true }
        assertFalse(saved)
        assertNotNull(viewModel.formError.value)
        assertTrue(repository.current.isEmpty())
    }

    @Test
    fun duplicateShowsAnError() = runTest {
        viewModel.save("example.com", null) {}
        viewModel.save("example.com", null) {}
        assertNotNull(viewModel.formError.value)
        viewModel.clearFormError()
        assertNull(viewModel.formError.value)
    }

    @Test
    fun searchFiltersTheVisibleRules() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        viewModel.save("ads.example.com", null) {}
        viewModel.save("tracker.net", null) {}
        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.rules.size)

        viewModel.onQueryChange("TRACK")
        advanceUntilIdle()
        assertEquals(listOf("tracker.net"), viewModel.uiState.value.rules.map { it.domain })
        assertEquals(2, viewModel.uiState.value.total)
    }

    @Test
    fun toggleAndDeleteReachTheRepository() = runTest {
        viewModel.save("example.com", null) {}
        val rule = repository.current.single()
        viewModel.setEnabled(rule, false)
        assertFalse(repository.current.single().enabled)
        viewModel.delete(rule)
        assertTrue(repository.current.isEmpty())
    }
}
