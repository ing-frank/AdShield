package com.adshield.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedDomainDao {
    @Query("SELECT * FROM blocked_domains WHERE source = 'custom' ORDER BY domain")
    fun observeCustom(): Flow<List<BlockedDomainEntity>>

    @Query("SELECT domain FROM blocked_domains WHERE source = 'custom' AND enabled = 1")
    suspend fun customEnabledDomains(): List<String>

    @Query("SELECT COUNT(*) FROM blocked_domains WHERE source = 'custom' AND domain = :domain")
    suspend fun countCustom(domain: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: BlockedDomainEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entities: List<BlockedDomainEntity>)

    @Query("UPDATE OR IGNORE blocked_domains SET domain = :domain WHERE id = :id")
    suspend fun rename(id: Long, domain: String)

    @Query("UPDATE blocked_domains SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM blocked_domains WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM blocked_domains WHERE source = :source")
    suspend fun deleteBySource(source: String)

    @Query("SELECT COUNT(*) FROM blocked_domains WHERE source = :source")
    suspend fun countBySource(source: String): Int

    @Query("SELECT COUNT(*) FROM blocked_domains WHERE source != 'custom'")
    suspend fun listRuleCount(): Int

    @Query(
        "SELECT id, domain, category, level FROM blocked_domains " +
            "WHERE source != 'custom' AND enabled = 1 AND level <= :maxLevel AND id > :afterId " +
            "ORDER BY id LIMIT :limit"
    )
    suspend fun rulesPage(maxLevel: Int, afterId: Long, limit: Int): List<RuleRow>
}

@Dao
interface AllowedDomainDao {
    @Query("SELECT * FROM allowed_domains ORDER BY domain")
    fun observeAll(): Flow<List<AllowedDomainEntity>>

    @Query("SELECT domain FROM allowed_domains WHERE enabled = 1")
    suspend fun enabledDomains(): List<String>

    @Query("SELECT COUNT(*) FROM allowed_domains WHERE domain = :domain")
    suspend fun count(domain: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: AllowedDomainEntity): Long

    @Query("UPDATE OR IGNORE allowed_domains SET domain = :domain WHERE id = :id")
    suspend fun rename(id: Long, domain: String)

    @Query("UPDATE allowed_domains SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM allowed_domains WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface ApplicationRuleDao {
    @Query("SELECT packageName FROM application_rules WHERE excluded = 1")
    fun observeExcluded(): Flow<List<String>>

    @Query("SELECT packageName FROM application_rules WHERE excluded = 1")
    suspend fun excludedPackages(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ApplicationRuleEntity)
}

@Dao
interface TrafficEventDao {
    @Insert
    suspend fun insertAll(events: List<TrafficEventEntity>)

    @Query("SELECT * FROM traffic_events ORDER BY id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<TrafficEventEntity>>

    /** Conserva solo los [keep] eventos más recientes. */
    @Query("DELETE FROM traffic_events WHERE id NOT IN (SELECT id FROM traffic_events ORDER BY id DESC LIMIT :keep)")
    suspend fun prune(keep: Int)

    @Query("DELETE FROM traffic_events")
    suspend fun clear()
}

@Dao
abstract class StatisticsDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertIfMissing(entity: StatisticsEntity): Long

    @Query(
        "UPDATE statistics SET allowed = allowed + :allowed, ads = ads + :ads, " +
            "trackers = trackers + :trackers, threats = threats + :threats, custom = custom + :custom " +
            "WHERE hourStart = :hourStart"
    )
    abstract suspend fun increment(hourStart: Long, allowed: Long, ads: Long, trackers: Long, threats: Long, custom: Long)

    /** Suma contadores a la hora indicada, creando la fila si no existe. */
    @Transaction
    open suspend fun add(hourStart: Long, allowed: Long, ads: Long, trackers: Long, threats: Long, custom: Long) {
        insertIfMissing(StatisticsEntity(hourStart = hourStart))
        increment(hourStart, allowed, ads, trackers, threats, custom)
    }

    @Query("SELECT * FROM statistics WHERE hourStart >= :from ORDER BY hourStart")
    abstract fun observeSince(from: Long): Flow<List<StatisticsEntity>>

    @Query("SELECT COUNT(*) FROM statistics")
    abstract suspend fun count(): Int

    @Query("DELETE FROM statistics WHERE hourStart < :before")
    abstract suspend fun deleteOlderThan(before: Long)

    @Query("DELETE FROM statistics")
    abstract suspend fun clear()
}

@Dao
interface BlocklistSourceDao {
    @Query("SELECT * FROM blocklist_sources")
    fun observeAll(): Flow<List<BlocklistSourceEntity>>

    @Query("SELECT * FROM blocklist_sources")
    suspend fun all(): List<BlocklistSourceEntity>

    @Query("SELECT * FROM blocklist_sources WHERE sourceId = :id")
    suspend fun get(id: String): BlocklistSourceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: BlocklistSourceEntity)
}
