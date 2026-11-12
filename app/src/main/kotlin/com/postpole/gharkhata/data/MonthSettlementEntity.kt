package com.postpole.gharkhata.data

import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "month_settlements",
    primaryKeys = ["providerId", "yearMonth"],
    foreignKeys = [
        ForeignKey(
            entity = ProviderEntity::class,
            parentColumns = ["id"],
            childColumns = ["providerId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class MonthSettlementEntity(
    val providerId: Long,
    /** yyyymm, e.g. 202611. */
    val yearMonth: Int,
    /** Snapshot of the bill at settle time. */
    val billedPaise: Long,
    val paidPaise: Long,
    val paidOnEpochDay: Long,
    val note: String? = null,
)
