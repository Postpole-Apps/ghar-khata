package com.postpole.gharkhata.ads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * Lets modal UI (day-mark sheet, mark-paid dialog, Pro sheet) hide the banner while it is open, so an ad
 * is never visible next to a purchase flow or day cells. Counted, so overlapping overlays are safe.
 */
class AdSuppression {
    private var count by mutableIntStateOf(0)
    val suppressed: Boolean get() = count > 0

    fun acquire() { count++ }
    fun release() { if (count > 0) count-- }
}

val LocalAdSuppression = compositionLocalOf { AdSuppression() }

/** Hides banners for as long as this call is in the composition. */
@Composable
fun SuppressBanner() {
    val suppression = LocalAdSuppression.current
    DisposableEffect(suppression) {
        suppression.acquire()
        onDispose { suppression.release() }
    }
}
