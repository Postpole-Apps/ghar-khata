package com.postpole.gharkhata.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import com.postpole.gharkhata.data.ProviderRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** Keeps the home-screen widget in step with the data layer and the calendar day. */
object TodayWidgetUpdater {
    private val day = MutableStateFlow(LocalDate.now().toEpochDay())

    /** Emits whenever today's marks, the active providers or any settlement change (and when the day rolls over). */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun changes(repository: ProviderRepository): Flow<Unit> =
        day.flatMapLatest { d ->
            combine(
                repository.observeActive(),
                repository.observeMarksForDay(d),
                repository.observeAllSettlements(),
            ) { p, m, s -> Triple(p, m, s) }
        }.distinctUntilChanged().drop(1).map { }

    /** Re-reads the current date and redraws every placed widget. */
    suspend fun refresh(context: Context) {
        day.value = LocalDate.now().toEpochDay()
        TodayWidget().updateAll(context.applicationContext)
        if (GlanceAppWidgetManager(context.applicationContext).getGlanceIds(TodayWidget::class.java).isNotEmpty()) {
            MidnightScheduler.schedule(context)
        }
    }
}
