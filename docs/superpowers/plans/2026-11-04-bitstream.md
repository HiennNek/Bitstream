# Bitstream Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a local Android FLAC player that sends integer PCM to a USB DAC through a native UAC driver, wrapped in a library-first Material 3 Expressive UI that works on phone and tablet in portrait and landscape.

**Architecture:** Five Gradle modules (`app`, `core:model`, `core:data`, `core:playback`, `core:designsystem`). A native engine decodes FLAC with libFLAC into a lock-free ring buffer and writes it to the DAC with libusb isochronous transfers. Media3 supplies only `SimpleBasePlayer` + `MediaSessionService`. The library is SAF folders + Room; the UI is Compose with Material 3 Expressive and adaptive layouts.

**Tech Stack:** Kotlin 2.4.21, AGP 9.3.1, Gradle 9.5.0, JDK 17, Compose BOM 2026.09.00, material3 1.5.0-beta02, Media3 1.11.1, Room 2.8.5 + KSP 2.3.13, Coil 3.6.3, NDK 29.0.14206865, CMake 3.22.1, libusb 1.0.27, libFLAC 1.4.3.

**Spec:** `docs/superpowers/specs/2026-11-04-bitstream-design.md`

## Global Constraints

- `minSdk = 29`, `targetSdk = 36`, `compileSdk = 37`; ABIs `arm64-v8a` and `x86_64`.
  compileSdk 37 is required by the pinned 2026.09 AndroidX/Compose set; targetSdk stays 36.
  Install it with `sdkmanager "platforms;android-37.0"`.
- Application ID and namespace: `dev.hien.bitstream`.
- Stereo FLAC only, 16/24/32-bit, 44.1–384 kHz. No DSD, no multichannel, no mono, no CUE.
- Bit-perfect means: integer samples only, exact source rate, source significant bits preserved; left-justified zero-fill into a wider subslot is allowed; no resampling, gain, dither, mixing, or DSP. Hardware volume preferred; software volume is opt-in and off by default.
- Playback requires a USB DAC. No speaker or Bluetooth fallback. UAC2 is the validated path; UAC1 is best-effort.
- Library uses SAF folder trees only; no `READ_MEDIA_AUDIO`, no MediaStore.
- UI is Material 3 Expressive and must work on phone and tablet, portrait and landscape, via window size classes.
- No network permission, no analytics, no ads.
- Test device: Samsung Galaxy Tab S10 FE (`SM-X526B`), Android 16 / API 36, over adb wireless, JA11 attached.
- Commit after every task. Conventional commit messages (`feat:`, `fix:`, `test:`, `docs:`, `chore:`).
- Read the spec before starting. The spec's §5 bit-perfect definition and §13 status rules are normative.

## Execution Order

Task numbers group by subsystem; they are not a strict execution order. Build Task 1 first. Then execute **Task 10's USB spike before any other feature work**, using a temporary debug screen that opens the first audio-class device through `UsbManager` and hardcodes the JA11's endpoint/alt-setting values; the parser and manager from Tasks 8–9 are not needed for the spike. If the spike fails, stop and fix the claim strategy — do not build the engine on an unverified USB path. After it passes, continue in numeric order from Task 2. The spike code is kept and refined by Tasks 8–13.

## Review Focus

These are the failure modes most likely to bite a user and are not fully exercised by ordinary happy-path tests. Each has a test in the task that owns the code.

1. **DAC unplugged mid-track** — must pause, keep the queue, post a notice, and never crash or spin. Owned by Task 19.
2. **FLAC without MD5, with an ID3v2 prefix, or with a corrupt frame** — must index/play gracefully and report `Unavailable`/`Failed`, not crash. Owned by Task 4 and Task 12.
3. **24-bit source on a 32-bit subslot** — significant bits must land in the DAC's valid-bit positions; the Path chip stays `Bit-perfect`. Owned by Task 11 and Task 13.
4. **Rotation or window resize mid-playback** — audio must not stop, and queue/position/scroll must survive. Owned by Task 18.
5. **Mixed-rate queue with a track the DAC cannot play** — auto-advance must skip it with a notice and continue; stop only when nothing playable remains. Owned by Task 12.
6. **USB permission denied** — must explain, retry, and leave the library usable. Owned by Task 9.

---

### Task 1: Project scaffold, build, and core models

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `local.properties`, `.gitignore`, `gradle/libs.versions.toml`
- Create: `app/build.gradle.kts`, `core/model/build.gradle.kts`, `core/data/build.gradle.kts`, `core/playback/build.gradle.kts`, `core/designsystem/build.gradle.kts`
- Create: `core/model/src/main/kotlin/dev/hien/bitstream/core/model/Models.kt`
- Create: `app/src/main/AndroidManifest.xml`, `app/src/main/kotlin/dev/hien/bitstream/BitstreamApp.kt`, `app/src/main/kotlin/dev/hien/bitstream/MainActivity.kt`
- Test: `core/model/src/test/kotlin/dev/hien/bitstream/core/model/ModelsTest.kt`

**Interfaces:**
- Produces: `Track`, `Album`, `AudioFormat`, `PlaybackStatus`, `BitPerfectPath`, `Integrity`, `RepeatMode`, `PlaybackState` in `dev.hien.bitstream.core.model`.
- Produces: module names `:app`, `:core:model`, `:core:data`, `:core:playback`, `:core:designsystem`.

- [ ] **Step 1: Create the build scaffold and write the failing model test**

Create the five modules, version catalog, manifests, `.gitignore` (ignore `local.properties`, `.gradle/`, `build/`, `.idea/`), `local.properties`, and the Gradle wrapper (no system Gradle is present):

```bash
curl -L -o /tmp/gradle-9.5.0-bin.zip https://services.gradle.org/distributions/gradle-9.5.0-bin.zip
unzip -q /tmp/gradle-9.5.0-bin.zip -d /tmp
/tmp/gradle-9.5.0/bin/gradle wrapper --gradle-version 9.5.0
```

`gradle/libs.versions.toml` must pin:

```toml
[versions]
agp = "9.3.1"
kotlin = "2.4.21"
ksp = "2.3.13"
composeBom = "2026.09.00"
material3 = "1.5.0-beta02"
media3 = "1.11.1"
room = "2.8.5"
coil = "3.6.3"
adaptive = "1.3.0"
coroutines = "1.11.0"
serialization = "1.12.0"
datastore = "1.3.0"
lifecycle = "2.11.0"
navigation = "2.10.2"
activityCompose = "1.13.0"
documentfile = "1.1.0"
work = "2.12.0"
junit = "4.13.2"
androidxTestJunit = "1.3.0"
espresso = "3.7.0"
```

Create all five modules. `:core:model` is a pure Kotlin JVM module; the other `core` modules and `:app` are Android modules. Compose is enabled only in `:core:designsystem` and `:app`. Room/KSP only in `:core:data`. NDK/CMake only in `:core:playback` (added in Task 10; leave the block out now).

Write `local.properties` with `sdk.dir=/home/hien/Android/Sdk`. Set `ndkVersion = "29.0.14206865"` in `:core:playback` and `:app`.

Write the failing model test:

```kotlin
class ModelsTest {
    @Test
    fun `audio format keeps source depth separate from subslot width`() {
        val format = AudioFormat(sampleRate = 96_000, bitDepth = 24, channels = 2)
        assertEquals(96_000, format.sampleRate)
        assertEquals(24, format.bitDepth)
        assertEquals(2, format.channels)
    }

    @Test
    fun `playback state starts idle with unavailable integrity`() {
        val state = PlaybackState()
        assertEquals(PlaybackStatus.IDLE, state.status)
        assertEquals(Integrity.UNAVAILABLE, state.integrity)
        assertEquals(BitPerfectPath.NO_DAC, state.path)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :core:model:test`
Expected: FAIL — `Models.kt` does not exist yet.

- [ ] **Step 3: Implement the models**

`Models.kt` defines the data classes and enums exactly as used in the test, with defaults: `PlaybackState(status = IDLE, path = NO_DAC, integrity = UNAVAILABLE, positionMs = 0, durationMs = 0, underruns = 0, queueSize = 0, volume = null)`. Define `sealed interface VolumeState { data object Fixed; data class Hardware(val level: Int); data object Software }`. `Track` uses `String` URIs (no Android types in `:core:model`).

- [ ] **Step 4: Run the test and the build**

Run: `./gradlew :core:model:test :app:assembleDebug`
Expected: PASS, and `app/build/outputs/apk/debug/app-debug.apk` exists.

- [ ] **Step 5: Install and launch the empty app**

Run:
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n dev.hien.bitstream/.MainActivity
```
Expected: app opens to a blank "Bitstream" screen without crashing.

- [ ] **Step 6: Commit**

```bash
git add .
git commit -m "chore: scaffold Bitstream modules and core models"
```

---

### Task 2: Material 3 Expressive theme and adaptive navigation shell

**Files:**
- Create: `core/designsystem/src/main/kotlin/dev/hien/bitstream/core/designsystem/BitstreamTheme.kt`
- Create: `core/designsystem/src/main/kotlin/dev/hien/bitstream/core/designsystem/NavigationChrome.kt`
- Create: `app/src/main/kotlin/dev/hien/bitstream/ui/BitstreamApp.kt`
- Create: `app/src/main/kotlin/dev/hien/bitstream/ui/PlaceholderScreens.kt`
- Modify: `app/src/main/kotlin/dev/hien/bitstream/MainActivity.kt`
- Test: `core/designsystem/src/test/kotlin/dev/hien/bitstream/core/designsystem/NavigationChromeTest.kt`

**Interfaces:**
- Produces: `enum class NavChrome { BAR, RAIL }`
- Produces: `fun navChromeFor(widthDp: Int): NavChrome` — `< 600` returns `BAR`, otherwise `RAIL`.
- Produces: `@Composable fun BitstreamTheme(darkTheme: Boolean = isSystemInDarkTheme(), dynamicColor: Boolean = true, content: @Composable () -> Unit)`.
- Produces: `@Composable fun BitstreamApp()` with destinations Library, Search, Playlists, Settings.

- [ ] **Step 1: Write the failing navigation test**

```kotlin
class NavigationChromeTest {
    @Test fun `compact width uses bottom bar`() = assertEquals(NavChrome.BAR, navChromeFor(599))
    @Test fun `medium width uses rail`() = assertEquals(NavChrome.RAIL, navChromeFor(600))
    @Test fun `expanded width uses rail`() = assertEquals(NavChrome.RAIL, navChromeFor(1280))
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :core:designsystem:test`
Expected: FAIL — `navChromeFor` is unresolved.

- [ ] **Step 3: Implement the theme and shell**

Use `MaterialExpressiveTheme(motionScheme = MotionScheme.expressive(), ...)` and opt in with `@OptIn(ExperimentalMaterial3ExpressiveApi::class)` inside `BitstreamTheme` only. Define a static light/dark scheme from a deep-teal seed for API 29–30 and use `dynamicLightColorScheme`/`dynamicDarkColorScheme` on API 31+. Use `NavigationSuiteScaffold` for the chrome and `navChromeFor` for the pure size decision. Call `enableEdgeToEdge()` in `MainActivity` and opt into predictive back (`android:enableOnBackInvokedCallback="true"`). Use Material Symbols as vector drawables, not the deprecated `material-icons` library. Placeholder screens are simple centered text.

- [ ] **Step 4: Run tests and build**

Run: `./gradlew :core:designsystem:test :app:assembleDebug`
Expected: PASS.

- [ ] **Step 5: Verify on the tablet in both orientations**

Install, launch, confirm the rail appears in landscape and the bar/rail behavior is sane after rotation. No crash.

- [ ] **Step 6: Commit**

```bash
git add .
git commit -m "feat: add M3 expressive theme and adaptive navigation shell"
```

---

### Task 3: Room database, DAOs, and repositories

**Files:**
- Create: `core/data/src/main/kotlin/dev/hien/bitstream/core/data/db/Entities.kt`
- Create: `core/data/src/main/kotlin/dev/hien/bitstream/core/data/db/Daos.kt`
- Create: `core/data/src/main/kotlin/dev/hien/bitstream/core/data/db/BitstreamDatabase.kt`
- Create: `core/data/src/main/kotlin/dev/hien/bitstream/core/data/MusicRepository.kt`
- Create: `core/data/src/main/kotlin/dev/hien/bitstream/core/data/PlaybackStateStore.kt`
- Test: `core/data/src/androidTest/kotlin/dev/hien/bitstream/core/data/BitstreamDatabaseTest.kt`

**Interfaces:**
- Produces: entities `TrackEntity`, `AlbumEntity`, `ArtistEntity`, `PlaylistEntity`, `PlaylistEntryEntity`, `ScanFolderEntity`, `PlaybackStateEntity`.
- Produces: `TrackDao.upsertAll(tracks: List<TrackEntity>)`, `observeTracks(): Flow<List<TrackEntity>>`, `search(query: String): Flow<List<TrackEntity>>`, `tracksByIds(ids: List<Long>): List<TrackEntity>`.
- Produces: `AlbumDao.observeAlbums(): Flow<List<AlbumEntity>>`, `rebuild()`.
- Produces: `PlaylistDao.observePlaylists()`, `create(name)`, `addTrack(playlistId, trackId)`, `reorder(...)`.
- Produces: `PlaybackStateStore.save(queueIds, index, positionMs, shuffle, repeat)`, `load(): PlaybackStateEntity?`.
- Produces: `MusicRepository` exposing the flows the UI needs, mapping entities to `:core:model` types.

- [ ] **Step 1: Write the failing database test**

```kotlin
@RunWith(AndroidJUnit4::class)
class BitstreamDatabaseTest {
    private lateinit var db: BitstreamDatabase

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            BitstreamDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After fun tearDown() = db.close()

    @Test fun `album query groups tracks and keeps album artist`() = runBlocking {
        db.trackDao().upsertAll(listOf(
            track(id = 1, title = "One", album = "Record", albumArtist = "Artist"),
            track(id = 2, title = "Two", album = "Record", albumArtist = "Artist")
        ))
        val albums = db.albumDao().observeAlbums().first()
        assertEquals(1, albums.size)
        assertEquals("Record", albums.single().title)
        assertEquals("Artist", albums.single().albumArtist)
    }

    @Test fun `search matches title artist and album`() = runBlocking {
        db.trackDao().upsertAll(listOf(track(id = 1, title = "Blue Train", artist = "Coltrane")))
        assertEquals(1, db.trackDao().search("colt").first().size)
        assertEquals(1, db.trackDao().search("blue").first().size)
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :core:data:connectedDebugAndroidTest`
Expected: FAIL — database classes do not exist.

- [ ] **Step 3: Implement entities, DAOs, database, and repository**

Schema follows spec §10. Add the `has_md5` column to `tracks`. Use an FTS4 table `track_fts` for search, kept in sync by triggers or by the DAO's upsert transaction. `AlbumEntity` is derived in `AlbumDao.rebuild()` from tracks. `PlaybackStateStore` serializes queue IDs as JSON with kotlinx-serialization.

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :core:data:connectedDebugAndroidTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add .
git commit -m "feat: add Room schema, DAOs, and music repository"
```

---

### Task 4: FLAC metadata reader

**Files:**
- Create: `core/data/src/main/kotlin/dev/hien/bitstream/core/data/metadata/FlacMetadataReader.kt`
- Test: `core/data/src/test/kotlin/dev/hien/bitstream/core/data/metadata/FlacMetadataReaderTest.kt`

**Interfaces:**
- Produces: `data class StreamInfo(val sampleRate: Int, val channels: Int, val bitsPerSample: Int, val totalSamples: Long, val md5: ByteArray?)`.
- Produces: `data class FlacTags(val streamInfo: StreamInfo, val vorbis: Map<String, List<String>>, val picture: ByteArray?)`.
- Produces: `class FlacMetadataReader { fun read(input: InputStream): FlacTags }`.

- [ ] **Step 1: Write the failing tests**

Build minimal FLAC byte arrays in the test: `fLaC` magic, a STREAMINFO block (34 bytes, 96 kHz, 24-bit, stereo, MD5 `0x01..0x10`), and a VORBIS_COMMENT block with `TITLE=Blue Train`, `ARTIST=Coltrane`, `ALBUM=Blue Train`. Assert those values.

Add three more tests:
- ID3v2-prefixed FLAC (10-byte header with sync-safe size) is read correctly.
- All-zero STREAMINFO MD5 yields `md5 == null`.
- A PICTURE block returns non-null bytes and the declared MIME type is preserved in the Vorbis map as `COVERARTMIME` only if present.

```kotlin
@Test fun `reads stream info and vorbis comments`() {
    val tags = FlacMetadataReader().read(flacBytes().inputStream())
    assertEquals(96_000, tags.streamInfo.sampleRate)
    assertEquals(24, tags.streamInfo.bitsPerSample)
    assertEquals(2, tags.streamInfo.channels)
    assertEquals(listOf("Coltrane"), tags.vorbis["ARTIST"])
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :core:data:testDebugUnitTest --tests '*FlacMetadataReaderTest*'`
Expected: FAIL — class missing.

- [ ] **Step 3: Implement the reader**

Parse metadata blocks only, never the audio frames: 4-byte block header (last-block flag + type + 24-bit length), STREAMINFO bit packing, Vorbis comment little-endian lengths, PICTURE block (type, MIME, description, dimensions, data). Skip a leading ID3v2 block when present. Cap each block at 16 MiB and stop at the first audio frame. Return `md5 = null` when all 16 bytes are zero.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :core:data:testDebugUnitTest --tests '*FlacMetadataReaderTest*'`
Expected: PASS, including the ID3v2, no-MD5, and picture cases.

- [ ] **Step 5: Commit**

```bash
git add .
git commit -m "feat: parse FLAC stream info, tags, and artwork"
```

---

### Task 5: SAF library scanner and artwork cache

**Files:**
- Create: `core/data/src/main/kotlin/dev/hien/bitstream/core/data/library/FileTree.kt`
- Create: `core/data/src/main/kotlin/dev/hien/bitstream/core/data/library/DocumentFileTree.kt`
- Create: `core/data/src/main/kotlin/dev/hien/bitstream/core/data/library/LibraryScanner.kt`
- Create: `core/data/src/main/kotlin/dev/hien/bitstream/core/data/library/ArtworkCache.kt`
- Create: `core/data/src/main/kotlin/dev/hien/bitstream/core/data/library/ScanWorker.kt`
- Test: `core/data/src/test/kotlin/dev/hien/bitstream/core/data/library/LibraryScannerTest.kt`

**Interfaces:**
- Produces: `interface FileNode { val name: String; val uri: String; val size: Long; val lastModified: Long; val isDirectory: Boolean }`
- Produces: `interface FileTree { fun children(node: FileNode): List<FileNode>; fun open(node: FileNode): InputStream }`
- Produces: `class LibraryScanner(tree: FileTree, reader: FlacMetadataReader) { suspend fun scan(roots: List<FileNode>, onProgress: (Int) -> Unit = {}): ScanResult }`
- Produces: `data class ScanResult(val tracks: List<Track>, val skipped: Int)`.
- Produces: `class ArtworkCache(context: Context) { suspend fun store(key: String, bytes: ByteArray): String; fun file(key: String): File? }`.
- Produces: `class ScanWorker(context, params) : CoroutineWorker` enqueued as unique work `library-scan`, so a long scan survives the app leaving the foreground.

- [ ] **Step 1: Write the failing scanner test**

```kotlin
@Test fun `indexes flac files and ignores other extensions`() = runTest {
    val tree = FakeFileTree(
        root = dir("Music"),
        files = mapOf(
            "Music/a.flac" to flacBytes(title = "A"),
            "Music/b.FLAC" to flacBytes(title = "B"),
            "Music/cover.jpg" to byteArrayOf(1, 2, 3)
        )
    )
    val result = LibraryScanner(tree, FlacMetadataReader()).scan(listOf(tree.root))
    assertEquals(setOf("A", "B"), result.tracks.map { it.title }.toSet())
    assertEquals(1, result.skipped)
}

@Test fun `scanner keeps exact source format`() = runTest {
    val tree = FakeFileTree(root = dir("Music"), files = mapOf("Music/hires.flac" to flacBytes(rate = 192_000, depth = 24)))
    val track = LibraryScanner(tree, FlacMetadataReader()).scan(listOf(tree.root)).tracks.single()
    assertEquals(192_000, track.sampleRate)
    assertEquals(24, track.bitDepth)
    assertTrue(track.hasMd5)
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :core:data:testDebugUnitTest --tests '*LibraryScannerTest*'`
Expected: FAIL — scanner missing.

- [ ] **Step 3: Implement the scanner and cache**

`DocumentFileTree` wraps `DocumentFile.fromTreeUri` and `ContentResolver.openInputStream`. `LibraryScanner` walks breadth-first, filters `.flac` case-insensitively, reads tags through `FlacMetadataReader`, derives title from `TITLE` or file name, album from `ALBUM` or folder, duration from `totalSamples / sampleRate`, and `hasMd5` from the STREAMINFO MD5. Cache artwork by content hash with an LRU cap (256 MiB). Add `ScanWorker` with WorkManager (`work-runtime-ktx`) for scans that continue after the app leaves the foreground; the in-app scan uses the same scanner on `Dispatchers.IO`.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :core:data:testDebugUnitTest --tests '*LibraryScannerTest*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add .
git commit -m "feat: scan SAF folders and cache FLAC artwork"
```

---

### Task 6: Library UI with adaptive list-detail

**Files:**
- Create: `app/src/main/kotlin/dev/hien/bitstream/ui/library/LibraryViewModel.kt`
- Create: `app/src/main/kotlin/dev/hien/bitstream/ui/library/LibraryScreen.kt`
- Create: `app/src/main/kotlin/dev/hien/bitstream/ui/library/AlbumScreen.kt`
- Create: `app/src/main/kotlin/dev/hien/bitstream/ui/library/ArtistScreen.kt`
- Modify: `app/src/main/kotlin/dev/hien/bitstream/ui/BitstreamApp.kt`
- Test: `app/src/test/kotlin/dev/hien/bitstream/ui/library/LibraryViewModelTest.kt`
- Test: `app/src/androidTest/kotlin/dev/hien/bitstream/ui/library/LibraryScreenTest.kt`

**Interfaces:**
- Consumes: `MusicRepository`, `Track`, `Album`.
- Produces: `LibraryViewModel.state: StateFlow<LibraryUiState>` with `albums`, `artists`, `tracks`, `isScanning`, `empty`.
- Produces: `LibraryScreen(state: LibraryUiState, onAlbum: (String) -> Unit, onTrack: (Long) -> Unit)` (stateless) and a `LibraryRoute` wrapper that collects the ViewModel.
- Produces: `AlbumScreen(albumId: String, onTrack: (Long) -> Unit)`.

- [ ] **Step 1: Write the failing ViewModel test**

```kotlin
@Test fun `empty library exposes empty state`() = runTest {
    val vm = LibraryViewModel(FakeMusicRepository(emptyList()))
    assertEquals(true, vm.state.first().empty)
}

@Test fun `albums are grouped and sorted by album artist`() = runTest {
    val vm = LibraryViewModel(FakeMusicRepository(listOf(album("B", "Zed"), album("A", "Amy"))))
    assertEquals(listOf("A", "B"), vm.state.first().albums.map { it.title })
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*LibraryViewModelTest*'`
Expected: FAIL.

- [ ] **Step 3: Implement the ViewModel and screens**

Use `LazyVerticalGrid` with adaptive `GridCells.Adaptive(minSize = 160.dp)` for albums. Use `ListDetailPaneScaffold` on medium/expanded; on compact, navigate to a full-screen detail. Show sample-rate/bit-depth as `24/96` in track rows. Add the folder picker entry point (`ActivityResultContracts.OpenDocumentTree`) in the empty state.

- [ ] **Step 4: Write the failing Compose test**

```kotlin
@Test fun `album title is shown in the library`() {
    composeRule.setContent {
        BitstreamTheme {
            LibraryScreen(state = LibraryUiState(albums = listOf(album("Blue Train", "Coltrane"))))
        }
    }
    composeRule.onNodeWithText("Blue Train").assertIsDisplayed()
}
```

Run: `./gradlew :app:connectedDebugAndroidTest --tests '*LibraryScreenTest*'`
Expected: FAIL until the screen accepts the album list parameter.

- [ ] **Step 5: Run all tests and build**

Run: `./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:assembleDebug`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add .
git commit -m "feat: add adaptive library and album screens"
```

---

### Task 7: Search and playlists

**Files:**
- Create: `app/src/main/kotlin/dev/hien/bitstream/ui/search/SearchViewModel.kt`
- Create: `app/src/main/kotlin/dev/hien/bitstream/ui/search/SearchScreen.kt`
- Create: `app/src/main/kotlin/dev/hien/bitstream/ui/playlists/PlaylistsViewModel.kt`
- Create: `app/src/main/kotlin/dev/hien/bitstream/ui/playlists/PlaylistsScreen.kt`
- Test: `app/src/test/kotlin/dev/hien/bitstream/ui/search/SearchViewModelTest.kt`
- Test: `app/src/test/kotlin/dev/hien/bitstream/ui/playlists/PlaylistsViewModelTest.kt`

**Interfaces:**
- Produces: `SearchViewModel.query(query: String)`, `state: StateFlow<SearchUiState>`.
- Produces: `PlaylistsViewModel.create(name)`, `rename(id, name)`, `delete(id)`, `addTrack(id, trackId)`, `reorder(id, from, to)`, `state`.
- Consumes: `MusicRepository`, `PlaylistDao`.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun `blank query clears results`() = runTest {
    val vm = SearchViewModel(FakeMusicRepository(listOf(track("Blue Train"))))
    vm.query("   ")
    assertTrue(vm.state.first().results.isEmpty())
}

@Test fun `search is case insensitive`() = runTest {
    val vm = SearchViewModel(FakeMusicRepository(listOf(track("Blue Train"))))
    vm.query("BLUE")
    assertEquals(1, vm.state.first().results.size)
}

@Test fun `reorder moves entry to new position`() = runTest {
    val vm = PlaylistsViewModel(FakePlaylistStore(listOf(1, 2, 3)))
    vm.reorder(playlistId = 1, from = 0, to = 2)
    assertEquals(listOf(2, 3, 1), vm.state.first().entries)
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*SearchViewModelTest*' --tests '*PlaylistsViewModelTest*'`
Expected: FAIL.

- [ ] **Step 3: Implement search and playlist state**

Debounce search by 250 ms; group results by album, artist, and track. Playlists are ordered lists of track IDs; reorder updates positions in a single transaction. Add create/rename/delete dialogs and a reorderable list using `LazyColumn` drag handles.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests '*SearchViewModelTest*' --tests '*PlaylistsViewModelTest*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add .
git commit -m "feat: add search and playlist management"
```

---

### Task 8: UAC descriptor parser

**Files:**
- Create: `core/playback/src/main/kotlin/dev/hien/bitstream/core/playback/usb/UacModels.kt`
- Create: `core/playback/src/main/kotlin/dev/hien/bitstream/core/playback/usb/UacDescriptorParser.kt`
- Test: `core/playback/src/test/kotlin/dev/hien/bitstream/core/playback/usb/UacDescriptorParserTest.kt`

**Interfaces:**
- Produces: `data class UacDevice(val uacVersion: Int, val controlInterface: Int, val streamingInterface: Int, val altSetting: Int, val endpointAddress: Int, val syncType: SyncType, val feedbackEndpoint: Int?, val formats: List<UacFormat>, val clockSourceId: Int?, val featureUnitId: Int?, val volume: VolumeRange?, val rawDescriptor: ByteArray)`.
- Produces: `data class UacFormat(val channels: Int, val subslotBytes: Int, val bitResolution: Int, val sampleRates: List<Int>)`.
- Produces: `enum class SyncType { ASYNC, ADAPTIVE, SYNC, NONE }`.
- Produces: `class UacDescriptorParser { fun parse(raw: ByteArray): UacDevice? }`.

- [ ] **Step 1: Write the failing parser tests**

Construct synthetic UAC2 and UAC1 descriptor byte arrays with a small test builder. Assert:

```kotlin
@Test fun `uac2 picks the isochronous out alt setting and rates`() {
    val device = UacDescriptorParser().parse(uac2Descriptors())!!
    assertEquals(2, device.uacVersion)
    assertEquals(0x01, device.endpointAddress)
    assertEquals(listOf(44_100, 48_000, 96_000, 192_000, 384_000), device.formats.single().sampleRates)
    assertEquals(4, device.formats.single().subslotBytes)
    assertEquals(24, device.formats.single().bitResolution)
}

@Test fun `uac2 finds async feedback endpoint and clock source`() {
    val device = UacDescriptorParser().parse(uac2Descriptors())!!
    assertEquals(SyncType.ASYNC, device.syncType)
    assertEquals(0x82, device.feedbackEndpoint)
    assertEquals(10, device.clockSourceId)
}

@Test fun `uac1 reads endpoint sample rates`() {
    val device = UacDescriptorParser().parse(uac1Descriptors())!!
    assertEquals(1, device.uacVersion)
    assertEquals(listOf(44_100, 48_000), device.formats.single().sampleRates)
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :core:playback:testDebugUnitTest --tests '*UacDescriptorParserTest*'`
Expected: FAIL.

- [ ] **Step 3: Implement the parser**

Walk the raw descriptor bytes: skip interface associations, find the AudioControl interface (class 1, subclass 1), then the AudioStreaming interface alt settings with an isochronous OUT endpoint (`bmAttributes` transfer type 1). Parse type-I format descriptors, UAC2 clock source and feature unit, UAC1 endpoint sample-rate control, and feedback endpoints. Validate lengths; return `null` on malformed input rather than throwing.

- [ ] **Step 4: Run all playback unit tests**

Run: `./gradlew :core:playback:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add .
git commit -m "feat: parse UAC1/UAC2 descriptors"
```

---

### Task 9: USB device manager, permission flow, and descriptor dump

**Files:**
- Create: `core/playback/src/main/kotlin/dev/hien/bitstream/core/playback/usb/UsbDeviceSource.kt`
- Create: `core/playback/src/main/kotlin/dev/hien/bitstream/core/playback/usb/AndroidUsbDeviceSource.kt`
- Create: `core/playback/src/main/kotlin/dev/hien/bitstream/core/playback/usb/UsbDeviceManager.kt`
- Create: `app/src/main/kotlin/dev/hien/bitstream/ui/debug/UsbDebugScreen.kt`
- Test: `core/playback/src/test/kotlin/dev/hien/bitstream/core/playback/usb/UsbDeviceManagerTest.kt`

**Interfaces:**
- Produces: `interface UsbDeviceSource { fun devices(): List<UsbDeviceHandle>; fun open(handle: UsbDeviceHandle): Int }` where `UsbDeviceHandle` carries id/vendor/product/name/raw descriptor bytes and `open` returns a file descriptor.
- Produces: `class UsbDeviceManager(source, parser, store)` with `state: StateFlow<DacState>`, `connect(handle)`, `disconnect()`, `preferredDevice()`, `lastDeviceKey`.
- Produces: `sealed interface DacState { NoDevice; PermissionNeeded; Connecting; Connected(UacDevice); Error(reason) }`.
- Produces: `UsbDebugScreen` showing the parsed device, alt setting, rates, subslot, sync type, and a copyable descriptor hex dump.

- [ ] **Step 1: Write the failing selection test**

```kotlin
@Test fun `remembers last device and prefers it`() {
    val a = handle(id = "a", vendor = 1, product = 2)
    val b = handle(id = "b", vendor = 3, product = 4)
    val manager = UsbDeviceManager(FakeSource(listOf(a, b)), UacDescriptorParser(), lastKey = "b")
    assertEquals("b", manager.preferredDevice()?.key)
}

@Test fun `denied permission leaves state retryable`() {
    val manager = UsbDeviceManager(FakeSource(emptyList()), UacDescriptorParser(), lastKey = null)
    manager.onPermissionDenied("a")
    assertTrue(manager.state.value is DacState.PermissionNeeded)
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :core:playback:testDebugUnitTest --tests '*UsbDeviceManagerTest*'`
Expected: FAIL.

- [ ] **Step 3: Implement the Android USB manager**

Use `UsbManager.deviceList`, `requestPermission` with an immutable `PendingIntent`, and broadcasts for attach/detach/permission. Persist the last device key in DataStore. On open, call `usbManager.openDevice(device)` and return `connection.fileDescriptor`; keep the `UsbDeviceConnection` alive in the manager. Add `USB_DEVICE_ATTACHED` intent filtering and auto-connect when the remembered device returns.

- [ ] **Step 4: Run the tests and build**

Run: `./gradlew :core:playback:testDebugUnitTest :app:assembleDebug`
Expected: PASS.

- [ ] **Step 5: Verify the permission flow on hardware**

Install, open the debug screen, plug the JA11, grant permission, and confirm the parsed rates and descriptor dump appear. Deny permission once and confirm the app explains and offers retry without crashing.

- [ ] **Step 6: Commit**

```bash
git add .
git commit -m "feat: add USB DAC discovery, permission flow, and descriptor dump"
```

---

### Task 10: Native build, libusb wrap, and isochronous silence spike

**Files:**
- Create: `core/playback/src/main/cpp/CMakeLists.txt`
- Create: `core/playback/src/main/cpp/ring_buffer.h`
- Create: `core/playback/src/main/cpp/usb_audio.cpp`
- Create: `core/playback/src/main/cpp/jni_bridge.cpp`
- Create: `core/playback/src/main/kotlin/dev/hien/bitstream/core/playback/native/NativeAudio.kt`
- Modify: `core/playback/build.gradle.kts` (externalNativeBuild, ndkVersion)
- Create: `docs/usb-spike.md`
- Test: `core/playback/src/main/cpp/tests/ring_buffer_test.cpp`

**Interfaces:**
- Produces: `object NativeAudio { init { System.loadLibrary("bitstream_audio") } }` and external methods `nativeOpen(fd, endpoint, interfaceNumber, altSetting, subslotBytes, channels, sampleRate, syncType, feedbackEndpoint, clockSourceId): Long`, `nativeStartSilence(handle, frames): Int`, `nativeStop(handle)`, `nativeClose(handle)`.
- Produces: a debug-screen button "Send 2 s silence" that opens the DAC and streams zeros at 48 kHz.

- [ ] **Step 1: Write the failing ring buffer test**

```cpp
int main() {
  RingBuffer rb(64);
  uint8_t in[8] = {1,2,3,4,5,6,7,8};
  assert(rb.write(in, 8) == 8);
  uint8_t out[8] = {};
  assert(rb.read(out, 8) == 8);
  assert(out[0] == 1 && out[7] == 8);
  assert(rb.write(in, 64) == 64);   // fills
  assert(rb.write(in, 1) == 0);     // full
  assert(rb.read(out, 8) == 8);
  assert(rb.write(in, 8) == 8);
  return 0;
}
```

- [ ] **Step 2: Run it to verify it fails**

Run:
```bash
cmake -S core/playback/src/main/cpp -B build/native -DBITSTREAM_HOST_TEST=ON -G Ninja
cmake --build build/native
ctest --test-dir build/native --output-on-failure
```
Expected: FAIL — CMake project missing.

- [ ] **Step 3: Vendor and build libusb + libFLAC**

```bash
mkdir -p third_party
curl -L https://github.com/libusb/libusb/releases/download/v1.0.27/libusb-1.0.27.tar.bz2 -o /tmp/libusb.tar.bz2
tar -xjf /tmp/libusb.tar.bz2 -C third_party
curl -L https://downloads.xiph.org/releases/flac/flac-1.4.3.tar.xz -o /tmp/flac.tar.xz
tar -xJf /tmp/flac.tar.xz -C third_party
```

CMake: `BUILD_SHARED_LIBS=ON` for libusb; disable libusb tests/examples; disable FLAC programs, examples, tests, docs, and Ogg. Link `usb-1.0` and `FLAC` into `bitstream_audio`. Guard the host test target with `if(BITSTREAM_HOST_TEST)` so it compiles only the ring buffer and packet-sizing code with the system compiler.

- [ ] **Step 4: Implement the ring buffer and silence spike**

`RingBuffer` is a single-producer/single-consumer byte ring with `write`, `read`, `available`, `free`, and `clear`, using atomics. `usb_audio.cpp` wraps the fd with `libusb_init_context(..., LIBUSB_OPTION_NO_DEVICE_DISCOVERY)` + `libusb_wrap_sys_device`, sets auto-detach, claims the streaming interface, selects the alt setting, and submits a small pool of isochronous OUT URBs filled with silence. `jni_bridge.cpp` exposes open/start/stop/close.

- [ ] **Step 5: Run the host test to verify it passes**

Run:
```bash
cmake -S core/playback/src/main/cpp -B build/native -DBITSTREAM_HOST_TEST=ON -G Ninja
cmake --build build/native
ctest --test-dir build/native --output-on-failure
```
Expected: PASS.

- [ ] **Step 6: Run the hardware spike**

Install the debug build, open the debug screen, connect the JA11, and press "Send 2 s silence". Success criteria:
- Logcat tag `BitstreamUsb` shows the wrapped fd, the chosen claim strategy, alt setting, and URB submissions.
- No `LIBUSB_ERROR_BUSY`, no kernel-driver claim failure.
- The DAC stays connected and silent; no phone reboot or audio-server crash.
- Record the claim strategy and any JA11 quirks in `docs/usb-spike.md`.

If the spike fails, stop and fix the claim strategy before continuing. Do not build the rest of the engine on an unverified path.

- [ ] **Step 7: Commit**

```bash
git add .
git commit -m "feat: wrap USB fd with libusb and stream isochronous silence"
```

---

### Task 11: FLAC decode, ring buffer, and MD5 verification

**Files:**
- Create: `core/playback/src/main/cpp/flac_decoder.cpp`
- Create: `core/playback/src/main/cpp/flac_decoder.h`
- Create: `core/playback/src/main/cpp/pcm_packer.h`
- Modify: `core/playback/src/main/cpp/usb_audio.cpp`
- Modify: `core/playback/src/main/kotlin/dev/hien/bitstream/core/playback/native/NativeAudio.kt`
- Test: `core/playback/src/main/cpp/tests/pcm_packer_test.cpp`

**Interfaces:**
- Produces: `nativePlayFile(handle, fd, startFrame, sampleRate, bitDepth, channels, subslotBytes): Int`.
- Produces: `nativeEnqueueNext(handle, fd, sampleRate, bitDepth, channels, subslotBytes): Int` for gapless.
- Produces: `nativeStats(handle): LongArray` = `[state, underruns, framesWritten, md5State, errorCode]`.
- Produces: `PcmPacker` C++ equivalent used by both host tests and the decoder.

- [ ] **Step 1: Write the failing packer test**

```cpp
int main() {
  int32_t in[2] = {0x123456, -0x123456};
  uint8_t out[8] = {};
  pack_pcm(in, 2, 24, 4, out);
  assert(read_le32(out) == 0x12345600);
  assert(read_le32(out + 4) == (uint32_t)(-0x123456 << 8));
  return 0;
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cmake --build build/native && ctest --test-dir build/native --output-on-failure`
Expected: FAIL — packer missing.

- [ ] **Step 3: Implement libFLAC decoding**

Open the fd with `fdopen(dup(fd), "rb")`; initialize `FLAC__stream_decoder` with `init_FILE` and `write_callback`. In the callback: accumulate the STREAMINFO MD5 with `FLAC__MD5Accumulate` over the native integer samples, pack to the USB subslot with `pack_pcm`, and write to the ring. On `process_until_end_of_stream`, compare the accumulated MD5 with STREAMINFO's; set `md5State` to 1 (verified), 2 (failed), or 0 (unavailable/partial). Seek uses `FLAC__stream_decoder_seek_absolute`.

- [ ] **Step 4: Run host tests**

Run: `cmake --build build/native && ctest --test-dir build/native --output-on-failure`
Expected: PASS.

- [ ] **Step 5: Verify decode on hardware**

Play a 24/96 FLAC through the debug screen. Confirm the DAC reports 96 kHz, audio is correct, and `md5State` becomes verified at end of track. Play the same file partially and confirm it stays partial.

- [ ] **Step 6: Commit**

```bash
git add .
git commit -m "feat: decode FLAC to integer PCM with MD5 verification"
```

---

### Task 12: Playback engine, queue, gapless decisions

**Files:**
- Create: `core/playback/src/main/kotlin/dev/hien/bitstream/core/playback/engine/AudioBackend.kt`
- Create: `core/playback/src/main/kotlin/dev/hien/bitstream/core/playback/engine/PlaybackEngine.kt`
- Create: `core/playback/src/main/kotlin/dev/hien/bitstream/core/playback/engine/Queue.kt`
- Create: `core/playback/src/main/kotlin/dev/hien/bitstream/core/playback/engine/NativeAudioBackend.kt`
- Test: `core/playback/src/test/kotlin/dev/hien/bitstream/core/playback/engine/PlaybackEngineTest.kt`

**Interfaces:**
- Produces: `interface AudioBackend { suspend fun open(track: Track, format: AudioFormat): OpenResult; suspend fun start(uri: String, startFrame: Long); suspend fun pause(); suspend fun resume(); suspend fun stop(); suspend fun close(); fun setSoftwareVolume(enabled: Boolean); val events: Flow<BackendEvent> }`.
- Produces: `sealed interface BackendEvent { data class Ended(...); data class Underrun(...); data class Md5(val integrity: Integrity); data class Failed(val reason: String) }`.
- Produces: `interface PlaybackController { val state: StateFlow<PlaybackState>; fun play(); fun pause(); fun seekTo(ms: Long); fun next(); fun previous() }`.
- Produces: `class PlaybackEngine(backend, scope) : PlaybackController { val notices: StateFlow<List<String>>; fun setQueue(tracks, startIndex); fun setShuffle(on); fun setRepeat(mode); fun setSoftwareVolume(on) }` (notices keeps the last 20, newest last).
- Produces: `Queue` with `current`, `peekNext()`, `advance()`, `previous()`, seeded shuffle history, repeat off/all/one.

- [ ] **Step 1: Write the failing engine tests**

```kotlin
@Test fun `auto-advance skips an unsupported rate with a notice`() = runTest {
    val backend = FakeBackend(supportedRates = setOf(44_100, 48_000))
    val engine = PlaybackEngine(backend, this)
    engine.setQueue(listOf(track(1, 44_100), track(2, 384_000), track(3, 48_000)), 0)
    backend.finishCurrent()
    assertEquals(3L, engine.state.first().track?.id)
    assertEquals(1, engine.notices.value.size)
}

@Test fun `gapless is requested only when format matches`() = runTest {
    val backend = FakeBackend(supportedRates = setOf(44_100))
    val engine = PlaybackEngine(backend, this)
    engine.setQueue(listOf(track(1, 44_100, 24), track(2, 44_100, 16)), 0)
    backend.advanceToNearEnd()
    assertTrue(backend.enqueued.isEmpty())   // depth differs
}

@Test fun `queue persists position and repeat mode`() = runTest {
    val queue = Queue(listOf(1L, 2L, 3L), startIndex = 1, repeat = RepeatMode.ALL)
    queue.advance(); queue.advance(); queue.advance()
    assertEquals(2L, queue.current)
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :core:playback:testDebugUnitTest --tests '*PlaybackEngineTest*'`
Expected: FAIL.

- [ ] **Step 3: Implement the queue and engine**

The engine is a serialized state machine on a single dispatcher. `setQueue` filters nothing; `play()` asks the backend to open the current track and maps failures to `Error`/`NoDac`. Auto-advance requests the next playable track, emitting a notice for each skipped one. Gapless pre-enqueue happens when the current track is within 10 s of its end and `AudioFormat` matches exactly (rate, depth, channels). Position comes from `framesWritten - ringFrames`, not wall clock. Poll `nativeStats` every 100 ms in `NativeAudioBackend`.

- [ ] **Step 4: Add the corrupt-file test**

```kotlin
@Test fun `decode failure advances and reports`() = runTest {
    val backend = FakeBackend(failOn = setOf(2L))
    val engine = PlaybackEngine(backend, this)
    engine.setQueue(listOf(track(1), track(2), track(3)), 0)
    backend.finishCurrent()
    assertEquals(3L, engine.state.first().track?.id)
    assertTrue(engine.notices.value.any { it.contains("could not be decoded") })
}
```

- [ ] **Step 5: Run all engine tests**

Run: `./gradlew :core:playback:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add .
git commit -m "feat: add playback engine, queue, gapless, and skip rules"
```

---

### Task 13: Native USB writer integration (rates, feedback, underruns)

**Files:**
- Modify: `core/playback/src/main/cpp/usb_audio.cpp`
- Create: `core/playback/src/main/cpp/feedback.cpp`
- Modify: `core/playback/src/main/cpp/jni_bridge.cpp`
- Modify: `core/playback/src/main/kotlin/dev/hien/bitstream/core/playback/native/NativeAudio.kt`
- Test: `core/playback/src/main/cpp/tests/feedback_test.cpp`

**Interfaces:**
- Produces: `nativeSetSampleRate(handle, rate): Int` (UAC2 clock source `SET_CUR`, UAC1 endpoint `SET_CUR`).
- Produces: feedback parser `uint32_t feedback_to_q16_16(const uint8_t* data, int uacVersion)` and packet sizing `int packet_frames(feedback_q16_16, rate, interval_frames, min, max)`.
- Produces: `nativeStats` underrun count and last error code.

- [ ] **Step 1: Write the failing feedback tests**

```cpp
int main() {
  uint8_t uac2[4] = {0x00, 0x80, 0x00, 0x00}; // 0.5 in 16.16, little-endian
  assert(feedback_to_q16_16(uac2, 2) == 0x00008000);
  uint8_t uac1[3] = {0x00, 0x40, 0x00};       // 1.0 in 10.14
  assert(feedback_to_q16_16(uac1, 1) == 0x00010000);
  assert(packet_frames(0x00010000, 48000, 48, 44, 52) == 48);
  assert(packet_frames(0x00018000, 48000, 48, 44, 52) == 52); // clamped
  return 0;
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cmake --build build/native && ctest --test-dir build/native --output-on-failure`
Expected: FAIL.

- [ ] **Step 3: Implement rate control and the writer loop**

Before starting the stream, send the class-specific rate request for the selected alt setting. For async endpoints, submit an isochronous IN transfer on the feedback endpoint, parse the feedback value, and size each OUT packet within the endpoint's min/max. Keep a ring-fill outer correction that nudges packet sizing by at most one frame. Zero-fill on underrun and increment the counter. Map errors to typed codes. Support stop/start without reopening the device for same-format gapless.

- [ ] **Step 4: Run host tests**

Run: `cmake --build build/native && ctest --test-dir build/native --output-on-failure`
Expected: PASS.

- [ ] **Step 5: Hardware validation across rates**

Play 44.1/48/96/192/384 kHz files. For each: the DAC shows the correct rate, audio is clean, `underruns` stays 0 after 60 s, and the Path status is `Bit-perfect`. Test 16-bit and 24-bit files. Confirm 24-in-32 preserves the significant bits (DAC display and audible correctness).

- [ ] **Step 6: Commit**

```bash
git add .
git commit -m "feat: add UAC rate switching, async feedback, and underrun handling"
```

---

### Task 14: Media3 player adapter, service, notification, and audio focus

**Files:**
- Create: `core/playback/src/main/kotlin/dev/hien/bitstream/core/playback/session/BitstreamPlayer.kt`
- Create: `app/src/main/kotlin/dev/hien/bitstream/PlaybackService.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/kotlin/dev/hien/bitstream/BitstreamApp.kt`
- Test: `core/playback/src/androidTest/kotlin/dev/hien/bitstream/core/playback/session/BitstreamPlayerTest.kt`

**Interfaces:**
- Produces: `class BitstreamPlayer(controller: PlaybackController, looper: Looper) : SimpleBasePlayer(looper)` overriding `getState()` and the handlers for play/pause/seek/next/previous/setVolume.
- Produces: `class PlaybackService : MediaSessionService` owning `MediaSession` and `BitstreamPlayer`.
- Produces: manifest permissions `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `POST_NOTIFICATIONS`, `WAKE_LOCK`; service `foregroundServiceType="mediaPlayback"`.

- [ ] **Step 1: Write the failing player test**

```kotlin
@Test fun `player state reflects engine pause`() {
    val controller = FakePlaybackController(state = PlaybackState(status = PlaybackStatus.PAUSED))
    val player = BitstreamPlayer(controller, Looper.getMainLooper())
    assertEquals(Player.STATE_READY, player.playbackState)
    assertFalse(player.playWhenReady)
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :core:playback:connectedDebugAndroidTest --tests '*BitstreamPlayerTest*'`
Expected: FAIL.

- [ ] **Step 3: Implement the adapter and service**

Map engine state to `SimpleBasePlayer.State`: media items from the queue, `setPlayWhenReady`, `setContentPositionMs`, available commands, and `setVolume` only when hardware volume exists. `invalidateState()` on every engine state emission. The service builds the engine from `AppContainer`, creates the session, and handles `onGetSession`, `onTaskRemoved`, and `onDestroy`. Request `POST_NOTIFICATIONS` from the activity before first playback. Audio focus: `AUDIOFOCUS_GAIN`, pause on loss, no ducking.

- [ ] **Step 4: Run the test and build**

Run: `./gradlew :core:playback:connectedDebugAndroidTest :app:assembleDebug`
Expected: PASS.

- [ ] **Step 5: Verify background playback**

Start a track, press home, turn the screen off, and confirm playback continues with a notification and lockscreen controls. Test headset play/pause. Confirm the service stops when playback stops and the task is removed.

- [ ] **Step 6: Commit**

```bash
git add .
git commit -m "feat: add Media3 session, foreground service, and audio focus"
```

---

### Task 15: Now Playing UI, status chip, and volume

**Files:**
- Create: `app/src/main/kotlin/dev/hien/bitstream/ui/nowplaying/NowPlayingViewModel.kt`
- Create: `app/src/main/kotlin/dev/hien/bitstream/ui/nowplaying/NowPlayingScreen.kt`
- Create: `core/designsystem/src/main/kotlin/dev/hien/bitstream/core/designsystem/StatusChip.kt`
- Test: `app/src/test/kotlin/dev/hien/bitstream/ui/nowplaying/NowPlayingViewModelTest.kt`
- Test: `core/designsystem/src/test/kotlin/dev/hien/bitstream/core/designsystem/StatusChipTest.kt`

**Interfaces:**
- Produces: `fun pathLabel(path: BitPerfectPath, volume: VolumeState): String` and `fun integrityLabel(integrity: Integrity): String` — pure, tested.
- Produces: `NowPlayingViewModel.state: StateFlow<NowPlayingUiState>` with track, position, duration, format, path, integrity, underruns, dacName, volume.
- Produces: `NowPlayingScreen(onQueue: () -> Unit, onOutput: () -> Unit)`.

- [ ] **Step 1: Write the failing label tests**

```kotlin
@Test fun `hardware volume keeps bit perfect label`() {
    assertEquals("Bit-perfect", pathLabel(BitPerfectPath.BIT_PERFECT, VolumeState.Hardware(50)))
}
@Test fun `software volume demotes path`() {
    assertEquals("Software volume", pathLabel(BitPerfectPath.SOFTWARE_VOLUME, VolumeState.Software))
}
@Test fun `missing md5 is unavailable not failed`() {
    assertEquals("Unavailable", integrityLabel(Integrity.UNAVAILABLE))
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :core:designsystem:test :app:testDebugUnitTest --tests '*NowPlayingViewModelTest*'`
Expected: FAIL.

- [ ] **Step 3: Implement the status labels and screen**

Add `:core:model` as a dependency of `:core:designsystem` for `VolumeState` and status enums. Now Playing: large artwork, title/artist/album, expressive seek bar, transport, queue button, output sheet, and three status facts (path, integrity, underruns) with icon + text. Volume slider maps to hardware volume; when unavailable, show "Fixed output". Shared-element transition from the mini player's artwork. Use `MaterialTheme.motionScheme` springs; no custom easing.

- [ ] **Step 4: Add the ViewModel test**

```kotlin
@Test fun `state exposes exact source format`() = runTest {
    val vm = NowPlayingViewModel(FakeEngine(state = state(rate = 192_000, depth = 24)))
    assertEquals("24/192", vm.state.first().formatLabel)
}
```

- [ ] **Step 5: Run tests and build**

Run: `./gradlew :app:testDebugUnitTest :core:designsystem:test :app:assembleDebug`
Expected: PASS.

- [ ] **Step 6: Verify on hardware**

Play 16/44.1, 24/96, and 24/192 files. Confirm the chip reads `Bit-perfect`, MD5 becomes verified at track end, and underruns stay 0. Enable software volume and confirm the chip demotes.

- [ ] **Step 7: Commit**

```bash
git add .
git commit -m "feat: add Now Playing UI with bit-perfect status and volume"
```

---

### Task 16: Queue UI

**Files:**
- Create: `app/src/main/kotlin/dev/hien/bitstream/ui/queue/QueueViewModel.kt`
- Create: `app/src/main/kotlin/dev/hien/bitstream/ui/queue/QueueSheet.kt`
- Test: `app/src/test/kotlin/dev/hien/bitstream/ui/queue/QueueViewModelTest.kt`

**Interfaces:**
- Produces: `QueueViewModel.state` with items, current index, shuffle, repeat.
- Produces: `QueueSheet(onDismiss, onMove(from, to), onRemove(index), onPlay(index))`.

- [ ] **Step 1: Write the failing test**

```kotlin
@Test fun `remove keeps current index valid`() = runTest {
    val vm = QueueViewModel(FakeEngine(queue = listOf(1, 2, 3), index = 2))
    vm.remove(0)
    assertEquals(1, vm.state.first().currentIndex)
    assertEquals(listOf(2L, 3L), vm.state.first().items.map { it.id })
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*QueueViewModelTest*'`
Expected: FAIL.

- [ ] **Step 3: Implement the queue sheet**

Modal bottom sheet on compact, supporting pane on medium/expanded. Drag to reorder, swipe to remove, tap to play. Show format per row. Handle empty queue with a "Nothing queued" state.

- [ ] **Step 4: Run tests and build**

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add .
git commit -m "feat: add reorderable queue sheet"
```

---

### Task 17: Settings, library folders, and diagnostics

**Files:**
- Create: `app/src/main/kotlin/dev/hien/bitstream/ui/settings/SettingsViewModel.kt`
- Create: `app/src/main/kotlin/dev/hien/bitstream/ui/settings/SettingsScreen.kt`
- Create: `app/src/main/kotlin/dev/hien/bitstream/ui/settings/DiagnosticsScreen.kt`
- Test: `app/src/test/kotlin/dev/hien/bitstream/ui/settings/SettingsViewModelTest.kt`

**Interfaces:**
- Produces: `SettingsViewModel.addFolder(uri)`, `removeFolder(uri)`, `rescan()`, `setSoftwareVolume(on)`, `state`.
- Produces: diagnostics fields: UAC version, alt setting, endpoint, sync type, subslot/valid bits, rate, ring fill, URB stats, libusb errors, descriptor dump.

- [ ] **Step 1: Write the failing test**

```kotlin
@Test fun `software volume defaults off and persists`() = runTest {
    val store = FakeSettingsStore()
    val vm = SettingsViewModel(store)
    assertFalse(vm.state.first().softwareVolume)
    vm.setSoftwareVolume(true)
    assertTrue(store.softwareVolume)
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*SettingsViewModelTest*'`
Expected: FAIL.

- [ ] **Step 3: Implement settings and diagnostics**

Sections: Library (folders, rescan), Playback (DAC status, bit-perfect info, software volume, gapless), Appearance (theme, dynamic color), About, Diagnostics. Include the JA11 note: disable PEQ in FiiO Control and keep UAC2 for bit-perfect output. The diagnostics screen copies the descriptor dump to the clipboard.

- [ ] **Step 4: Run tests and build**

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add .
git commit -m "feat: add settings, folder management, and diagnostics"
```

---

### Task 18: Responsive pass for phone/tablet and portrait/landscape

**Files:**
- Create: `app/src/main/kotlin/dev/hien/bitstream/ui/adaptive/AdaptiveScaffold.kt`
- Modify: `app/src/main/kotlin/dev/hien/bitstream/ui/BitstreamApp.kt`
- Modify: `app/src/main/kotlin/dev/hien/bitstream/ui/library/LibraryScreen.kt`
- Modify: `app/src/main/kotlin/dev/hien/bitstream/ui/nowplaying/NowPlayingScreen.kt`
- Test: `app/src/androidTest/kotlin/dev/hien/bitstream/ui/adaptive/AdaptiveLayoutTest.kt`

**Interfaces:**
- Produces: `@Composable fun AdaptiveScaffold(content: @Composable (WindowSizeClass) -> Unit)` using `currentWindowAdaptiveInfo()`.
- Produces: `LibraryScreen` list-detail on medium/expanded; single pane on compact.
- Produces: `NowPlayingScreen` stacked on compact portrait, side-by-side art/controls on compact landscape and wider.

- [ ] **Step 1: Write the failing size-class tests**

```kotlin
@Test fun `compact width shows bottom navigation`() {
    composeRule.setContent {
        DeviceConfigurationOverride.ForcedSize(DpSize(400.dp, 800.dp)) { BitstreamApp() }
    }
    composeRule.onNodeWithTag("navigation_bar").assertIsDisplayed()
    composeRule.onNodeWithTag("navigation_rail").assertDoesNotExist()
}

@Test fun `expanded width shows navigation rail`() {
    composeRule.setContent {
        DeviceConfigurationOverride.ForcedSize(DpSize(1280.dp, 800.dp)) { BitstreamApp() }
    }
    composeRule.onNodeWithTag("navigation_rail").assertIsDisplayed()
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :app:connectedDebugAndroidTest --tests '*AdaptiveLayoutTest*'`
Expected: FAIL until the semantics and adaptive scaffold exist.

- [ ] **Step 3: Implement the adaptive layouts**

Use `ListDetailPaneScaffold` for library → album/artist and `SupportingPaneScaffold` for the queue on medium/expanded. On compact landscape, put Now Playing art and controls side by side. Cap text columns at 840 dp and Settings at 720 dp. Add test tags `navigation_bar` and `navigation_rail` to the chrome. Ensure predictive back and `SavedStateHandle` keep queue, position, scroll, and back stack across rotation.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :app:connectedDebugAndroidTest --tests '*AdaptiveLayoutTest*'`
Expected: PASS.

- [ ] **Step 5: Manual four-way check**

On the tablet and on a phone emulator, check portrait and landscape for: Library, Album, Now Playing, Queue, Search, Playlists, Settings. Rotate during playback; audio must continue and position/queue/scroll must survive. Check split-screen on the tablet.

- [ ] **Step 6: Commit**

```bash
git add .
git commit -m "feat: make every screen adaptive across phone/tablet and orientation"
```

---

### Task 19: Error handling, reconnect, and hardware validation

**Files:**
- Modify: `core/playback/src/main/kotlin/dev/hien/bitstream/core/playback/engine/PlaybackEngine.kt`
- Modify: `core/playback/src/main/kotlin/dev/hien/bitstream/core/playback/usb/UsbDeviceManager.kt`
- Modify: `app/src/main/kotlin/dev/hien/bitstream/PlaybackService.kt`
- Create: `docs/validation-checklist.md`
- Test: `core/playback/src/test/kotlin/dev/hien/bitstream/core/playback/engine/ErrorHandlingTest.kt`

**Interfaces:**
- Produces: typed error mapping for `NO_DEVICE`, `BUSY`, `STALL`, `OVERRUN`, `UNDERRUN`, `PROTOCOL`, `UNSUPPORTED_RATE`, `UNSUPPORTED_CHANNELS`, `DECODE_FAILED`.
- Produces: detach handling: pause, retain queue, notice, reconnect/resume offer.
- Produces: `docs/validation-checklist.md` with the JA11 checklist and results.

- [ ] **Step 1: Write the failing detach test**

```kotlin
@Test fun `detach pauses and keeps the queue`() = runTest {
    val backend = FakeBackend()
    val engine = PlaybackEngine(backend, this)
    engine.setQueue(listOf(track(1), track(2)), 0)
    engine.play()
    backend.detach()
    assertEquals(PlaybackStatus.PAUSED, engine.state.first().status)
    assertEquals(2, engine.state.first().queueSize)
    assertTrue(engine.notices.value.any { it.contains("DAC disconnected") })
}

@Test fun `reconnect does not auto-play without user action`() = runTest {
    val backend = FakeBackend()
    val engine = PlaybackEngine(backend, this)
    engine.setQueue(listOf(track(1)), 0)
    engine.play()
    backend.detach(); backend.reattach()
    assertEquals(PlaybackStatus.PAUSED, engine.state.first().status)
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :core:playback:testDebugUnitTest --tests '*ErrorHandlingTest*'`
Expected: FAIL.

- [ ] **Step 3: Implement error and reconnect handling**

Map every native error code to a typed reason and user-facing message. On detach: stop the stream, release the wakelock, set `PAUSED`, keep the queue and position, and post a notification. On reattach: restore `NoDac → Ready` but never auto-play; show a resume action. On folder permission loss: mark the library stale and prompt to re-pick. On service death: restore queue and position from `PlaybackStateStore`.

- [ ] **Step 4: Run the tests**

Run: `./gradlew :core:playback:testDebugUnitTest --tests '*ErrorHandlingTest*'`
Expected: PASS.

- [ ] **Step 5: Run the full hardware checklist**

Create `docs/validation-checklist.md` and execute it on the Tab S10 FE + JA11:

- Detect, permission grant/deny, connect.
- 44.1/48/88.2/96/176.4/192/352.8/384 kHz; 16/24/32-bit.
- Gapless same-format; gap on format change.
- Seek, pause/resume, next/previous, shuffle, repeat.
- Background + screen off; notification, lockscreen, headset buttons.
- Hardware volume; software volume demotion.
- MD5 verified for full tracks; `Unavailable` for a no-MD5 file; `Failed` for a corrupted file.
- Unplug mid-track: pause + notice + queue intact; replug: no auto-play, resume works.
- Mixed-rate queue with an unsupported rate: skip + notice.
- Rotation during playback in all four layouts.
- Battery/wakelock: wakelock released when paused.

- [ ] **Step 6: Build the release candidate**

Run: `./gradlew :app:assembleDebug` (debug-signed; personal use). Install and run the checklist once more.

- [ ] **Step 7: Commit**

```bash
git add .
git commit -m "fix: handle USB detach, errors, and reconnect; add validation checklist"
```

---

## Self-Review Notes

- **Spec coverage:** §7 modules → Task 1; §8.1–8.2 → Tasks 8–9; §8.3–8.6 → Tasks 10–13; §9 → Task 11; §10 → Tasks 3–5; §11 → Tasks 12, 14; §12–13 → Tasks 15, 17; §14 → Tasks 2, 6, 15, 16, 18; §15 → Task 19; §16 → tests throughout; §17 → Task 1; §18 → tasks in order.
- **Deferred to hardware:** UAC1 has no device; Task 8 covers parser fixtures and Task 19 marks UAC1 best-effort.
- **PCM packing lives once, in C++** (`pack_pcm` in Task 11). There is deliberately no Kotlin duplicate; the 24-in-32 rule is tested natively and on hardware.
- **Known coupling:** Task 10 is a spike; if the claim strategy fails, fix it before Task 11. Do not proceed on an unverified USB path.
