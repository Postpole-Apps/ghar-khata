package com.postpole.gharkhata.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Unit rate (PER_UNIT / PER_VISIT) or monthly salary (MONTHLY_FIXED), in paise, from a date on. */
@Entity(
    tableName = "rate_changes",
    foreignKeys = [
        ForeignKey(
            entity = ProviderEntity::class,
            parentColumns = ["id"],
            childColumns = ["providerId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["providerId", "effectiveFromEpochDay"], unique = true)],
)
data class RateChangeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val providerId: Long,
    val effectiveFromEpochDay: Long,
    val amountPaise: Long,
)
