package com.dustvalve.next.android.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dustvalve.next.android.R
import com.dustvalve.next.android.util.DetectedLink
import com.dustvalve.next.android.util.LinkResourceType

/**
 * Inline action affordance shown under any search bar when a pasted link is detected.
 * Uses an M3 [AssistChip] (the semantic for a dynamic, contextual "smart action"), a tonal
 * secondary-container look, and motion-scheme spring enter/exit — matching house style.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PastedLinkChip(detected: DetectedLink?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    // Retain the last non-null link so the exit animation can keep rendering it.
    val shown = remember { mutableStateOf<DetectedLink?>(null) }
    if (detected != null) shown.value = detected
    val link = shown.value

    AnimatedVisibility(
        visible = detected != null,
        modifier = modifier,
        enter = expandVertically(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()) +
            fadeIn(animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()),
        exit = shrinkVertically(animationSpec = MaterialTheme.motionScheme.fastSpatialSpec()) +
            fadeOut(animationSpec = MaterialTheme.motionScheme.fastEffectsSpec()),
    ) {
        if (link == null) return@AnimatedVisibility
        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val pressScale by animateFloatAsState(
            targetValue = if (isPressed) 0.97f else 1f,
            animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
            label = "linkChipPressScale",
        )

        AssistChip(
            onClick = onClick,
            label = { Text(stringResource(labelRes(link.type))) },
            leadingIcon = {
                Icon(
                    painter = painterResource(iconRes(link.type)),
                    contentDescription = null,
                    modifier = Modifier.size(AssistChipDefaults.IconSize),
                )
            },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                leadingIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
            border = null,
            interactionSource = interactionSource,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                },
        )
    }
}

private fun labelRes(type: LinkResourceType): Int = when (type) {
    LinkResourceType.VIDEO -> R.string.link_open_video
    LinkResourceType.SONG -> R.string.link_open_song
    LinkResourceType.PLAYLIST -> R.string.link_open_playlist
    LinkResourceType.ALBUM -> R.string.link_open_album
    LinkResourceType.ARTIST -> R.string.link_open_artist
    LinkResourceType.TRACK -> R.string.link_open_track
}

private fun iconRes(type: LinkResourceType): Int = when (type) {
    LinkResourceType.VIDEO, LinkResourceType.SONG -> R.drawable.ic_play_circle
    else -> R.drawable.ic_open_in_new
}
