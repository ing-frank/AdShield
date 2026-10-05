package com.adshield.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        BlockedDomainEntity::class,
        AllowedDomainEntity::class,
        ApplicationRuleEntity::class,
        TrafficEventEntity::class,
        StatisticsEntity::class,
        BlocklistSourceEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun blockedDomainDao(): BlockedDomainDao
    abstract fun allowedDomainDao(): AllowedDomainDao
    abstract fun applicationRuleDao(): ApplicationRuleDao
    abstract fun trafficEventDao(): TrafficEventDao
    abstract fun statisticsDao(): StatisticsDao
    abstract fun blocklistSourceDao(): BlocklistSourceDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "adshield.db")
                // Si el archivo está dañado, SQLite lo descarta y Room crea uno nuevo.
                // Si en el futuro cambia el esquema sin migración, se recrea en vez de cerrar la app.
                .fallbackToDestructiveMigration()
                .build()
    }
}
