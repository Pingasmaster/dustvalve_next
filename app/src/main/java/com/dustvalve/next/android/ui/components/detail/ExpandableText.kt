package com.dustvalve.next.android.ui.components.detail

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dustvalve.next.android.R

/**
 * M3E-idiomatic "expand to show more text" pattern (no first-party component
 * exists). Multi-line `Text` with overflow detection driving a chevron + label
 * row; rotation animates with `MotionScheme.fastEffectsSpec`, height reflow
 * animates with `MotionScheme.defaultSpatialSpec`. The trigger is hidden when
 * the text fits without ellipsis.
 *
 * Used by every detail screen (album about, artist bio, collection
 * description). Moved here from `ui/screens/album/` so the three detail
 * screens can share it without cross-screen `internal` imports.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpandableText(
    text: String,
    collapsedMaxLines: Int,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable(text) { mutableStateOf(false) }
    var hasOverflow by remember(text) { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "show_more_chevron",
    )

    Column(
        modifier = modifier
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .animateContentSize(
                animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
            ),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (expanded) Int.MAX_VALUE else collapsedMaxLines,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { result ->
                if (!expanded) hasOverflow = result.hasVisualOverflow
            },
        )
        if (hasOverflow || expanded) {
            val expandLabel = stringResource(R.string.detail_show_more)
            val collapseLabel = stringResource(R.string.detail_show_less)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clickable(
                        onClickLabel = if (expanded) collapseLabel else expandLabel,
                    ) { expanded = !expanded }
                    .semantics(mergeDescendants = true) {
                        if (expanded) collapse { expanded = false; true }
                        else expand { expanded = true; true }
                    }
                    .padding(horizontal = 4.dp, vertical = 4.dp),
            ) {
                Text(
                    text = if (expanded) collapseLabel else expandLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Icon(
                    painter = painterResource(R.drawable.ic_expand_more),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(rotation),
                )
            }
        }
    }
}
