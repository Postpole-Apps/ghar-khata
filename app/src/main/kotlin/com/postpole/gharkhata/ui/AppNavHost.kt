package com.postpole.gharkhata.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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

@Composable
fun AppNavHost(onScreen: (String) -> Unit = {}) {
    val navController = rememberNavController()
    DisposableEffect(navController) {
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            destination.route?.let(onScreen)
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose { navController.removeOnDestinationChangedListener(listener) }
    }
    val current by navController.currentBackStackEntryAsState()
    val route = current?.destination?.route

    fun switchTab(target: String) {
        navController.navigate(target) {
            popUpTo(Routes.TODAY) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = { if (TABS.any { it.route == route }) GharBottomNav(route, ::switchTab) },
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
                SettingsScreen(onBack = { navController.popBackStack() })
            }
        }
    }
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
