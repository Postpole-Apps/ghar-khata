package com.postpole.gharkhata.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        ProviderEntity::class,
        RateChangeEntity::class,
        DayMarkEntity::class,
        MonthSettlementEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun providerDao(): ProviderDao
    abstract fun rateChangeDao(): RateChangeDao
    abstract fun dayMarkDao(): DayMarkDao
    abstract fun monthSettlementDao(): MonthSettlementDao
}

/** v1 -> v2: remember the day a provider was archived (shown on the Providers list). */
val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE providers ADD COLUMN archivedOnEpochDay INTEGER")
    }
}
