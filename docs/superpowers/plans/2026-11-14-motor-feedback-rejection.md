# Motor feedback rejection implementation plan

> Execute inline; one fresh read-only review after the implementation and regression suite.

**Goal:** Stop the measured self-sustaining output after a short sound while preserving external sound duration/level and session cleanup. Physical strength/continuity/P95 remain native acceptance gates.

**Spec:** #17, #18, AGENTS.md; user's explicit transfer of all work and priority on sound strength/duration over AI.

**Evidence:** Current808d3f4/c435... manual single-event trial has39 half-second windows,178 waveform attempts, no rejected frames, requested amplitudes26..217. Active-input RMS reaches~0.4 after the initial transient and returns below0.008 when output stops. This supports motor coupling; temporal correlation alone is not proof that every correlated sound is self-noise. Prior requested quiet/small/large trial was actually quiet-only per the user's correction.

**Architecture:** Obtain a quiet, user-started finite calibration of motor-only spectral power at two requested amplitudes, matching live60ms replacement noise. Retain numeric spectral bounds, not microphone samples. Before DSP, subtract calibrated motor power only for audio sample intervals overlapping actual successful output requests and their bounded tail. AI receives the existing unmodified fan-out. Missing/invalid profile routes to explicit calibration and blocks microphone/service start rather than entering the known feedback path. Stop/screen exit cancels calibration, microphone and output and retains the single-owner lease until native release.

Files: `dsp/FrameSpectrum.kt`; `haptics/MotorNoiseProfile.kt`, `MotorOutputTimeline.kt`, `MotorNoiseSuppressor.kt`, `MotorCalibration.kt`; Android calibration UI/driver; narrow `LiveDsp` and `ListeningService` integration; existing diagnostic entry. Core interfaces and frame timestamps unchanged.

## Tasks

- [x] Preserve inherited #17 analyzer commits, integrate808d; baseline test/build/lint succeeded33s.
- [x] RED: spectral energy and round trip; overlapping bounded output history including cancel/tail; motor-only rejection, preservation of external tone/transient/duration, no-output identity and session reset; finite calibration sample-window/profile validation.
- [x] Implement fixed512-point FFT with bounded scratch arrays, immutable numeric profile, interval-aware preprocessor and finite calibration model. No PCM persistence/upload/logging; no disk/log I/O in DSP.
- [x] Integrate explicit calibration using existing capture ownership, VIBRATE/RECORD_AUDIO permission checks and cancellation on activity exit. Save only device/firmware-keyed numeric profile atomically.
- [x] Regress full suite/build/lint; fresh read-only review and fix Important findings. Full209 tests/build/lint passed after reproducing and fixing the no-amplitude-control output-mode mismatch. Keep Draft until native results.
- [ ] Native quiet/motor/external event comparison when user returns. No microphone activity while the user is away showering. Restore settings; verify Stop cleanup. Do not label amplitude support flags or software tests as physical success.

Review focus: preserve real sound in overlapping bands; fail-safe missing/corrupt calibration; no duplicate mic on navigation/stop; stale timestamps must query historical motor intervals, not the most recent future request; firmware changes invalidate profile. Low/strong distinguishability and continuous rendering require explicit actual-device validation even if feedback rejection works.
