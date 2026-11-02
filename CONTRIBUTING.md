# Contributing

Thanks for your interest in improving Music Player.

## Getting started

1. Fork the repository and clone your fork.
2. Open it in Android Studio (JDK 21) and run the `app` configuration, or run `./gradlew assembleDebug`.
3. Create a branch for your change: `git checkout -b fix/short-description`.

## Guidelines

- Keep changes focused. One fix or feature per pull request.
- Follow the existing structure: data in `data/`, playback in `player/`, UI in `ui/`. The app uses manual dependency injection through `AppContainer`; please don't add a DI framework.
- The app is local-first and private. Changes that add accounts, analytics, advertising, or network calls beyond the optional MusicBrainz lookup won't be accepted.
- Keep the monochrome interface; colours come from album art, not fixed accents.
- Before opening a pull request, run `./gradlew assembleDebug lintDebug testDebugUnitTest` and check that the app still plays music, queues songs and survives rotation.
- Describe what changed and why, and include screenshots for UI changes.

## Reporting bugs

Open an issue with your Android version, device, the app version (Settings → About), and steps to reproduce. For audio problems, mention the file format.

## License

By contributing you agree that your work is released under the project's [MIT License](LICENSE).
