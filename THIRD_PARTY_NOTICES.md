# Third-party notices

Music Player uses the following open-source components. Each remains under its own license.

| Component | License |
|---|---|
| AndroidX (Core, Lifecycle, Activity, Navigation, Room, Palette) | Apache License 2.0 |
| Jetpack Compose (UI, Material 3, Material Icons) | Apache License 2.0 |
| AndroidX Media3 (ExoPlayer, MediaSession) | Apache License 2.0 |
| Coil | Apache License 2.0 |
| Kotlin and kotlinx.coroutines | Apache License 2.0 |
| Guava | Apache License 2.0 |
| jaudiotagger (Android fork, used to write MP3 and M4A tags) | GNU LGPL 2.1 or later |
| Figtree typeface | SIL Open Font License 1.1 (see [FIGTREE_LICENSE.txt](FIGTREE_LICENSE.txt)) |

## Data

Tag suggestions come from the [MusicBrainz](https://musicbrainz.org) web service. MusicBrainz core data is available under the CC0 license; see <https://musicbrainz.org/doc/About/Data_License>. MusicBrainz is a trademark of the MetaBrainz Foundation; this app is not affiliated with or endorsed by it.

## About jaudiotagger and the LGPL

jaudiotagger is used unmodified as a separate library. Its source code and license are available from the project the app depends on (see `gradle/libs.versions.toml` and `app/build.gradle.kts`). Anyone may rebuild this app against a different version of the library using the instructions in the README.
