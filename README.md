# Music Player

A fast, private music player for the songs already on your Android phone. No account, no ads, no tracking, and it works fully offline.

Made by [Zayan Zaman](https://github.com/zayanzaman).

<!-- Add screenshots to docs/screenshots and link them here, e.g. ![Home](docs/screenshots/home.png) -->

## Features

- **Plays your own files:** reads the music library on your device (MP3, M4A, FLAC, OGG, WAV and anything else Android can decode).
- **Library:** Home, Songs, Library (liked songs, playlists, artists, albums) and search.
- **Home:** a personalized, curated feed of your recent, most played, and recommended music.
- **Playlists:** create playlists with custom covers, reorder by dragging, add songs from anywhere.
- **Smart playlists:** Liked Songs, Recently Played, and On Repeat, all maintained by the app.
- **Now Playing:** dynamic themes, intuitive navigation, queueing, sleep timer.
- **Sound:** equalizer with presets, bass boost, virtualizer, playback speed and pitch.
- **Background playback:** media notification, lock-screen and Bluetooth controls.
- **Tidy tags (optional, requires an internet connection):** looks up missing titles, artists and albums on [MusicBrainz](https://musicbrainz.org/) and writes them into MP3 and M4A files after you approve each change.
- **Customizable:** light, dark or follow-device theme, multiple seek bar styles, dynamic colors and several app icon styles.

## Requirements

- Android 11 (API 30) or newer
- To build: Android Studio (latest stable) and JDK 21

## Build

```bash
git clone https://github.com/zayanzaman/MusicPlayer.git
cd MusicPlayer
./gradlew assembleDebug        # Windows PowerShell: .\gradlew.bat assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/`. You can also open the folder in Android Studio and press Run.

Scrolling and animation are noticeably smoother in a release build than in a debug build, so use the `release` build variant when judging performance.

### Release builds

Release builds are signed with a key that is **not** part of this repository. Copy `keystore.properties.example` to `keystore.properties`, fill in your own keystore details, and run:

```bash
./gradlew bundleRelease        # Play Store (.aab)
./gradlew assembleRelease      # installable .apk
```

If `keystore.properties` is missing, the release variant is signed with the debug key so it can be tested locally.

## Project layout

```
app/src/main/java/com/zxn/musicplayer/
  data/      Room databases, repositories, MediaStore access, tag reading and writing, MusicBrainz client
  player/    Media3 playback service, queue persistence, equalizer, sleep timer, artwork cache
  ui/        Jetpack Compose screens, view models, theme and components
```

Built with Kotlin, Jetpack Compose, Media3 (ExoPlayer and MediaSession), Room, Coil and Navigation Compose, with plain manual dependency injection (`AppContainer`).

## Privacy

The app collects no data. See [PRIVACY.md](PRIVACY.md) for the full policy, including exactly what the optional tag lookup sends.

## Contributing

Bug reports and pull requests are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md). To report a security problem, see [SECURITY.md](SECURITY.md).

## License

Released under the [MIT License](LICENSE). Third-party libraries and fonts keep their own licenses, listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
