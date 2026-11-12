package com.postpole.gharkhata.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * EXTRA = delivery on an unscheduled day (counts like present, with qty).
 * PRESENT with qty != default = changed quantity on a scheduled day.
 */
enum class DayStatus { PRESENT, ABSENT, HALF, EXTRA }

/** Stores only exceptions/overrides; a day without a row follows the provider's schedule. */
@Entity(
    tableName = "day_marks",
    primaryKeys = ["providerId", "epochDay"],
    foreignKeys = [
        ForeignKey(
            entity = ProviderEntity::class,
            parentColumns = ["id"],
            childColumns = ["providerId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("epochDay")],
)
data class DayMarkEntity(
    val providerId: Long,
    val epochDay: Long,
    val status: DayStatus,
    /** null = provider default. */
    val qty: Double? = null,
    val note: String? = null,
    val updatedAt: Long,
)
