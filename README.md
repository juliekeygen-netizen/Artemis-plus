# Artemis Plus

Artemis Plus is an experimental community derivative of **Artemis Android** focused on richer on-screen controls, better desktop/keyboard use, and continued improvements to the Android GameStream experience.

It is built on **Marssvoodoo/artemis-android**, which carries forward Artemis with newer reconnect, Wi-Fi telemetry, performance-overlay, and stability work. Artemis Plus selectively ports useful On-Screen Controller (OSC) ideas from **ZDPepos/diana-oscsuite** instead of replacing the newer streaming base with Diana's older branch.

> **Status:** active development. The first Diana-inspired OSC/action-button implementation is on `main`; follow-up audit work adds regression tests, state-recovery fixes, and an easy GitHub Actions APK build.

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

The custom keyboard/buttons OSC layer can now contain controls that execute an action **inside Artemis itself** instead of sending a keyboard/mouse/gamepad input to the PC.

Current local actions are:

- **Soft Keyboard** — show/hide the Android soft keyboard
- **Full Keyboard** — toggle Artemis's full on-screen keyboard
- **Rotate Screen**
- **Quick Menu**
- **Toggle HUD**
- **Mouse Mode** — open Artemis's mouse-mode selector
- **Toggle Zoom**
- **Gamepad Overlay** — toggle the virtual gamepad
- **Custom Buttons Overlay** — toggle the custom-button/keyboard controller

These use the existing custom OSC element system, so they can be moved, resized, hidden/enabled, and have their geometry persisted like normal buttons.

### Adding action buttons

1. Show Artemis's custom-buttons / keyboard OSC layer.
2. Tap its settings gear to enter the first configuration mode.
3. Alongside **Clear All** and **Add Keys**, tap **Add Actions**.
4. Select the Artemis actions you want and tap **Apply**.
5. Continue tapping the settings gear to enter Move and Resize modes and place the buttons where you want them.
6. Exit configuration mode to save the layout.

The action selection is stored per existing keyboard OSC layout/profile. Support for putting Artemis actions directly into the existing custom-key import/export JSON format is still planned.

## Existing deposited controls

The Marssvoodoo base already contained much of Diana's useful keyboard-OSC work, including the **Add Keys** flow for depositing keyboard keys, mouse controls, joysticks/D-pads, and imported custom key combinations into the movable on-screen layer. Artemis Plus keeps that code and extends the same layer with **Add Actions** rather than replacing it.

## Not ported yet

The first pass intentionally does **not** include everything from Diana:

- Automatic per-game OSC profile selection/UI
- Artemis Action entries in custom-key import/export files
- Final localization/icons/polish for the new menus
- Diana's foldable cover-screen trigger controller and analog trigger emulation
- Diana's full profile-overlay/cover-screen UX

Those can be evaluated independently without pulling Diana's foldable dependencies into the initial OSC port.

## Server compatibility

Artemis Plus is primarily intended for [Apollo](https://github.com/ClassicOldSong/Apollo), while retaining the compatibility inherited from its Artemis base where possible.

## Easy debug APK build

You do **not** need Android Studio just to get a test APK. The repository includes a GitHub Actions workflow that builds debug-signed APKs in the cloud.

1. Open the repository's **Actions** tab.
2. Choose **Build Debug APK**.
3. Click **Run workflow** and run it from `main`.
4. Open the completed workflow run and download the **Artemis-Plus-debug-APKs** artifact.
5. Extract the ZIP and install the APK matching your device CPU. `arm64-v8a` is the usual choice for modern Android phones/tablets.

The artifact also contains `INSTALL.txt` and SHA-256 checksums. The debug application ID is separate from the normal release application ID, so the debug build can normally coexist with a regular Artemis installation.

### Local build

If you want to build locally instead:

1. Install Android Studio and the Android NDK required by the project.
2. Clone the repository with its submodules, or run:

   ```bash
   git submodule update --init --recursive
   ```

3. Create `local.properties` in the project root if needed and point `ndk.dir` at your installed Android NDK.
4. Run:

   ```bash
   ./gradlew :app:assembleNonRoot_gameDebug
   ```

The generated APKs are placed under `app/build/outputs/apk/`.

## Verification

GitHub Actions now performs both a Java compile check and the `nonRoot_gameDebug` unit-test suite on `main`, audit branches, and pull requests. OSC profile recovery has dedicated regression tests in addition to the existing project tests.

## Credits

Artemis Plus builds on substantial work by many people. In particular:

- **Moonlight Android** — Cameron Gutman, Diego Waxemberg, Aaron Neyer, Andrew Hennessy, and the wider Moonlight contributor community
- **Artemis / Moonlight Noir and Apollo** — ClassicOldSong and contributors
- **Marssvoodoo/artemis-android** — Marssvoodoo, including the newer Artemis reliability/streaming work used as this project's base
- **Diana OSC Suite** — ZDPepos, whose OSC profile, snapping, paired-sizing, deposited-control, and foldable-control experiments are important references for this project

Please preserve upstream copyright and attribution notices when redistributing modified builds.

## License

This project inherits the **GNU General Public License v3.0** licensing of the upstream Moonlight/Artemis codebase. See [LICENSE.txt](LICENSE.txt) for the full license text.
