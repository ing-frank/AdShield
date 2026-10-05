package com.adshield.domain

import com.adshield.domain.model.BlockCategory
import com.adshield.domain.model.BlocklistSource
import com.adshield.domain.model.BlocklistSourceStatus
import com.adshield.domain.model.DiagnosticItem
import com.adshield.domain.model.DiagnosticStatus
import com.adshield.domain.model.ProtectionState
import com.adshield.domain.model.RuleCounts
import com.adshield.domain.repository.SystemStatusProvider
import com.adshield.domain.usecase.RunDiagnosticsUseCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RunDiagnosticsUseCaseTest {

    private class FakeSystem(
        var internet: Boolean = true,
        var permission: Boolean = true,
        var privateDns: String? = null,
        var service: Boolean = true,
        var database: Boolean = true
    ) : SystemStatusProvider {
        override fun internetAvailable() = internet
        override fun vpnPermissionGranted() = permission
        override fun strictPrivateDnsHost() = privateDns
        override fun serviceRunning() = service
        override suspend fun databaseWorking() = database
    }

    private val now = 1_000_000_000_000L
    private val source = BlocklistSource("s", "Lista", "https://example.com/l.txt", BlockCategory.ADVERTISING, 1, "MIT")

    private fun run(
        system: FakeSystem = FakeSystem(),
        state: ProtectionState = ProtectionState.ACTIVE,
        error: String? = null,
        counts: RuleCounts = RuleCounts(lists = 1000, loaded = true),
        sources: List<BlocklistSourceStatus> = listOf(BlocklistSourceStatus(source, now, now, 1000, null))
    ): Map<String, DiagnosticItem> = runBlocking {
        RunDiagnosticsUseCase(system, { state }, { error }, { counts }, { sources })(now).associateBy { it.title }
    }

    @Test
    fun everythingOkWhenHealthy() {
        val items = run()
        assertTrue(items.values.all { it.status == DiagnosticStatus.OK })
        assertNull(items["DNS privado de Android"])
        assertEquals(6, items.size)
    }

    @Test
    fun disabledProtectionIsAWarningNotAnError() {
        val items = run(system = FakeSystem(service = false), state = ProtectionState.DISABLED)
        assertEquals(DiagnosticStatus.WARNING, items.getValue("VPN").status)
        assertEquals(DiagnosticStatus.WARNING, items.getValue("Servicio").status)
    }

    @Test
    fun missingPermissionIsReported() {
        val items = run(system = FakeSystem(permission = false, service = false), state = ProtectionState.DISABLED)
        assertEquals("Permiso de VPN no concedido", items.getValue("VPN").detail)
    }

    @Test
    fun vpnErrorShowsTheRealMessage() {
        val items = run(state = ProtectionState.ERROR, error = "fallo de prueba")
        assertEquals(DiagnosticStatus.ERROR, items.getValue("VPN").status)
        assertEquals("fallo de prueba", items.getValue("VPN").detail)
    }

    @Test
    fun noInternetAndBrokenDatabaseAreErrors() {
        val items = run(system = FakeSystem(internet = false, database = false))
        assertEquals(DiagnosticStatus.ERROR, items.getValue("Internet").status)
        assertEquals(DiagnosticStatus.ERROR, items.getValue("Base de datos").status)
    }

    @Test
    fun listStatesAreDistinguished() {
        val never = listOf(BlocklistSourceStatus(source, null, null, 0, null))
        assertEquals(DiagnosticStatus.WARNING, run(sources = never).getValue("Lista de bloqueo").status)

        val stale = listOf(BlocklistSourceStatus(source, now - 15L * 24 * 3_600_000, now, 1000, null))
        assertEquals(DiagnosticStatus.WARNING, run(sources = stale).getValue("Lista de bloqueo").status)

        val empty = run(counts = RuleCounts(lists = 0, loaded = true))
        assertEquals(DiagnosticStatus.ERROR, empty.getValue("Lista de bloqueo").status)

        val notLoaded = run(counts = RuleCounts())
        assertEquals(DiagnosticStatus.ERROR, notLoaded.getValue("Motor de filtrado").status)
    }

    @Test
    fun activeStateWithoutServiceIsAnError() {
        val items = run(system = FakeSystem(service = false))
        assertEquals(DiagnosticStatus.ERROR, items.getValue("Servicio").status)
    }

    @Test
    fun strictPrivateDnsAddsAWarning() {
        val items = run(system = FakeSystem(privateDns = "dns.example"))
        assertEquals(DiagnosticStatus.WARNING, items.getValue("DNS privado de Android").status)
    }
}
