# Watch7 controlled output comparison plan (#18)

> **For agentic workers:** Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Separate waveform segmentation from repeated OS effect replacement on the actual Watch7 before changing the live scheduler.

**Architecture:** Add a finite, cancellable diagnostic comparison using the same AndroidWaveformDevice/USAGE_TOUCH as live output. Compare one600ms, thirty20ms segments in one call, ten60ms calls and three200ms calls, all at amplitude220 and total600ms. Do not change DSP, live scheduler defaults, core contracts or perception/physical latency acceptance criteria.

**Tech Stack:** Kotlin, Android main Handler/SystemClock, existing WaveformDevice, Wear Compose, Robolectric/JUnit.

**Spec:** GitHub issue #18; AGENTS.md. User reports choppy live output but smooth existing single600ms diagnostic on SM-L300/API37. That observation does not isolate attributes versus segmentation versus supersession yet.

## Constraints and review focus

- No PCM/voice storage or transmission, no classified label logs, no network or Actions additions.
- No infinite patterns/queues, catch-up bursts, output after stop/activity exit, or claims that dispatch timing is physical latency.
- Always cancel old diagnostic on replacement; reject unavailable motors; fail closed on native exception.
- A delayed UI thread must abort the comparison rather than send multiple missed chunks together.
- Keep #18 Draft until actual source-to-motor P95, strong/weak correlation and duration/rhythm gates are measured; previous feature dependencies remain unmerged.

## Task: controlled comparison and evidence

- [ ] Add failing tests for equal amplitude/total duration but different segmentation/call counts, replacement/stop cancellation, delayed dispatch and platform failure.
- [ ] Run targeted tests and record RED before implementation.
- [ ] Implement bounded comparison driver and diagnostic buttons with accurate command-only status.
- [ ] Run targeted tests, full tests, assemble/lint locally.
- [ ] Inspect/review implementation read-only, address important defects.
- [ ] Install without deleting settings; compare on same Watch7 with user, record actual outcomes including failures. Restore any temporary device settings.
- [ ] Document measurement procedure and raw-audio-free evidence; commit/push Draft PR main with Closes #18. No DoD shortcut.

## Follow-up: full amplitude range

Actual A/B smooth, C rough, D three pulses; E60/130/220 ramp has no perceived strength difference, F overlap remains rough. Separate existing100ms buttons also feel similar. Keep live scheduling unchanged. Add G/H as separate single600ms calls through the same TOUCH adapter at the legal amplitude endpoints1/255. This tests a wider range and longer steady output without introducing microphone/DSP variables or changing system intensity settings. A perceived difference would not prove three-level blind accuracy; no difference would still need physical/API-path investigation rather than a fabricated hardware conclusion.

- [x] RED: endpoint probes missing; then verify exact1/255 amplitude, identical600ms duration, one finite call and completion.
- [x] Add labels explaining requested values and preserve all cancellation/failure behavior.
- [ ] Run local checks, read-only review, install the exact comparison APK and obtain actual G/H feedback. Keep #18 Draft.

## Follow-up: locate the live loss of strength and continuity

User still reports binary-feeling choppy output for small and large sounds after ZZK1. Existing A~H tests already establish a shared-output-path failure; repeating unchanged endpoints is not useful. Record the actual live DSP-to-OS numeric boundaries before changing mapping or scheduler behavior. This diagnostic is the builder's choice within #18, not a new user-facing mode or a weaker product goal.

- [x] Integrate #5 reviewed microphone source/permission/status fixes, preserving typed input errors from #30 and the existing capture tests; latest main #4 documents are included. Full local test/build/lint success1m,178 tests/0 failures/errors/skips.
- [ ] Add `haptics/HapticTelemetry.kt`: per-session numeric ranges/counters only, fixed25-frame windows, one replaceable latest snapshot; no PCM, audio labels or unbounded queues. Track RMS/envelope/noise/level/requested intensity, rejected frames, callback gap, frame age and actual waveform API request counts/intervals/amplitude range. API times are not physical motor times.
- [ ] Add a transparent `TracingEnvelopeDevice` wrapper; pass original arrays/plans and propagate failures unchanged. Only debug builds instantiate trace/wrapper. Main heartbeat consumes at most one latest snapshot; no logging I/O on capture/DSP callbacks.
- [ ] Add RED tests for exact numeric ranges/window rollover, latest-only retention, session mismatch, waveform request timing and transparent adapter delegation/failure propagation. Implement, then full tests/build/lint.
- [ ] Fresh read-only review of this unit; correct Important findings with RED/GREEN, retain Draft.
- [ ] Install candidate on Watch7 without clearing app data. Preserve exact UI35 backup952d2a0... outside git. Perform bounded microphone trial with normal Stop, retrieve only UllimTrace numeric logs, confirm service/microphone cleanup and original settings.
- [ ] Use measured input/request/call data to choose the actual repair; do not claim this instrumentation fixes perceived amplitude/continuity. Keep #18/#37/#39 open while quality and physical P95 are unresolved.

Ruling: preserve #35 on its independent branch9d2e09c and test this #18 candidate with its existing UI. Merge the #5 correction into the earlier runtime dependency instead of importing unrelated #35 UI work into PR69. The previously installed UI35 APK is verified and backed up locally for exact restoration.
