package com.postpole.gharkhata

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import com.postpole.gharkhata.reminder.ReminderController
import com.postpole.gharkhata.ads.AdsManager
import com.postpole.gharkhata.analytics.Analytics
import com.postpole.gharkhata.analytics.AnalyticsEvents
import com.postpole.gharkhata.billing.BillingRepository
import com.postpole.gharkhata.data.ProviderRepository
import com.postpole.gharkhata.widget.TodayWidget
import com.postpole.gharkhata.widget.TodayWidgetUpdater
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class GharKhataApp : Application(), Configuration.Provider {
    @Inject lateinit var billing: BillingRepository
    @Inject lateinit var analytics: Analytics
    @Inject lateinit var dataStore: DataStore<Preferences>
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var reminders: ReminderController
    @Inject lateinit var ads: AdsManager
    @Inject lateinit var providerRepository: ProviderRepository
    @Inject lateinit var proPrompts: com.postpole.gharkhata.prompt.ProPromptRepository

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        billing.start()
        ads.start()
        logLaunch()
        // One session per process start; also pulls the prompt Remote Config values.
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch { proPrompts.onSessionStart() }
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            // Re-runs whenever the entitlement changes (e.g. Play revokes Pro after a refund).
            billing.isPro.collect { reminders.reconcile() }
        }
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            // Marks or providers changed in the app: redraw the home-screen Today widget.
            TodayWidgetUpdater.changes(providerRepository).collect { TodayWidget().updateAll(this@GharKhataApp) }
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    billing.onForeground()
                    ads.onForeground()
                    proPrompts.refreshConfig()
                }
            },
        )
    }

    private fun logLaunch() {
        analytics.logEvent(AnalyticsEvents.APP_OPEN)
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            var first = false
            dataStore.edit {
                if (it[LAUNCHED_BEFORE] != true) {
                    first = true
                    it[LAUNCHED_BEFORE] = true
                }
            }
            if (first) analytics.logEvent(AnalyticsEvents.FIRST_LAUNCH)
        }
    }

    private companion object {
        val LAUNCHED_BEFORE = booleanPreferencesKey("launched_before")
    }
}
