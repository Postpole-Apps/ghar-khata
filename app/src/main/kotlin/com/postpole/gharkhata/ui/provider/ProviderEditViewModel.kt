package com.postpole.gharkhata.ui.provider

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.postpole.gharkhata.analytics.Analytics
import com.postpole.gharkhata.analytics.AnalyticsEvents
import com.postpole.gharkhata.analytics.AnalyticsParams
import com.postpole.gharkhata.data.BillingMode
import com.postpole.gharkhata.data.ProviderEntity
import com.postpole.gharkhata.data.ProviderRepository
import com.postpole.gharkhata.data.ProviderType
import com.postpole.gharkhata.data.RateChangeEntity
import com.postpole.gharkhata.domain.Money
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

const val NEW_PROVIDER_ID = 0L

/** Text-field backed form state; numbers stay strings until [ProviderEditViewModel.save] validates them. */
data class ProviderForm(
    val type: ProviderType = ProviderType.MILK,
    val name: String = "",
    val billingMode: BillingMode = BillingMode.PER_UNIT,
    val unitLabel: String = "L",
    val defaultQty: String = "1",
    /** Rupees: rate per unit/visit, or the monthly amount for MONTHLY_FIXED. */
    val rate: String = "",
    val scheduleMask: Int = 0,
    val paidLeaves: String = "0",
    val startEpochDay: Long = 0,
    val phone: String = "",
    val note: String = "",
)

enum class FormError { NAME_REQUIRED, RATE_INVALID, QTY_INVALID, LEAVES_INVALID }

sealed interface SaveResult {
    data object Saved : SaveResult
    data class Invalid(val errors: Set<FormError>) : SaveResult

    /** Free user already has [FREE_ACTIVE_PROVIDER_LIMIT] active providers; show the Pro sheet. */
    data object LimitReached : SaveResult

    /** Rate on an existing provider changed; ask for an "Effective from" date and call save again. */
    data object NeedsEffectiveFrom : SaveResult
}

@HiltViewModel
class ProviderEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ProviderRepository,
    private val proStatus: ProStatus,
    private val analytics: Analytics,
) : ViewModel() {

    val providerId: Long = savedStateHandle.get<Long>("id") ?: NEW_PROVIDER_ID
    val isNew: Boolean get() = providerId == NEW_PROVIDER_ID

    private val _form = MutableStateFlow(
        ProviderForm(startEpochDay = LocalDate.now().toEpochDay()).applyPreset(ProviderType.MILK),
    )
    val form: StateFlow<ProviderForm> = _form.asStateFlow()

    private val _errors = MutableStateFlow<Set<FormError>>(emptySet())
    val errors: StateFlow<Set<FormError>> = _errors.asStateFlow()

    private val _archived = MutableStateFlow(false)
    val archived: StateFlow<Boolean> = _archived.asStateFlow()

    private val _loaded = MutableStateFlow(isNew)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private var existing: ProviderEntity? = null
    private var originalRatePaise: Long? = null

    /** The rate (or monthly amount) stored for this provider before any unsaved edit; null for a new provider. */
    val currentRatePaise: Long? get() = originalRatePaise

    /** 1st of the current month: the default for "Effective from". */
    val defaultEffectiveFrom: Long get() = LocalDate.now().withDayOfMonth(1).toEpochDay()

    init {
        if (!isNew) {
            viewModelScope.launch {
                val p = repository.getProvider(providerId)
                if (p != null) {
                    existing = p
                    originalRatePaise = repository.observeRates(providerId).first().firstOrNull()?.amountPaise
                    _archived.value = p.archived
                    _form.value = ProviderForm(
                        type = p.type,
                        name = p.name,
                        billingMode = p.billingMode,
                        unitLabel = p.unitLabel,
                        defaultQty = formatQty(p.defaultQty),
                        rate = originalRatePaise?.let(::paiseToInput).orEmpty(),
                        scheduleMask = p.scheduleMask,
                        paidLeaves = p.paidLeavesPerMonth.toString(),
                        startEpochDay = p.startEpochDay,
                        phone = p.phone.orEmpty(),
                        note = p.note.orEmpty(),
                    )
                }
                _loaded.value = true
            }
        }
    }

    /** Selecting a type fills that type's defaults; the user can still edit every field. */
    fun selectType(type: ProviderType) = _form.update { it.applyPreset(type) }

    fun update(transform: (ProviderForm) -> ProviderForm) {
        _form.update(transform)
        if (_errors.value.isNotEmpty()) _errors.value = validate(_form.value)
    }

    fun toggleDay(bit: Int) = update { it.copy(scheduleMask = it.scheduleMask xor bit) }

    private fun validate(f: ProviderForm): Set<FormError> = buildSet {
        if (f.name.isBlank()) add(FormError.NAME_REQUIRED)
        val paise = Money.parse(f.rate)
        if (paise == null || paise <= 0) add(FormError.RATE_INVALID)
        if (f.billingMode != BillingMode.MONTHLY_FIXED) {
            val qty = f.defaultQty.trim().toDoubleOrNull()
            if (qty == null || qty <= 0.0) add(FormError.QTY_INVALID)
        }
        val leaves = f.paidLeaves.trim().ifEmpty { "0" }.toIntOrNull()
        if (leaves == null || leaves < 0) add(FormError.LEAVES_INVALID)
    }

    /**
     * Validates and stores the provider. For an existing provider whose rate changed, [effectiveFrom]
     * (epoch day) is required; without it [SaveResult.NeedsEffectiveFrom] is returned and nothing is written.
     */
    suspend fun save(effectiveFrom: Long? = null): SaveResult {
        val f = _form.value
        val errors = validate(f)
        _errors.value = errors
        if (errors.isNotEmpty()) return SaveResult.Invalid(errors)
        val paise = Money.parse(f.rate)!!
        val qty = f.defaultQty.trim().toDoubleOrNull() ?: 1.0
        val leaves = f.paidLeaves.trim().ifEmpty { "0" }.toInt()
        val prev = existing

        if (prev == null) {
            if (!canAddActiveProvider(repository.countActive().first(), proStatus.isPro.value)) {
                analytics.logEvent(AnalyticsEvents.PROVIDER_LIMIT_REACHED)
                return SaveResult.LimitReached
            }
            val id = repository.saveProvider(
                ProviderEntity(
                    name = f.name.trim(),
                    type = f.type,
                    billingMode = f.billingMode,
                    unitLabel = f.unitLabel.trim(),
                    defaultQty = qty,
                    scheduleMask = f.scheduleMask,
                    paidLeavesPerMonth = leaves,
                    startEpochDay = f.startEpochDay,
                    phone = f.phone.trim().ifEmpty { null },
                    note = f.note.trim().ifEmpty { null },
                    createdAt = System.currentTimeMillis(),
                ),
            )
            repository.addRate(RateChangeEntity(providerId = id, effectiveFromEpochDay = f.startEpochDay, amountPaise = paise))
            analytics.logEvent(AnalyticsEvents.PROVIDER_ADDED, mapOf(AnalyticsParams.PROVIDER_TYPE to f.type.name))
            return SaveResult.Saved
        }

        val rateChanged = paise != originalRatePaise
        if (rateChanged && effectiveFrom == null) return SaveResult.NeedsEffectiveFrom
        repository.saveProvider(
            prev.copy(
                name = f.name.trim(),
                type = f.type,
                billingMode = f.billingMode,
                unitLabel = f.unitLabel.trim(),
                defaultQty = qty,
                scheduleMask = f.scheduleMask,
                paidLeavesPerMonth = leaves,
                startEpochDay = f.startEpochDay,
                phone = f.phone.trim().ifEmpty { null },
                note = f.note.trim().ifEmpty { null },
            ),
        )
        if (rateChanged && effectiveFrom != null) {
            repository.addRate(RateChangeEntity(providerId = prev.id, effectiveFromEpochDay = effectiveFrom, amountPaise = paise))
            originalRatePaise = paise
        }
        return SaveResult.Saved
    }

    /** Hides the provider from the active list; all its data stays for old months. Frees a free-tier slot. */
    suspend fun archive() {
        val p = existing ?: return
        repository.setArchived(p.id, true)
        _archived.value = true
    }

    /** Permanently deletes the provider and (by cascade) its rates, marks and settlements. */
    suspend fun delete() {
        val p = existing ?: return
        repository.deleteProvider(p)
    }

    private fun ProviderForm.applyPreset(type: ProviderType): ProviderForm {
        val p = ProviderPresets.forType(type)
        return copy(
            type = type,
            billingMode = p.billingMode,
            unitLabel = p.unitLabel,
            defaultQty = formatQty(p.defaultQty),
            scheduleMask = p.scheduleMask,
            paidLeaves = p.paidLeavesPerMonth.toString(),
        )
    }

    private fun formatQty(q: Double): String = if (q % 1.0 == 0.0) q.toLong().toString() else q.toString()

    private fun paiseToInput(p: Long): String =
        if (p % 100 == 0L) (p / 100).toString() else "${p / 100}.${(p % 100).toString().padStart(2, '0')}"
}
