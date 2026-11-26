package com.postpole.gharkhata.ui.provider

import androidx.lifecycle.SavedStateHandle
import com.postpole.gharkhata.analytics.AnalyticsEvents
import com.postpole.gharkhata.data.BillingMode
import com.postpole.gharkhata.data.ProviderType
import com.postpole.gharkhata.domain.Schedule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProviderEditViewModelTest {

    private val db = TestDb()
    private val analytics = RecordingAnalytics()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm(id: Long = NEW_PROVIDER_ID, pro: FakeProStatus = FakeProStatus()) =
        ProviderEditViewModel(SavedStateHandle(mapOf("id" to id)), db.repository, pro, analytics)

    private fun ProviderEditViewModel.fill(name: String, rate: String = "60", type: ProviderType = ProviderType.MILK) {
        selectType(type)
        update { it.copy(name = name, rate = rate, startEpochDay = START) }
    }

    private suspend fun addActive(pro: FakeProStatus, name: String): SaveResult {
        val v = vm(pro = pro)
        v.fill(name)
        return v.save()
    }

    @Test
    fun newProvider_getsNextSortOrder() = runTest {
        val pro = FakeProStatus(true)
        listOf("A", "B", "C").forEach { addActive(pro, it) }
        db.providers.reorder(db.providers.all.value.sortedBy { it.name }.map { it.id })
        assertEquals(listOf(0, 1, 2), db.providers.all.value.sortedBy { it.name }.map { it.sortOrder })

        addActive(pro, "D")

        assertEquals(3, db.providers.all.value.first { it.name == "D" }.sortOrder)
    }

    @Test
    fun editProvider_keepsSortOrder() = runTest {
        val pro = FakeProStatus(true)
        addActive(pro, "A")
        addActive(pro, "B")
        val b = db.providers.all.value.first { it.name == "B" }
        assertEquals(1, b.sortOrder)

        val v = vm(id = b.id)
        v.update { it.copy(name = "B2") }
        v.save()

        assertEquals(1, db.providers.getById(b.id)!!.sortOrder)
    }

    // ---- presets ----

    @Test
    fun preset_milk() = assertPreset(ProviderType.MILK, BillingMode.PER_UNIT, "L", "1", Schedule.ALL_DAYS, "0")

    @Test
    fun preset_maid() = assertPreset(
        ProviderType.MAID, BillingMode.MONTHLY_FIXED, "day", "1",
        Schedule.MON or Schedule.TUE or Schedule.WED or Schedule.THU or Schedule.FRI or Schedule.SAT, "2",
    )

    @Test
    fun preset_newspaper() = assertPreset(ProviderType.NEWSPAPER, BillingMode.PER_UNIT, "copy", "1", Schedule.ALL_DAYS, "0")

    @Test
    fun preset_waterCan_isOnDemand() = assertPreset(ProviderType.WATER_CAN, BillingMode.PER_UNIT, "can", "1", 0, "0")

    @Test
    fun preset_cook() = assertPreset(ProviderType.COOK, BillingMode.PER_VISIT, "visit", "1", Schedule.ALL_DAYS, "0")

    @Test
    fun preset_driver() = assertPreset(ProviderType.DRIVER, BillingMode.PER_VISIT, "day", "1", Schedule.ALL_DAYS, "0")

    @Test
    fun preset_other_isOnDemand() = assertPreset(ProviderType.OTHER, BillingMode.PER_VISIT, "visit", "1", 0, "0")

    private fun assertPreset(type: ProviderType, mode: BillingMode, unit: String, qty: String, mask: Int, leaves: String) {
        val v = vm()
        v.selectType(type)
        val f = v.form.value
        assertEquals(type, f.type)
        assertEquals(mode, f.billingMode)
        assertEquals(unit, f.unitLabel)
        assertEquals(qty, f.defaultQty)
        assertEquals(mask, f.scheduleMask)
        assertEquals(leaves, f.paidLeaves)
    }

    @Test
    fun preset_staysEditable() {
        val v = vm()
        v.selectType(ProviderType.MILK)
        v.update { it.copy(unitLabel = "ml", scheduleMask = Schedule.MON) }
        assertEquals("ml", v.form.value.unitLabel)
        assertEquals(Schedule.MON, v.form.value.scheduleMask)
    }

    // ---- validation ----

    @Test
    fun validation_blocksEmptyName() = runTest {
        val v = vm()
        v.fill("  ")
        val r = v.save()
        assertEquals(SaveResult.Invalid(setOf(FormError.NAME_REQUIRED)), r)
        assertTrue(db.providers.all.value.isEmpty())
    }

    @Test
    fun validation_blocksZeroNegativeAndBlankRate() = runTest {
        for (bad in listOf("", "0", "0.00", "-5", "abc")) {
            val v = vm()
            v.fill("Raju", rate = bad)
            assertEquals("rate '$bad'", SaveResult.Invalid(setOf(FormError.RATE_INVALID)), v.save())
        }
        assertTrue(db.providers.all.value.isEmpty())
    }

    @Test
    fun validation_monthlyAmountMustBePositive() = runTest {
        val v = vm()
        v.fill("Sunita", rate = "0", type = ProviderType.MAID)
        assertEquals(SaveResult.Invalid(setOf(FormError.RATE_INVALID)), v.save())
    }

    // ---- create + rates ----

    @Test
    fun create_savesProviderAndFirstRateAtStartDate() = runTest {
        val v = vm()
        v.fill("Raju", rate = "60.50")
        assertEquals(SaveResult.Saved, v.save())
        val p = db.providers.all.value.single()
        assertEquals("Raju", p.name)
        assertEquals(ProviderType.MILK, p.type)
        val rate = db.rates.all.value.single()
        assertEquals(p.id, rate.providerId)
        assertEquals(START, rate.effectiveFromEpochDay)
        assertEquals(6050L, rate.amountPaise)
        assertTrue(AnalyticsEvents.PROVIDER_ADDED in analytics.events)
    }

    @Test
    fun editRate_requiresEffectiveFrom_thenInsertsRateChange() = runTest {
        vm().apply { fill("Raju", "60") }.save()
        val id = db.providers.all.value.single().id

        val edit = vm(id)
        assertTrue(edit.loaded.first { it })
        assertEquals("60", edit.form.value.rate)
        edit.update { it.copy(rate = "65") }
        assertEquals(SaveResult.NeedsEffectiveFrom, edit.save())
        assertEquals(1, db.rates.all.value.size) // nothing written yet

        val effective = START + 40
        assertEquals(SaveResult.Saved, edit.save(effectiveFrom = effective))
        assertEquals(2, db.rates.all.value.size)
        // Earlier days keep the old rate, later days get the new one.
        assertEquals(6000L, db.rates.rateOn(id, effective - 1))
        assertEquals(6500L, db.rates.rateOn(id, effective))
    }

    @Test
    fun editWithoutRateChange_doesNotAskForEffectiveFromOrAddRate() = runTest {
        vm().apply { fill("Raju", "60") }.save()
        val id = db.providers.all.value.single().id
        val edit = vm(id)
        edit.update { it.copy(name = "Raju ji") }
        assertEquals(SaveResult.Saved, edit.save())
        assertEquals("Raju ji", db.providers.all.value.single().name)
        assertEquals(1, db.rates.all.value.size)
    }

    // ---- free limit ----

    @Test
    fun freeUser_canAddThreeActive_fourthIsBlocked() = runTest {
        val pro = FakeProStatus(false)
        assertEquals(SaveResult.Saved, addActive(pro, "A"))
        assertEquals(SaveResult.Saved, addActive(pro, "B"))
        assertEquals(SaveResult.Saved, addActive(pro, "C"))
        assertEquals(SaveResult.LimitReached, addActive(pro, "D"))
        assertEquals(3, db.providers.all.value.size)
        assertTrue(AnalyticsEvents.PROVIDER_LIMIT_REACHED in analytics.events)
    }

    @Test
    fun proUser_hasNoCap() = runTest {
        val pro = FakeProStatus(true)
        repeat(6) { assertEquals(SaveResult.Saved, addActive(pro, "P$it")) }
        assertEquals(6, db.providers.all.value.size)
    }

    @Test
    fun upgradingToPro_unblocksFourth() = runTest {
        val pro = FakeProStatus(false)
        repeat(3) { addActive(pro, "P$it") }
        assertEquals(SaveResult.LimitReached, addActive(pro, "D"))
        pro.isPro.value = true
        assertEquals(SaveResult.Saved, addActive(pro, "D"))
    }

    @Test
    fun archiving_freesASlot() = runTest {
        val pro = FakeProStatus(false)
        repeat(3) { addActive(pro, "P$it") }
        assertEquals(SaveResult.LimitReached, addActive(pro, "D"))

        val id = db.providers.all.value.first().id
        val edit = vm(id, pro)
        assertTrue(edit.loaded.first { it })
        edit.archive()

        assertTrue(edit.archived.value)
        assertEquals(4 - 1, db.providers.all.value.size) // archived row is kept
        assertTrue(db.providers.all.value.first { it.id == id }.archived)
        assertEquals(SaveResult.Saved, addActive(pro, "D"))
        assertEquals(4, db.providers.all.value.size)
    }

    @Test
    fun deleting_freesASlot_andRemovesRates() = runTest {
        val pro = FakeProStatus(false)
        repeat(3) { addActive(pro, "P$it") }
        val id = db.providers.all.value.first().id
        val edit = vm(id, pro)
        assertTrue(edit.loaded.first { it })
        edit.delete()
        assertEquals(2, db.providers.all.value.size)
        assertFalse(db.rates.all.value.any { it.providerId == id })
        assertEquals(SaveResult.Saved, addActive(pro, "D"))
    }

    @Test
    fun limitHelper() {
        assertTrue(canAddActiveProvider(2, false))
        assertFalse(canAddActiveProvider(3, false))
        assertTrue(canAddActiveProvider(50, true))
        assertNotNull(FREE_ACTIVE_PROVIDER_LIMIT)
        assertEquals(3, FREE_ACTIVE_PROVIDER_LIMIT)
    }

    private companion object {
        const val START = 20_400L
    }
}
