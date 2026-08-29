# Artemis Plus

Artemis Plus is an experimental community derivative of **Artemis Android** focused on richer on-screen controls, better desktop/keyboard use, and continued improvements to the Android GameStream experience.

It is built on **Marssvoodoo/artemis-android**, which carries forward Artemis with newer reconnect, Wi-Fi telemetry, performance-overlay, and stability work. Artemis Plus selectively ports useful On-Screen Controller (OSC) ideas from **ZDPepos/diana-oscsuite** instead of replacing the newer streaming base with Diana's older branch.

> **Status:** active development. The Diana-inspired OSC/action-button implementation has completed multiple implementation audits with state-recovery fixes, dedicated regression tests, verified APK builds, expanded local actions, native-style icon controls, and persistent native floating-control positions.

## Project lineage

Artemis Plus exists because several open-source projects and community forks built on one another:

1. [Moonlight Android](https://github.com/moonlight-stream/moonlight-android) — the original Android GameStream client.
2. [Artemis / Moonlight Noir](https://github.com/ClassicOldSong/moonlight-android) by ClassicOldSong — expanded Moonlight with Apollo integration, custom virtual controls, extra mouse modes, portrait/external-display features, custom shortcuts, and many other desktop-oriented additions.
3. [Marssvoodoo/artemis-android](https://github.com/Marssvoodoo/artemis-android) — the base used by Artemis Plus, with newer work such as smarter reconnect behavior, Wi-Fi quality monitoring, enhanced stream statistics, and lifecycle/thread-safety fixes.
4. [Diana OSC Suite](https://github.com/ZDPepos/diana-oscsuite) by ZDPepos — reference implementation for advanced On-Screen Controller ideas such as OSC profiles, smart snapping, paired sizing, deposited controls, and foldable-device experiments.
5. **Artemis Plus** — combines the newer Marssvoodoo base with selected Diana OSC ideas and new local Artemis-action controls.

Artemis Plus is an independent community derivative and is not an official Moonlight, Artemis, Apollo, Marssvoodoo, or Diana release.

## What is OSC?

In this project, **OSC means On-Screen Controller**: touch controls drawn over the streamed PC image. This includes virtual gamepad controls, keyboard buttons, mouse buttons, sticks, D-pads, triggers, and custom controls.

## Inherited Artemis features

The Marssvoodoo base retains the broad Artemis feature set, including:

- Custom virtual buttons with import/export support
- Custom resolutions and bitrates
- Multiple mouse modes: normal mouse, multi-touch, touchpad, disabled, and local-cursor modes
- Optimized virtual gamepad controls and free joystick mode
- External-monitor support
- Custom shortcut commands
- Easy Android soft-keyboard switching and a full on-screen keyboard
- Portrait mode and in-game screen rotation
- Trackpad/touchpad improvements and non-QWERTY keyboard-layout support
- Video scaling: Fit / Fill / Stretch
- Pan/zoom support
- Samsung DeX input improvements
- Apollo virtual-display, server-command, and clipboard integration
- SBS 3D support for external displays

## Marssvoodoo-base improvements

Artemis Plus deliberately starts from the newer Marssvoodoo code rather than the older Diana branch. That preserves work such as:

- Smarter automatic reconnect behavior
- Wi-Fi quality monitoring for adaptive streaming
- Expanded stream statistics and diagnostics
- Reconnect/overlay lifecycle fixes
- Thread-safety improvements
- Reduced avoidable UI/GC pressure

## Artemis Plus OSC additions

The first implementation selectively brings Diana-inspired controller editing ideas onto the newer base.

### Gamepad OSC editing

- **Smart snapping** — gamepad controls can snap to screen edges, a layout grid, and nearby controls while moving.
- **Paired sizing** — related groups can resize together, including A/B/X/Y, LT/RT, LB/RB, both sticks, both stick-clicks, and Start/Back.
- Snapping and paired sizing can each be toggled from the OSC profile dialog.
- Those two editing preferences persist between controller recreations/app sessions.
- Loaded controller geometry is clamped to safe minimum sizes/positions instead of blindly accepting broken saved values.

### Multiple OSC profiles

The gamepad OSC now has a basic multi-profile system:

- Create unlimited named layouts
- Switch layouts while streaming
- Rename profiles
- Delete profiles (the built-in `Default` profile is protected)
- Save the current layout
- Existing Artemis `OSC` layout data remains compatible; Artemis Plus snapshots/restores profiles around the original format rather than replacing it.
- Invalid/missing profile metadata is repaired back to a valid `Default` state instead of leaving stale profile references behind.
- Switching profiles while Move/Resize/Enable mode is active preserves that editor mode correctly.

**Usage:** long-press the gamepad OSC settings gear to open **OSC Profiles**. A normal tap on the gear still cycles through enable/disable, move, resize, and active modes as before.

Per-game profile metadata support is being laid down internally, but automatic per-game selection and its UI are **not wired yet**.

## Floating Artemis Action buttons

The custom keyboard/buttons OSC layer can contain controls that execute an action **inside Artemis itself** instead of sending a keyboard/mouse/gamepad input to the PC.

Current local actions are:

- **Soft Keyboard** — show/hide the Android soft keyboard
- **Full Keyboard** — toggle Artemis's full on-screen keyboard
- **Rotate Screen** — request a manual landscape/portrait switch; some devices currently do not apply the requested rotation until the activity is backgrounded/foregrounded, so this remains under investigation
- **Quick Menu** — open Artemis's floating game menu
- **Performance HUD** — toggle the legacy performance-statistics HUD (`performanceOverlay`); this is not a hide-all-UI control
- **Stats Overlay** — toggle the newer Artemis/Marssvoodoo statistics overlay
- **Floating Menu Button** — show/hide the native floating Quick Menu button
- **Touch Sensitivity** — toggle Artemis's custom touch-sensitivity processing
- **Clipboard to PC** — force-send the Android clipboard to the host
- **Clipboard from PC** — fetch the host clipboard into Android
- **Mouse Mode** — open Artemis's mouse-mode selector
- **Toggle Zoom** — toggle pan/zoom interaction mode
- **Gamepad Overlay** — toggle the virtual gamepad
- **Custom Buttons** — collapse/restore the custom-key/action layer while leaving this toggle itself visible so it can always restore the hidden controls

These use the existing custom OSC element system, so they can be moved, resized, hidden/enabled, and have their geometry persisted like normal buttons. Unlike ordinary keyboard buttons, Artemis-local action buttons require a deliberate direct press: sliding a held finger across neighbouring keys cannot accidentally trigger local actions such as Rotate, Menu, or Performance HUD.

### Native-style action icons

Artemis Action controls use icon-only buttons styled to match the native floating Quick Menu and Zoom/Pan controls:

- Default size is **36dp × 36dp**, matching the native floating controls.
- The controls reuse Artemis's existing `floating_menu_button` shell: translucent black circular fill, translucent white 2dp outline, and the same press ripple.
- Action glyphs are white Android VectorDrawables rendered in the same 24dp inner region used by the native floating controls.
- Resizing is **aspect-ratio locked**: action controls always remain square, so neither the circular shell nor its icon can be stretched on only one axis.
- Saved rectangular geometry from the older text-button implementation is normalized to a square when restored.
- Editor-state rings remain visible for Move, Resize, and Enable/Disable modes.
- **Custom Buttons** is state-aware: while the layer is expanded it shows the closed-eye icon (hide); after collapsing the layer it changes to the open-eye icon (show/restore).
- Soft Keyboard and Full Keyboard use related keyboard glyphs with different show/fullscreen cues, while Floating Menu Button uses a miniature circular-menu glyph so it remains distinct from Quick Menu.

The icon set is adapted from user-selected [Lucide](https://lucide.dev/) and [Tabler Icons](https://tabler.io/icons) designs and converted to Android VectorDrawables for Artemis Plus. A few glyphs are combined/modified for Artemis-specific actions.

### Adding action buttons

1. Show Artemis's custom-buttons / keyboard OSC layer.
2. Tap its settings gear to enter the first configuration mode.
3. Alongside **Clear All** and **Add Keys**, tap **Add Actions**.
4. Select the Artemis actions you want and tap **Apply**.
5. Continue tapping the settings gear to enter Move and Resize modes and place the buttons where you want them.
6. Exit configuration mode to save the layout.

The action selection is stored per existing keyboard OSC layout/profile. Hidden action state survives layout restoration, while explicitly re-adding an action makes it visible again. Support for putting Artemis actions directly into the existing custom-key import/export JSON format is still planned.

### Native floating-control position memory

The native **floating Quick Menu button** and **Zoom/Pan button** now remember where you drag them between stream sessions.

- Their final positions are saved automatically when a drag ends.
- Position is stored as normalized screen coordinates instead of raw pixels, making restoration safer across resolution changes.
- Portrait and landscape positions are stored independently.
- Existing click/drag behavior is otherwise unchanged.

## Existing deposited controls

The Marssvoodoo base already contained much of Diana's useful keyboard-OSC work, including the **Add Keys** flow for depositing keyboard keys, mouse controls, joysticks/D-pads, and imported custom key combinations into the movable on-screen layer. Artemis Plus keeps that code and extends the same layer with **Add Actions** rather than replacing it.

## Not ported yet

The current pass intentionally does **not** include everything from Diana or every planned Artemis Plus UI refinement:

- Automatic per-game OSC profile selection/UI
- Artemis Action entries in custom-key import/export files
- Final localization/menu polish
- Diana's foldable cover-screen trigger controller and analog trigger emulation
- Diana's full profile-overlay/cover-screen UX

Those can be evaluated independently without pulling Diana's foldable dependencies into the initial OSC port.

## Server compatibility

Artemis Plus is primarily intended for [Apollo](https://github.com/ClassicOldSong/Apollo), while retaining the compatibility inherited from its Artemis base where possible.

## Download the latest debug APK

The easiest download is the rolling [**Artemis Plus Debug (Latest)**](https://github.com/juliekeygen-netizen/Artemis-plus/releases/tag/debug-latest) prerelease.

Every successful build of `main` automatically refreshes that same release and moves the `debug-latest` tag to the newly built commit, so the URL stays stable instead of creating hundreds of release entries.

1. Open **Releases** and choose **Artemis Plus Debug (Latest)**.
2. Download the APK matching your device CPU.
3. For almost all modern Android phones/tablets, choose **`app-nonRoot_game-arm64-v8a-debug.apk`**.
4. Install the APK on Android.

The release also contains `INSTALL.txt` and `SHA256SUMS.txt`. These are debug-signed test builds, and the debug application ID is separate from the normal release application ID, so the build can normally coexist with a regular Artemis installation.

The workflow also keeps the **Artemis-Plus-debug-APKs** GitHub Actions artifact for 30 days as a secondary download method.

### Trigger a cloud build manually

You do **not** need Android Studio just to get a test APK:

1. Open the repository's **Actions** tab.
2. Choose **Build Debug APK**.
3. Click **Run workflow** and run it from `main`.
4. When the build succeeds, the **Artemis Plus Debug (Latest)** Release is refreshed automatically and the Actions artifact is uploaded too.

### Local build

For the simplest Windows workflow after the SDK/JDK are configured, run from the repository root:

```powershell
.\build-apk.ps1
```

That builds the non-root debug variant, selects the ARM64 APK, and copies it to the repository root as `Artemis-Plus-debug-arm64.apk`. Use `-OpenFolder` if you want Explorer to open with the result selected.

For a manual Gradle build instead:

1. Install Android Studio / the Android SDK and the Android NDK required by the project.
2. Clone the repository with its submodules, or run:

   ```bash
   git submodule update --init --recursive
   ```

3. Create `local.properties` in the project root and point `sdk.dir` at your Android SDK if Gradle does not find it automatically. Install Android platform 36 and NDK `27.0.12077973` to match CI.
4. Run:

   ```bash
   ./gradlew :app:assembleNonRoot_gameDebug
   ```

On Windows PowerShell use:

```powershell
.\gradlew.bat :app:assembleNonRoot_gameDebug
```

The generated APKs are placed under `app/build/outputs/apk/`.

## Verification

GitHub Actions uses the non-root debug variant as the main verification target:

- Java compilation is a hard gate.
- Artemis Plus OSC/profile/action regression tests are hard gates.
- The complete inherited Artemis/Marssvoodoo Robolectric suite is also run and its reports are uploaded for diagnostics.
- The debug APK workflow performs a full installable APK assembly in addition to Java compilation.

The inherited test baseline currently contains five known Robolectric failures across `LayoutInflationTest`, `SimpleStartupTest`, `StartupTest`, and `ProfilesNavigationTest`. The second audit reproduced the same five failures from the **pre-OSC base commit** (`f5587a81d73bf2501b68f1e5a48ca736aa5520a2`), proving they were not introduced by the Artemis Plus OSC changes. They remain visible in CI reports instead of being hidden, but do not make unrelated OSC commits fail their gate.

Dedicated Artemis Plus regression coverage currently includes profile metadata recovery, profile lifecycle behavior, direct-press-only safety for local Artemis Action buttons, and state-aware icon switching for the Custom Buttons toggle.

## Credits

Artemis Plus builds on substantial work by many people. In particular:

- **Moonlight Android** — Cameron Gutman, Diego Waxemberg, Aaron Neyer, Andrew Hennessy, and the wider Moonlight contributor community
- **Artemis / Moonlight Noir and Apollo** — ClassicOldSong and contributors
- **Marssvoodoo/artemis-android** — Marssvoodoo, including the newer Artemis reliability/streaming work used as this project's base
- **Diana OSC Suite** — ZDPepos, whose OSC profile, snapping, paired-sizing, deposited-control, and foldable-control experiments are important references for this project
- **Lucide Icons and Tabler Icons** — source designs for the Artemis Plus local-action icon set; several icons are adapted or combined for Artemis-specific meanings

Please preserve upstream copyright and attribution notices when redistributing modified builds.

## License

This project inherits the **GNU General Public License v3.0** licensing of the upstream Moonlight/Artemis codebase. See [LICENSE.txt](LICENSE.txt) for the full license text.
