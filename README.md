# Artemis Plus

Artemis Plus is an experimental community derivative of **Artemis Android** focused on richer on-screen controls, better desktop/keyboard use, and continued improvements to the Android GameStream experience.

It is built on **Marssvoodoo/artemis-android**, which carries forward Artemis with newer reconnect, Wi-Fi telemetry, performance-overlay, and stability work. Artemis Plus is also selectively porting useful On-Screen Controller (OSC) ideas from **ZDPepos/diana-oscsuite** instead of replacing the newer streaming code with the older Diana base.

> **Status:** active development. The OSC port and Artemis-local action buttons described below are being implemented incrementally and should not be treated as finished until they are marked complete.

## Project lineage

Artemis Plus exists because several open-source projects and community forks built on one another:

1. [Moonlight Android](https://github.com/moonlight-stream/moonlight-android) — the original Android GameStream client.
2. [Artemis / Moonlight Noir](https://github.com/ClassicOldSong/moonlight-android) by ClassicOldSong — expanded Moonlight with Apollo integration, custom virtual controls, extra mouse modes, portrait/external-display features, custom shortcuts, and many other desktop-oriented additions.
3. [Marssvoodoo/artemis-android](https://github.com/Marssvoodoo/artemis-android) — the base used by Artemis Plus, with newer work such as smarter reconnect behavior, Wi-Fi quality monitoring, enhanced stream statistics, and lifecycle/thread-safety fixes.
4. [Diana OSC Suite](https://github.com/ZDPepos/diana-oscsuite) by ZDPepos — reference implementation for advanced On-Screen Controller ideas such as OSC profiles, smart snapping, paired sizing, deposited controls, and foldable-device experiments.
5. **Artemis Plus** — combines the newer Marssvoodoo base with selected Diana OSC ideas and new local Artemis-action controls.

Artemis Plus is an independent community derivative and is not an official Moonlight, Artemis, Apollo, Marssvoodoo, or Diana release.

## What is OSC?

In this project, **OSC means On-Screen Controller**: the touch controls drawn over the streamed PC image. This includes virtual gamepad controls, keyboard buttons, mouse buttons, sticks, D-pads, triggers, and custom controls.

## Inherited Artemis features

The Marssvoodoo base retains the broad Artemis feature set, including:

- Custom virtual buttons with import/export support
- Custom resolutions and bitrates
- Multiple mouse modes: normal mouse, multi-touch, touchpad, disabled, and local-cursor modes
- Optimized virtual gamepad controls and free joystick mode
- External-monitor support
- Custom shortcut commands
- Soft-keyboard switching
- Full on-screen keyboard
- Portrait mode
- Trackpad/touchpad improvements
- Non-QWERTY keyboard-layout support
- Video scaling: Fit / Fill / Stretch
- Pan/zoom support
- In-game screen rotation
- Samsung DeX input improvements
- Apollo virtual-display integration
- Apollo server-command integration
- Clipboard synchronization with Apollo
- SBS 3D support for external displays

See the upstream Artemis repositories for the history behind these features.

## Marssvoodoo-base improvements

Artemis Plus deliberately starts from the newer Marssvoodoo code rather than the older Diana branch. That preserves work such as:

- Smarter automatic reconnect behavior
- Wi-Fi quality monitoring for adaptive streaming
- Expanded stream statistics and diagnostics
- Reconnect/overlay lifecycle fixes
- Thread-safety improvements
- Reduced avoidable UI/GC pressure

## OSC work being ported

The Diana OSC work is being brought over selectively so it can coexist with the newer base.

### Planned / in progress

- **Multiple OSC profiles** — save and switch between independent controller layouts
- **Per-game OSC profiles** — automatically choose a preferred layout for a game
- **Smart snapping** — align controls to screen edges, useful grid points, and nearby controls
- **Paired sizing** — resize related controls such as A/B/X/Y or LB/RB together
- **Deposited controls** — add keyboard, mouse, number, control, and function-key buttons to an OSC layout
- **OSC profile/configuration menu** — manage modes and layouts from the in-game quick menu

Diana's foldable/cover-screen controller experiments are intentionally **not part of the first port**. They can be evaluated separately later without adding their extra dependencies to the initial OSC work.

## Artemis Action buttons

A major Artemis Plus goal is to let an OSC button trigger an action **inside Artemis itself**, rather than only sending a keyboard/mouse/gamepad input to the PC.

Planned local actions include:

- Show / hide Android soft keyboard
- Toggle Artemis full on-screen keyboard
- Rotate stream screen
- Open the Quick Menu
- Toggle HUD / performance overlay
- Toggle or select mouse mode
- Toggle zoom mode
- Toggle virtual controller / keyboard controller

These are intended to behave like normal OSC elements: movable, resizable, saveable in profiles, and eventually import/export capable.

## Server compatibility

Artemis Plus is primarily intended for [Apollo](https://github.com/ClassicOldSong/Apollo), while retaining the compatibility inherited from its Artemis base where possible.

## Building

1. Install Android Studio and the Android NDK required by the project.
2. Clone the repository with its submodules, or run:

   ```bash
   git submodule update --init --recursive
   ```

3. Create `local.properties` in the project root if needed and point `ndk.dir` at your installed Android NDK.
4. Build the APK using Android Studio or Gradle.

## Credits

Artemis Plus builds on substantial work by many people. In particular:

- **Moonlight Android** — Cameron Gutman, Diego Waxemberg, Aaron Neyer, Andrew Hennessy, and the wider Moonlight contributor community
- **Artemis / Moonlight Noir and Apollo** — ClassicOldSong and contributors
- **Marssvoodoo/artemis-android** — Marssvoodoo, including the newer Artemis reliability/streaming work used as this project's base
- **Diana OSC Suite** — ZDPepos, whose OSC profile, snapping, paired-sizing, deposited-control, and foldable-control experiments are important references for this project

Please preserve upstream copyright and attribution notices when redistributing modified builds.

## License

This project inherits the **GNU General Public License v3.0** licensing of the upstream Moonlight/Artemis codebase. See [LICENSE.txt](LICENSE.txt) for the full license text.
