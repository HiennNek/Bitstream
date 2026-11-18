package dev.hien.bitstream.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hien.bitstream.LocalAppContainer
import dev.hien.bitstream.R
import dev.hien.bitstream.core.designsystem.BitstreamEmptyState
import dev.hien.bitstream.core.designsystem.BitstreamLargeHeader
import dev.hien.bitstream.core.designsystem.LocalBottomOverlayPadding
import dev.hien.bitstream.core.designsystem.Spacing
import dev.hien.bitstream.core.designsystem.TopBarBackButton

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ArtistScreen(
    artistId: String,
    onTrack: (Long) -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val container = LocalAppContainer.current
    val allTracks by container.musicRepository.tracks.collectAsStateWithLifecycle(
        initialValue = emptyList(),
    )

    val tracks = remember(artistId, allTracks) {
        allTracks
            .filter { it.artist == artistId || it.albumArtist == artistId }
            .sortedWith(
                compareBy(
                    { it.album.lowercase() },
                    { it.discNumber },
                    { it.trackNumber },
                    { it.title.lowercase() },
                ),
            )
    }

    val artwork = tracks.firstNotNullOfOrNull { it.artworkUri }
    val albumCount = tracks.map { albumKey(it) }.distinct().size
    val bottomOverlay = LocalBottomOverlayPadding.current

    Column(modifier = Modifier.fillMaxSize()) {
        BitstreamLargeHeader(
            title = if (tracks.isEmpty()) artistId else "Artist",
            navigationIcon = onBack?.let {
                {
                    TopBarBackButton(
                        onClick = it,
                        painter = painterResource(R.drawable.ic_arrow_back),
                    )
                }
            },
        )

        if (tracks.isEmpty()) {
            BitstreamEmptyState(
                title = "No tracks for this artist.",
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .padding(Spacing.xxl),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = Spacing.xxl + bottomOverlay),
            ) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.screen),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AlbumArtwork(uri = artwork, modifier = Modifier.size(112.dp))
                        Spacer(Modifier.width(Spacing.lg))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(artistId, style = MaterialTheme.typography.headlineMediumEmphasized)
                            Text(
                                text = "$albumCount albums · ${tracks.size} tracks",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(
                                    onClick = { container.playTracks(tracks, startIndex = 0, shuffle = false) },
                                ) {
                                    Text("Play")
                                }
                                OutlinedButton(
                                    onClick = { container.playTracks(tracks, startIndex = 0, shuffle = true) },
                                ) {
                                    Text("Shuffle")
                                }
                            }
                        }
                    }
                }

                itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
                    TrackRow(
                        track = track,
                        onClick = { container.playTracks(tracks, startIndex = index) },
                        modifier = Modifier.padding(horizontal = Spacing.sm),
                    )
                }
            }
        }
    }
}
