package dev.hien.bitstream.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.hien.bitstream.LocalAppContainer
import dev.hien.bitstream.R
import dev.hien.bitstream.core.designsystem.BitstreamEmptyState
import dev.hien.bitstream.core.designsystem.BitstreamLargeHeader
import dev.hien.bitstream.core.designsystem.Spacing

@Composable
fun FavoritesRoute(
    onTrack: (Long) -> Unit,
    onBack: () -> Unit,
) {
    val container = LocalAppContainer.current
    val viewModel: FavoritesViewModel = viewModel(
        factory = viewModelFactory {
            initializer { FavoritesViewModel(container.musicRepository) }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    FavoritesScreen(
        state = state,
        onTrack = onTrack,
        onToggleFavorite = viewModel::toggle,
        onBack = onBack,
    )
}

@Composable
fun FavoritesScreen(
    state: FavoritesUiState,
    onTrack: (Long) -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        BitstreamLargeHeader(title = "Favorites") {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = "Back",
                )
            }
        }

        if (state.tracks.isEmpty()) {
            BitstreamEmptyState(
                title = "No favorites yet",
                message = "Tap the heart on a track to keep it here.",
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(Spacing.xxxl),
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.tracks, key = { it.id }) { track ->
                    val favorite = track.id in state.favoriteIds
                    ListItem(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onTrack(track.id) }
                            .padding(horizontal = Spacing.sm)
                            .testTag("favorite_${track.id}"),
                        leadingContent = {
                            AlbumArtwork(
                                uri = track.artworkUri,
                                modifier = Modifier.size(48.dp),
                            )
                        },
                        headlineContent = {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        supportingContent = {
                            Text(
                                text = track.artist,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        trailingContent = {
                            IconToggleButton(
                                checked = favorite,
                                onCheckedChange = { onToggleFavorite(track.id) },
                            ) {
                                Icon(
                                    painter = painterResource(
                                        if (favorite) R.drawable.ic_favorite else R.drawable.ic_favorite_border,
                                    ),
                                    contentDescription = if (favorite) {
                                        "Remove from favorites"
                                    } else {
                                        "Add to favorites"
                                    },
                                    tint = if (favorite) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}
