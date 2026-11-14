# Main listening UI implementation plan (#25)

> For agentic workers: use superpowers:executing-plans task by task.

**Goal:** Bind the Wear home/start/stop UI to real controller/service state and restore it after navigation/recreation.
**Architecture:** Integrate #59's Wear theme/scaffold/navigation with #61's runtime. MainActivity retains permission and service commands; ui/UllimApp receives immutable actual state and callbacks without optimistic listening flags. A separate read-only controller eligibility flow updates retry availability even when the Error value is unchanged. Mode/permission changes remain on the main control thread; audio/AI execution stays independent.
**Tech stack:** Existing Wear Material3/Compose, lifecycle-runtime-compose 2.9.2 (Kotlin2.0-compatible), Compose UI tests and Robolectric 4.17 on SDK34 round192dp. JBR21 flags follow official Robolectric setup. No app network dependency.
**Spec:** GitHub #25, #23 session-lifecycle.md and #24 PR59; user-authorized code/local-check-first continuation. Device acceptance gates unchanged.

## Global constraints
- No fake remembered listening state, duplicate microphone sessions or automatic microphone restart on recreation.
- Keep shared core contracts, actual error strings, privacy, limited-output indication and permission/service restrictions.
- Primary start/stop is prominent; Starting/Stopping show disabled progress text, with explicit starting-cancel action. Errors/permissions have an actionable entry.
- Use Wear Material3 exclusively for controls and retain round scrolling/back navigation/heading semantics.
- No Actions; Robolectric is host evidence, not Watch7 installation/touch/physical behavior.

## Review focus
- Start/stop taps dispatch the right current-session intent once; old stop tokens cannot cancel a newer session.
- Activity recreation and guide/settings navigation keep actual Listening state without starting another service.
- Retry eligibility can change while Error stays equal; UI must recompose from an observable flow.
- Cleanup-pending errors disable restart/diagnostics; active capture remains stoppable.
- Permission changes after returning from Android settings refresh UI without optimistic microphone status.

### Task 1: Observable eligibility and command boundary
Modify runtime/ListeningController.kt, runtime/CaptureRuntime.kt and tests. Add startAllowed: StateFlow<Boolean> derived under the same controller lock; retain canStart() command guard. Add stop session token to MainActivity commands/notification and require matching current or pending token in service stop handling. Preserve old core state types.
- [x] Write failing eligibility test (Error → finish → owner release changes permission to retry while Error remains equal).
- [x] Add Compose tests for actual MainActivity/Controller start, stop, pending cancel and stale stop intent boundaries using Robolectric's Android shadows; never instantiate real AudioRecord.
- [x] Observe missing/incorrect behavior failures, then implement the flow and tokenized commands.

### Task 2: Integrate actual Wear screens
Modify MainActivity.kt and ui/UllimApp.kt; create ui/ListeningUiState.kt; extend Korean strings. Reuse ui/theme/UllimTheme.kt from #24.
Produces: UllimApp(state: ListeningUiState, onStart, onStop, onAiSettings, onDiagnostics, onAppSettings), with default static preview parameters only.
- [x] Configure Android-resource JVM tests, Compose test manifest/junit4 and Robolectric dependencies. Add lifecycle-aware flow collection to MainActivity; refresh permission flags on create/resume/result.
- [x] Bind home phase/action/status/levels/limits to runtime and startAllowed. Keep primary control above secondary navigation; add loading/stop labels and starting-cancel. Route settings to existing AI controls/idle-only diagnostics, and help/error screens to permission settings. Remove visible foundation placeholders that no longer describe the app.
- [x] Verify real Compose semantics/clicks for start→Starting disabled→Listening stop→Stopping disabled→Idle, unchanged-error retry, permission entry, settings/guide navigation and Activity recreation without duplicate service intent.
- [x] Run the entire unit suite, assembleDebug and lintDebug. Obtain independent read-only review and fix important findings with regressions. Publication follows local verification as a main-target Draft with Closes #25 and the predecessor/device gaps recorded.

Native microphone/service behavior and Watch7 round-screen/touch/return/rapid-tap video remain unverified. #26 graphs, #27 AI label details, #28 persistent sensitivity/AI settings and #29/#30 onboarding/recovery are following scopes; don't substitute placeholders for their completion.

