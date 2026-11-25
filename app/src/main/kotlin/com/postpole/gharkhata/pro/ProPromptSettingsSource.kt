package com.postpole.gharkhata.pro

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Source of [ProPromptSettings]; Remote Config in the app, a fake in tests. */
interface ProPromptSettingsSource {
    val settings: StateFlow<ProPromptSettings>

    /** Fetches and activates fresh values (no-op when Firebase isn't configured). */
    fun refresh()
}

/**
 * Remote Config backed settings. Without `google-services.json` Firebase isn't initialised and this stays
 * on the static defaults. A key the server doesn't carry also falls back to its static default. Defaults
 * are not registered with Remote Config (that call replaces the ads defaults), so values are only taken
 * from a remote source.
 */
class RemoteConfigProPromptSettings(context: Context) : ProPromptSettingsSource {
    private val remote: FirebaseRemoteConfig? =
        if (FirebaseApp.getApps(context).isEmpty()) null else FirebaseRemoteConfig.getInstance()

    private val _settings = MutableStateFlow(read())
    override val settings: StateFlow<ProPromptSettings> = _settings.asStateFlow()

    override fun refresh() {
        val rc = remote ?: return
        rc.fetchAndActivate().addOnCompleteListener { task ->
            if (task.isSuccessful) _settings.value = read() else Log.w(TAG, "Remote Config fetch failed", task.exception)
        }
    }

    private fun read(): ProPromptSettings {
        val rc = remote ?: return ProPromptSettings.DEFAULT
        val d = ProPromptSettings.DEFAULT
        fun FirebaseRemoteConfigValue.isRemote() = source == FirebaseRemoteConfig.VALUE_SOURCE_REMOTE
        fun int(key: String, default: Int) = rc.getValue(key).let { if (it.isRemote()) it.asLong().toInt() else default }
        return ProPromptSettings.of(
            enabled = rc.getValue(ProPromptKeys.ENABLED).let { if (it.isRemote()) it.asBoolean() else d.enabled },
            minSession = int(ProPromptKeys.MIN_SESSION, d.minSession),
            markedDays = int(ProPromptKeys.MARKED_DAYS, d.markedDays),
            billMilestones = int(ProPromptKeys.BILL_MILESTONES, d.billMilestones),
        )
    }

    private companion object {
        const val TAG = "ProPromptSettings"
    }
}
