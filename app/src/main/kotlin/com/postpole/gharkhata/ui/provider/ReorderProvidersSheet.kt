package com.postpole.gharkhata.ui.provider

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.postpole.gharkhata.R
import com.postpole.gharkhata.data.ProviderEntity
import com.postpole.gharkhata.ui.theme.GharShapesExtra

/**
 * Bottom sheet to reorder the active providers with move-up / move-down buttons. [providers] are in the
 * current Providers-tab order. Nothing is written until Save; Cancel or swiping down discards.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReorderProvidersSheet(
    providers: List<ProviderEntity>,
    onSave: (List<Long>) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, shape = GharShapesExtra.SheetTop) {
        ReorderProvidersContent(providers, onSave = onSave, onCancel = onDismiss)
    }
}

/** Sheet body, separate from the window so tests can render it. */
@Composable
internal fun ReorderProvidersContent(
    providers: List<ProviderEntity>,
    onSave: (List<Long>) -> Unit,
    onCancel: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val byId = providers.associateBy { it.id }
    val initial = providers.map { it.id }
    var ids by rememberSaveable { mutableStateOf(initial) }
    var movedId by rememberSaveable { mutableStateOf<Long?>(null) }

    fun move(index: Int, delta: Int) {
        val target = index + delta
        if (target !in ids.indices) return
        ids = ids.toMutableList().also { java.util.Collections.swap(it, index, target) }
        movedId = ids[target]
    }

    Column(Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.providers_reorder_title),
            style = MaterialTheme.typography.titleLarge,
            color = cs.onSurface,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp),
        )
        val moved = movedId?.let { id -> byId[id]?.let { it to ids.indexOf(id) } }
        Text(
            text = moved?.let { (p, i) -> stringResource(R.string.providers_reorder_moved, p.name, i + 1, ids.size) }.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp).semantics { liveRegion = LiveRegionMode.Polite },
        )
        LazyColumn(Modifier.weight(1f, fill = false)) {
            itemsIndexed(ids, key = { _, id -> id }) { index, id ->
                val p = byId[id] ?: return@itemsIndexed
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ProviderAvatar(p.type, container = cs.primaryContainer, content = cs.onPrimaryContainer, size = 40.dp)
                    Text(
                        p.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = cs.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { move(index, -1) }, modifier = Modifier.size(48.dp), enabled = index > 0) {
                        Icon(
                            Icons.Filled.KeyboardArrowUp,
                            contentDescription = stringResource(R.string.providers_reorder_up_cd, p.name),
                            tint = cs.onSurfaceVariant.let { if (index > 0) it else it.copy(alpha = 0.38f) },
                        )
                    }
                    IconButton(onClick = { move(index, 1) }, modifier = Modifier.size(48.dp), enabled = index < ids.lastIndex) {
                        Icon(
                            Icons.Filled.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.providers_reorder_down_cd, p.name),
                            tint = cs.onSurfaceVariant.let { if (index < ids.lastIndex) it else it.copy(alpha = 0.38f) },
                        )
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.cancel))
            }
            Button(onClick = { onSave(ids) }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.providers_reorder_save))
            }
        }
    }
}
