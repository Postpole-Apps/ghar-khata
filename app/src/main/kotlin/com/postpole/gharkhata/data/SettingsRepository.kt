package com.postpole.gharkhata.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Currency
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Currency for [locale] (India -> INR), or USD if the locale has no country/currency. */
fun defaultCurrencyFor(locale: Locale): String =
    try {
        Currency.getInstance(locale).currencyCode
    } catch (_: IllegalArgumentException) {
        "USD"
    } catch (_: NullPointerException) {
        "USD"
    }

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    /** Whether to use Material You dynamic colours (Android 12+). */
    val dynamicColor: Flow<Boolean> = dataStore.data.map { it[DYNAMIC_COLOR] ?: false }

    val themeMode: Flow<ThemeMode> = dataStore.data.map { prefs ->
        prefs[THEME_MODE]?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: ThemeMode.SYSTEM
    }

    /** User's choice, or the device-locale currency until they pick one. */
    val defaultCurrency: Flow<String> = dataStore.data.map {
        it[DEFAULT_CURRENCY] ?: defaultCurrencyFor(Locale.getDefault())
    }

    /** Daily reminder time as minutes after midnight (0..1439); null = reminder off. */
    val reminderMinutes: Flow<Int?> = dataStore.data.map { it[REMINDER_MINUTES] }

    suspend fun setReminderMinutes(minutes: Int?) {
        require(minutes == null || minutes in 0..1439) { "minutes out of range" }
        dataStore.edit { if (minutes == null) it.remove(REMINDER_MINUTES) else it[REMINDER_MINUTES] = minutes }
    }

    /** Epoch day Today was last shown on its real date; null = never. The reminder stays quiet if it equals today. */
    val lastTodayOpenEpochDay: Flow<Long?> = dataStore.data.map { it[LAST_TODAY_OPEN] }

    suspend fun setLastTodayOpenEpochDay(epochDay: Long) {
        dataStore.edit { it[LAST_TODAY_OPEN] = epochDay }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { it[DYNAMIC_COLOR] = enabled }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[THEME_MODE] = mode.name }
    }

    suspend fun setDefaultCurrency(code: String) {
        dataStore.edit { it[DEFAULT_CURRENCY] = code }
    }

    private companion object {
        val REMINDER_MINUTES = intPreferencesKey("reminder_minutes")
        val LAST_TODAY_OPEN = longPreferencesKey("last_today_open_epoch_day")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DEFAULT_CURRENCY = stringPreferencesKey("default_currency")
    }
}
