# Capture cleanup backport implementation plan

> **For agentic workers:** Execute this existing-issue correction inline with superpowers:executing-plans; use one read-only final reviewer.

**Goal:** Keep the independent #5 PR from shipping the native-stop blocking and failed-release ownership bugs already reproduced in #23.

**Architecture:** Preserve `PcmInput`, `AudioFrame` and `MicrophoneCapture` public signatures. A stop request marks the run once and schedules serialized native cleanup without waiting on the caller. A failed release retains the active run and rejects a second recorder.

**Tech Stack:** Kotlin/JUnit4, existing Android/Gradle versions; no new dependencies.

**Spec:** GitHub issue #5; AGENTS.md; existing #23 coordination comment on #5.

## Global constraints

- 16kHz mono PCM16, single owner, memory-only PCM; no raw audio files/logs/network.
- No Actions; no change to core contracts, service/DSP/AI/UI policy or final hardware criteria.
- Preserve the installed watch app and pending rhythm trial; device tests follow local verification.
- Keep PR44 Draft until real 60-second capture/permission/cleanup evidence and prerequisite merges.

## Review focus

- Native start or stop can remain blocked: the caller must return while a second owner remains rejected.
- Failed native release must report CLEANUP and keep rejecting new captures.
- Repeated stop/start and factory/consumer failures retain existing regression coverage.
- Host timing assertions check caller blocking only, never physical sound-to-haptic latency.
- Real Android permissions/initialization and 60-second capture remain device gates, not fake-input claims.

## Task 1: Carry the cleanup guarantees into #5

Files: `audio/MicrophoneCapture.kt`, `audio/MicrophoneCaptureTest.kt`, `docs/evidence/issue5-cleanup-validation.md`.
Consumes/produces: unchanged `start(sessionId): Boolean`, `stop()`, `awaitStopped(timeoutMs): Boolean` and typed capture statuses.

- [x] Check clean isolated clone; merge current main; baseline test/build/lint succeeded18s.
- [x] Add direct regressions for stop while native start/stop is blocked and failed release retaining ownership. Use latches and finally cleanup; assert actual caller return and rejection of a second recorder.
- [x] Run `:app:testDebugUnitTest --tests com.ullim.watch.audio.MicrophoneCaptureTest`; observe these failures before production edits. RED: 10 tests, 3 failures, 7s.
- [x] Apply the already-established #23 atomic stop/serialized asynchronous native cleanup and failed-release lease retention, without importing other issue APIs.
- [x] Run full `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug`; 21 tests/0 failures/errors/skips, success7s; latest main docs merge recheck success2s.
- [ ] Commit and read-only review; update existing Draft PR44. Continue watch verification after the outstanding rhythm trial. No early issue close.

Ruling: use the existing clean `work/ullim-ui-check` clone on its own #5 branch, preserving primary #35 and #4 review checkouts; no new worktree or implementation agent is needed.
