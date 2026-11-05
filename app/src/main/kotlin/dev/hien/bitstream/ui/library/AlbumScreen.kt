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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import dev.hien.bitstream.core.designsystem.Spacing
import dev.hien.bitstream.core.model.Track

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AlbumScreen(
    albumId: String,
    onTrack: (Long) -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val container = LocalAppContainer.current
    val allTracks by container.musicRepository.tracks.collectAsStateWithLifecycle(
        initialValue = emptyList(),
    )

    val tracks = remember(albumId, allTracks) {
        allTracks
            .filter { albumKey(it) == albumId }
            .sortedWith(compareBy({ it.discNumber }, { it.trackNumber }, { it.title.lowercase() }))
    }

    val album = tracks.firstOrNull()
    val title = album?.album?.takeIf { it.isNotBlank() } ?: albumId.substringAfter('|')
    val artist = album?.albumArtist?.takeIf { it.isNotBlank() } ?: album?.artist.orEmpty()
    val artwork = tracks.firstNotNullOfOrNull { it.artworkUri }

    Column(modifier = Modifier.fillMaxSize()) {
        BitstreamLargeHeader(title = title) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = "Back",
                    )
                }
            }
        }

        if (tracks.isEmpty()) {
            BitstreamEmptyState(
                title = "No tracks in this album.",
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
                contentPadding = PaddingValues(bottom = Spacing.xxl),
            ) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.screen),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AlbumArtwork(uri = artwork, modifier = Modifier.size(132.dp))
                        Spacer(Modifier.width(Spacing.lg))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(title, style = MaterialTheme.typography.headlineMediumEmphasized)
                            if (artist.isNotBlank()) {
                                Text(
                                    text = artist,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                text = "${tracks.size} tracks",
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
                    HorizontalDivider()
                }

                itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
                    TrackRow(
                        track = track.copy(title = "${index + 1}. ${track.title}"),
                        onClick = { container.playTracks(tracks, startIndex = index) },
                    )
                }
            }
        }
    }
}

internal fun albumKey(track: Track): String =
    "${track.albumArtist.ifBlank { track.artist }}|${track.album}"
