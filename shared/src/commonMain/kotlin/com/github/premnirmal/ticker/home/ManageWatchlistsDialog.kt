package com.github.premnirmal.ticker.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.github.premnirmal.shared.resources.Res
import com.github.premnirmal.shared.resources.cancel
import com.github.premnirmal.shared.resources.create
import com.github.premnirmal.shared.resources.delete
import com.github.premnirmal.shared.resources.delete_watchlist_message
import com.github.premnirmal.shared.resources.done
import com.github.premnirmal.shared.resources.ic_add
import com.github.premnirmal.shared.resources.ic_done
import com.github.premnirmal.shared.resources.ic_more_vert
import com.github.premnirmal.shared.resources.manage_watchlists
import com.github.premnirmal.shared.resources.manage_watchlists_subtitle
import com.github.premnirmal.shared.resources.new_watchlist
import com.github.premnirmal.shared.resources.rename_watchlist
import com.github.premnirmal.shared.resources.save
import com.github.premnirmal.shared.resources.watchlist_exists
import com.github.premnirmal.shared.resources.watchlist_name
import com.github.premnirmal.shared.resources.watchlist_name_blank
import com.github.premnirmal.ticker.portfolio.search.CreateWatchlistError
import com.github.premnirmal.tickerwidget.ui.Divider
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** A manageable watchlist row (the master "All Symbols" list is excluded by the caller). */
data class ManageWatchlistItem(
    val id: Long,
    val name: String,
)

/**
 * Shared (Compose Multiplatform) "Manage Watchlists" dialog reached from the home watchlist-selector
 * dropdown (mirrors the macOS Stocks manage sheet). Lists the user's watchlists - All Symbols is
 * omitted by the caller since it can't be renamed or deleted - with a tap-to-rename row, a per-row
 * delete (with confirmation), and a "New Watchlist" action.
 *
 * Stateless: [watchlists] and the create/rename/delete/dismiss events are hoisted. [onCreate] and
 * [onRename] return a [CreateWatchlistError] to display inline (or 'null' on success). Deleting a
 * watchlist only removes the list; its symbols remain in All Symbols.
 */
@Composable
fun ManageWatchlistsDialogContent(
    watchlists: List<ManageWatchlistItem>,
    onCreate: (String) -> CreateWatchlistError?,
    onRename: (Long, String) -> CreateWatchlistError?,
    onDelete: (Long) -> Unit,
    onDismissRequest: () -> Unit,
) {
    var showNewWatchlist by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<ManageWatchlistItem?>(null) }
    var deleteTarget by remember { mutableStateOf<ManageWatchlistItem?>(null) }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        LazyColumn(
            modifier = Modifier
                .background(color = MaterialTheme.colorScheme.surface)
                .padding(all = 16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        modifier = Modifier.weight(1f),
                        text = stringResource(Res.string.manage_watchlists),
                        style = MaterialTheme.typography.headlineSmall
                    )
                    FilledIconButton(onClick = onDismissRequest) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_done),
                            contentDescription = stringResource(Res.string.done),
                        )
                    }
                }
                Text(
                    text = stringResource(Res.string.manage_watchlists_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
            }
            items(watchlists.size) { i ->
                val watchlist = watchlists[i]
                var menuExpanded by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 8.dp),
                        text = watchlist.name,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                    )
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_more_vert),
                                contentDescription = watchlist.name,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(text = stringResource(Res.string.rename_watchlist)) },
                                onClick = {
                                    menuExpanded = false
                                    renameTarget = watchlist
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(text = stringResource(Res.string.delete)) },
                                onClick = {
                                    menuExpanded = false
                                    deleteTarget = watchlist
                                },
                            )
                        }
                    }
                }
                Divider(modifier = Modifier.fillMaxWidth())
            }
            item {
                OutlinedButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    onClick = { showNewWatchlist = true },
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_add),
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(text = stringResource(Res.string.new_watchlist))
                }
            }
        }
    }

    if (showNewWatchlist) {
        WatchlistNameDialog(
            title = stringResource(Res.string.new_watchlist),
            confirmLabel = stringResource(Res.string.create),
            initialName = "",
            onSubmit = onCreate,
            onDismissRequest = { showNewWatchlist = false },
        )
    }

    renameTarget?.let { target ->
        WatchlistNameDialog(
            title = stringResource(Res.string.rename_watchlist),
            confirmLabel = stringResource(Res.string.save),
            initialName = target.name,
            onSubmit = { name -> onRename(target.id, name) },
            onDismissRequest = { renameTarget = null },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(text = stringResource(Res.string.delete)) },
            text = { Text(text = stringResource(Res.string.delete_watchlist_message, target.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(target.id)
                        deleteTarget = null
                    }
                ) {
                    Text(
                        text = stringResource(Res.string.delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(text = stringResource(Res.string.cancel))
                }
            },
        )
    }
}

/**
 * The direct "New Watchlist" action (from the watchlist-selector dropdown): a create-name prompt
 * with the shared labels, so hosts don't deal with string resources.
 */
@Composable
fun NewWatchlistPrompt(
    onCreate: (String) -> CreateWatchlistError?,
    onDismissRequest: () -> Unit,
) {
    WatchlistNameDialog(
        title = stringResource(Res.string.new_watchlist),
        confirmLabel = stringResource(Res.string.create),
        initialName = "",
        onSubmit = onCreate,
        onDismissRequest = onDismissRequest,
    )
}

/**
 * Name-entry dialog shared by the "New Watchlist" and "Rename Watchlist" actions. Calls [onSubmit]
 * with the entered name; if it returns a [CreateWatchlistError] the message is shown inline and the
 * dialog stays open, otherwise the dialog closes.
 */
@Composable
fun WatchlistNameDialog(
    title: String,
    confirmLabel: String,
    initialName: String,
    onSubmit: (String) -> CreateWatchlistError?,
    onDismissRequest: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var error by remember { mutableStateOf<CreateWatchlistError?>(null) }

    val submit = {
        when (val result = onSubmit(name)) {
            null -> onDismissRequest()
            else -> error = result
        }
    }

    Dialog(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .background(color = MaterialTheme.colorScheme.surface)
                .padding(all = 16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = name,
                onValueChange = {
                    name = it
                    error = null
                },
                singleLine = true,
                label = { Text(text = stringResource(Res.string.watchlist_name)) },
                isError = error != null,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
            )
            error?.let {
                Text(
                    modifier = Modifier.padding(top = 4.dp),
                    text = when (it) {
                        CreateWatchlistError.BLANK -> stringResource(Res.string.watchlist_name_blank)
                        CreateWatchlistError.DUPLICATE -> stringResource(Res.string.watchlist_exists)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(Res.string.cancel))
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = submit) {
                    Text(text = confirmLabel)
                }
            }
        }
    }
}