package com.postpole.gharkhata

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.postpole.gharkhata.ads.AdsManager
import com.postpole.gharkhata.analytics.Analytics
import com.postpole.gharkhata.data.ThemeMode
import com.postpole.gharkhata.ui.AppNavHost
import com.postpole.gharkhata.ui.MainViewModel
import com.postpole.gharkhata.ui.theme.GharKhataTheme
import com.postpole.gharkhata.update.InAppUpdate
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    @Inject lateinit var analytics: Analytics

    @Inject lateinit var ads: AdsManager

    private lateinit var update: InAppUpdate

    /** Bumped each time a reminder tap asks for Today; AppNavHost navigates on every change. */
    private val goToToday = mutableIntStateOf(0)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeGoToToday(intent)
    }

    private fun consumeGoToToday(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_GO_TO_TODAY, false) == true) {
            intent.removeExtra(EXTRA_GO_TO_TODAY)
            goToToday.intValue++
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        update = InAppUpdate(this, onError = analytics::recordNonFatal)
        // UMP consent first; MobileAds.initialize only follows once ads can be requested. Once per process.
        ads.start(this)
        if (savedInstanceState == null) {
            update.check()
            consumeGoToToday(intent)
        }
        setContent {
            val theme by viewModel.theme.collectAsStateWithLifecycle()
            val updateReady by update.readyToInstall.collectAsStateWithLifecycle()
            var dismissed by rememberSaveable { mutableStateOf(false) }
            val dark = when (theme.mode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            GharKhataTheme(darkTheme = dark, dynamicColor = theme.dynamicColor) {
                AppNavHost(onScreen = viewModel::onScreen, goToToday = goToToday.intValue, ads = ads)
                if (updateReady && !dismissed) {
                    AlertDialog(
                        onDismissRequest = { dismissed = true },
                        title = { Text(stringResource(R.string.update_ready_title)) },
                        text = { Text(stringResource(R.string.update_ready_message)) },
                        confirmButton = {
                            TextButton(onClick = update::completeUpdate) { Text(stringResource(R.string.update_restart)) }
                        },
                        dismissButton = {
                            TextButton(onClick = { dismissed = true }) { Text(stringResource(R.string.update_later)) }
                        },
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        update.release()
        super.onDestroy()
    }

    companion object {
        /** Boolean extra set by the reminder notification: open on the Today tab. */
        const val EXTRA_GO_TO_TODAY = "com.postpole.gharkhata.GO_TO_TODAY"
    }
}
