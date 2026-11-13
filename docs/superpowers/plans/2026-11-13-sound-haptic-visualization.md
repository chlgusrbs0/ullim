# Sound/haptic visualization (#26)

Goal: plot actual DSP RMS/envelope and mapped HapticFrame commands on the same monotonic source timeline, on the watch only.

Use a process-local per-session bounded 256-slot ring of immutable numeric points (5-second display window at 50 DSP frames/s). The single audio producer performs a constant-time atomic write; it never calls UI code or copies history. The UI reads snapshots at at most 10Hz while its Activity is started; no observer is needed for DSP/rendering to continue. Session replacement makes old writes unreachable; no PCM is retained. A point stores a common source timestamp, RMS, envelope, commanded intensity and command duration. Motor measurements are never implied.

Two labeled Wear Canvas rows use one window's bounds. Input includes RMS and envelope; output draws commanded intensity over its actual HapticFrame duration. Gaps remain gaps, silence is a baseline, and stopped status is explicit. Keep #25 primary controls above charts and existing error/AI paths independent. Read-only screenshots/tests are host evidence only.

- [x] RED pure tests: exact synthetic PCM/DSP/mapper correspondence, shared timestamps, bounded/expired history, session/stale rejection and processing without UI snapshots.
- [x] Implement numeric history and service/controller bridge, without core contract changes.
- [x] Add lifecycle-limited UI snapshots and synchronized Canvas rows with silence/stopped/commanded semantics; verify actual Compose UI labels and state without microphone hardware.
- [x] Full suite/build/lint and independent read-only review; fix important findings, prepare publication as a main-target Draft Closes #26. Real PCM/Watch7 video, screen-off DSP/haptic performance and predecessor merges remain unverified.

