package com.postpole.gharkhata.ui.pro

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CalendarViewMonth
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.postpole.gharkhata.BuildConfig
import com.postpole.gharkhata.R
import com.postpole.gharkhata.prompt.ProPromptTrigger
import com.postpole.gharkhata.billing.BillingMessage
import com.postpole.gharkhata.ui.provider.BadgeShape
import com.postpole.gharkhata.ui.provider.FREE_ACTIVE_PROVIDER_LIMIT
import com.postpole.gharkhata.ui.theme.GharNumerals
import com.postpole.gharkhata.ui.theme.GharShapesExtra
import com.postpole.gharkhata.ui.theme.gharColors

/**
 * Why the Pro sheet was opened; shown under the price. [hardGate] reasons start the auto-prompt cooldown;
 * [autoTrigger] reasons came from an automatic prompt and tag analytics with `auto_<trigger>`.
 */
enum class ProReason(val autoTrigger: ProPromptTrigger? = null, val hardGate: Boolean = false) {
    GENERAL,
    PROVIDER_LIMIT(hardGate = true),
    BILL_IMAGE(hardGate = true),
    CSV(hardGate = true),
    YEAR_SUMMARY(hardGate = true),
    REMINDER(hardGate = true),

    /** Automatic sheet after bill shares / settlements (Month screen only). */
    AUTO_BILL_MILESTONE(autoTrigger = ProPromptTrigger.BILL_MILESTONE),

    /** Opened from the inline card's "See Pro". */
    AUTO_MARKED_DAYS_MILESTONE(autoTrigger = ProPromptTrigger.MARKED_DAYS_MILESTONE),
}

/** How a Pro sheet was dismissed; scrim tap and system back both count as [SWIPE]. */
enum class ProDismissMethod(val analyticsName: String) { CLOSE("close"), NOT_NOW("not_now"), SWIPE("swipe") }

/** What the sheet needs to draw itself; the screenshot tests build it directly. */
data class ProSheetState(
    val isPro: Boolean = false,
    val price: String? = null,
    val pending: Boolean = false,
    val unavailable: Boolean = false,
    val canBuy: Boolean = true,
    /** Show the "No ads" benefit: only in the ads-shipping build, and not to someone who already removed ads. */
    val showNoAds: Boolean = false,
)

/**
 * Pro upgrade as a modal bottom sheet. The only Pro surface in the app (there is no separate Pro route):
 * Today, Providers, the provider form, the bill preview, year summary and Settings all open this.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProSheet(
    onDismiss: () -> Unit,
    reason: ProReason = ProReason.PROVIDER_LIMIT,
    viewModel: ProViewModel = hiltViewModel(),
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val purchaseError = stringResource(R.string.pro_error_purchase)
    val launchError = stringResource(R.string.pro_error_launch)
    LaunchedEffect(viewModel) {
        viewModel.messages.collect {
            snackbarHostState.showSnackbar(
                when (it) {
                    BillingMessage.PURCHASE_ERROR -> purchaseError
                    BillingMessage.LAUNCH_FAILED -> launchError
                },
            )
        }
    }
    val isPro by viewModel.isPro.collectAsStateWithLifecycle()
    val price by viewModel.price.collectAsStateWithLifecycle()
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val unavailable by viewModel.productUnavailable.collectAsStateWithLifecycle()
    val hasRemoveAds by viewModel.hasRemoveAds.collectAsStateWithLifecycle()
    val activity = LocalContext.current.findActivity()
    LaunchedEffect(viewModel) { viewModel.onShown(reason) }
    // Once the purchase goes through the sheet has done its job.
    LaunchedEffect(isPro) { if (isPro) onDismiss() }

    fun dismiss(method: ProDismissMethod) {
        viewModel.onDismissed(reason, method)
        onDismiss()
    }

    ModalBottomSheet(
        // Scrim tap, swipe down and system back all arrive here.
        onDismissRequest = { dismiss(ProDismissMethod.SWIPE) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = GharShapesExtra.SheetTop,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
    ) {
        Box {
            ProSheetContent(
                state = ProSheetState(
                    isPro, price, pending, unavailable,
                    canBuy = activity != null,
                    showNoAds = BuildConfig.ADS_SHIPPING && !hasRemoveAds,
                ),
                reason = reason,
                onBuy = { activity?.let { viewModel.buy(it, reason) } },
                onRestore = viewModel::restore,
                onClose = { dismiss(ProDismissMethod.CLOSE) },
                onNotNow = { dismiss(ProDismissMethod.NOT_NOW) },
            )
            SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
        }
    }
}

/** Stateless sheet body: gradient hero, benefit cards, always-free note, buy and restore. */
@Composable
internal fun ProSheetContent(
    state: ProSheetState,
    reason: ProReason,
    onBuy: () -> Unit,
    onRestore: () -> Unit,
    onClose: () -> Unit = {},
    onNotNow: () -> Unit = {},
) {
    val gk = MaterialTheme.gharColors
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
        // Hero
        Column(
            Modifier.fillMaxWidth().clip(GharShapesExtra.SheetTop)
                .background(Brush.verticalGradient(listOf(gk.heroVariant, gk.hero)))
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp),
        ) {
            Box(Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Box(
                    Modifier.align(Alignment.TopCenter).size(width = 32.dp, height = 4.dp)
                        .clip(CircleShape).background(gk.onHeroVariant.copy(alpha = 0.5f)),
                )
                IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).size(48.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.pro_prompt_close_cd), tint = gk.onHero)
                }
            }
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.size(72.dp).clip(BadgeShape).background(gk.lime), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(52.dp).clip(CircleShape).background(gk.hero), contentAlignment = Alignment.Center) {
                        Icon(CrownIcon, contentDescription = null, tint = gk.saffron, modifier = Modifier.size(32.dp))
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(if (reason == ProReason.AUTO_BILL_MILESTONE) R.string.pro_prompt_sheet_title else R.string.pro_title),
                        style = MaterialTheme.typography.headlineSmall, color = gk.onHero,
                    )
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(state.price ?: "—", style = GharNumerals.L, color = gk.lime, maxLines = 1)
                        Text(
                            stringResource(R.string.pro_price_note),
                            style = MaterialTheme.typography.bodyMedium, color = gk.onHeroVariant,
                            modifier = Modifier.weight(1f, fill = false).padding(bottom = 8.dp),
                        )
                    }
                }
            }
            Text(
                reasonText(reason),
                style = MaterialTheme.typography.bodyLarge, color = gk.onHeroVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BenefitCard(
                    Icons.Outlined.PersonAdd, gk.lime, gk.onLime,
                    stringResource(R.string.pro_benefit_providers),
                    stringResource(R.string.pro_benefit_providers_note, FREE_ACTIVE_PROVIDER_LIMIT),
                    Modifier.weight(1f),
                )
                BenefitCard(Icons.Outlined.Image, gk.saffronContainer, gk.onSaffronContainer, stringResource(R.string.pro_benefit_image), null, Modifier.weight(1f))
            }
            Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BenefitCard(Icons.Outlined.TableChart, cs.tertiaryContainer, cs.onTertiaryContainer, stringResource(R.string.pro_benefit_csv), null, Modifier.weight(1f))
                BenefitCard(Icons.Outlined.NotificationsActive, cs.primaryContainer, cs.onPrimaryContainer, stringResource(R.string.pro_benefit_reminder), null, Modifier.weight(1f))
            }
            BenefitCard(Icons.Outlined.CalendarViewMonth, gk.hero, gk.lime, stringResource(R.string.pro_benefit_year), null, Modifier.fillMaxWidth(), wide = true)
            if (state.showNoAds) {
                BenefitCard(Icons.Outlined.Block, cs.secondaryContainer, cs.onSecondaryContainer, stringResource(R.string.pro_benefit_no_ads), null, Modifier.fillMaxWidth(), wide = true)
            }

            Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(cs.primaryContainer).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Outlined.VerifiedUser, contentDescription = null, tint = cs.onPrimaryContainer)
                Text(stringResource(R.string.pro_always_free), style = MaterialTheme.typography.bodyLarge, color = cs.onPrimaryContainer)
            }

            when {
                state.isPro -> Text(
                    stringResource(R.string.pro_active), style = MaterialTheme.typography.titleMedium,
                    color = cs.primary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                )
                state.pending -> Text(stringResource(R.string.pro_pending), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 8.dp))
                state.unavailable && state.price == null -> Text(stringResource(R.string.pro_unavailable), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 8.dp))
                else -> {
                    val enabled = state.price != null && state.canBuy
                    Box(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(CircleShape)
                            .background(if (enabled) gk.hero else cs.surfaceContainerHigh)
                            .clickable(enabled = enabled, role = Role.Button, onClick = onBuy),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            state.price?.let { stringResource(R.string.pro_buy, it) } ?: stringResource(R.string.pro_loading),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (enabled) gk.lime else cs.onSurfaceVariant,
                        )
                    }
                }
            }
            TextButton(onClick = onNotNow, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.pro_prompt_not_now), style = MaterialTheme.typography.titleMedium)
            }
            Box(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onRestore),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.pro_restore), style = MaterialTheme.typography.titleMedium, color = cs.primary)
            }
        }
    }
}

@Composable
private fun reasonText(reason: ProReason): String = when (reason) {
    ProReason.PROVIDER_LIMIT -> stringResource(R.string.pro_reason_provider_limit, FREE_ACTIVE_PROVIDER_LIMIT)
    ProReason.BILL_IMAGE -> stringResource(R.string.pro_reason_bill_image)
    ProReason.CSV -> stringResource(R.string.pro_reason_csv)
    ProReason.YEAR_SUMMARY -> stringResource(R.string.pro_reason_year_summary)
    ProReason.REMINDER -> stringResource(R.string.pro_reason_reminder)
    ProReason.GENERAL, ProReason.AUTO_MARKED_DAYS_MILESTONE -> stringResource(R.string.pro_reason_general)
    ProReason.AUTO_BILL_MILESTONE -> stringResource(R.string.pro_prompt_sheet_subtitle)
}

@Composable
private fun BenefitCard(
    icon: ImageVector,
    badge: Color,
    onBadge: Color,
    title: String,
    note: String?,
    modifier: Modifier,
    wide: Boolean = false,
) {
    val cs = MaterialTheme.colorScheme
    val content: @Composable () -> Unit = {
        Box(Modifier.size(40.dp).clip(BadgeShape).background(badge), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = onBadge, modifier = Modifier.size(22.dp))
        }
        Column {
            Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium), color = cs.onSurface)
            if (note != null) Text(note, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp), color = cs.onSurfaceVariant)
        }
    }
    if (wide) {
        Row(
            modifier.clip(GharShapesExtra.Card).background(cs.surfaceContainerHigh).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) { content() }
    } else {
        Column(
            modifier.clip(GharShapesExtra.Card).background(cs.surfaceContainerHigh).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) { content() }
    }
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
