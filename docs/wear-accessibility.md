# Wear accessibility and verification (#31)

Capture's primary button exposes its actual phase as stateDescription alongside its visible action and status text. Settings sensitivity buttons expose the current numeric value. AI choice uses one native Wear SwitchButton with checked state, explicit Switch role, visible label and localized state description, shared by settings and AI details. Screen panes/headings/traversal grouping identify navigation without replacing useful child text. Rapidly changing graph values are not live regions, so no continuous speech announcement is requested.

Input RMS is a solid path and envelope a dashed path, with explicit labels and both latest numeric values in the Canvas description. Output remains a separate duration/intensity row labeled as commands rather than physical measurements. Path subpaths preserve missing intervals. Color is not the only means of interpreting states or traces.

UllimTheme keeps the watch's dynamic palette only when its enabled text/button pairs reach4.5:1 and chart/outline pairs reach3:1, including actual output alpha. Otherwise it uses the tested Wear fallback. The gate is remembered per palette; no new high-contrast preference is stored. Disabled controls remain visibly disabled with independent status text. This numerical color gate does not substitute for device settings/readability checks.

Controls have a minimum48dp touch target and text is allowed to wrap; font scale is not reduced to make text fit. Main/settings/guide/errors use a scrollable round-screen layout. AI details has a pane title and a full-width wrapping Back control rather than a fixed circular text button. Diagnostics uses the same theme, heading/pane information and explicit touch minima. System Back navigation in the main screen returns through existing routes without starting capture.

## Host evidence

- Pure tests measure fallback enabled text/button/chart/outline pairs and low-contrast palette rejection.
- On a192dp round-qualified host, WearAccessibilityTest renders at actual Compose fontScale2.0, checks TextLayoutResult overflow/ellipsis/wrapping for primary controls, sensitivity/AI and graph labels, measures touch minima, validates state/role/checked semantics and invokes the real Activity back dispatcher.
- The real AiControlsActivity test changes Android font configuration and asserts its layout density is2.0. Its old52dp circular Back label overflowed; the wrapping full-width control removes that overflow.
- Existing MainActivity, settings persistence, AI and capture/error tests run alongside these checks; accessibility labels do not drive microphone or model state.
- A synchronous View draw generated and was visually inspected at `app/build/host-ui/large-font-home.png`. It is a192px host viewport after scrolling to the large primary button, not a Watch7 screenshot. PixelCopy/captureToImage timed out under PAUSED Robolectric and is not claimed as working device capture.

Run `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug` locally. Native TalkBack has not been run; semantics tests do not establish spoken focus order or actual assistive-service behavior.

## Remaining device/dependency checklist

- [ ] Integrate #29 onboarding with its original owner's consent/permission behavior preserved, then include it in these checks. PR65 remains separate; its current host visibility-test failure was reported to the owner.
- [ ] On Watch7, read and operate Start/Starting/Stop/Stopping/error, sensitivity limits and AI OFF/ON/fault with TalkBack; verify focus movement and announcements without graph speech spam.
- [ ] Change system font/display scale and high-contrast settings; capture every screen, including long model originals, errors, diagnostics and onboarding.
- [ ] Verify circular-edge clipping, scroll reachability, touch accuracy, system Back/gestures and return from system permission settings.
- [ ] Confirm no status relies solely on color or vibration, and microphone/AI descriptions do not imply complete hearing assistance or treatment.

These boxes stay unchecked until actual evidence exists. Predecessor main merges and issue DoD remain incomplete; keep #31/its PR open/Draft. No Actions, raw audio storage, transmission, diagnosis or native accessibility validation is implied.

References: [Compose semantics](https://developer.android.com/develop/ui/compose/accessibility/semantics), [Android touch targets](https://developer.android.com/guide/topics/ui/accessibility/apps).
