package com.dustvalve.next.android.ui.components.sheet

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.dustvalve.next.android.R
import com.dustvalve.next.android.domain.model.ExportableTrack
import com.dustvalve.next.android.domain.model.TrackSource
import com.dustvalve.next.android.ui.theme.AppShapes

/**
 * Bottom sheet that lists every downloaded track with multi-select and a
 * Material 3 Expressive [ExtendedFloatingActionButton] for exporting the
 * current selection. The overflow menu adds shortcuts for "Export all",
 * "Select all", and "Deselect all".
 *
 * Selection state is internal and survives configuration changes.
 *
 * @param tracks The downloaded tracks the user can pick from.
 * @param onDismiss Called when the user dismisses the sheet.
 * @param onExport Invoked with the set of selected track IDs to export. For
 *   "Export all" this is every id in [tracks] regardless of current selection.
 */
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
)
@Composable
fun ExportTracksSheet(
    tracks: List<ExportableTrack>,
    onDismiss: () -> Unit,
    onExport: (selectedIds: Set<String>) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        ExportTracksSheetContent(
            tracks = tracks,
            onExport = onExport,
        )
    }
}

/**
 * Stateful body of [ExportTracksSheet]. Holds the selection / overflow state
 * and forwards user actions to [ExportTracksSheetBody].
 *
 * @param overflowMenuRenderer hook to swap [DropdownMenu] for a non-popup
 *   surface in tests; defaults to the production [DropdownMenu].
 */
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
)
@Composable
internal fun ExportTracksSheetContent(
    tracks: List<ExportableTrack>,
    onExport: (selectedIds: Set<String>) -> Unit,
    overflowMenuRenderer: @Composable (
        expanded: Boolean,
        onDismiss: () -> Unit,
        items: @Composable () -> Unit,
    ) -> Unit = DefaultOverflowMenu,
) {
    var selected by rememberSaveable { mutableStateOf<Set<String>>(emptySet()) }
    var showOverflow by remember { mutableStateOf(false) }
    ExportTracksSheetBody(
        tracks = tracks,
        selected = selected,
        showOverflow = showOverflow,
        onToggleTrack = { id ->
            selected = if (id in selected) selected - id else selected + id
        },
        onSelectAll = { selected = tracks.map { it.track.id }.toSet() },
        onDeselectAll = { selected = emptySet() },
        onExportSelected = { onExport(selected) },
        onExportAll = { onExport(tracks.map { it.track.id }.toSet()) },
        onShowOverflow = { showOverflow = true },
        onHideOverflow = { showOverflow = false },
        overflowMenuRenderer = overflowMenuRenderer,
    )
}

/**
 * Stateless body of the Export Tracks sheet — easy to drive from unit tests
 * without going through [DropdownMenu]'s popup window or relying on internal
 * state machinery.
 */
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
)
@Composable
internal fun ExportTracksSheetBody(
    tracks: List<ExportableTrack>,
    selected: Set<String>,
    showOverflow: Boolean,
    onToggleTrack: (String) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onExportSelected: () -> Unit,
    onExportAll: () -> Unit,
    onShowOverflow: () -> Unit,
    onHideOverflow: () -> Unit,
    overflowMenuRenderer: @Composable (
        expanded: Boolean,
        onDismiss: () -> Unit,
        items: @Composable () -> Unit,
    ) -> Unit = DefaultOverflowMenu,
) {
    // We size the LazyColumn to wrap its rows (~96 dp each) up to a sensible
    // ceiling, then the bottom action Row sits below. This avoids weight()
    // shenanigans inside ModalBottomSheet (which itself wraps content).
    val maxListHeight = (tracks.size.coerceAtMost(8) * 110).dp
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.export_tracks_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(
                    R.string.export_tracks_count,
                    selected.size,
                    tracks.size,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxListHeight),
        ) {
            items(items = tracks, key = { it.track.id }) { exportable ->
                ExportTrackRow(
                    exportable = exportable,
                    isSelected = exportable.track.id in selected,
                    onToggle = { onToggleTrack(exportable.track.id) },
                    modifier = Modifier.animateItem(),
                )
            }
        }

        // Bottom action row holds the FAB + overflow menu.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            val canExport = selected.isNotEmpty()
            ExtendedFloatingActionButton(
                onClick = { if (canExport) onExportSelected() },
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_folder_open),
                        contentDescription = null,
                    )
                },
                text = {
                    Text(
                        stringResource(
                            R.string.export_tracks_export_selected,
                            selected.size,
                        ),
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .semantics {
                        if (!canExport) disabled()
                    }
                    .testTag(TestTags.ExportFab),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box {
                IconButton(
                    onClick = onShowOverflow,
                    modifier = Modifier.testTag(TestTags.OverflowButton),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_more_vert),
                        contentDescription = stringResource(R.string.export_tracks_more_options),
                    )
                }
                overflowMenuRenderer(
                    showOverflow,
                    onHideOverflow,
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.export_tracks_export_all)) },
                        onClick = {
                            onHideOverflow()
                            onExportAll()
                        },
                        modifier = Modifier.testTag(TestTags.ExportAll),
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.export_tracks_select_all)) },
                        onClick = {
                            onHideOverflow()
                            onSelectAll()
                        },
                        modifier = Modifier.testTag(TestTags.SelectAll),
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.export_tracks_deselect_all)) },
                        onClick = {
                            onHideOverflow()
                            onDeselectAll()
                        },
                        modifier = Modifier.testTag(TestTags.DeselectAll),
                    )
                }
            }
        }
    }
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
)
@Composable
private fun ExportTrackRow(
    exportable: ExportableTrack,
    isSelected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val track = exportable.track
    Surface(
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onToggle,
                onLongClick = null,
            )
            .testTag("${TestTags.RowPrefix}${track.id}"),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            ListItem(
                headlineContent = {
                    Text(
                        text = track.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                supportingContent = {
                    Text(
                        text = "${track.artist} • ${track.albumTitle}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                leadingContent = {
                    AsyncImage(
                        model = track.artUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(AppShapes.SearchResultTrack),
                    )
                },
                trailingContent = {
                    Checkbox(
                        checked = isSelected,
                        // Toggle is driven by the row's combinedClickable; the
                        // checkbox visual is purely an indicator. Ignoring the
                        // onCheckedChange event prevents double-toggling.
                        onCheckedChange = { onToggle() },
                        modifier = Modifier.testTag("${TestTags.CheckboxPrefix}${track.id}"),
                    )
                },
                colors = ListItemDefaults.colors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 80.dp, end = 16.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AssistChip(
                    onClick = onToggle,
                    label = { Text(platformLabel(track.source)) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(platformIcon(track.source)),
                            contentDescription = null,
                            modifier = Modifier.size(AssistChipDefaults.IconSize),
                        )
                    },
                )
                AssistChip(
                    onClick = onToggle,
                    label = { Text(stringResource(exportable.format.displayNameRes)) },
                )
                AssistChip(
                    onClick = onToggle,
                    label = { Text(exportable.qualityLabel) },
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

private fun platformLabel(source: TrackSource): String = when (source) {
    TrackSource.LOCAL -> "Local"
    TrackSource.BANDCAMP -> "Bandcamp"
    TrackSource.YOUTUBE -> "Youtube"
    TrackSource.SPOTIFY -> "Spotify"
}

private fun platformIcon(source: TrackSource): Int = when (source) {
    TrackSource.SPOTIFY -> R.drawable.ic_spotify
    // Bandcamp / YouTube / Local fall back to a generic music note icon
    // until dedicated brand drawables ship.
    else -> R.drawable.ic_music_note
}

/**
 * Production renderer for the overflow menu — a real [DropdownMenu]. Tests
 * can swap in a non-popup version so the menu items live in the main test
 * composition tree.
 */
private val DefaultOverflowMenu: @Composable (
    expanded: Boolean,
    onDismiss: () -> Unit,
    items: @Composable () -> Unit,
) -> Unit = { expanded, onDismiss, items ->
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        content = { items() },
    )
}

/** Test tags for use by ExportTracksSheetTest. */
internal object TestTags {
    const val ExportFab = "export_tracks_fab"
    const val OverflowButton = "export_tracks_overflow"
    const val ExportAll = "export_tracks_menu_export_all"
    const val SelectAll = "export_tracks_menu_select_all"
    const val DeselectAll = "export_tracks_menu_deselect_all"
    const val RowPrefix = "export_tracks_row_"
    const val CheckboxPrefix = "export_tracks_checkbox_"
}
