# Privacy Policy

**Music Player** — effective 2 November 2026

Music Player is built to keep everything on your phone. This policy explains what the app accesses, what it stores, and the single optional feature that uses the internet.

## Summary

- No account, no sign-in.
- No ads, no analytics, no crash reporting, no tracking.
- No data is sent to the developer or to any server operated by the developer.
- Your music files never leave your device.

## What the app accesses

| Permission | Why |
|---|---|
| Audio files (`READ_MEDIA_AUDIO`, or storage on Android 12 and older) | To list and play the music on your device. |
| Notifications | To show the playback notification and controls. |
| Foreground service (media playback), wake lock | To keep music playing with the screen off. |
| Audio settings | For the equalizer and sound effects. |
| Internet | Only for the optional "Update tags" feature (see below). |
| Modify system settings | Only if you choose "Set as ringtone"; Android asks you to allow it separately. |

## What is stored on your device

The following is stored in the app's private storage and is never uploaded: playlists, liked songs, play counts and listening history (used for Recently Played and On Repeat), the current queue, hidden songs, your settings, playlist cover images you choose, a cache of album art, and the results of past tag lookups. Cloud backup and device-to-device transfer are turned off for this data.

You can delete all of it at any time by clearing the app's storage in Android settings or uninstalling the app. Listening history older than 120 days is deleted automatically.

## The optional internet feature: Update tags

If you use **Settings → Update tags**, the app searches [MusicBrainz](https://musicbrainz.org), a public music database, to suggest corrected titles, artists, albums, years and genres.

- For each song that is scanned, the app sends only that song's **title and artist** (as already stored in the file or derived from its file name). No audio, file path, playlist, device identifier or account information is sent.
- As with any internet request, MusicBrainz can see your IP address and the app's identifying request header. Its handling of that data is described in the [MusicBrainz privacy policy](https://metabrainz.org/privacy).
- Requests use HTTPS only.
- Nothing is changed in your files until you approve it. Approved changes are written into the file itself (MP3 and M4A).

If you never open this feature, the app makes no network requests.

## Children

The app has no accounts and collects no personal information from anyone, including children.

## Changes to this policy

If the app's data practices change, this file will be updated and the effective date above will change. The version history is available in this repository.

## Contact

Questions or concerns: open an issue at <https://github.com/zayanzaman/MusicPlayer/issues> or visit <https://github.com/zayanzaman>.
