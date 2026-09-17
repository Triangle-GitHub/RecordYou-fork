<!-- ---------- Header ---------- -->
<div align="center">
  <img width="200" height="200" src="fastlane/metadata/android/en-US/images/icon.png">
  <h1>RecordYou-pro</h1>
<p><b>Privacy-first voice &amp; screen recorder that never loses a recording — even if the app crashes.</b></p>

<!-- ---------- Badges ---------- -->
  <div align="center">
    <img alt="License" src="https://img.shields.io/github/license/Triangle-GitHub/RecordYou-fork?color=c3e7ff&style=flat-square">
    <img alt="Downloads" src="https://img.shields.io/github/downloads/Triangle-GitHub/RecordYou-fork/total.svg?color=c3e7ff&style=flat-square">
    <img alt="Last commit" src="https://img.shields.io/github/last-commit/Triangle-GitHub/RecordYou-fork?color=c3e7ff&style=flat-square">
    <img alt="Stars" src="https://img.shields.io/github/stars/Triangle-GitHub/RecordYou-fork?color=c3e7ff&style=flat-square">
    <br>
  </div>
</div>

> **RecordYou-pro is a maintained fork of [Record You](https://github.com/you-apps/RecordYou)** (upstream is archived/feature-frozen). All credit for the original app, its design and its translations goes to [Bnyro](https://github.com/Bnyro) and the You Apps contributors. This fork keeps the same privacy guarantees and adds the reliability and convenience features below.

<!-- ---------- Why ---------- -->
## Why this fork

Upstream is feature-complete but unmaintained — and long lossless WAV recordings had two nasty failure modes: stopping a recording could block the UI for a long time, and a crash mid-recording lost **everything**. This fork fixes both, and adds the quality-of-life switches the original never had.

### 🛟 Your recording survives a crash
- While recording, the app keeps writing a **valid WAV** (placeholder header, length fields re-sealed every few seconds), so an interrupted file is still a playable WAV, not a hidden raw dump.
- After a crash or a force-stop, the next time you open the app it offers to recover the interrupted recording — **Save / Delete / Ignore**, no timeout, no blocking dialog, and recording stays usable meanwhile.
- Files saved later are named after the moment the recording **started**, so the name always matches the audio.

### ⚡ Stopping is instant, saving never blocks you
- Save = seal the header + move the file. On the app's own storage that's an atomic rename (instant); on a user-picked SAF folder it's a buffered background copy.
- The save work runs outside the service lifecycle, so tearing the app down can't cancel it mid-way.
- A top banner tells you what's happening: *Saving…* → *Saved: <file>* (or *Saving failed*), then dismisses itself. You can start the next recording immediately.

### 🎛 Recording behaviour switches
- **Tap anywhere to record** — hide the record button and start from the whole center area.
- **Keep recording when swiped away** — removing the app from recents no longer stops the recording.
- **Hide app from background tasks** — best effort, never appears in recents.
- **Go to desktop after recording starts** — record and lock your phone in one tap.
- **Auto start recording on app open** — open the app and it is already recording. (Mutually exclusive with the switch above.)

### 🎚 Existing strengths, preserved
- Lossless **WAV** recording plus M4A / AAC / 3GP / OPUS with configurable sample rate, bitrate and channels
- **Screen recording** (H.264 / H.265 / VP8 / VP9) with an on-screen annotation overlay
- Pause / resume, live waveform, timer, in-app player and lossless **trimmer** (no re-encode)
- SAF support (choose any output folder), custom naming patterns, Material Design 3 with dynamic color
- **No ads, no trackers, and the app declares no `INTERNET` permission at all**

<!-- ---------- Download ---------- -->
## Download

<div align="center">

[<img src="https://raw.githubusercontent.com/vadret/android/master/assets/get-github.png" alt="Get it on GitHub" height="80">](https://github.com/Triangle-GitHub/RecordYou-fork/releases)

</div>

Releases are built and signed by CI from the tagged source. F-Droid is **not** available yet — with the new application id (`com.recordyou.pro`) a submission is possible, but the app is not on F-Droid today.

<!-- ---------- Screenshots ---------- -->
## Screenshots
<div style="display: flex">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/1-audio-recorder.png" width="24%">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/2-screen-recorder.png" width="24%">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/3-recordings.png" width="24%">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/4-settings.png" width="24%">
</div>

<!-- ---------- Limitations ---------- -->
## Known limitations (please read before reporting)

| Behaviour | Reality |
|---|---|
| Hide from recents | Uses the platform's `setExcludeFromRecents`, which has no public setter. Applied reflectively and **silently ignored where unavailable**. |
| Keep recording when swiped away | A foreground service survives the swipe, but aggressive OEM task killers (EMUI, MIUI, …) may still stop it. Battery-optimisation exemptions help. |
| Crash recovery | Recovers everything written to disk. The last few seconds may be missing if the process died between two header seals. |
| `pending_*` files | Interrupted recordings live in `…/tmp/` until you save or delete them from the recovery banner. |

<!-- ---------- Feedback ---------- -->
## Feedback and contributions
***All contributions are very welcome!***

* Bug reports and feature requests: please use the [issue tracker](https://github.com/Triangle-GitHub/RecordYou-fork/issues).
* For the upstream project itself (original app, translations, design), see [you-apps/RecordYou](https://github.com/you-apps/RecordYou).

## Credits
* [Bnyro](https://github.com/Bnyro) and the You Apps contributors — the original Record You app
* Icon design by [M00NJ](https://github.com/M00NJ)
* UI translations come from the upstream project's Weblate instance

## License

RecordYou-pro is licensed under the [**GNU General Public License v3.0**](https://www.gnu.org/licenses/gpl-3.0.html), like the upstream project: you can use, study, share and modify it as you want.
