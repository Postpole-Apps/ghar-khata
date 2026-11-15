package com.postpole.gharkhata.ads

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError

/**
 * Anchored adaptive banner. The slot's height is reserved up front so the layout never jumps when the
 * ad arrives (a shifting ad is an accidental-click risk); if the request fails the slot collapses.
 * The AdView follows the lifecycle: pause / resume with the Activity, destroy when it leaves composition.
 */
@Composable
fun AdBanner(placement: BannerPlacement, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val widthDp = LocalConfiguration.current.screenWidthDp
    val adSize = remember(widthDp) {
        AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp)
    }
    var failed by remember(placement) { mutableStateOf(false) }
    val adView = remember(placement, adSize) {
        AdView(context).apply {
            setAdSize(adSize)
            adUnitId = AdUnits.banner(placement)
            adListener = object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    failed = true
                }

                override fun onAdLoaded() {
                    failed = false
                }
            }
            loadAd(AdRequest.Builder().build())
        }
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(adView, lifecycle) {
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) adView.resume() else adView.pause()
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> adView.resume()
                Lifecycle.Event.ON_PAUSE -> adView.pause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            adView.destroy()
        }
    }

    if (!failed) {
        Box(modifier.fillMaxWidth().height(with(androidx.compose.ui.platform.LocalDensity.current) { adSize.getHeightInPixels(context).toDp() })) {
            AndroidView(factory = { adView }, modifier = Modifier.fillMaxWidth())
        }
    }
}
