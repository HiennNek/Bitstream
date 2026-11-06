# Bitstream — M3 Expressive redesign from the `mork.html` mockup

**Status:** design, awaiting approval
**Date:** 2026-11-05

## 1. Intent

Make Bitstream look and move like the bundled mockup (`Downloads/mork.html`, eight
artboards: Library and Now Playing in phone portrait/landscape, tablet portrait/
landscape, plus a tablet list + player split). The mockup is a Material 3
Expressive local-music player whose identity is:

- a color scheme generated from the current track's hue,
- generated "blob" cover art that slowly spins while playing,
- oversized rounded cards and pill controls,
- springy press feedback and morphing shapes,
- a waveform seek bar.

The rule for this work: **do not hand-build what Material 3 already ships.** Use
`MaterialExpressiveTheme`, `MotionScheme.expressive()`, the emphasized type
styles, `MaterialShapes`, `NavigationBar`/`NavigationRail`, `ToggleButton`/
`IconToggleButton` (their default shapes already morph 28 → 18 dp), `ButtonGroup`,
`Slider`, `ModalBottomSheet`, `Card`, `ListItem`, `FilledIconButton`, `SearchBar`.
Hand-draw only the two things M3 has no component for: generated cover art and
the waveform track inside the stock `Slider`.

## 2. Scope

In:

- Design system: hue palette, animated scheme swap, cover art, waveform seek,
  shared header/section/tile components.
- Library screen, Now Playing (all four layouts), mini player, navigation bar/rail.
- Consistency pass on every other screen: Search, Playlists, Settings,
  Diagnostics, Album, Artist, Queue.
- Favorites, because the mockup's tile and heart need a real backing feature.

Out:

- Bundling Roboto Flex. Use the platform font through the M3 type scale.
- Android wallpaper dynamic color. It is replaced by the album-hue palette.
- The mockup's Home/Songs/Albums/Playlists IA. Destinations stay Library,
  Search, Playlists, Settings.
- Any audio, scanning, or playback-engine change.

## 3. Design system (`core:designsystem`)

### 3.1 Color

Port the mockup's generator verbatim into `TrackPalette.kt`: OKLCH → sRGB with
chroma reduction (`hx`), plus the role table (`pal`). This is ~40 lines of pure
Kotlin and needs no new dependency.

Roles, given hue `h`, tone curve `L(t)`, light tone `lt`, dark tone `dt`:

| role | chroma | hue | light L* | dark L* |
|---|---|---|---|---|
| primary / onPrimary | .16 | h | 40 / 100 | 80 / 20 |
| primaryContainer / onPrimaryContainer | .16 | h | 90 / 10 | 30 / 90 |
| secondary / onSecondary | .05 | h | 40 / 100 | 80 / 20 |
| secondaryContainer / onSecondaryContainer | .05 | h | 90 / 10 | 30 / 90 |
| tertiary / onTertiary | .11 | h+60 | 40 / 100 | 80 / 20 |
| tertiaryContainer / onTertiaryContainer | .11 | h+60 | 90 / 10 | 30 / 90 |
| surface | .012 | h | 98 | 6 |
| surfaceContainer | .012 | h | 94 | 12 |
| surfaceContainerHigh | .012 | h | 92 | 17 |
| surfaceContainerHighest | .012 | h | 90 | 22 |
| onSurface | .022 | h | 10 | 90 |
| onSurfaceVariant | .022 | h | 30 | 80 |
| outlineVariant | .022 | h | 80 | 30 |

`TrackHue.kt` derives the hue deterministically:
`hash("$albumArtist|$album") % 360`, falling back to `"$artist|$title"` when the
album is blank. Same album → same hue. Idle default is 190 (deep teal).

`BitstreamTheme(darkTheme, albumHue: Int?, albumColors: Boolean, content)`:
when `albumColors` is true and a track exists, build the generated scheme;
otherwise keep the current static `DeepTealLight/Dark`. The nine roles that paint
large surfaces (`surface`, `surfaceContainer`, `surfaceContainerHigh`,
`onSurface`, `onSurfaceVariant`, `primary`, `primaryContainer`,
`secondaryContainer`, `tertiaryContainer`) cross-fade with
`animateColorAsState(tween(500))`; the rest switch instantly.

Theme selection moves out of `MainActivity` into `BitstreamApp()` so it can read
the playing track. The existing `dynamic_color` setting is relabelled **Album art
colors** (same key, so no data migration) and toggles `albumColors`.

### 3.2 Type

Use the M3 Expressive type scale that ships with material3 1.5, not the mockup's
pixel literals:

| mockup | M3 token |
|---|---|
| Library title 36/44 w750 | `displaySmallEmphasized` |
| section title 22/28 w650 | `titleLargeEmphasized` |
| card title 22/28 w700 | `titleLarge` |
| track title 28/34 w700 | `headlineMediumEmphasized` |
| list title 16/22 w600 | `titleMedium` |
| body / secondary 14–16 | `bodyMedium` / `bodyLarge` |
| nav label 12 | `labelMedium` |
| "UP NEXT" 12 w600 tracking .8 | `labelMedium` + `letterSpacing` |

### 3.3 Shape

From the mockup: hero cards 32 dp, tiles and mini player 28 dp, list rows 24 dp,
search pill and nav indicator full-round. Add `Radii.hero = 32.dp` and
`Radii.tile = 28.dp` to `DesignTokens.kt`; reuse the existing scale elsewhere.

### 3.4 Motion

No custom easing curves. Everything reads from `MaterialTheme.motionScheme`:

- press scale, shape morph, and state color come free from `Button`,
  `FilledIconButton`, `ToggleButton`, `IconToggleButton`, `NavigationBarItem`;
- screen transitions keep the existing `FadeThrough` / `Detail` specs, retimed to
  `motionScheme.defaultSpatialSpec()` / `fastSpatialSpec()`;
- artwork spin is a 40 s linear infinite rotation, paused when not playing;
- waveform drift is a 1.4 s linear infinite amplitude oscillation;
- transport play button radius animates 40 → 28 dp when playing via
  `animateDpAsState` with `defaultSpatialSpec()`.

Where a screen needs a press-scale on a non-button surface (hero cards), wrap it
in a `Surface` with `onClick`; M3 supplies the ripple and the expressive
indication. No custom `pointerInput` scale modifiers.

### 3.5 Cover art — `CoverArt.kt`

One composable used at every size (48, 52, 290, 310 dp):

- If `track.artworkUri != null`: Coil `AsyncImage`, cropped, clipped to a
  `MaterialShapes` outline.
- Otherwise: a `Canvas` drawing two nested `RoundedPolygon`s from
  `MaterialShapes`, converted with `RoundedPolygon.toPath().asComposePath()`,
  outer filled `primary`, inner filled `tertiaryContainer`, plus a small center
  circle in `primary` (the mockup's vinyl hole).
- The outer shape is picked deterministically from
  `[Cookie9Sided, Clover4Leaf, Sunny, Flower, Puffy, Burst]` by track id; the
  inner from `[Clover4Leaf, Cookie6Sided, Sunny, PuffyDiamond]`.
- Rotation only while playing, only for art ≥ 120 dp.

### 3.6 Waveform seek — `WaveformSeek.kt`

A stock `Slider` with custom `track` and `thumb` slots:

- the inactive track is a 5 dp rounded line in `secondaryContainer`;
- the active track is the same line in `primary`;
- a sine wave (Canvas `Path`, 15 dp period, 4.5 dp amplitude while playing,
  0 while paused) is drawn along the elapsed portion, shifted by the infinite
  drift animation;
- `Slider` keeps drag, keyboard, and TalkBack semantics;
- time labels sit under it in `labelMedium` / `onSurfaceVariant`.

## 4. Screens

### 4.1 Shared chrome

- `BitstreamLargeHeader(title, actions)`: display title at 24 dp start, 48 dp
  icon buttons, used by every top-level and detail screen instead of `TopAppBar`.
- `BitstreamSectionHeader` becomes `titleLargeEmphasized` with 24 dp start inset
  and 24 dp top gap.
- `BitstreamSearchPill`: full-width 56 dp `Surface`, `surfaceContainerHigh`,
  radius 28, leading search icon, "Search your music"; used on Library.

### 4.2 Library

Top to bottom, matching `library-phone-portrait`:

1. header "Library" + overflow button;
2. search pill (navigates to the Search destination);
3. "Jump back in": `LazyRow` of 236 × 272 dp album cards, radius 32,
   `primaryContainer` background, generated art bleeding off the bottom-right,
   title + artist, 52 dp play/pause circle in the bottom-left;
4. 2 × 2 tile grid, 80 dp tall, radius 28, `surfaceContainerHigh`, 52 dp
   `MaterialShapes` glyph + label: **Shuffle all**, **Favorites**, **Artists**,
   **Folders**;
5. "Recently added": 64 dp rows, radius 24, 46 dp art, title/artist, duration.

Empty state and the scanning indicator keep working.

### 4.3 Now Playing

All layouts share one state and one control stack; only the arrangement differs:

- **phone portrait / tablet portrait**: close button + "Playing from" header +
  overflow; centered `CoverArt`; title + artist + heart `IconToggleButton`;
  waveform seek; prev / play / next transport row with asymmetric pill radii
  (40/14 dp outer buttons, morphing play); shuffle and repeat
  `IconToggleButton`s at 56 dp height; "Up next" peek card at the bottom that
  opens the existing queue `ModalBottomSheet`.
- **phone landscape**: 310 dp artwork on the left, the same control stack on the
  right.
- **tablet landscape**: two-pane, library list on the left and the player pane on
  the right (see §7 risk).

The mockup has no bit-perfect or volume UI, but Bitstream must keep it: a compact
`StatusChip` sits under the header and opens the existing output
`ModalBottomSheet` with DAC name, path, integrity, underruns, and volume. The
overflow button opens the same sheet.

Play/pause icon uses the new Material drawables; the heart uses `ic_favorite` /
`ic_favorite_border` (add the two vector drawables, copied from Material
Icons — not hand-drawn).

### 4.4 Mini player

`surfaceContainerHigh` card, radius 28, 52 dp art, title/artist, 56 × 52 play
button with 18/26 dp radius morph, 48 dp next, 4 dp progress line at the bottom.

### 4.5 Navigation

Keep the existing four destinations and the `navChromeFor` breakpoint. Replace
`NavigationBar`/`NavigationRail` styling with the stock M3 variants: pill
indicator, `labelMedium` labels, `surfaceContainer` background. Test tags
`navigation_bar` and `navigation_rail` stay on the stock components.

### 4.6 Consistency pass

- Search: `BitstreamLargeHeader("Search")` + stock `SearchBar`; results keep
  `ListItem` rows at 24 dp radius inside `surfaceContainerHigh` cards.
- Playlists: large header, 28 dp cards, FAB stays.
- Settings: large header, section cards at 28 dp, `Switch` rows unchanged.
- Diagnostics: large header, `LabeledValueRow` unchanged.
- Album / Artist: large header, 24 dp rows, cover art hero at the top.
- Queue: keep the sheet; restyle rows to 64 dp / 24 dp radius.

## 5. Favorites

Minimal vertical slice, no new screens beyond one list:

- `FavoriteEntity(trackId PK, addedAt)` and `FavoriteDao`
  (`observeIds`, `observeTracks` joined to `tracks`, `set`, `remove`).
- Database version 1 → 2 with a `Migration(1, 2)` that creates the table. The
  exported schema in `core/data/schemas/` is updated by the build.
- `MusicRepository` gains `favoriteTracks: Flow<List<Track>>`,
  `favoriteIds: Flow<Set<Long>>`, `suspend fun setFavorite(trackId, Boolean)`.
- `FavoritesViewModel` (repository + playback controller) backs both the Now
  Playing heart and the Favorites list; toggling is optimistic via the Room flow.
- Library tile → `favorites` route → `FavoritesScreen`: large header, rows with
  a filled heart to unfavorite, tap to play.

## 6. Accessibility

- All icon-only buttons get `contentDescription`; hearts expose toggle state via
  `IconToggleButton` semantics.
- Minimum 48 dp touch targets; nav labels are not the only affordance.
- The waveform slider keeps the stock `Slider` semantics; time labels are text.
- Contrast follows the generated scheme; the fallback teal scheme is unchanged.

## 7. Risks

- **Tablet landscape split**: the mockup shows Library and Now Playing
  side-by-side. Implemented in the expanded-width `NOW_PLAYING_ROUTE` layout:
  the route renders the same rail as the library, the "Songs" list on the left
  and the player pane on the right. No change to the nav graph, so deep links and
  back behaviour stay as they are. This is the highest-risk screen; it gets its
  own instrumented assertion (`now_playing_pane` tag on expanded width).
- **Hue-derived contrast** is not guaranteed by OKLCH alone. The port keeps the
  mockup's chroma reduction, which keeps colors in gamut; spot-check the darkest
  and lightest hues in both themes.
- **Generated art** replaces real artwork only when none exists; existing
  artwork rendering must not regress.

## 8. Testing

- `TrackPaletteTest` (pure): all 360 hues produce valid colors in light and dark;
  same input → same output.
- `TrackHueTest`: album-stable hue, idle default, range 0..359.
- `FavoriteDaoTest` (instrumented): set / remove / observe.
- `FavoritesViewModelTest`: toggle updates the emitted set.
- Update `LibraryViewModelTest` fakes for the new repository members.
- Keep `LibraryScreenTest` and `AdaptiveLayoutTest` green; they assert existing
  titles, "Choose folder", and the nav test tags.
- Build gate: `./gradlew :app:assembleDebug :app:testDebugUnitTest
  :core:data:testDebugUnitTest :core:model:testDebugUnitTest`.
