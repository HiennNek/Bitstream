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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hien.bitstream.LocalAppContainer
import dev.hien.bitstream.R

@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(artistId, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                painter = painterResource(R.drawable.ic_arrow_back),
                                contentDescription = "Back",
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (tracks.isEmpty()) {
            Text(
                text = "No tracks for this artist.",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AlbumArtwork(uri = artwork, modifier = Modifier.size(112.dp))
                    Spacer(Modifier.width(16.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(artistId, style = MaterialTheme.typography.headlineSmall)
                        Text(
                            text = "$albumCount albums · ${tracks.size} tracks",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                HorizontalDivider()
            }

            items(tracks, key = { it.id }) { track ->
                TrackRow(track = track, onClick = { onTrack(track.id) })
            }
        }
    }
}
