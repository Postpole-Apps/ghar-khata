package com.postpole.gharkhata.ui.pro

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.postpole.gharkhata.R
import com.postpole.gharkhata.ui.theme.GharShapesExtra
import com.postpole.gharkhata.ui.theme.gharColors

/**
 * Dismissible tonal card for the automatic "marked days" Pro prompt (Providers only). [price] is the Play
 * Billing formatted price; without it the body drops the price sentence. No ads wording, ever.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProInlineCard(
    price: String?,
    onSeePro: () -> Unit,
    onNotNow: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val gk = MaterialTheme.gharColors
    val cs = MaterialTheme.colorScheme
    Column(
        modifier.fillMaxWidth().clip(GharShapesExtra.Card).background(cs.primaryContainer).padding(start = 16.dp, top = 8.dp, end = 4.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                stringResource(R.string.pro_prompt_card_title),
                style = MaterialTheme.typography.titleMedium,
                color = cs.onPrimaryContainer,
                modifier = Modifier.weight(1f).padding(top = 12.dp),
            )
            IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.pro_prompt_close_cd),
                    tint = cs.onPrimaryContainer,
                )
            }
        }
        Text(
            if (price != null) stringResource(R.string.pro_prompt_card_body, price) else stringResource(R.string.pro_prompt_card_body_no_price),
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onPrimaryContainer,
            modifier = Modifier.padding(end = 12.dp),
        )
        FlowRow(
            Modifier.fillMaxWidth().padding(top = 8.dp, end = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.heightIn(min = 48.dp).clip(CircleShape).background(gk.hero)
                    .clickable(role = Role.Button, onClick = onSeePro).padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.pro_prompt_see_pro), style = MaterialTheme.typography.labelLarge, color = gk.lime)
            }
            TextButton(onClick = onNotNow, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.pro_prompt_not_now), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
