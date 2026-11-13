# Issue 5 native capture verification implementation plan

> **For agentic workers:** Execute this verification unit inline with superpowers:executing-plans. Do not delegate implementation.

**Goal:** Verify the existing #5 candidate on Watch7 with denied permission, 60 seconds of allowed capture, and normal stop/release.

**Architecture:** Keep product capture code and core contracts unchanged. An explicitly enabled debug-only Activity calls the same MicrophoneCapture and AndroidPcmInput, renders a visible bounded test, and logs scalar statistics only. Lifecycle loss stops capture. Normal debug and every release build exclude this entry point.

**Tech Stack:** Existing Kotlin/Android SDK36, AudioRecord, Wear Material3; no new library or service.

**Spec:** GitHub issue #5 and AGENTS.md. User has authorized issue implementation, temporary native tests, and restoring their working app.

## Approach and constraints

Reusing the cumulative #17 app would not prove PR44's exact adapter. Adding a product capture screen would overlap #25. Use an opt-in debug probe instead. Never persist or transmit PCM, print sample arrays, add workflows, or change global audio/haptic settings. Preserve the improved APK and private calibration and restore them with original microphone/notification grants after testing. Client format is distinct from hardware format: https://developer.android.com/reference/android/media/AudioRecordingConfiguration#getClientFormat().

## Review focus

- A normal/release APK must not contain an exported test capture entry.
- An offscreen/paused probe must stop instead of continuing capture.
- Duplicate start and cleanup must use the real capture owner's admission rules.
- Permission rejection must not be reported as successful recording.
- Logs may include counters/RMS/timestamps, never raw PCM or a voice file.

## One verification unit

- [x] Read issue/all comments/PR; confirm #3 merged; integrate current main (#24).
- [x] Add conditional debug manifest/source directories behind `-PnativeCaptureProbe=true`; implement visible 1–60 second probe with numeric-only reporting, duplicate-start check and lifecycle stop. No product behavior changes.
- [x] Run existing full unit tests, debug assemble/lint and opt-in assemble/lint. Verify ordinary debug/release manifest excludes the probe; record APK hashes.
- [x] Review immutable candidate diff once with a read-only reviewer and fix required findings. Focus-loss regression RED17s → GREEN31 tests/46s; final ordinary 30 tests/33s. No second review requested.
- [x] Install exact probe APK. Revoke original microphone grant temporarily and verify PERMISSION/no running microphone. Restore original grant and run 60 seconds in foreground. Ask user for one external sound while actually recording. Record format, sample counts, RMS range, continuity, Stopped, and framework release. [Results](../../evidence/issue5-watch7-native.md). Ruling: sample-derived timestamp gaps establish the frame contract only; use elapsed/sample totals/native observations together and do not claim independent hardware drop detection.
- [x] Restore exact improved APK, calibration hash and original runtime permission states; verify microphone stopped.
- [ ] Commit evidence, push PR44, mark ready/merge with expected SHA only if all #5 gates pass; verify issue auto-closure.
