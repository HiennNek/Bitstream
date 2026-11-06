# M3 Expressive Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild Bitstream's UI from the `mork.html` mockup using stock Material 3 Expressive components, an album-hue color scheme, generated cover art, and a real favorites feature.

**Architecture:** Pure color/hue math lives in `core:designsystem` and is unit-tested. The theme is applied inside `BitstreamApp` so it can follow the playing track. Screens use stock M3 components; only cover art and the waveform slider track are drawn. Favorites are a Room table exposed through `MusicRepository`.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3 1.5.0-beta02, `MaterialExpressiveTheme`, `MotionScheme.expressive()`, `MaterialShapes`, Room, Coil 3.

**Spec:** `docs/superpowers/specs/2026-11-05-expressive-redesign-design.md`

## Global Constraints

- No new Gradle dependencies. Coil stays app-only; never import it from `core:designsystem`.
- Navigation destinations stay Library / Search / Playlists / Settings.
- Keep test tags `navigation_bar`, `navigation_rail`, `now_playing_play_pause`, `status_chip`.
- Do not use Android wallpaper dynamic color. `albumColors` (the existing `dynamic_color` setting) selects hue-derived vs static deep teal.
- Every icon comes from a vector drawable; no hand-drawn Canvas glyphs for standard icons.
- Build gate after every task: `./gradlew :app:assembleDebug :core:designsystem:testDebugUnitTest`.

## Review Focus

- **Missing / blank artwork** — generated art must render, never an empty box or a crash when `artworkUri` is null.
- **Zero-length tracks** — curve math must not divide by zero when `duration == 0`.
- **Very long titles and artists** — every row and header ellipsizes instead of pushing controls off-screen.
- **Extreme hues in both themes** — palette output stays in gamut and text stays legible at hues 0, 60, 120, 180, 240, 300.
- **Favorite of a rescan-deleted track** — the row disappears via the foreign-key cascade, and the heart returns to unfilled for a missing id.

---

### Task 1: Favorites data layer

**Files:**
- Modify: `core/data/src/main/kotlin/dev/hien/bitstream/core/data/db/Entities.kt`
- Modify: `core/data/src/main/kotlin/dev/hien/bitstream/core/data/db/Daos.kt`
- Create: `core/data/src/main/kotlin/dev/hien/bitstream/core/data/db/Migrations.kt`
- Modify: `core/data/src/main/kotlin/dev/hien/bitstream/core/data/db/BitstreamDatabase.kt`
- Modify: `core/data/src/main/kotlin/dev/hien/bitstream/core/data/MusicRepository.kt`
- Modify: `app/src/main/kotlin/dev/hien/bitstream/AppContainer.kt`
- Test: `core/data/src/androidTest/kotlin/dev/hien/bitstream/core/data/BitstreamDatabaseTest.kt`
- Test: `app/src/test/kotlin/dev/hien/bitstream/ui/library/LibraryViewModelTest.kt`

**Interfaces:**
- Produces: `FavoriteEntity(trackId: Long, addedAt: Long)`, `FavoriteDao` with `observeIds(): Flow<List<Long>>`, `observeTracks(): Flow<List<TrackEntity>>`, `suspend fun set(trackId: Long, favorite: Boolean)`.
- Produces: `MIGRATION_1_2` in `dev.hien.bitstream.core.data.db`.
- Produces on `MusicRepository`: `val favoriteTracks: Flow<List<Track>>`, `val favoriteIds: Flow<Set<Long>>`, `suspend fun setFavorite(trackId: Long, favorite: Boolean)`.
- Produces: `RoomMusicRepository(trackDao, albumDao, artistDao, favoriteDao)`.

- [ ] **Step 1: Write the failing DAO test**

Add to `BitstreamDatabaseTest`:

```kotlin
@Test
fun favoritesToggleAndCascadeWhenTrackIsDeleted() = runBlocking {
    db.trackDao().upsertAll(listOf(track(id = 1, title = "Blue Train")))
    db.favoriteDao().set(1, true)
    assertEquals(setOf(1L), db.favoriteDao().observeIds().first().toSet())
    assertEquals(listOf(1L), db.favoriteDao().observeTracks().first().map { it.id })

    db.favoriteDao().set(1, false)
    assertTrue(db.favoriteDao().observeIds().first().isEmpty())

    db.favoriteDao().set(1, true)
    db.trackDao().deleteByIds(listOf(1))
    assertTrue(db.favoriteDao().observeIds().first().isEmpty())
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :core:data:compileDebugAndroidTestKotlin`
Expected: FAIL — `favoriteDao` / `set` unresolved.

- [ ] **Step 3: Add entity, DAO, migration, database wiring**

In `Entities.kt`:

```kotlin
@Entity(
    tableName = "favorites",
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["track_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class FavoriteEntity(
    @PrimaryKey @ColumnInfo(name = "track_id") val trackId: Long,
    @ColumnInfo(name = "added_at") val addedAt: Long,
)
```

In `Daos.kt`, `FavoriteDao` with `@Insert(onConflict = OnConflictStrategy.REPLACE)`, a joined `observeTracks()` ordered by `added_at DESC`, and a `@Transaction suspend fun set(...)` that inserts or deletes.

In `Migrations.kt`, `val MIGRATION_1_2 = object : Migration(1, 2) { ... }` executing exactly the `CREATE TABLE favorites` SQL Room exports (column names `track_id`, `added_at`, integer types, and the `ON DELETE CASCADE` foreign key). Add `.addMigrations(MIGRATION_1_2)` in `BitstreamDatabase.create`.

- [ ] **Step 4: Update the repository and container**

Map `favoriteTracks` and `favoriteIds` in `RoomMusicRepository`; delegate `setFavorite`. Pass `database.favoriteDao()` from `AppContainer`.

- [ ] **Step 5: Fix the existing fake**

`LibraryViewModelTest.FakeMusicRepository` must implement the three new members (`flowOf(emptyList())`, `flowOf(emptySet())`, no-op).

- [ ] **Step 6: Verify schema and run tests**

Run: `./gradlew :core:data:kspDebugKotlin`
Expected: `core/data/schemas/.../2.json` created and contains the `favorites` table.

Run: `./gradlew :app:testDebugUnitTest :core:data:compileDebugAndroidTestKotlin`
Expected: PASS (instrumented test compiles; run it on a device as `./gradlew :core:data:connectedDebugAndroidTest` when available).

- [ ] **Step 7: Commit**

```bash
git add core/data app/src/main/kotlin/dev/hien/bitstream/AppContainer.kt app/src/test/kotlin/dev/hien/bitstream/ui/library/LibraryViewModelTest.kt
git commit -m "feat: add favorites table and repository"
```

---

### Task 2: Favorites screen and ViewModel

**Files:**
- Create: `app/src/main/kotlin/dev/hien/bitstream/ui/library/FavoritesViewModel.kt`
- Create: `app/src/main/kotlin/dev/hien/bitstream/ui/library/FavoritesScreen.kt`
- Modify: `app/src/main/kotlin/dev/hien/bitstream/ui/BitstreamApp.kt`
- Test: `app/src/test/kotlin/dev/hien/bitstream/ui/library/FavoritesViewModelTest.kt`

**Interfaces:**
- Consumes: `MusicRepository.favoriteTracks`, `favoriteIds`, `setFavorite`.
- Produces: `data class FavoritesUiState(val tracks: List<Track> = emptyList(), val favoriteIds: Set<Long> = emptySet())`.
- Produces: `class FavoritesViewModel(repository: MusicRepository) : ViewModel()` with `val state: StateFlow<FavoritesUiState>` and `fun toggle(trackId: Long)`.
- Produces: `@Composable fun FavoritesRoute(onTrack: (Long) -> Unit, onBack: () -> Unit)`.

- [ ] **Step 1: Write the failing ViewModel test**

Use a fake repository backed by `MutableStateFlow` and assert that `toggle(7)` calls `setFavorite(7, true)` and the state contains `7L`.

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*FavoritesViewModelTest*'`
Expected: FAIL — class unresolved.

- [ ] **Step 3: Implement the ViewModel**

Combine `favoriteTracks` and `favoriteIds` with `stateIn(SharingStarted.Eagerly)`. `toggle` launches `repository.setFavorite(trackId, trackId !in currentIds)`.

- [ ] **Step 4: Implement the screen**

`FavoritesScreen(state, onTrack, onToggleFavorite, onBack)` = `BitstreamLargeHeader("Favorites", back action)` + `LazyColumn` of `ListItem` rows (leading `AlbumArtwork`, trailing filled `IconToggleButton` heart that calls `onToggleFavorite`). Empty state reuses `BitstreamEmptyState(title = "No favorites yet")`. Rows use `Modifier.testTag("favorite_${track.id}")`.

- [ ] **Step 5: Add the route**

In `BitstreamApp`, add `FAVORITES_ROUTE = "favorites"` and a `composable` with `DetailEnter`/`DetailPopEnter` transitions rendering `FavoritesRoute(onTrack = container::playTrack, onBack = ...)`.

- [ ] **Step 6: Run tests and build**

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/dev/hien/bitstream/ui/library/Favorites*.kt app/src/test/kotlin/dev/hien/bitstream/ui/library/FavoritesViewModelTest.kt app/src/main/kotlin/dev/hien/bitstream/ui/BitstreamApp.kt
git commit -m "feat: add favorites screen"
```

---

### Task 3: Track hue and palette math

**Files:**
- Create: `core/designsystem/src/main/kotlin/dev/hien/bitstream/core/designsystem/TrackHue.kt`
- Create: `core/designsystem/src/main/kotlin/dev/hien/bitstream/core/designsystem/TrackPalette.kt`
- Test: `core/designsystem/src/test/kotlin/dev/hien/bitstream/core/designsystem/TrackHueTest.kt`
- Test: `core/designsystem/src/test/kotlin/dev/hien/bitstream/core/designsystem/TrackPaletteTest.kt`

**Interfaces:**
- Produces: `fun trackHue(albumArtist: String, album: String, artist: String, title: String): Int` (0..359).
- Produces: `fun trackPalette(hue: Int, dark: Boolean): TrackPalette`.
- Produces: `data class TrackPalette` with ARGB `Int` fields: `primary`, `onPrimary`, `primaryContainer`, `onPrimaryContainer`, `secondary`, `onSecondary`, `secondaryContainer`, `onSecondaryContainer`, `tertiary`, `onTertiary`, `tertiaryContainer`, `onTertiaryContainer`, `surface`, `surfaceContainer`, `surfaceContainerHigh`, `surfaceContainerHighest`, `onSurface`, `onSurfaceVariant`, `outlineVariant`.

- [ ] **Step 1: Write the failing hue test**

```kotlin
@Test fun sameAlbumSameHue() {
    assertEquals(
        trackHue("Mira Holloway", "Paper Lanterns", "", "Paper Lanterns"),
        trackHue("Mira Holloway", "Paper Lanterns", "", "Another Song"),
    )
}
@Test fun hueIsAlwaysInRange() {
    listOf("", "a", "Blue Train", "multi word title").forEach { title ->
        assertTrue(trackHue("", "", "", title) in 0..359)
    }
}
@Test fun emptyAlbumFallsBackToArtistAndTitle() {
    assertNotEquals(trackHue("", "", "A", "Song"), trackHue("", "", "B", "Song"))
}
```

- [ ] **Step 2: Write the failing palette test**

```kotlin
@Test fun hue190LightMatchesMockup() {
    val p = trackPalette(190, dark = false)
    assertEquals(0xFF006D69.toInt(), p.primary)
    assertEquals(0xFFF1FCFB.toInt(), p.surface)
    assertEquals(0xFFE5F1F0.toInt(), p.surfaceContainer)
}
@Test fun hue190DarkMatchesMockup() {
    val p = trackPalette(190, dark = true)
    assertEquals(0xFF1BE3DB.toInt(), p.primary)
    assertEquals(0xFF0D1514.toInt(), p.surface)
}
@Test fun allHuesStayInGamut() {
    for (hue in 0 until 360) {
        val p = trackPalette(hue, dark = false)
        listOf(p.primary, p.surface, p.onSurface).forEach { argb ->
            val r = (argb shr 16) and 0xFF; val g = (argb shr 8) and 0xFF; val b = argb and 0xFF
            assertTrue(r in 0..255 && g in 0..255 && b in 0..255)
        }
        assertNotEquals(0, p.primary and 0xFF000000.toInt())
    }
}
```

- [ ] **Step 3: Run to verify they fail**

Run: `./gradlew :core:designsystem:testDebugUnitTest`
Expected: FAIL — unresolved `trackHue` / `trackPalette`.

- [ ] **Step 4: Port the mockup math**

`TrackHue.kt`: stable multiply-hash (31) over `"$albumArtist|$album"` when `album.isNotBlank()`, else `"$artist|$title"`; `Math.floorMod(hash, 360)`.

`TrackPalette.kt`: port `L(t)` and `hx(L, C, h)` exactly as in the mockup (`hx` shrinks chroma by 0.004 until sRGB is in range), then the role table with chromas `primary .16`, `secondary .05`, `tertiary .11 @ h+60`, `neutral .012`, `variant .022`. Emit ARGB `Int` (`0xFF shl 24 or (r shl 16) or (g shl 8) or b`).

- [ ] **Step 5: Run tests**

Run: `./gradlew :core:designsystem:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add core/designsystem/src/main/kotlin/dev/hien/bitstream/core/designsystem/Track*.kt core/designsystem/src/test/kotlin/dev/hien/bitstream/core/designsystem/Track*Test.kt
git commit -m "feat: derive album palette from track hue"
```

---

### Task 4: Album-hue theme

**Files:**
- Modify: `core/designsystem/src/main/kotlin/dev/hien/bitstream/core/designsystem/BitstreamTheme.kt`
- Modify: `app/src/main/kotlin/dev/hien/bitstream/MainActivity.kt`
- Modify: `app/src/main/kotlin/dev/hien/bitstream/ui/BitstreamApp.kt`
- Modify: `app/src/main/kotlin/dev/hien/bitstream/ui/settings/SettingsScreen.kt:270-292`

**Interfaces:**
- Consumes: `trackPalette`, `trackHue`.
- Produces: `@Composable fun BitstreamTheme(darkTheme: Boolean = isSystemInDarkTheme(), albumHue: Int? = null, albumColors: Boolean = true, content: @Composable () -> Unit)`.

- [ ] **Step 1: Implement the scheme builder**

In `BitstreamTheme.kt`, add `private fun TrackPalette.toColorScheme(): ColorScheme` mapping every field to `lightColorScheme`/`darkColorScheme`, then `rememberAnimatedColorScheme(target, dark)` that wraps the nine large-surface roles in `animateColorAsState(tween(500), label = role)` and returns `target.copy(...)`.

- [ ] **Step 2: Wire the theme into the app**

Replace the `dynamicColor` branch with: `albumColors && albumHue != null -> trackPalette(albumHue, darkTheme).toColorScheme()`, else the existing `DeepTealLight/Dark`. Keep `MotionScheme.expressive()`.

`BitstreamApp()` reads `container.settingsStore.themeFlow`/`dynamicColorFlow` and `nowPlayingState.track`, computes `darkTheme` and `albumHue = track?.let { trackHue(it.albumArtist, it.album, it.artist, it.title) }`, and wraps `AdaptiveNavigationScaffold` in `BitstreamTheme`. `MainActivity` keeps only the `CompositionLocalProvider(LocalAppContainer)`.

- [ ] **Step 3: Relabel the setting**

Change the label to "Album art colors" and the supporting text to "Tints the app from the current album; off uses deep teal." Keep the key and callback names.

- [ ] **Step 4: Verify**

Run: `./gradlew :app:assembleDebug :core:designsystem:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/designsystem/src/main/kotlin/dev/hien/bitstream/core/designsystem/BitstreamTheme.kt app/src/main/kotlin/dev/hien/bitstream/MainActivity.kt app/src/main/kotlin/dev/hien/bitstream/ui/BitstreamApp.kt app/src/main/kotlin/dev/hien/bitstream/ui/settings/SettingsScreen.kt
git commit -m "feat: tint the theme from the playing album"
```

---

### Task 5: Material icon drawables

**Files:**
- Create in `app/src/main/res/drawable/`: `ic_play.xml`, `ic_pause.xml`, `ic_skip_next.xml`, `ic_skip_previous.xml`, `ic_favorite.xml`, `ic_favorite_border.xml`, `ic_more_vert.xml`, `ic_keyboard_arrow_down.xml`, `ic_person.xml`, `ic_folder.xml`, `ic_album.xml`.

- [ ] **Step 1: Add the vectors**

Copy the official Material Icons path data (24 dp viewport, `android:fillColor="#FF000000"`, `android:tint` handled by Compose). Follow the existing `ic_shuffle.xml` structure.

- [ ] **Step 2: Verify**

Run: `./gradlew :app:assembleDebug`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/drawable
git commit -m "feat: add Material icon vectors"
```

---

### Task 6: Shared chrome and motion tokens

**Files:**
- Modify: `core/designsystem/src/main/kotlin/dev/hien/bitstream/core/designsystem/DesignTokens.kt`
- Create: `core/designsystem/src/main/kotlin/dev/hien/bitstream/core/designsystem/BitstreamComponents.kt`
- Modify: `app/src/main/kotlin/dev/hien/bitstream/ui/BitstreamApp.kt`

**Interfaces:**
- Produces: `Radii.hero = 32.dp`, `Radii.tile = 28.dp`.
- Produces: `fun fadeThroughEnter(spec: FiniteAnimationSpec<Float>): EnterTransition`, `fadeThroughExit`, `detailEnter`, `detailExit`, `detailPopEnter`, `detailPopExit` (replaces the four `val`s).
- Produces: `@Composable fun BitstreamLargeHeader(title: String, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {})`.
- Produces: `@Composable fun BitstreamSearchPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier)`.
- Produces: `@Composable fun BitstreamTile(label: String, painter: Painter, onClick: () -> Unit, modifier: Modifier = Modifier)`.

- [ ] **Step 1: Retime transitions**

Replace the four top-level transition vals with the functions above. `BitstreamApp` reads `val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()` and `val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()` once and passes them to every `enterTransition`/`exitTransition`/`pop*`.

- [ ] **Step 2: Add the three components**

Header: 56 dp row, 24 dp start padding, `displaySmallEmphasized`, trailing 48 dp buttons. Search pill: full-width 56 dp `Surface`, `surfaceContainerHigh`, `CircleShape`, leading search icon, `bodyLarge` in `onSurfaceVariant`. Tile: 80 dp `Surface` in `surfaceContainerHigh`, `RoundedCornerShape(Radii.tile)`, 52 dp `MaterialShapes.Cookie9Sided` background in `secondaryContainer` with the icon centered, label in `titleMedium`.

- [ ] **Step 3: Verify**

Run: `./gradlew :app:assembleDebug`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add core/designsystem/src/main/kotlin/dev/hien/bitstream/core/designsystem app/src/main/kotlin/dev/hien/bitstream/ui/BitstreamApp.kt
git commit -m "feat: add shared expressive chrome and motion tokens"
```

---

### Task 7: Generated cover art

**Files:**
- Create: `app/src/main/kotlin/dev/hien/bitstream/ui/components/CoverArt.kt`
- Modify: `app/src/main/kotlin/dev/hien/bitstream/ui/library/LibraryScreen.kt` (`AlbumArtwork` delegates to `CoverArt`)

**Interfaces:**
- Produces: `@Composable fun CoverArt(track: Track, modifier: Modifier = Modifier, spinning: Boolean = false, contentDescription: String? = null)`.
- Produces: `@Composable fun CoverArt(artworkUri: String?, seed: Long, modifier: Modifier = Modifier, spinning: Boolean = false)`.
- Produces: `fun coverShape(seed: Long): RoundedPolygon` and `fun innerCoverShape(seed: Long): RoundedPolygon` (pure, testable determinism).

- [ ] **Step 1: Write the failing shape test**

`app/src/test/.../CoverArtShapeTest.kt`: `assertEquals(coverShape(7), coverShape(7))` and the returned polygon is never null for seeds 0..99.

- [ ] **Step 2: Implement**

Use `MaterialShapes` lists from the spec. Draw with `Canvas`: outer path from `coverShape(seed).toPath()` scaled to the canvas, filled `primary`; inner from `innerCoverShape(seed)` at 62% scale, filled `tertiaryContainer`; center circle radius 9% in `primary`. When `artworkUri != null`, use Coil `AsyncImage` clipped to a `Shape` built from the outer polygon (`GenericShape { size, _ -> addPath(...) }`). Spin only when `spinning` is true via `rememberInfiniteTransition` (40 s linear, pause by not animating).

- [ ] **Step 3: Replace `AlbumArtwork` internals**

`AlbumArtwork(uri, modifier)` builds a seed from `uri.hashCode()` and calls the uri overload, so album cards and detail screens get the new art for free. `CoverArt` always renders inside `Modifier.testTag("cover_art")`.

- [ ] **Step 3b: Add the missing-artwork instrumented test**

Create `app/src/androidTest/kotlin/dev/hien/bitstream/ui/components/CoverArtTest.kt` asserting `onNodeWithTag("cover_art").assertIsDisplayed()` when composed with `artworkUri = null` and `Track(artworkUri = null)`.

- [ ] **Step 4: Verify and commit**

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:compileDebugAndroidTestKotlin` → PASS.

```bash
git add app/src/main/kotlin/dev/hien/bitstream/ui/components/CoverArt.kt app/src/main/kotlin/dev/hien/bitstream/ui/library/LibraryScreen.kt app/src/test/kotlin/dev/hien/bitstream/ui/components/CoverArtShapeTest.kt app/src/androidTest/kotlin/dev/hien/bitstream/ui/components/CoverArtTest.kt
git commit -m "feat: draw generated cover art with Material shapes"
```

---

### Task 8: Waveform seek bar

**Files:**
- Create: `core/designsystem/src/main/kotlin/dev/hien/bitstream/core/designsystem/WaveformSeek.kt`
- Test: `core/designsystem/src/test/kotlin/dev/hien/bitstream/core/designsystem/WaveformSeekTest.kt`

**Interfaces:**
- Produces: `fun seekProgress(positionMs: Long, durationMs: Long): Float` (0f..1f, 0f when duration ≤ 0).
- Produces: `@Composable fun WaveformSeek(positionMs: Long, durationMs: Long, playing: Boolean, onSeek: (Long) -> Unit, modifier: Modifier = Modifier)`.

- [ ] **Step 1: Write the failing progress test**

```kotlin
@Test fun zeroDurationIsZero() { assertEquals(0f, seekProgress(1000, 0), 0f) }
@Test fun halfwayIsHalf() { assertEquals(0.5f, seekProgress(30_000, 60_000), 0.0001f) }
@Test fun pastEndClamps() { assertEquals(1f, seekProgress(90_000, 60_000), 0f) }
@Test fun negativePositionClamps() { assertEquals(0f, seekProgress(-1, 60_000), 0f) }
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :core:designsystem:testDebugUnitTest --tests '*WaveformSeekTest*'`
Expected: FAIL — unresolved.

- [ ] **Step 3: Implement**

Stock `Slider` with `state = rememberSliderState(value = positionMs.toFloat(), valueRange = 0f..durationMs.coerceAtLeast(1).toFloat())`, a `thumb` that is a 6 × 28 dp `RoundedCornerShape(3.dp)` pill in `primary`, and a `track` that draws a 5 dp rounded line plus a sine `Path` whose amplitude animates 0 → 4.5 dp (`infiniteRepeatable(tween(1400, easing = LinearEasing))`) while `playing`. `onValueChangeFinished` calls `onSeek`.

- [ ] **Step 4: Run tests**

Run: `./gradlew :core:designsystem:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/designsystem/src/main/kotlin/dev/hien/bitstream/core/designsystem/WaveformSeek.kt core/designsystem/src/test/kotlin/dev/hien/bitstream/core/designsystem/WaveformSeekTest.kt
git commit -m "feat: add waveform seek slider"
```

---

### Task 9: Library rebuild

**Files:**
- Modify: `app/src/main/kotlin/dev/hien/bitstream/ui/library/LibraryScreen.kt`
- Modify: `app/src/main/kotlin/dev/hien/bitstream/ui/BitstreamApp.kt`
- Test: `app/src/androidTest/kotlin/dev/hien/bitstream/ui/library/LibraryScreenTest.kt`

**Interfaces:**
- Consumes: `BitstreamLargeHeader`, `BitstreamSearchPill`, `BitstreamTile`, `CoverArt`, `Radii.hero`, `Radii.tile`.
- Produces: `LibraryRoute(onTrack: (Long) -> Unit, onSearch: () -> Unit, onFavorites: () -> Unit, onSettings: () -> Unit)`.
- Produces: `LibraryScreen(state, onAlbum, onTrack, onArtist, onPickFolder, onSearch: () -> Unit = {}, onFavorites: () -> Unit = {}, onShuffleAll: () -> Unit = {}, onSettings: () -> Unit = {})`.
- Produces: `TrackRow(track, onClick, leading: (@Composable () -> Unit)? = null, trailing: (@Composable () -> Unit)? = null)` (defaults preserve all existing callers).

- [ ] **Step 1: Extend the instrumented test**

Add assertions that "Jump back in", "Favorites", and "Recently added" are displayed for the existing one-album state, and keep the "Blue Train" / "Choose folder" assertions untouched.

- [ ] **Step 2: Rebuild `LibraryContent`**

`LazyColumn` (not a grid) with 24 dp start/end insets:

1. `BitstreamLargeHeader("Library") { IconButton(more) }`
2. `BitstreamSearchPill("Search your music", onSearch)`
3. `BitstreamSectionHeader("Jump back in")` + `LazyRow` of 236 × 272 dp `Surface(onClick)` cards: radius `Radii.hero`, `primaryContainer`, `CoverArt` bleeding bottom-right at 250 dp, title (`titleLarge`), artist (`bodyMedium`), 52 dp play circle bottom-left.
4. 2-column grid (two `Row`s of two `BitstreamTile`s) — Shuffle all, Favorites, Artists, Folders.
5. `BitstreamSectionHeader("Recently added")` + `state.tracks.take(MAX_LIBRARY_TRACKS)` as `TrackRow` with `CoverArt` leading and duration trailing.

Artists tile opens a `ModalBottomSheet` listing `state.artists` (`LazyColumn` of `ListItem`); a click calls `onArtist` and closes it. Folders tile calls `onSettings`.

- [ ] **Step 3: Wire `LibraryRoute`**

Pass `onSearch` (navigate to the Search destination), `onFavorites` (navigate to `favorites`), `onShuffleAll` (`container.playTracks(state.tracks, shuffle = true)`), `onSettings` (navigate to Settings) from `BitstreamApp` into `LibraryRoute`.

- [ ] **Step 4: Keep empty and scanning states**

Empty → existing `EmptyLibrary` with "Choose folder". Scanning → the same indicator row, now above "Jump back in".

- [ ] **Step 5: Verify**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: PASS. Drop the shared-element `AlbumArtwork` usage here only if it breaks; the API stays.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/dev/hien/bitstream/ui/library/LibraryScreen.kt app/src/main/kotlin/dev/hien/bitstream/ui/BitstreamApp.kt app/src/androidTest/kotlin/dev/hien/bitstream/ui/library/LibraryScreenTest.kt
git commit -m "feat: rebuild the library to match the mockup"
```

---

### Task 10: Now Playing core layouts

**Files:**
- Create: `app/src/main/kotlin/dev/hien/bitstream/ui/nowplaying/NowPlayingControls.kt`
- Create: `app/src/main/kotlin/dev/hien/bitstream/ui/nowplaying/NowPlayingOutputSheet.kt`
- Modify: `app/src/main/kotlin/dev/hien/bitstream/ui/nowplaying/NowPlayingScreen.kt`
- Modify: `app/src/main/kotlin/dev/hien/bitstream/ui/BitstreamApp.kt`

**Interfaces:**
- Produces: `NowPlayingRoute(onBack, onQueue, favoriteIds: Set<Long>, onToggleFavorite: (Long) -> Unit, sharedTransitionScope, animatedVisibilityScope)`.
- Produces: `NowPlayingContent(state, positionMs, durationMs, favoriteIds, onBack, onPlayPause, onSeek, onNext, onPrevious, onToggleShuffle, onCycleRepeat, onToggleFavorite, onOutput, onQueue, sharedTransitionScope, animatedVisibilityScope)`.
- Produces: `@Composable fun NowPlayingControls(...)` (transport + toggles) and `@Composable fun OutputSheet(...)` moved out of the 1110-line file.

- [ ] **Step 1: Split the file**

Move `TransportRow`, `VolumeSection`, and `OutputSheet` into the two new files. Delete `TransportGlyph` and use `ic_play` / `ic_pause` / `ic_skip_previous` / `ic_skip_next`.

- [ ] **Step 2: Rebuild the header**

48 dp down button (`ic_keyboard_arrow_down`), centered "Playing from" (`labelMedium` `onSurfaceVariant`) + `state.track.folder` (`titleMedium`), 48 dp overflow. `StatusChip` on the next line with `onClick = onOutput`.

- [ ] **Step 3: Rebuild the body**

`BoxWithConstraints`: `maxWidth < 600.dp` → portrait column (290 dp art, title/artist/heart row, `WaveformSeek`, transport, shuffle/repeat, up-next peek); otherwise a `Row` with 310 dp art on the left and the same controls on the right. Heart = `IconToggleButton` with `ic_favorite`/`ic_favorite_border`, 56 dp, `ToggleButtonDefaults.shapes()`, `tertiaryContainer` when set.

Transport: `Row` of prev (92 × 80 dp, shape `RoundedCornerShape(40.dp, 14.dp, 14.dp, 40.dp)`), play (124 × 80 dp, uniform radius animated 40 → 28 dp on play with `defaultSpatialSpec`), next (mirrored shape). Shuffle/repeat: two `IconToggleButton`s, 56 dp tall, weight 1f.

- [ ] **Step 4: Wire state**

Inside `NowPlayingRoute`, create a `FavoritesViewModel` the same way `QueueViewModel` is created, collect `state.favoriteIds`, and pass `favoriteIds` + `onToggleFavorite = favoritesViewModel::toggle` into `NowPlayingContent`.

- [ ] **Step 5: Verify**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/dev/hien/bitstream/ui/nowplaying app/src/main/kotlin/dev/hien/bitstream/ui/BitstreamApp.kt
git commit -m "feat: rebuild now playing with expressive controls"
```

---

### Task 11: Mini player and navigation chrome

**Files:**
- Modify: `app/src/main/kotlin/dev/hien/bitstream/ui/nowplaying/NowPlayingScreen.kt` (`MiniPlayerBar`)
- Modify: `app/src/main/kotlin/dev/hien/bitstream/ui/adaptive/AdaptiveScaffold.kt`

**Interfaces:**
- Consumes: `CoverArt`, `Radii.tile`.
- Keeps: `MiniPlayerBar` parameter list, and the `navigation_bar` / `navigation_rail` test tags.

- [ ] **Step 1: Restyle the mini player**

`Surface` shape `RoundedCornerShape(Radii.tile)`, `surfaceContainerHigh`, 52 dp `CoverArt`, `titleMedium`/`bodyMedium` ellipsized, 56 × 52 play button with radius animated 18 → 26 dp, 48 dp next (`ic_skip_next`), 4 dp progress line pinned at the bottom inset 22 dp.

- [ ] **Step 2: Use stock nav**

Keep `NavigationBar`/`NavigationRail` and their items. Set `containerColor = surfaceContainer`, `NavigationBarItemDefaults.colors(indicatorColor = secondaryContainer, selectedIconColor = onSecondaryContainer, unselectedIconColor = onSurfaceVariant)`, labels `labelMedium`. Remove any custom item wrapper; keep `Modifier.testTag` on the bar/rail.

- [ ] **Step 3: Verify**

Run: `./gradlew :app:assembleDebug`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/dev/hien/bitstream/ui/nowplaying/NowPlayingScreen.kt app/src/main/kotlin/dev/hien/bitstream/ui/adaptive/AdaptiveScaffold.kt
git commit -m "feat: restyle mini player and navigation chrome"
```

---

### Task 12: Tablet landscape two-pane player

**Files:**
- Modify: `app/src/main/kotlin/dev/hien/bitstream/ui/nowplaying/NowPlayingScreen.kt` (`NowPlayingRoute`)

**Interfaces:**
- Consumes: `LibraryViewModel` state shape (`LibraryUiState`).
- Produces: test tag `now_playing_pane` on the right pane, `now_playing_song_list` on the left list.

- [ ] **Step 1: Add the expanded layout**

Inside `NowPlayingRoute`'s `BoxWithConstraints`, when `maxWidth >= 840.dp`, render a `Row`:
- left 320 dp: `LazyColumn` of `LibraryUiState.tracks` with `TrackRow`, tag `now_playing_song_list`;
- right: the existing player column in a `Surface(surfaceContainer)` with tag `now_playing_pane`.

Below 840 dp, keep the current single-pane behavior.

- [ ] **Step 2: Load the list**

Reuse `LibraryViewModel` via `viewModel(factory = ...)` inside the route; it already exposes sorted `tracks`.

- [ ] **Step 3: Add the instrumented assertion**

In `AdaptiveLayoutTest`, add an expanded-size test that opens the player and asserts `onNodeWithTag("now_playing_pane").assertIsDisplayed()`.

- [ ] **Step 4: Verify**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/dev/hien/bitstream/ui/nowplaying/NowPlayingScreen.kt app/src/androidTest/kotlin/dev/hien/bitstream/ui/adaptive/AdaptiveLayoutTest.kt
git commit -m "feat: add tablet landscape player pane"
```

---

### Task 13: Consistency pass

**Files:**
- Modify: `app/src/main/kotlin/dev/hien/bitstream/ui/search/SearchScreen.kt`
- Modify: `app/src/main/kotlin/dev/hien/bitstream/ui/playlists/PlaylistsScreen.kt`
- Modify: `app/src/main/kotlin/dev/hien/bitstream/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/kotlin/dev/hien/bitstream/ui/settings/DiagnosticsScreen.kt`
- Modify: `app/src/main/kotlin/dev/hien/bitstream/ui/library/AlbumScreen.kt`
- Modify: `app/src/main/kotlin/dev/hien/bitstream/ui/library/ArtistScreen.kt`
- Modify: `app/src/main/kotlin/dev/hien/bitstream/ui/queue/QueueSheet.kt`
- Modify: `app/src/main/kotlin/dev/hien/bitstream/ui/components/CoverArt.kt` (no change needed unless a detail screen wants the spin)

**Interfaces:**
- Consumes: `BitstreamLargeHeader`, `BitstreamSectionHeader`, `Radii.hero`, `Radii.tile`, `CoverArt`.

- [ ] **Step 1: Replace every `TopAppBar`**

Swap `Scaffold(topBar = { TopAppBar(...) })` for the content + `BitstreamLargeHeader`, using the back icon (`ic_arrow_back`) as the first action on detail screens. Keep `Scaffold` only where a FAB is anchored.

- [ ] **Step 2: Restyle cards and rows**

Section cards → `RoundedCornerShape(Radii.hero)`; list rows → 64 dp with `RoundedCornerShape(Radii.tile)`; album/artist hero uses `CoverArt`.

- [ ] **Step 3: Search**

Use the stock `SearchBar` (expanded state false, `inputField` with the existing query state) instead of `OutlinedTextField`; result rows stay `ListItem` with `TrackRow`.

- [ ] **Step 4: Verify**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/dev/hien/bitstream/ui
git commit -m "feat: align remaining screens with the expressive system"
```

---

### Task 14: Final verification

- [ ] **Step 1: Full build and unit tests**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest :core:designsystem:testDebugUnitTest :core:data:testDebugUnitTest :core:model:testDebugUnitTest`
Expected: EXIT 0.

- [ ] **Step 2: Lint**

Run: `./gradlew :app:lintDebug`
Expected: no new errors.

- [ ] **Step 3: Manual checklist (device needed)**

- empty library → "Choose folder" visible;
- one album → "Jump back in" card, Favorites tile, "Recently added" rows;
- play → artwork spins, theme hue changes, mini player appears;
- pause → spin stops, play button radius grows;
- favorite from Now Playing → appears in Favorites;
- toggle Album art colors off → deep teal scheme;
- phone landscape and tablet portrait layouts render without overlap;
- tablet landscape ≥ 840 dp shows the two-pane player;
- a 90-character title and artist ellipsize in the library card, row, mini player, and Now Playing header;
- TalkBack announces every icon-only control (close, overflow, play/pause, next, previous, heart, shuffle, repeat).

- [ ] **Step 4: Commit any fixes**

```bash
git add -A
git commit -m "fix: resolve expressive redesign review findings"
```
