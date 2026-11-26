package com.postpole.gharkhata.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.postpole.gharkhata.ads.AdBanner
import com.postpole.gharkhata.ads.AdsManager
import com.postpole.gharkhata.ads.BannerPlacement
import com.postpole.gharkhata.pro.ProPromptScreen
import com.postpole.gharkhata.pro.ProPromptTrigger
import com.postpole.gharkhata.pro.ProPrompts
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.postpole.gharkhata.R
import com.postpole.gharkhata.ui.month.BillPreviewScreen
import com.postpole.gharkhata.ui.month.ProviderMonthScreen
import com.postpole.gharkhata.ui.month.YearSummaryScreen
import com.postpole.gharkhata.ui.provider.NEW_PROVIDER_ID
import com.postpole.gharkhata.ui.provider.ProviderEditScreen
import com.postpole.gharkhata.ui.provider.ProviderListScreen
import com.postpole.gharkhata.ui.settings.SettingsScreen
import com.postpole.gharkhata.ui.today.TodayScreen
import com.postpole.gharkhata.data.ProviderType
import com.postpole.gharkhata.ui.theme.gharColors

object Routes {
    /** Today: the start destination. */
    const val TODAY = "today"
    const val PROVIDERS = "providers"
    const val PROVIDER_EDIT = "provider/edit/{id}?type={type}"
    const val SETTINGS = "settings"

    const val PROVIDER_MONTH = "provider/{id}/month/{yyyymm}"
    const val BILL = "bill/{id}/{yyyymm}"
    const val YEAR = "provider/{id}/year/{year}"

    fun providerEdit(id: Long) = "provider/edit/$id"

    /** Current month when [yearMonth] is omitted. */
    fun providerMonth(id: Long, yearMonth: Int = currentYearMonth()) = "provider/$id/month/$yearMonth"
    fun bill(id: Long, yearMonth: Int) = "bill/$id/$yearMonth"
    fun year(id: Long, year: Int) = "provider/$id/year/$year"

    private fun currentYearMonth() = java.time.LocalDate.now().let { it.year * 100 + it.monthValue }

    /** New provider pre-filled with the preset for [type]. */
    fun providerNew(type: ProviderType) = "provider/edit/$NEW_PROVIDER_ID?type=${type.name}"
}

private data class Tab(val route: String, val icon: ImageVector, val label: Int)

private val TABS = listOf(
    Tab(Routes.TODAY, Icons.Filled.DateRange, R.string.nav_today),
    Tab(Routes.PROVIDERS, Icons.Filled.Person, R.string.nav_providers),
    Tab(Routes.SETTINGS, Icons.Filled.Settings, R.string.nav_settings),
)

/**
 * Banner slot per route. Only browse screens have one: never Provider edit, Bill preview, Settings,
 * and (by hiding it while a sheet is open) never next to DayMarkSheet / ProSheet.
 */
internal fun bannerPlacementFor(route: String?): BannerPlacement? = when (route) {
    Routes.TODAY -> BannerPlacement.TODAY
    Routes.PROVIDERS -> BannerPlacement.PROVIDERS
    Routes.PROVIDER_MONTH -> BannerPlacement.MONTH
    Routes.YEAR -> BannerPlacement.YEAR
    else -> null
}

/** Screens the interstitial may appear on after Bill preview is left. */
internal fun isInterstitialDestination(route: String?): Boolean =
    route == Routes.TODAY || route == Routes.PROVIDERS || route == Routes.PROVIDER_MONTH

/** Settle time after leaving Bill preview before the interstitial may show. */
private const val INTERSTITIAL_SETTLE_MS = 1_500L

@Composable
fun AppNavHost(ads: AdsManager, prompts: ProPrompts, onScreen: (String) -> Unit = {}, goToToday: Int = 0) {
    val navController = rememberNavController()
    // Bumped each time Bill preview is left for Today / Providers / Month.
    var billExitTick by remember { mutableIntStateOf(0) }
    // True while a sheet / dialog is open on the Month screen: the banner is hidden meanwhile.
    var monthOverlayOpen by remember { mutableStateOf(false) }
    // Set when the auto Pro sheet was granted on return from Bill preview; Month consumes it.
    var autoProSheetRequest by remember { mutableStateOf(false) }
    DisposableEffect(navController) {
        var lastRoute: String? = null
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            val dest = destination.route
            dest?.let(onScreen)
            if (lastRoute == Routes.BILL && isInterstitialDestination(dest)) billExitTick++
            lastRoute = dest
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose { navController.removeOnDestinationChangedListener(listener) }
    }
    val current by navController.currentBackStackEntryAsState()
    val route = current?.destination?.route
    val bannersAllowed by ads.bannersAllowed.collectAsStateWithLifecycle()
    val activity = LocalContext.current.findActivity()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Warm the interstitial while Bill preview is open (it is never shown there).
    LaunchedEffect(route) {
        if (route == Routes.BILL && activity != null) ads.preloadInterstitial(activity)
    }
    // Back from Bill preview: an eligible auto Pro prompt wins over the interstitial, and any auto prompt
    // already shown this session means no interstitial this session. Otherwise the interstitial: only
    // on Today / Providers / Month, Activity resumed, no sheet open, caps pass (checked in AdsManager),
    // after a short settle delay.
    LaunchedEffect(billExitTick) {
        if (billExitTick == 0 || activity == null) return@LaunchedEffect
        if (navController.currentDestination?.route == Routes.PROVIDER_MONTH &&
            prompts.reserve(ProPromptTrigger.BILL_MILESTONE, ProPromptScreen.PROVIDER_MONTH)
        ) {
            autoProSheetRequest = true
            return@LaunchedEffect
        }
        if (prompts.autoPromptShownThisSession) return@LaunchedEffect
        delay(INTERSTITIAL_SETTLE_MS)
        if (isInterstitialDestination(navController.currentDestination?.route) &&
            !monthOverlayOpen &&
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        ) {
            if (ads.showInterstitialIfAllowed(activity)) prompts.onInterstitialShown()
        }
    }

    fun switchTab(target: String) {
        navController.navigate(target) {
            popUpTo(Routes.TODAY) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // A reminder tap on a warm start: back to Today from wherever the user was.
    LaunchedEffect(goToToday) {
        if (goToToday > 0) {
            navController.navigate(Routes.TODAY) {
                popUpTo(Routes.TODAY) { inclusive = false }
                launchSingleTop = true
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            val isTab = TABS.any { it.route == route }
            val placement = bannerPlacementFor(route)
            Column {
                if (bannersAllowed && placement != null && !(placement == BannerPlacement.MONTH && monthOverlayOpen)) {
                    // Above the floating nav on tabs; on non-tab screens it clears the system nav bar.
                    key(placement) {
                        AdBanner(placement, if (isTab) Modifier else Modifier.navigationBarsPadding())
                    }
                }
                if (isTab) GharBottomNav(route, ::switchTab)
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.TODAY,
            modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
        ) {
            composable(Routes.TODAY) {
                TodayScreen(
                    onAddProvider = { navController.navigate(Routes.providerEdit(NEW_PROVIDER_ID)) },
                    onPreset = { navController.navigate(Routes.providerNew(it)) },
                    onOpenBills = { switchTab(Routes.PROVIDERS) },
                    onOpenProvider = { navController.navigate(Routes.providerMonth(it)) },
                    onOpenProviderMonth = { id, yyyymm -> navController.navigate(Routes.providerMonth(id, yyyymm)) },
                    onOpenSettings = { switchTab(Routes.SETTINGS) },
                )
            }
            composable(Routes.PROVIDERS) {
                ProviderListScreen(
                    onOpenProvider = { navController.navigate(Routes.providerMonth(it)) },
                    onAddProvider = { navController.navigate(Routes.providerEdit(NEW_PROVIDER_ID)) },
                    onOpenSettings = { switchTab(Routes.SETTINGS) },
                )
            }
            composable(
                Routes.PROVIDER_EDIT,
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType },
                    navArgument("type") { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
            ) {
                ProviderEditScreen(onBack = { navController.popBackStack() })
            }
            composable(
                Routes.PROVIDER_MONTH,
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType },
                    navArgument("yyyymm") { type = NavType.IntType },
                ),
            ) {
                ProviderMonthScreen(
                    onBack = { navController.popBackStack() },
                    onEditProvider = { navController.navigate(Routes.providerEdit(it)) },
                    onOpenBill = { id, ym -> navController.navigate(Routes.bill(id, ym)) },
                    onOpenYear = { id, ym -> navController.navigate(Routes.year(id, ym / 100)) },
                    onOverlayChanged = { monthOverlayOpen = it },
                    autoProSheetRequest = autoProSheetRequest,
                    onAutoProSheetConsumed = { autoProSheetRequest = false },
                )
            }
            composable(
                Routes.BILL,
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType },
                    navArgument("yyyymm") { type = NavType.IntType },
                ),
            ) {
                BillPreviewScreen(onBack = { navController.popBackStack() })
            }
            composable(
                Routes.YEAR,
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType },
                    navArgument("year") { type = NavType.IntType },
                ),
            ) {
                YearSummaryScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.SETTINGS) {
                val privacyOptionsRequired by ads.privacyOptionsRequired.collectAsStateWithLifecycle()
                SettingsScreen(
                    showPrivacyOptions = privacyOptionsRequired,
                    onPrivacyOptions = { activity?.let { ads.showPrivacyOptions(it) } },
                )
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var c = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/** Floating solid pill nav (no blur). Inactive tabs are icon-only with a content description. */
@Composable
private fun GharBottomNav(currentRoute: String?, onSelect: (String) -> Unit) {
    val gk = MaterialTheme.gharColors
    Row(
        Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp).fillMaxWidth()
            .shadow(12.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.18f), spotColor = Color.Black.copy(alpha = 0.18f))
            .clip(CircleShape).background(gk.hero).padding(8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TABS.forEach { tab ->
            val selected = tab.route == currentRoute
            val label = stringResource(tab.label)
            Row(
                Modifier.heightIn(min = 48.dp).clip(CircleShape)
                    .background(if (selected) gk.lime else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(tab.route) }
                    .padding(horizontal = if (selected) 24.dp else 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(tab.icon, contentDescription = label, tint = if (selected) gk.onLime else gk.onHeroVariant)
                if (selected) Text(label, color = gk.onLime, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
