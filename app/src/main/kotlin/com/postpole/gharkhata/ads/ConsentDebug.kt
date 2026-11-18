package com.postpole.gharkhata.ads

/** UMP debug settings to apply. Empty/false means none are set. */
data class ConsentDebugConfig(val eeaGeography: Boolean, val testDeviceIds: List<String>) {
    val isActive: Boolean get() = eeaGeography || testDeviceIds.isNotEmpty()
}

/**
 * Decides the UMP debug settings. Release builds ([isDebugBuild] false) never get debug geography
 * or test devices, whatever the other inputs say. [idsCsv] is the comma-separated hashed test device ids.
 */
fun consentDebugConfig(isDebugBuild: Boolean, eeaRequested: Boolean, idsCsv: String): ConsentDebugConfig {
    if (!isDebugBuild) return ConsentDebugConfig(false, emptyList())
    val ids = idsCsv.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    return ConsentDebugConfig(eeaRequested, ids)
}
