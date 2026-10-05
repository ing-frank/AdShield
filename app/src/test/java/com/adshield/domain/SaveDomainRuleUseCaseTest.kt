package com.adshield.domain

import com.adshield.domain.usecase.SaveDomainResult
import com.adshield.domain.usecase.SaveDomainRuleUseCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveDomainRuleUseCaseTest {

    private val repository = FakeDomainRuleRepository()
    private val save = SaveDomainRuleUseCase(repository)

    @Test
    fun savesNormalizedDomain() = runBlocking {
        val result = save("  https://Ads.Example.com/banner ")
        assertEquals(SaveDomainResult.Saved("ads.example.com"), result)
        assertEquals(listOf("ads.example.com"), repository.current.map { it.domain })
    }

    @Test
    fun rejectsInvalidDomain() = runBlocking {
        assertEquals(SaveDomainResult.Invalid, save("no es un dominio"))
        assertEquals(SaveDomainResult.Invalid, save("192.168.0.1"))
        assertTrue(repository.current.isEmpty())
    }

    @Test
    fun rejectsDuplicate() = runBlocking {
        save("example.com")
        assertEquals(SaveDomainResult.Duplicate, save("EXAMPLE.com"))
        assertEquals(1, repository.current.size)
    }

    @Test
    fun editingRenamesTheSameRow() = runBlocking {
        save("old.example.com")
        val rule = repository.current.single()
        assertEquals(SaveDomainResult.Saved("new.example.com"), save("new.example.com", rule))
        assertEquals(listOf("new.example.com"), repository.current.map { it.domain })
        assertEquals(rule.id, repository.current.single().id)
    }

    @Test
    fun editingWithoutChangesIsAccepted() = runBlocking {
        save("example.com")
        val rule = repository.current.single()
        assertEquals(SaveDomainResult.Saved("example.com"), save("example.com", rule))
    }

    @Test
    fun editingToAnExistingDomainIsDuplicate() = runBlocking {
        save("a.example.com")
        save("b.example.com")
        val second = repository.current.last()
        assertEquals(SaveDomainResult.Duplicate, save("a.example.com", second))
    }
}
