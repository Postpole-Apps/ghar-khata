package com.postpole.gharkhata.prompt

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ProPromptKeys {
    const val ENABLED = "pro_auto_prompt_enabled"
    const val MIN_SESSION = "pro_auto_prompt_min_session"
    const val MARKED_DAYS = "pro_prompt_marked_days"
    const val BILL_MILESTONES = "pro_prompt_bill_milestones"
}

interface ProPromptSettingsSource {
    val settings: StateFlow<ProPromptSettings>

    /** Fetches and activates fresh values (no-op when Firebase isn't configured). */
    fun refresh()
}

/** Fixed settings (static defaults); used in tests. */
class StaticProPromptSettings(value: ProPromptSettings = ProPromptSettings()) : ProPromptSettingsSource {
    override val settings = MutableStateFlow(value)
    override fun refresh() = Unit
}

/**
 * Remote Config backed settings. Without `google-services.json` Firebase isn't initialised and the
 * static defaults of [ProPromptSettings] apply. Only values that really came from the server are used,
 * so this never registers defaults (which would replace the ads defaults on the shared instance).
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
        val rc = remote ?: return ProPromptSettings()
        val d = ProPromptSettings()
        fun remoteValue(key: String) = rc.getValue(key).takeIf { it.source == FirebaseRemoteConfig.VALUE_SOURCE_REMOTE }
        return ProPromptSettings(
            enabled = remoteValue(ProPromptKeys.ENABLED)?.asBoolean() ?: d.enabled,
            minSession = remoteValue(ProPromptKeys.MIN_SESSION)?.asLong()?.toInt() ?: d.minSession,
            markedDays = remoteValue(ProPromptKeys.MARKED_DAYS)?.asLong()?.toInt() ?: d.markedDays,
            billMilestones = remoteValue(ProPromptKeys.BILL_MILESTONES)?.asLong()?.toInt() ?: d.billMilestones,
        ).sanitized()
    }

    private companion object {
        const val TAG = "ProPromptSettings"
    }
}
