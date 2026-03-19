package com.dustvalve.next.android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import com.dustvalve.next.android.R

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RecentSearchesList(
    recentSearches: List<String>,
    onSearchClick: (String) -> Unit,
    onRemoveClick: (String) -> Unit,
    onClearAllClick: () -> Unit,
) {
    LazyColumn {
        item(key = "recent_header") {
            ListItem(
                headlineContent = {
                    Text(
                        text = "Recent searches",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                trailingContent = {
                    TextButton(onClick = onClearAllClick) {
                        Text("Clear all")
                    }
                },
            )
        }
        items(
            items = recentSearches,
            key = { it },
        ) { query ->
            ListItem(
                headlineContent = {
                    Text(
                        text = query,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                leadingContent = {
                    Icon(
                        painter = painterResource(R.drawable.ic_history),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                trailingContent = {
                    IconButton(onClick = { onRemoveClick(query) }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = "Remove",
                        )
                    }
                },
                modifier = Modifier
                    .animateItem()
                    .clickable { onSearchClick(query) },
            )
        }
    }
}
