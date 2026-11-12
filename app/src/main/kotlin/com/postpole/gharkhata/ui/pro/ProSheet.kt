package com.postpole.gharkhata.ui.pro

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.postpole.gharkhata.R
import com.postpole.gharkhata.billing.BillingMessage
import com.postpole.gharkhata.ui.provider.FREE_ACTIVE_PROVIDER_LIMIT
import com.postpole.gharkhata.ui.theme.GharShapesExtra

/** Pro upsell as a bottom sheet (used when a free user hits the provider limit). Same purchase flow as [ProScreen]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProSheet(onDismiss: () -> Unit, viewModel: ProViewModel = hiltViewModel()) {
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
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = GharShapesExtra.SheetTop,
    ) {
        Column(Modifier.navigationBarsPadding()) {
            Text(
                stringResource(R.string.provider_limit_message, FREE_ACTIVE_PROVIDER_LIMIT),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            ProContent(viewModel, Modifier.padding(16.dp))
            SnackbarHost(snackbarHostState)
        }
    }
}
