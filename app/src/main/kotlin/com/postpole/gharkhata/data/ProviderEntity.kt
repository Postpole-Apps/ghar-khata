package com.postpole.gharkhata.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class ProviderType { MILK, MAID, COOK, NEWSPAPER, WATER_CAN, DRIVER, OTHER }

enum class BillingMode { PER_UNIT, PER_VISIT, MONTHLY_FIXED }

@Entity(tableName = "providers", indices = [Index("archived")])
data class ProviderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: ProviderType,
    val billingMode: BillingMode,
    /** e.g. "L", "copy", "can", "visit". */
    val unitLabel: String,
    val defaultQty: Double = 1.0,
    /** bit0 = Mon … bit6 = Sun; 0 = on-demand only. */
    val scheduleMask: Int = 0,
    val paidLeavesPerMonth: Int = 0,
    val startEpochDay: Long,
    val archived: Boolean = false,
    /** Local epoch day the provider was archived; null when active or archived before this was recorded. */
    val archivedOnEpochDay: Long? = null,
    val phone: String? = null,
    val note: String? = null,
    val sortOrder: Int = 0,
    /** Epoch millis. */
    val createdAt: Long,
)
