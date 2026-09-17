# Changelog

All notable changes to this fork are documented here. Versions before 9.0 come from the
upstream [Record You](https://github.com/you-apps/RecordYou) project (last upstream release: 8.0).

## 9.0 — first RecordYou-pro release (versionCode 20)

### Never lose a recording
- Lossless recordings are written as a **valid WAV from the first second**: a placeholder header
  is written up front and the length fields are re-sealed every few seconds, so an interrupted
  file is still playable instead of being an unreadable raw dump.
- Crash / force-stop recovery: the next app start offers the interrupted recording with
  **Save / Delete / Ignore** in a non-blocking banner. Recovered files are named `Recovered_…`.
- Saved files are named after the moment the recording **started** (taken from the pending file
  name) instead of the moment you pressed stop.
- Interrupted copies left behind in the output folder are re-sealed silently on start.

### Saving no longer blocks the app
- Stop = seal the header + move the file: an atomic rename on app-owned storage (instant), a
  buffered background copy for SAF folders.
- The finalise step runs on an application-scoped coroutine, so it survives service teardown.
- WAV header handling rewritten: exact length calculation (no reliance on
  `InputStream.available()`), no garbage tail on short reads, `FileChannel`-based header patching.
- Save feedback lives in the UI: a banner shows *Saving…* → *Saved: <file>* (or *Saving failed*)
  and dismisses itself; the notification always points at the real output file.

### Recording behaviour switches (Settings)
- **Tap anywhere to record** — hide the record button, start from the centre area; the original
  stop/pause buttons reappear while recording.
- **Keep recording when swiped away**.
- **Hide app from background tasks** (best effort).
- **Go to desktop after recording starts**.
- **Auto start recording on app open** (mutually exclusive with the previous switch).

### Reliability fixes
- Timer and waveform self-heal: both loops are self-sustaining and are re-synced whenever the app
  comes back to the foreground, so a recreated activity no longer sits at 0:00 or loses the
  waveform. The service exposes the true elapsed time for that re-sync.
- Fixed a crash when saving a recovered recording (toast on a non-Looper thread), plus a
  coroutine exception handler so a failing background save can never kill the app.
- Fixed the saving banner getting stuck after re-entering the home screen.
- The running recording's pending file is no longer offered as an "interrupted recording".
- App-bar title shows `RecordYou` with a superscript gold `PRO`.

### Housekeeping
- Application id is now `com.recordyou.pro` (own identity, so the fork can coexist with upstream
  and be eligible for F-Droid later).
- Release signing (keystore kept out of the repository) and a CI workflow that builds, verifies
  and attaches a signed APK for every `v*` tag.
- App name is `RecordYou-pro`; `versionCode` 20 / `versionName` 9.0.
