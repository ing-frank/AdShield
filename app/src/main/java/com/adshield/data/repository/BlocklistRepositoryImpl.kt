package com.adshield.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.adshield.data.blocklist.BlocklistDownloader
import com.adshield.data.blocklist.BlocklistSources
import com.adshield.data.blocklist.HostsParser
import com.adshield.data.database.AppDatabase
import com.adshield.data.database.BlockedDomainEntity
import com.adshield.data.database.BlocklistSourceEntity
import com.adshield.domain.model.BlocklistSource
import com.adshield.domain.model.BlocklistSourceStatus
import com.adshield.domain.model.UpdateSummary
import com.adshield.domain.repository.BlocklistRepository
import com.adshield.domain.repository.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicBoolean

class BlocklistRepositoryImpl(
    private val context: Context,
    private val database: AppDatabase,
    private val settings: SettingsRepository,
    private val ruleLoader: RuleLoader,
    private val downloader: BlocklistDownloader = BlocklistDownloader()
) : BlocklistRepository {

    private val blockedDao = database.blockedDomainDao()
    private val sourceDao = database.blocklistSourceDao()
    private val running = AtomicBoolean(false)

    private val _updating = MutableStateFlow(false)
    override val updating: StateFlow<Boolean> = _updating.asStateFlow()

    override fun observeSources(): Flow<List<BlocklistSourceStatus>> =
        sourceDao.observeAll().map { toStatus(it) }

    override suspend fun currentSources(): List<BlocklistSourceStatus> = toStatus(sourceDao.all())

    private fun toStatus(rows: List<BlocklistSourceEntity>): List<BlocklistSourceStatus> {
        val byId = rows.associateBy { it.sourceId }
        return BlocklistSources.ALL.map { source ->
            val row = byId[source.id]
            BlocklistSourceStatus(source, row?.lastSuccessAt, row?.lastAttemptAt, row?.domainCount ?: 0, row?.lastError)
        }
    }

    /**
     * Descarga las listas que corresponden al modo actual. Si una lista falla o llega
     * corrupta se conserva la versión anterior; las demás se actualizan igualmente.
     */
    override suspend fun updateAll(): UpdateSummary = withContext(Dispatchers.IO) {
        if (!running.compareAndSet(false, true)) return@withContext UpdateSummary(0, 0, alreadyRunning = true)
        _updating.value = true
        try {
            val mode = settings.settings.first().mode
            var updated = 0
            var failed = 0
            for (source in BlocklistSources.ALL) {
                if (source.level > mode.level) continue
                val attemptAt = System.currentTimeMillis()
                try {
                    val count = downloadAndStore(source)
                    sourceDao.upsert(BlocklistSourceEntity(source.id, attemptAt, attemptAt, count, null))
                    updated++
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val previous = sourceDao.get(source.id)
                    sourceDao.upsert(
                        BlocklistSourceEntity(
                            sourceId = source.id,
                            lastSuccessAt = previous?.lastSuccessAt,
                            lastAttemptAt = attemptAt,
                            domainCount = previous?.domainCount ?: 0,
                            lastError = describe(e)
                        )
                    )
                    failed++
                }
            }
            if (updated > 0) ruleLoader.reloadBlocklists(mode)
            UpdateSummary(updated, failed)
        } finally {
            _updating.value = false
            running.set(false)
        }
    }

    private fun describe(e: Exception): String = when (e) {
        is UnknownHostException -> "Sin conexión o servidor no encontrado"
        is SocketTimeoutException -> "Tiempo de espera agotado"
        else -> e.message ?: e.javaClass.simpleName
    }

    private suspend fun downloadAndStore(source: BlocklistSource): Int {
        val file = File.createTempFile("blocklist_", ".txt", context.cacheDir)
        try {
            downloader.download(source.url, file)

            // Primera pasada: validar antes de tocar la base de datos.
            var valid = 0
            file.bufferedReader().use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    if (HostsParser.parseLine(line) != null) valid++
                }
            }
            if (valid < MIN_VALID_DOMAINS) {
                throw IOException("Lista vacía o corrupta ($valid dominios válidos)")
            }

            // Segunda pasada: reemplazar la lista anterior dentro de una transacción.
            val now = System.currentTimeMillis()
            database.withTransaction {
                blockedDao.deleteBySource(source.id)
                val batch = ArrayList<BlockedDomainEntity>(BATCH_SIZE)
                file.bufferedReader().use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        val domain = HostsParser.parseLine(line) ?: continue
                        batch.add(
                            BlockedDomainEntity(
                                domain = domain, category = source.category.name, source = source.id,
                                createdAt = now, level = source.level
                            )
                        )
                        if (batch.size >= BATCH_SIZE) {
                            blockedDao.insertAll(batch)
                            batch.clear()
                        }
                    }
                }
                if (batch.isNotEmpty()) blockedDao.insertAll(batch)
            }
            return blockedDao.countBySource(source.id)
        } finally {
            file.delete()
        }
    }

    private companion object {
        const val MIN_VALID_DOMAINS = 20
        const val BATCH_SIZE = 1000
    }
}
