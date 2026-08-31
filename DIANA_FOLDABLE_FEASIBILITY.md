# Diana Foldable Feasibility Audit

**Status:** read-only planning audit; no Android runtime behavior changes.

## Decision

Do not port a Diana foldable feature wholesale. The referenced Diana source does not contain a
separate cover-screen trigger controller, virtual analog-trigger implementation, or a
cover-screen profile-overlay system that Artemis Plus can safely transplant. Its relevant
foldable commits are generic upstream compatibility fixes that are already present, or are
superseded by, the newer Artemis base.

Any product work should begin with a small, independently reviewable proof of concept (POC),
with ordinary non-foldable operation as the default fallback.

## Evidence reviewed

- Artemis Plus `main` at `de32c77770346c7029dcde8011157b3ab302ed13`.
- The local historical `origin/feature/diana-osc-port` branch. It is an older OSC-port branch,
  not a foldable implementation; its tip is `502f08bbd3696b292c663592534de4eda2e3aae3`.
- [ZDPepos/diana-oscsuite](https://github.com/ZDPepos/diana-oscsuite) at
  `3397ec7750969466ad8983364ee1a33182bbffa1`, including its reachable history.

The original source's relevant history is upstream Android compatibility work:

- `593616d2` refreshes browsing-grid layouts on a configuration change;
- `f5ad5d97` repairs Fold 4 DeX touchpad click normalization; and
- `010dfdf8` reloads Stream Settings when the default display's physical pixel count changes.

There is no fold/posture API, cover-display Activity, device-vendor trigger service, or dedicated
cover-screen profile UI in that source. The latter settings change is already retained in
`StreamSettings`, and Artemis Plus has additional `Game.onConfigurationChanged()` refresh work.

## Current Artemis Plus seams

| Concern | Existing owner | Audit result |
| --- | --- | --- |
| Configuration and changing screen dimensions | `Game`, `StreamSettings`, manifest `configChanges` | Generic resize/configuration handling exists. `StreamSettings` deliberately compares physical pixel counts because a foldable cover and inner screen can share `DEFAULT_DISPLAY`. |
| External displays | `Game` and `utils/ExternalDisplayControlActivity` | This handles a secondary external display; it must not be repurposed as a cover-screen abstraction. |
| Physical-controller analog triggers | `ControllerHandler` | Existing Android `InputDevice` axes are normalized and reported as analog triggers. |
| On-screen triggers | `VirtualController`, `LeftTrigger`, `RightTrigger` | The current OSC triggers are digital: press sends `0xFF`, release sends `0x00`, through `reportOscState()`. |
| OSC profile persistence/switching | `OscProfilesManager` | It snapshots/restores the legacy OSC working set, refreshes the controller, and preserves the editor mode. This remains the only profile state owner. |

The build currently has no AndroidX WindowManager dependency. Adding posture awareness would be a
deliberate new dependency/API decision, not a missing Diana import.

## Feasible follow-up POCs

### 1. Cover-screen trigger controller

Only pursue after defining the intended interaction on a named physical device. A POC should use a
small capability abstraction which reports *available/unavailable* rather than hard-coding a
Samsung, Motorola, or Pixel model. It may use a supported fold/posture API if one is needed, but
must leave the standard `Game` view, decoder surface, and existing OSC unchanged when unavailable.

The POC's scope should be limited to showing/hiding a small trigger affordance and forwarding its
state through the existing `VirtualController` input path. It must not create a second stream,
second `ControllerHandler`, or a cover-specific persistent layout format.

### 2. On-screen analog-trigger emulation

This is feasible without foldable hardware, but is distinct from physical-controller analog input.
The safe seam is the byte-valued trigger fields already forwarded by
`VirtualController.sendControllerInputContextInternal()` to `ControllerHandler.reportOscState()`.
An isolated control could vary that value from `0x00` through `0xFF`; it should not change the
physical-controller `reportControllerState()` dead-zone or capability logic.

Before exposing it, define a gesture that has an unambiguous release/cancel path, keeps existing
digital trigger layouts unchanged, and has a normal digital fallback. Regression tests should prove
value clamping, cancel/release reset, retransmission, and coexistence with the existing digital
trigger controls.

### 3. Profile-overlay / cover-screen UX

Treat this as a presentation layer over `OscProfilesManager`, not new profile storage. A POC may
surface the existing active/per-game profile selector in a compact overlay only when the capability
is available. It must call the manager's normal profile-switch operation so snapshot recovery,
legacy-layout compatibility, and edit-mode restoration remain intact.

Do not couple profile selection to a display ID: foldable screen changes can retain
`DEFAULT_DISPLAY`. Do not infer that the external-display controller is a cover-screen UI.

## POC acceptance and device matrix

No hardware-specific POC should be promoted without checks on at least one real foldable covering
both closed/cover and open/inner states, plus one non-foldable phone or tablet. Validate:

- folding/unfolding during an active stream and while the app is backgrounded/foregrounded;
- configuration, size, density, and inset changes without losing the decoder surface or input;
- OSC visibility, editor drag/resize, profile switch, and position persistence on each state;
- digital and any experimental analog trigger press, cancel, release, and reconnect behavior;
- Fast Resume, Keep Connection Alive, PiP/multi-window policy, and external-display behavior;
- clean fallback when posture APIs, a cover screen, or the optional POC capability are absent.

Real-device testing must include the vendor/Android versions that the proposed UI claims to
support. Robolectric can cover the pure capability policy and trigger-value state machine, but not
fold transitions, decoder-surface replacement, OEM insets, or device input routing.

## Recommended order

1. Keep this audit as the design boundary; do not add a dependency or hardware code yet.
2. If user demand supports it, prototype standalone virtual analog triggers first because they are
   device-independent and reuse an existing byte-valued OSC seam.
3. Separately prototype fold/posture detection behind a capability interface, with no visible
   behavior until a real device confirms a viable cover-screen interaction.
4. Only then evaluate a compact cover-specific trigger or profile presentation, preserving the
   current single-stream, single-controller, and single-profile-manager ownership model.
