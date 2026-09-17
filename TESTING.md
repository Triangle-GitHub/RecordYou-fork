# Manual test checklist

The project has no automated tests yet, so please run this list before a release. Most items come
from real regressions and are cheap to check on a device.

## Lossless (WAV) recording
- [ ] Record a long take (≥ 5 min) and stop: the file appears **immediately** (app-owned folder) and
      plays back with the correct duration.
- [ ] Output folder set to a SAF folder (Settings → directory): stop still saves without freezing
      the UI, and the file lands in the chosen folder.
- [ ] Pause / resume mid-recording: resulting WAV plays, duration matches, no glitches at the seam.
- [ ] Saved file name reflects the **recording start** time, not the stop time.

## Crash / interruption recovery
- [ ] Force-stop the app mid-recording (`am force-stop` or "Force stop" in system settings), reopen:
      a pending banner offers the recording; **Save** produces a playable `Recovered_…wav`.
- [ ] Same, but tapping **✕ (ignore)**: banner disappears, recording keeps running / app stays usable.
- [ ] Same, but tapping **Delete**: pending file is gone, no banner on next start.
- [ ] While a recording is RUNNING, reopen the app: the recovery banner must **not** appear for the
      recording in progress.

## Background / foreground transitions
- [ ] Record → press Home → reopen: elapsed time shows the **real** value and keeps counting.
- [ ] Record → press Home → reopen: the waveform is scrolling again (not empty, not frozen).
- [ ] Record → navigate to the recordings list and back while the save banner is visible: the banner
      clears on its own (never sticks on "Saving…").
- [ ] Switch "Keep recording when swiped away" ON → record → swipe the app from recents: the
      foreground notification stays and the recording continues.
- [ ] Same switch OFF → swipe from recents: the recording is saved and the notification disappears.
- [ ] Switch "Hide app from background tasks" ON → the app does not appear in recents (best effort).

## Save feedback
- [ ] Stop tap shows the in-UI banner (`Saving…` → `Saved: <name>`), no toast, auto-dismisses.
- [ ] Failure path (e.g. revoke the SAF folder): banner shows *Saving failed* and the app does not
      crash.

## Behaviour switches
- [ ] "Tap anywhere to record": idle page hides the red button, tapping the centre starts a
      recording, and the stop/pause buttons reappear while recording.
- [ ] "Go to desktop after recording starts": the app minimises immediately after starting (audio
      and screen recording).
- [ ] "Auto start recording on app open": opening the app starts an audio recording; enabling one of
      the last two switches disables the other.

## Screen recording & other inherited features
- [ ] Screen recording with microphone audio, and with internal audio where supported.
- [ ] Annotation overlay toggle works while screen recording.
- [ ] In-app player plays a saved file; trimmer produces a trimmed file without re-encoding.
- [ ] Release build sanity: `./gradlew assembleRelease`, then `apksigner verify --print-certs` shows
      the `CN=RecordYou-pro` certificate (never the debug key).
