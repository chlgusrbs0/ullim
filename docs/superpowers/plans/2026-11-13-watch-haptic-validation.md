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
