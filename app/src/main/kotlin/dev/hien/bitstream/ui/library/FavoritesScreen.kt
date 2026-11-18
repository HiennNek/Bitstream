package dev.hien.bitstream.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import dev.hien.bitstream.core.designsystem.LocalBottomOverlayPadding
import dev.hien.bitstream.core.designsystem.Spacing
import dev.hien.bitstream.core.designsystem.TopBarBackButton
import dev.hien.bitstream.core.designsystem.segmentedItemShape

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
    val bottomOverlay = LocalBottomOverlayPadding.current
    Column(modifier = Modifier.fillMaxSize()) {
        BitstreamLargeHeader(
            title = "Favorites",
            navigationIcon = {
                TopBarBackButton(
                    onClick = onBack,
                    painter = painterResource(R.drawable.ic_arrow_back),
                )
            },
        )

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
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Spacing.screen,
                    end = Spacing.screen,
                    top = Spacing.sm,
                    bottom = Spacing.xxl + bottomOverlay,
                ),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                itemsIndexed(state.tracks, key = { _, track -> track.id }) { index, track ->
                    val favorite = track.id in state.favoriteIds
                    ListItem(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(segmentedItemShape(index, state.tracks.size))
                            .clickable { onTrack(track.id) }
                            .testTag("favorite_${track.id}"),
                        colors = ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                        leadingContent = {
                            AlbumArtwork(
                                uri = track.artworkUri,
                                modifier = Modifier.size(48.dp),
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
                        content = {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }
        }
    }
}
