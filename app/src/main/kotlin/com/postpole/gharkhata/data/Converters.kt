package com.postpole.gharkhata.data

import androidx.room.TypeConverter

/** Enums are stored as their names. */
class Converters {
    @TypeConverter fun providerTypeToString(v: ProviderType): String = v.name
    @TypeConverter fun stringToProviderType(v: String): ProviderType = ProviderType.valueOf(v)

    @TypeConverter fun billingModeToString(v: BillingMode): String = v.name
    @TypeConverter fun stringToBillingMode(v: String): BillingMode = BillingMode.valueOf(v)

    @TypeConverter fun dayStatusToString(v: DayStatus): String = v.name
    @TypeConverter fun stringToDayStatus(v: String): DayStatus = DayStatus.valueOf(v)
}
