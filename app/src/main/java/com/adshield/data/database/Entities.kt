package com.adshield.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

const val SOURCE_CUSTOM = "custom"
const val SOURCE_BUILTIN = "builtin"

/**
 * Dominio bloqueado. Las listas descargadas y la lista negra del usuario
 * (source = "custom", category = "CUSTOM") comparten tabla.
 */
@Entity(
    tableName = "blocked_domains",
    indices = [Index(value = ["domain", "source"], unique = true), Index("source")]
)
data class BlockedDomainEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val domain: String,
    val category: String,
    val source: String,
    val enabled: Boolean = true,
    val createdAt: Long,
    val level: Int = 1
)

@Entity(tableName = "allowed_domains", indices = [Index(value = ["domain"], unique = true)])
data class AllowedDomainEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val domain: String,
    val enabled: Boolean = true,
    val createdAt: Long
)

/** Regla por aplicación: excluded = true significa que la app no pasa por la VPN. */
@Entity(tableName = "application_rules")
data class ApplicationRuleEntity(
    @PrimaryKey val packageName: String,
    val excluded: Boolean,
    val updatedAt: Long
)

/** Evento de tráfico. Solo se guarda el nombre de dominio consultado, nunca contenido. */
@Entity(tableName = "traffic_events", indices = [Index("timestamp")])
data class TrafficEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val domain: String,
    val blocked: Boolean,
    val category: String?,
    val appPackage: String?
)

@Entity(tableName = "statistics")
data class StatisticsEntity(
    @PrimaryKey val hourStart: Long,
    val allowed: Long = 0,
    val ads: Long = 0,
    val trackers: Long = 0,
    val threats: Long = 0,
    val custom: Long = 0
)

/** Estado de la última descarga de cada lista de bloqueo. */
@Entity(tableName = "blocklist_sources")
data class BlocklistSourceEntity(
    @PrimaryKey val sourceId: String,
    val lastSuccessAt: Long?,
    val lastAttemptAt: Long?,
    val domainCount: Int,
    val lastError: String?
)

/** Proyección ligera para cargar reglas en memoria. */
data class RuleRow(val id: Long, val domain: String, val category: String, val level: Int)
