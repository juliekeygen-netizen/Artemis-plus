# Artemis Plus — Codex Review Handoff

This is the rolling review packet for the latest coherent task. Inspect live GitHub state before
relying on recorded SHAs.

---

## Task

Preserve Tailscale/VPN routing during computer discovery and polling.

## Repository state

- Branch: `fix/tailscale-vpn-host-polling`
- Base: `main` at `14d8c0485815db8cdd67354976e9e6c02e6c0d3e`
- Product implementation commit: `a2fb5f1231e0f329a5789c92bcb9e0da29cafd3a`
- Pull request: [#79](https://github.com/juliekeygen-netizen/Artemis-plus/pull/79)
- PR state at packet creation: open; CI pending

## Intent and user-visible behavior

When Android is connected to Tailscale or another VPN, Artemis must poll saved/manual host
addresses through that VPN. Convenience STUN discovery must not temporarily reroute the entire app
onto the underlying Wi-Fi and make the VPN host appear Offline.

## Implementation summary

- `ComputerManagerService` now skips external/WAN address discovery while a VPN is active.
- It no longer calls process-wide `bindProcessToNetwork()` or `setProcessDefaultNetwork()` for
  STUN. Normal PC polling therefore retains Android's selected VPN route.
- Non-VPN mDNS discovery still performs STUN and stores the discovered remote fallback address.
- `ComputerManagerVpnRoutingTest` locks the VPN/non-VPN policy and is in the mandatory focused CI
  gate.

## Materially changed files

- `app/src/main/java/com/limelight/computers/ComputerManagerService.java`
- `app/src/test/java/com/limelight/computers/ComputerManagerVpnRoutingTest.java`
- `.github/workflows/android-ci.yml`
- `PROJECT_STATE.md`
- `CODEX_HANDOFF.md`

## Architecture, compatibility, lifecycle, and performance

- Existing local, manual, remote, and IPv6 address precedence is unchanged.
- Saved Tailscale addresses and host database formats are unchanged; no migration is required.
- `CHANGE_NETWORK_STATE` remains declared because the connected-device foreground service uses it
  as an Android foreground-service prerequisite. The fix removes only global process routing.
- Skipping STUN on VPN avoids a global routing race and one blocking network operation. Host polls
  remain parallel and continue using the active Android route.
- The PC currently listens on TCP 47984, 47989, 47990, and 48010 on all interfaces; each port was
  reachable through this PC's Tailscale IPv4 address during the audit. Sunshine/Apollo firewall
  rules are enabled for all profiles.

## Validation actually run

- `ComputerManagerVpnRoutingTest` — PASS (2 tests).
- `:app:compileNonRoot_gameDebugJavaWithJavac` — PASS.
- `:app:assembleNonRoot_gameDebug` — PASS, including all four configured ABIs.
- Mandatory focused test command — attempted locally; the new test and most focused tests passed,
  but the command retained the known Windows-only source-contract line-ending failures in
  `GameDelayedCallbackLifecycleTest`, `GameStopWorkerLifecycleTest`, and
  `StreamContainerSurfaceLifecycleTest`. The implementation does not touch those sources/tests.
- `git -c core.whitespace=cr-at-eol diff --check` — PASS before documentation update.

## CI and release state

- PR #79 CI: pending at packet creation.
- No signed rolling APK is published from feature branches. A successful merge to `main` should
  trigger the established signed four-ABI `debug-latest` workflow.

## Required device follow-up

- Ensure Tailscale is connected on the OnePlus and the PC is online in the Tailscale peer list.
- If Artemis Plus has a LAN-only saved record, add the PC's stable Tailscale IP once through Add PC;
  debug Artemis Plus uses `com.limelight.noirdebug` and does not share the normal Artemis release
  app's host database.
- Confirm the PC becomes Online and a stream starts while the phone is off the home LAN.

## Audit hotspots and deferred work

- Do not reintroduce process-wide network binding for STUN while concurrent host polling exists.
- The user-reported Quick Menu/editor/Add Keys/key-picker issues and per-key long-press toggle are
  intentionally deferred to the next coherent UI phase.
- Lint, broader string/accessibility work, and test-baseline cleanup remain separate phases.
