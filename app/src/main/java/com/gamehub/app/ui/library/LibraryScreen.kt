package com.gamehub.app.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gamehub.app.R
import com.gamehub.app.data.model.LibraryStatus
import com.gamehub.app.data.model.SavedGame
import com.gamehub.app.ui.common.messageRes
import com.gamehub.app.ui.components.FilterPill
import com.gamehub.app.ui.components.GameCover
import com.gamehub.app.ui.components.RatingBadge

@Composable
fun LibraryScreen(viewModel: LibraryViewModel, onGameClick: (Int) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onResume()
    }

    Column(modifier = Modifier.fillMaxSize().padding(top = 16.dp)) {
        Text(stringResource(R.string.library_title), style = MaterialTheme.typography.headlineMedium, color = colors.onBackground, modifier = Modifier.padding(horizontal = 20.dp))
        Text(
            text = stringResource(R.string.library_count, state.allGames.size),
            style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(Modifier.height(12.dp))

        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { FilterPill(text = stringResource(R.string.library_all), selected = state.selectedFilter == null, onClick = { viewModel.selectFilter(null) }) }
            items(LibraryStatus.entries) { status ->
                FilterPill(text = stringResource(status.labelRes()), selected = state.selectedFilter == status, onClick = { viewModel.selectFilter(status) })
            }
        }
        Spacer(Modifier.height(16.dp))

        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = colors.primary) }
            state.error != null && state.allGames.isEmpty() -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(state.error!!.messageRes()), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = viewModel::retry) { Text(stringResource(R.string.common_retry)) }
                }
            }
            state.visibleGames.isEmpty() -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.library_empty), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
            else -> LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.visibleGames, key = { it.gameId }) { game ->
                    SavedGameRow(game = game, onClick = { onGameClick(game.gameId) }, onRemove = { viewModel.removeGame(game.gameId) })
                }
            }
        }
    }
}

/** Row used by Library, Wishlist and Favourites. [onMoveToLibrary] is only shown for wishlist items. */
@Composable
internal fun SavedGameRow(
    game: SavedGame,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onMoveToLibrary: (() -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surfaceVariant)
            .clickable(onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(width = 56.dp, height = 72.dp).clip(RoundedCornerShape(10.dp))) {
            GameCover(url = game.coverUrl, modifier = Modifier.fillMaxWidth().fillMaxWidth())
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(game.name, style = MaterialTheme.typography.titleMedium, color = colors.onSurface, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                game.releaseYear?.let {
                    Text("$it", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    Spacer(Modifier.width(6.dp))
                }
                RatingBadge(rating = game.rating)
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                game.status?.let { status ->
                    Text(
                        text = stringResource(status.labelRes()),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onPrimaryContainer,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(colors.primaryContainer).padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                game.platforms?.let { platforms ->
                    Text(text = platforms, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1)
                }
            }
        }
        if (onMoveToLibrary != null) {
            IconButton(onClick = onMoveToLibrary) {
                Icon(Icons.Filled.LibraryAdd, contentDescription = stringResource(R.string.wishlist_move_to_library), tint = colors.primary)
            }
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.DeleteOutline, contentDescription = stringResource(R.string.common_remove), tint = colors.onSurfaceVariant)
        }
    }
}

private fun LibraryStatus.labelRes(): Int = when (this) {
    LibraryStatus.WANT_TO_PLAY -> R.string.library_want_to_play
    LibraryStatus.PLAYING -> R.string.library_playing
    LibraryStatus.COMPLETED -> R.string.library_completed
}