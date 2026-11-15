package com.postpole.gharkhata.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.postpole.gharkhata.BuildConfig
import com.postpole.gharkhata.R
import com.postpole.gharkhata.data.ThemeMode
import com.postpole.gharkhata.ui.pro.ProBadge
import com.postpole.gharkhata.ui.pro.ProReason
import com.postpole.gharkhata.ui.pro.ProSheet
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.CurrencyRupee
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import com.postpole.gharkhata.ui.pro.CrownIcon
import com.postpole.gharkhata.ui.provider.BadgeShape
import com.postpole.gharkhata.ui.theme.GharShapesExtra
import com.postpole.gharkhata.ui.theme.gharColors
import java.util.Currency

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val isPro by viewModel.isPro.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val dynamicColor by viewModel.dynamicColor.collectAsStateWithLifecycle()
    val currency by viewModel.defaultCurrency.collectAsStateWithLifecycle()
    val reminderMinutes by viewModel.reminderMinutes.collectAsStateWithLifecycle()
    val privacyOptionsRequired by viewModel.privacyOptionsRequired.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resolver = context.contentResolver
    val snackbarHostState = remember { SnackbarHostState() }

    var showPro by rememberSaveable { mutableStateOf<String?>(null) }
    var showTheme by rememberSaveable { mutableStateOf(false) }
    var showCurrency by rememberSaveable { mutableStateOf(false) }
    var showReminderPicker by rememberSaveable { mutableStateOf(false) }
    var pendingReminder by rememberSaveable { mutableStateOf<Int?>(null) }
    var pendingImport by rememberSaveable { mutableStateOf<String?>(null) }

    // Storage Access Framework: the system picker grants access to the one chosen file, so no
    // storage permission is needed.
    val exportJson = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let { viewModel.exportJson(resolver, it) } }
    val exportCsv = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri -> uri?.let { viewModel.exportCsv(resolver, it) } }
    val pickImport = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> pendingImport = uri?.toString() }

    // Android 13+: ask for the notification permission when the user turns the reminder on. The
    // reminder is saved either way; without permission the worker simply posts nothing.
    val askNotifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ ->
        pendingReminder?.let(viewModel::setReminder)
        pendingReminder = null
    }
    fun confirmReminder(minutes: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS,
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            pendingReminder = minutes
            askNotifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.setReminder(minutes)
        }
    }

    val messages = rememberEventMessages()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { snackbarHostState.showSnackbar(messages(it)) }
    }

    Box(Modifier.fillMaxSize()) {
        SettingsContent(
            isPro = isPro,
            themeMode = themeMode,
            dynamicColor = dynamicColor,
            currency = currency,
            reminderMinutes = reminderMinutes,
            privacyOptionsRequired = privacyOptionsRequired,
            onPrivacyOptions = { context.findActivity()?.let(viewModel::showPrivacyOptions) },
            onUpgrade = { showPro = "general" },
            onRestorePurchases = viewModel::restore,
            onBackup = { exportJson.launch(backupFileName("json")) },
            onRestoreBackup = { pickImport.launch(arrayOf("application/json", "text/plain", "*/*")) },
            onExportCsv = { if (isPro) exportCsv.launch(backupFileName("csv")) else showPro = "csv" },
            onReminder = { if (isPro) showReminderPicker = true else showPro = "reminder" },
            onTurnOffReminder = viewModel::clearReminder,
            onTheme = { showTheme = true },
            onDynamicColor = viewModel::setDynamicColor,
            onCurrency = { showCurrency = true },
            onPrivacy = {
                context.openLink(Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(R.string.privacy_policy_url))))
            },
            onFeedback = {
                val email = context.getString(R.string.support_email)
                context.openLink(
                    Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("mailto:")
                        putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
                        putExtra(
                            Intent.EXTRA_SUBJECT,
                            context.getString(R.string.settings_feedback_subject, context.getString(R.string.app_name)),
                        )
                    },
                )
            },
        )
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }

    showPro?.let { why ->
        ProSheet(
            onDismiss = { showPro = null },
            reason = when (why) {
                "csv" -> ProReason.CSV
                "reminder" -> ProReason.REMINDER
                else -> ProReason.GENERAL
            },
        )
    }

    if (showReminderPicker) {
        val initial = reminderMinutes ?: (8 * 60)
        val pickerState = rememberTimePickerState(
            initialHour = initial / 60,
            initialMinute = initial % 60,
            is24Hour = android.text.format.DateFormat.is24HourFormat(context),
        )
        AlertDialog(
            onDismissRequest = { showReminderPicker = false },
            title = { Text(stringResource(R.string.settings_reminder)) },
            text = { TimePicker(state = pickerState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showReminderPicker = false
                        confirmReminder(pickerState.hour * 60 + pickerState.minute)
                    },
                ) { Text(stringResource(R.string.settings_reminder_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showReminderPicker = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (showTheme) {
        ChoiceDialog(
            title = R.string.settings_theme,
            onDismiss = { showTheme = false },
        ) {
            ThemeMode.entries.forEach { mode ->
                Row(
                    Modifier.fillMaxWidthClickable {
                        viewModel.setThemeMode(mode)
                        showTheme = false
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = mode == themeMode, onClick = null)
                    Text(stringResource(mode.label()), Modifier.padding(start = 12.dp))
                }
            }
        }
    }

    if (showCurrency) {
        val codes = remember { Currency.getAvailableCurrencies().map { it.currencyCode }.sorted() }
        AlertDialog(
            onDismissRequest = { showCurrency = false },
            title = { Text(stringResource(R.string.settings_currency)) },
            text = {
                LazyColumn {
                    items(codes, key = { it }) { code ->
                        Row(
                            Modifier.fillMaxWidthClickable {
                                viewModel.setDefaultCurrency(code)
                                showCurrency = false
                            },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = code == currency, onClick = null)
                            Text(currencyLabel(code), Modifier.padding(start = 12.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCurrency = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    pendingImport?.let { uriString ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text(stringResource(R.string.backup_import_confirm_title)) },
            text = { Text(stringResource(R.string.backup_import_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.importJson(resolver, Uri.parse(uriString))
                        pendingImport = null
                    },
                ) { Text(stringResource(R.string.backup_import_confirm_action)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingImport = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

/** Stateless Settings (also used by the screenshot tests): hero Pro card on top, grouped sections below. */
@Composable
internal fun SettingsContent(
    isPro: Boolean,
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    currency: String?,
    reminderMinutes: Int?,
    privacyOptionsRequired: Boolean = false,
    onPrivacyOptions: () -> Unit = {},
    onUpgrade: () -> Unit = {},
    onRestorePurchases: () -> Unit = {},
    onBackup: () -> Unit = {},
    onRestoreBackup: () -> Unit = {},
    onExportCsv: () -> Unit = {},
    onReminder: () -> Unit = {},
    onTurnOffReminder: () -> Unit = {},
    onTheme: () -> Unit = {},
    onDynamicColor: (Boolean) -> Unit = {},
    onCurrency: () -> Unit = {},
    onPrivacy: () -> Unit = {},
    onFeedback: () -> Unit = {},
) {
    val gk = MaterialTheme.gharColors
    val context = LocalContext.current
    val dynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    Column(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(gk.hero, gk.heroVariant))).statusBarsPadding(),
    ) {
        Text(
            stringResource(R.string.nav_settings),
            style = MaterialTheme.typography.headlineMedium,
            color = gk.onHero,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
        )
        ProHeroCard(isPro = isPro, onClick = onUpgrade)
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            TextButton(onClick = onRestorePurchases, modifier = Modifier.padding(end = 12.dp)) {
                Text(stringResource(R.string.pro_restore), style = MaterialTheme.typography.titleMedium, color = gk.lime)
            }
        }
        Column(
            Modifier.fillMaxWidth().weight(1f).clip(GharShapesExtra.SheetTop)
                .background(MaterialTheme.colorScheme.surface)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            SectionHeader(R.string.settings_section_data)
            SettingsGroup {
                SettingsRow(Icons.Outlined.FileUpload, stringResource(R.string.backup_export_json), stringResource(R.string.backup_export_json_hint), onClick = onBackup)
                SettingsRow(Icons.Outlined.Restore, stringResource(R.string.backup_import_json), stringResource(R.string.backup_import_json_hint), onClick = onRestoreBackup)
                SettingsRow(
                    Icons.Outlined.TableChart, stringResource(R.string.backup_export_csv_pro), stringResource(R.string.backup_export_csv_hint),
                    trailing = { if (!isPro) ProBadge() }, onClick = onExportCsv,
                )
            }

            SectionHeader(R.string.settings_section_reminder)
            SettingsGroup {
                SettingsRow(
                    Icons.Outlined.Notifications, stringResource(R.string.settings_reminder),
                    reminderMinutes?.let { formatTime(context, it) } ?: stringResource(R.string.settings_reminder_off),
                    trailing = {
                        if (!isPro) {
                            ProBadge()
                        } else if (reminderMinutes != null) {
                            TextButton(onClick = onTurnOffReminder) { Text(stringResource(R.string.settings_reminder_turn_off)) }
                        }
                    },
                    onClick = onReminder,
                )
                SettingsRow(
                    Icons.Outlined.Translate, stringResource(R.string.settings_language),
                    stringResource(R.string.settings_language_value, java.util.Locale.getDefault().getDisplayLanguage(java.util.Locale.getDefault())),
                )
            }

            SectionHeader(R.string.settings_section_appearance)
            SettingsGroup {
                SettingsRow(Icons.Outlined.Palette, stringResource(R.string.settings_theme), stringResource(themeMode.label()), onClick = onTheme)
                SettingsRow(
                    Icons.Outlined.ColorLens, stringResource(R.string.settings_dynamic_color),
                    if (dynamicSupported) null else stringResource(R.string.settings_dynamic_color_unsupported),
                    trailing = {
                        Switch(checked = dynamicColor && dynamicSupported, onCheckedChange = onDynamicColor, enabled = dynamicSupported)
                    },
                )
                SettingsRow(Icons.Outlined.CurrencyRupee, stringResource(R.string.settings_currency), currency?.let(::currencyLabel).orEmpty(), onClick = onCurrency)
            }

            SectionHeader(R.string.settings_section_about)
            SettingsGroup {
                SettingsRow(Icons.Outlined.Info, stringResource(R.string.settings_version), BuildConfig.VERSION_NAME)
                SettingsRow(Icons.Outlined.PrivacyTip, stringResource(R.string.settings_privacy_policy), null, onClick = onPrivacy)
                if (privacyOptionsRequired) {
                    SettingsRow(
                        Icons.Outlined.PrivacyTip, stringResource(R.string.settings_privacy_options),
                        stringResource(R.string.settings_privacy_options_hint), onClick = onPrivacyOptions,
                    )
                }
                SettingsRow(Icons.Outlined.Mail, stringResource(R.string.settings_feedback), stringResource(R.string.support_email), onClick = onFeedback)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Upgrade to Pro hero card; shows the active state (not clickable) once Pro is owned. */
@Composable
private fun ProHeroCard(isPro: Boolean, onClick: () -> Unit) {
    val gk = MaterialTheme.gharColors
    Row(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(GharShapesExtra.Card)
            .background(gk.onHero.copy(alpha = 0.10f))
            .then(if (isPro) Modifier else Modifier.clickable(role = Role.Button, onClick = onClick))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.size(56.dp).clip(BadgeShape).background(gk.lime), contentAlignment = Alignment.Center) {
            Icon(CrownIcon, contentDescription = null, tint = gk.onLime, modifier = Modifier.size(28.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(if (isPro) R.string.pro_active else R.string.upgrade_to_pro),
                style = MaterialTheme.typography.titleLarge, color = gk.onHero,
            )
            Text(
                stringResource(if (isPro) R.string.settings_pro_active_subtitle else R.string.settings_pro_hero_subtitle),
                style = MaterialTheme.typography.bodyMedium, color = gk.onHeroVariant,
            )
        }
        if (!isPro) Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = gk.onHero)
    }
}

/** Rounded card group: the rows inside share the outer corners and are separated by a thin gap. */
@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(GharShapesExtra.Card),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        content = content,
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().background(cs.surfaceContainerHigh)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(cs.surfaceContainerHighest), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = cs.onSurface, modifier = Modifier.size(24.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
            if (!subtitle.isNullOrEmpty()) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            }
        }
        trailing?.invoke()
    }
}

@Composable
private fun SectionHeader(titleRes: Int) {
    Text(
        stringResource(titleRes).uppercase(java.util.Locale.getDefault()),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp, top = 16.dp, end = 8.dp, bottom = 8.dp),
    )
}

@Composable
private fun ChoiceDialog(title: Int, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { Column { content() } },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private fun Modifier.fillMaxWidthClickable(onClick: () -> Unit): Modifier =
    fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp)

private fun ThemeMode.label(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}

private fun currencyLabel(code: String): String =
    runCatching { "$code — ${Currency.getInstance(code).displayName}" }.getOrDefault(code)

private fun formatTime(context: Context, minutesOfDay: Int): String =
    android.text.format.DateFormat.getTimeFormat(context).format(
        java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, minutesOfDay / 60)
            set(java.util.Calendar.MINUTE, minutesOfDay % 60)
        }.time,
    )

private fun backupFileName(extension: String): String {
    val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
    return "backup-$date.$extension"
}

private tailrec fun Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun Context.openLink(intent: Intent) {
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // No browser / mail app installed: nothing sensible to do.
    }
}

/** Resolves [SettingsEvent]s to localized text. */
@Composable
private fun rememberEventMessages(): (SettingsEvent) -> String {
    val exported = stringResource(R.string.backup_exported_generic)
    val exportFailed = stringResource(R.string.backup_export_failed)
    val imported = stringResource(R.string.backup_imported_generic)
    val importInvalid = stringResource(R.string.backup_import_invalid)
    val importFailed = stringResource(R.string.backup_import_failed)
    val restoreChecked = stringResource(R.string.restore_checked)
    val restoreFailed = stringResource(R.string.restore_failed)
    return { event ->
        when (event) {
            is SettingsEvent.Exported -> exported
            SettingsEvent.ExportFailed -> exportFailed
            is SettingsEvent.Imported -> imported
            SettingsEvent.ImportInvalid -> importInvalid
            SettingsEvent.ImportFailed -> importFailed
            SettingsEvent.RestoreChecked -> restoreChecked
            SettingsEvent.RestoreFailed -> restoreFailed
        }
    }
}
