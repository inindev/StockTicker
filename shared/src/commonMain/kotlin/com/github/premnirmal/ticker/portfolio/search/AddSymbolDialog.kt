package com.github.premnirmal.ticker.portfolio.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledIconButton
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.github.premnirmal.shared.resources.Res
import com.github.premnirmal.shared.resources.add_to_watchlist
import com.github.premnirmal.shared.resources.add_to_watchlist_subtitle
import com.github.premnirmal.shared.resources.cancel
import com.github.premnirmal.shared.resources.create
import com.github.premnirmal.shared.resources.ic_add
import com.github.premnirmal.shared.resources.ic_done
import com.github.premnirmal.shared.resources.new_watchlist
import com.github.premnirmal.shared.resources.watchlist_exists
import com.github.premnirmal.shared.resources.watchlist_name
import com.github.premnirmal.shared.resources.watchlist_name_blank
import com.github.premnirmal.tickerwidget.ui.Divider
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Snapshot of the watchlists a symbol could be added to, shared between platforms (it has no
 * Android/Glance dependency). The Android 'SuggestionViewModel' produces it; the shared
 * [AddSymbolDialogContent] renders it.
 */
data class SuggestionState(
    val symbol: String,
    val widgetDataList: List<SuggestionWidgetDataState>
)

data class SuggestionWidgetDataState(
    val symbol: String,
    val watchlistName: String,
    val watchlistId: Long,
    val exists: Boolean,
)

/** Reason a [AddSymbolDialogContent] "New Watchlist" creation was rejected. */
enum class CreateWatchlistError {
    BLANK,
    DUPLICATE,
}

/**
 * Shared (Compose Multiplatform) "Add to Watchlist" dialog: it lists the user's watchlists with a
 * leading checkbox (checked = the symbol is in that list) and offers a "New Watchlist" action that
 * creates a list and adds the symbol to it.
 *
 * It is stateless: the [suggestionState] and the add/remove/create/dismiss events are hoisted so the
 * dialog carries no dependency-injection or platform code. [onCreateWatchlist] creates a watchlist by
 * name and returns a [CreateWatchlistError] to display (or 'null' on success). The Android
 * 'AddSymbolDialog' host resolves the Koin 'SuggestionViewModel' and delegates here.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AddSymbolDialogContent(
    suggestionState: SuggestionState,
    onDismissRequest: () -> Unit,
    onRemoved: (SuggestionWidgetDataState) -> Unit,
    onAdded: (SuggestionWidgetDataState) -> Unit,
    onCreateWatchlist: (String) -> CreateWatchlistError?,
) {
    val openDialog = remember { mutableStateOf(true) }
    if (!openDialog.value) return

    var showNewWatchlist by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        LazyColumn(
            modifier = Modifier
                .background(color = MaterialTheme.colorScheme.surface)
                .padding(all = 16.dp)
        ) {
            stickyHeader {
                Column(
                    modifier = Modifier.background(color = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            modifier = Modifier.weight(1f),
                            text = stringResource(Res.string.add_to_watchlist),
                            style = MaterialTheme.typography.headlineSmall
                        )
                        FilledIconButton(
                            onClick = {
                                onDismissRequest()
                                openDialog.value = false
                            }
                        ) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_done),
                                contentDescription = stringResource(Res.string.add_to_watchlist),
                            )
                        }
                    }
                    Text(
                        text = stringResource(
                            Res.string.add_to_watchlist_subtitle,
                            suggestionState.symbol
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                    )
                }
            }
            items(suggestionState.widgetDataList.size) { i ->
                val widgetData = suggestionState.widgetDataList[i]
                val exists = remember(widgetData) { widgetData.exists }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = exists,
                        onCheckedChange = {
                            if (exists) {
                                onRemoved(widgetData)
                            } else {
                                onAdded(widgetData)
                            }
                        },
                    )
                    Text(
                        modifier = Modifier.weight(1f),
                        text = AnnotatedString(widgetData.watchlistName),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                    )
                }

                Divider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                )
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
        NewWatchlistDialog(
            onDismissRequest = { showNewWatchlist = false },
            onCreate = onCreateWatchlist,
        )
    }
}

/**
 * Name-entry dialog for creating a new watchlist. Calls [onCreate] with the entered name; if it
 * returns a [CreateWatchlistError] the message is shown inline and the dialog stays open, otherwise
 * the dialog closes.
 */
@Composable
private fun NewWatchlistDialog(
    onDismissRequest: () -> Unit,
    onCreate: (String) -> CreateWatchlistError?,
) {
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<CreateWatchlistError?>(null) }

    val submit = {
        when (val result = onCreate(name)) {
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
                text = stringResource(Res.string.new_watchlist),
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
                    Text(text = stringResource(Res.string.create))
                }
            }
        }
    }
}