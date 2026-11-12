package com.postpole.gharkhata.data

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** The backup file is not valid, or was written by a newer app version. */
class InvalidBackupException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Everything a backup file contains. */
internal data class BackupData(
    val providers: List<ProviderEntity>,
    val rateChanges: List<RateChangeEntity>,
    val dayMarks: List<DayMarkEntity>,
    val settlements: List<MonthSettlementEntity>,
) {
    val rowCount: Int get() = providers.size + rateChanges.size + dayMarks.size + settlements.size
}

/**
 * Versioned JSON backup of all Room data. Works on plain streams; the UI supplies them from the
 * Storage Access Framework, so no storage permission is needed.
 *
 * Format v2: `{"format":"postpole-backup","version":2,"exportedAt":<ms>,"providers":[…],
 * "rate_changes":[…],"day_marks":[…],"month_settlements":[…]}`; each row object uses the entity's
 * field names, enums as their names. Add new tables as new top-level arrays and bump
 * [FORMAT_VERSION]; keep reading older versions once the app is released.
 */
@Singleton
class BackupRepository @Inject constructor(
    private val db: AppDatabase,
) {
    /** Writes the backup and returns the number of providers exported. */
    suspend fun exportJson(out: OutputStream, now: Long = System.currentTimeMillis()): Int {
        val data = db.withTransaction {
            BackupData(
                providers = db.providerDao().getAll(),
                rateChanges = db.rateChangeDao().getAll(),
                dayMarks = db.dayMarkDao().getAll(),
                settlements = db.monthSettlementDao().getAll(),
            )
        }
        val json = JSONObject()
            .put("format", FORMAT_NAME)
            .put("version", FORMAT_VERSION)
            .put("exportedAt", now)
            .put("providers", JSONArray().apply { data.providers.forEach { put(it.toJson()) } })
            .put("rate_changes", JSONArray().apply { data.rateChanges.forEach { put(it.toJson()) } })
            .put("day_marks", JSONArray().apply { data.dayMarks.forEach { put(it.toJson()) } })
            .put("month_settlements", JSONArray().apply { data.settlements.forEach { put(it.toJson()) } })
        out.write(json.toString(2).toByteArray(Charsets.UTF_8))
        out.flush()
        return data.providers.size
    }

    /**
     * Replaces all data with the backup's contents, atomically. The file is fully parsed and
     * validated first, so an invalid file leaves existing data untouched. Returns the provider count.
     */
    suspend fun importJson(input: InputStream): Int {
        val data = parse(input.readBytes().toString(Charsets.UTF_8))
        db.withTransaction {
            // Deleting providers cascades to rates, marks and settlements.
            db.providerDao().deleteAll()
            db.providerDao().insertAll(data.providers)
            db.rateChangeDao().insertAll(data.rateChanges)
            db.dayMarkDao().insertAll(data.dayMarks)
            db.monthSettlementDao().insertAll(data.settlements)
        }
        return data.providers.size
    }

    /** Placeholder: writes a header-only attendance CSV. G7 replaces it with real attendance/bills. */
    suspend fun exportItemsCsv(out: OutputStream): Int {
        out.write("provider,date,status,qty\r\n".toByteArray(Charsets.UTF_8))
        out.flush()
        return 0
    }

    internal fun parse(text: String): BackupData {
        try {
            val root = JSONObject(text)
            if (root.optString("format") != FORMAT_NAME) throw InvalidBackupException("Not a backup file")
            val version = root.getInt("version")
            if (version != FORMAT_VERSION) throw InvalidBackupException("Unsupported backup version $version")

            val providers = root.getJSONArray("providers").map { it.toProvider() }
            val providerIds = HashSet<Long>()
            providers.forEach { if (!providerIds.add(it.id)) throw InvalidBackupException("Duplicate provider id ${it.id}") }

            val rates = root.getJSONArray("rate_changes").map { it.toRate() }
            val rateIds = HashSet<Long>()
            val rateKeys = HashSet<Pair<Long, Long>>()
            rates.forEach {
                if (!rateIds.add(it.id)) throw InvalidBackupException("Duplicate rate id ${it.id}")
                if (!rateKeys.add(it.providerId to it.effectiveFromEpochDay)) {
                    throw InvalidBackupException("Duplicate rate for provider ${it.providerId}")
                }
                if (it.providerId !in providerIds) throw InvalidBackupException("Rate for unknown provider ${it.providerId}")
            }

            val marks = root.getJSONArray("day_marks").map { it.toMark() }
            val markKeys = HashSet<Pair<Long, Long>>()
            marks.forEach {
                if (!markKeys.add(it.providerId to it.epochDay)) throw InvalidBackupException("Duplicate day mark")
                if (it.providerId !in providerIds) throw InvalidBackupException("Mark for unknown provider ${it.providerId}")
            }

            val settlements = root.getJSONArray("month_settlements").map { it.toSettlement() }
            val settlementKeys = HashSet<Pair<Long, Int>>()
            settlements.forEach {
                if (!settlementKeys.add(it.providerId to it.yearMonth)) throw InvalidBackupException("Duplicate settlement")
                if (it.providerId !in providerIds) {
                    throw InvalidBackupException("Settlement for unknown provider ${it.providerId}")
                }
            }
            return BackupData(providers, rates, marks, settlements)
        } catch (e: JSONException) {
            throw InvalidBackupException("Malformed backup file", e)
        } catch (e: IllegalArgumentException) {
            // Unknown enum value.
            throw InvalidBackupException("Malformed backup file", e)
        }
    }

    private inline fun <T> JSONArray.map(transform: (JSONObject) -> T): List<T> =
        List(length()) { transform(getJSONObject(it)) }

    private fun JSONObject.optNullableString(name: String): String? = if (isNull(name)) null else getString(name)

    private fun ProviderEntity.toJson() = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("type", type.name)
        .put("billingMode", billingMode.name)
        .put("unitLabel", unitLabel)
        .put("defaultQty", defaultQty)
        .put("scheduleMask", scheduleMask)
        .put("paidLeavesPerMonth", paidLeavesPerMonth)
        .put("startEpochDay", startEpochDay)
        .put("archived", archived)
        .put("archivedOnEpochDay", archivedOnEpochDay ?: JSONObject.NULL)
        .put("phone", phone ?: JSONObject.NULL)
        .put("note", note ?: JSONObject.NULL)
        .put("sortOrder", sortOrder)
        .put("createdAt", createdAt)

    private fun JSONObject.toProvider() = ProviderEntity(
        id = getLong("id"),
        name = getString("name"),
        type = ProviderType.valueOf(getString("type")),
        billingMode = BillingMode.valueOf(getString("billingMode")),
        unitLabel = getString("unitLabel"),
        defaultQty = getDouble("defaultQty"),
        scheduleMask = getInt("scheduleMask"),
        paidLeavesPerMonth = getInt("paidLeavesPerMonth"),
        startEpochDay = getLong("startEpochDay"),
        archived = getBoolean("archived"),
        archivedOnEpochDay = if (has("archivedOnEpochDay") && !isNull("archivedOnEpochDay")) getLong("archivedOnEpochDay") else null,
        phone = optNullableString("phone"),
        note = optNullableString("note"),
        sortOrder = getInt("sortOrder"),
        createdAt = getLong("createdAt"),
    )

    private fun RateChangeEntity.toJson() = JSONObject()
        .put("id", id)
        .put("providerId", providerId)
        .put("effectiveFromEpochDay", effectiveFromEpochDay)
        .put("amountPaise", amountPaise)

    private fun JSONObject.toRate() = RateChangeEntity(
        id = getLong("id"),
        providerId = getLong("providerId"),
        effectiveFromEpochDay = getLong("effectiveFromEpochDay"),
        amountPaise = getLong("amountPaise"),
    )

    private fun DayMarkEntity.toJson() = JSONObject()
        .put("providerId", providerId)
        .put("epochDay", epochDay)
        .put("status", status.name)
        .put("qty", qty ?: JSONObject.NULL)
        .put("note", note ?: JSONObject.NULL)
        .put("updatedAt", updatedAt)

    private fun JSONObject.toMark() = DayMarkEntity(
        providerId = getLong("providerId"),
        epochDay = getLong("epochDay"),
        status = DayStatus.valueOf(getString("status")),
        qty = if (isNull("qty")) null else getDouble("qty"),
        note = optNullableString("note"),
        updatedAt = getLong("updatedAt"),
    )

    private fun MonthSettlementEntity.toJson() = JSONObject()
        .put("providerId", providerId)
        .put("yearMonth", yearMonth)
        .put("billedPaise", billedPaise)
        .put("paidPaise", paidPaise)
        .put("paidOnEpochDay", paidOnEpochDay)
        .put("note", note ?: JSONObject.NULL)

    private fun JSONObject.toSettlement() = MonthSettlementEntity(
        providerId = getLong("providerId"),
        yearMonth = getInt("yearMonth"),
        billedPaise = getLong("billedPaise"),
        paidPaise = getLong("paidPaise"),
        paidOnEpochDay = getLong("paidOnEpochDay"),
        note = optNullableString("note"),
    )

    companion object {
        const val FORMAT_NAME = "postpole-backup"
        const val FORMAT_VERSION = 2
    }
}
