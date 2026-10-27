package com.gamehub.app.ui.wishlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gamehub.app.R
import com.gamehub.app.ui.common.messageRes
import com.gamehub.app.ui.components.FilterPill
import com.gamehub.app.ui.library.SavedGameRow

@Composable
fun WishlistScreen(viewModel: WishlistViewModel, onGameClick: (Int) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refresh()
    }

    Column(modifier = Modifier.fillMaxSize().padding(top = 16.dp)) {
        Text(stringResource(R.string.wishlist_title), style = MaterialTheme.typography.headlineMedium, color = colors.onBackground, modifier = Modifier.padding(horizontal = 20.dp))
        Spacer(Modifier.height(12.dp))

        Row(modifier = Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterPill(text = stringResource(R.string.wishlist_tab_wishlist), selected = state.tab == WishlistTab.WISHLIST, onClick = { viewModel.selectTab(WishlistTab.WISHLIST) })
            FilterPill(text = stringResource(R.string.wishlist_tab_favourites), selected = state.tab == WishlistTab.FAVOURITES, onClick = { viewModel.selectTab(WishlistTab.FAVOURITES) })
        }
        Spacer(Modifier.height(16.dp))

        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = colors.primary) }
            state.error != null && state.visibleGames.isEmpty() -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(state.error!!.messageRes()), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = viewModel::retry) { Text(stringResource(R.string.common_retry)) }
                }
            }
            state.visibleGames.isEmpty() -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(if (state.tab == WishlistTab.WISHLIST) R.string.wishlist_empty else R.string.favourites_empty),
                    style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant
                )
            }
            else -> LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.visibleGames, key = { it.gameId }) { game ->
                    if (state.tab == WishlistTab.WISHLIST) {
                        SavedGameRow(
                            game = game,
                            onClick = { onGameClick(game.gameId) },
                            onRemove = { viewModel.removeWishlistGame(game.gameId) },
                            onMoveToLibrary = { viewModel.moveToLibrary(game.gameId) }
                        )
                    } else {
                        SavedGameRow(game = game, onClick = { onGameClick(game.gameId) }, onRemove = { viewModel.removeFavourite(game.gameId) })
                    }
                }
            }
        }
    }
}