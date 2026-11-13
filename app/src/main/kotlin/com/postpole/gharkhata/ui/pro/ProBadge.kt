package com.postpole.gharkhata.ui.pro

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.postpole.gharkhata.R
import com.postpole.gharkhata.ui.theme.gharColors

// There is no Pro screen: Pro is the ModalBottomSheet in ProSheet.kt. This file holds the small
// helpers screens use to decide whether to show locked state and to mark Pro-only actions.

/** Current Pro entitlement from [ProViewModel]. */
@Composable
fun rememberIsPro(viewModel: ProViewModel = hiltViewModel()): Boolean =
    viewModel.isPro.collectAsStateWithLifecycle().value

/** Small saffron "Pro" tag shown next to Pro-only actions while the user is on the free plan. */
@Composable
fun ProBadge(modifier: Modifier = Modifier, showLock: Boolean = true) {
    val gk = MaterialTheme.gharColors
    Row(
        modifier.clip(CircleShape).background(gk.saffronContainer).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showLock) {
            Icon(Icons.Outlined.Lock, contentDescription = null, tint = gk.onSaffronContainer, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(stringResource(R.string.pro_badge), style = MaterialTheme.typography.labelLarge, color = gk.onSaffronContainer, maxLines = 1, softWrap = false)
    }
}
