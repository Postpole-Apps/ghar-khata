package com.postpole.gharkhata.ui.provider

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.postpole.gharkhata.R
import com.postpole.gharkhata.data.BillingMode
import com.postpole.gharkhata.data.ProviderEntity
import com.postpole.gharkhata.domain.Money
import com.postpole.gharkhata.ui.pro.ProSheet
import com.postpole.gharkhata.ui.theme.GharNumerals
import com.postpole.gharkhata.ui.theme.GharShapesExtra
import com.postpole.gharkhata.ui.theme.gharColors

/** Providers list: active first, then archived. Rows show this month's per-provider total only. */
@Composable
fun ProviderListScreen(
    onOpenProvider: (Long) -> Unit,
    onAddProvider: () -> Unit,
    viewModel: ProviderListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val gk = MaterialTheme.gharColors
    var showPro by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(gk.hero, gk.heroVariant)))
            .statusBarsPadding(),
    ) {
        Text(
            stringResource(R.string.providers_title),
            style = MaterialTheme.typography.headlineMedium,
            color = gk.onHero,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        )
        if (!state.isPro) {
            FreeLimitCard(used = state.active.size, onSeePro = { showPro = true })
        }
        Column(
            Modifier.padding(top = 16.dp).fillMaxSize().clip(GharShapesExtra.SheetTop)
                .background(MaterialTheme.colorScheme.surface),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.providers_active_header, state.active.size),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier.heightIn(min = 48.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable(role = Role.Button) { if (state.canAdd) onAddProvider() else showPro = true }
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("＋ " + stringResource(R.string.providers_add), style = MaterialTheme.typography.labelLarge)
                }
            }
            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.active.isEmpty() && state.archived.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.providers_empty),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                }
                items(state.active.size, key = { state.active[it].provider.id }) { i ->
                    ActiveRow(state.active[i], style = i % 3, onClick = { onOpenProvider(state.active[i].provider.id) })
                }
                if (state.archived.isNotEmpty()) {
                    item(key = "archived-header") {
                        Text(
                            stringResource(R.string.providers_archived_header),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                        )
                    }
                    items(state.archived, key = { "a${it.id}" }) { p ->
                        ArchivedRow(p, onClick = { onOpenProvider(p.id) })
                    }
                }
            }
        }
    }
    if (showPro) ProSheet(onDismiss = { showPro = false })
}

@Composable
private fun FreeLimitCard(used: Int, onSeePro: () -> Unit) {
    val gk = MaterialTheme.gharColors
    Row(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(GharShapesExtra.Card)
            .background(gk.onHero.copy(alpha = 0.10f)).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.providers_used_count, used, FREE_ACTIVE_PROVIDER_LIMIT),
            style = GharNumerals.M,
            color = if (used >= FREE_ACTIVE_PROVIDER_LIMIT) gk.lime else gk.onHero,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.providers_free_used), style = MaterialTheme.typography.titleSmall, color = gk.onHero)
            Text(stringResource(R.string.providers_free_hint), style = MaterialTheme.typography.bodySmall, color = gk.onHeroVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(FREE_ACTIVE_PROVIDER_LIMIT) { i ->
                    Box(
                        Modifier.width(28.dp).height(6.dp).clip(CircleShape)
                            .background(if (i < used) gk.lime else gk.onHero.copy(alpha = 0.2f)),
                    )
                }
            }
        }
        Box(
            Modifier.heightIn(min = 48.dp).clip(CircleShape).background(gk.lime)
                .clickable(role = Role.Button, onClick = onSeePro).padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.providers_see_pro), color = gk.onLime, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ActiveRow(row: ProviderRow, style: Int, onClick: () -> Unit) {
    val gk = MaterialTheme.gharColors
    val cs = MaterialTheme.colorScheme
    // 0 = lime card, 1 = hero card, 2 = neutral card (as in the mockup); text on lime is always onLime.
    val bg: Modifier = when (style) {
        0 -> Modifier.background(Brush.linearGradient(listOf(gk.lime, gk.limeDim)))
        1 -> Modifier.background(gk.hero)
        else -> Modifier.background(cs.surfaceContainerHigh)
    }
    val fg = when (style) { 0 -> gk.onLime; 1 -> gk.onHero; else -> cs.onSurface }
    val delivered = when (style) { 0 -> gk.onLime; 1 -> gk.lime; else -> cs.primary }
    val p = row.provider
    Column(
        Modifier.fillMaxWidth().clip(GharShapesExtra.Card).then(bg).clickable(role = Role.Button, onClick = onClick).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(fg.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Text(p.type.glyph(), style = MaterialTheme.typography.titleLarge)
            }
            Column(Modifier.weight(1f)) {
                Text(p.name, style = MaterialTheme.typography.titleMedium, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle(p, row.rateLabel), style = MaterialTheme.typography.bodySmall, color = fg.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Money.format(row.monthTotalPaise), style = GharNumerals.S, color = fg)
                Text(stringResource(R.string.providers_this_month), style = MaterialTheme.typography.bodySmall, color = fg.copy(alpha = 0.8f))
            }
        }
        Row(Modifier.fillMaxWidth().height(14.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            row.strip.forEach { day ->
                val color = when (day) {
                    StripDay.DELIVERED -> delivered
                    StripDay.ABSENT -> cs.error
                    StripDay.HALF -> gk.saffron
                    StripDay.NONE -> fg.copy(alpha = 0.18f)
                }
                Box(Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(50)).background(color))
            }
        }
    }
}

@Composable
private fun subtitle(p: ProviderEntity, rate: String): String {
    val type = stringResource(p.type.labelRes())
    return "$type · $rate"
}

private val ProviderRow.rateLabel: String
    get() = ratePaise?.let {
        when (provider.billingMode) {
            BillingMode.MONTHLY_FIXED -> "${Money.format(it)}/mo"
            else -> "${Money.format(it)}/${provider.unitLabel}"
        }
    } ?: ""

@Composable
private fun ArchivedRow(p: ProviderEntity, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(GharShapesExtra.Card).background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(role = Role.Button, onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
            Text(p.type.glyph(), style = MaterialTheme.typography.titleLarge)
        }
        Column(Modifier.weight(1f)) {
            Text(p.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                stringResource(R.string.providers_archived_subtitle, stringResource(p.type.labelRes())),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
