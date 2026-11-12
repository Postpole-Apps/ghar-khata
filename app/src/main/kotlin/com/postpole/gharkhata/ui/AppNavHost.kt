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
import com.postpole.gharkhata.ui.home.HomeScreen
import com.postpole.gharkhata.ui.pro.ProScreen
import com.postpole.gharkhata.ui.provider.NEW_PROVIDER_ID
import com.postpole.gharkhata.ui.provider.ProviderEditScreen
import com.postpole.gharkhata.ui.provider.ProviderListScreen
import com.postpole.gharkhata.ui.settings.SettingsScreen
import com.postpole.gharkhata.ui.theme.gharColors

object Routes {
    /** Today tab (placeholder until the Today screen lands). */
    const val HOME = "home"
    const val PROVIDERS = "providers"
    const val PROVIDER_EDIT = "provider/edit/{id}"
    const val SETTINGS = "settings"
    const val PRO = "pro"

    fun providerEdit(id: Long) = "provider/edit/$id"
}

private data class Tab(val route: String, val icon: ImageVector, val label: Int)

private val TABS = listOf(
    Tab(Routes.HOME, Icons.Filled.DateRange, R.string.nav_today),
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
            popUpTo(Routes.HOME) { saveState = true }
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
            startDestination = Routes.HOME,
            modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onOpenSettings = { switchTab(Routes.SETTINGS) },
                    onOpenPro = { navController.navigate(Routes.PRO) },
                )
            }
            composable(Routes.PROVIDERS) {
                ProviderListScreen(
                    onOpenProvider = { navController.navigate(Routes.providerEdit(it)) },
                    onAddProvider = { navController.navigate(Routes.providerEdit(NEW_PROVIDER_ID)) },
                )
            }
            composable(
                Routes.PROVIDER_EDIT,
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) {
                ProviderEditScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenPro = { navController.navigate(Routes.PRO) },
                )
            }
            composable(Routes.PRO) {
                ProScreen(onBack = { navController.popBackStack() })
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
