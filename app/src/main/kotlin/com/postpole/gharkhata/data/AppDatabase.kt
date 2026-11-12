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
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun providerDao(): ProviderDao
    abstract fun rateChangeDao(): RateChangeDao
    abstract fun dayMarkDao(): DayMarkDao
    abstract fun monthSettlementDao(): MonthSettlementDao
}
