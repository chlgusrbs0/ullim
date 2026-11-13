# Capture and AI error recovery (#30)

Add a stable capture failure code flow beside RuntimeState without changing core Contracts. A centralized reporter maps each code to Korean explanation/actions and emits the same code in metadata-only Android logs, only when the controller accepts that current-session error. Preserve original cleanup ownership and manual start admission; no automatic retries or microphone starts.

Distinguish microphone permission, system-silenced input (may be another app/policy, never infer from PCM zeros), initialization, read, processing, haptic unavailable/output, foreground-start, service command and unsafe cleanup. AudioRecord configuration callbacks/initial configuration provide silencing state without 50Hz Binder polling. Typed capture failures survive the capture worker; native release failure overrides with cleanup and retains ownership. Guard vibrator setup/start against runtime errors. AI errors remain independent with displayed actual fault code, baseline DSP and explicit retry via OFF/ON.

UI provides code, explanation and appropriate actions: permission/settings, manual retry after release, stop during active sessions, AI details/OFF, or restart guidance when cleanup is unproven. Nonfatal notification/capability limits remain distinct from capture errors. Late callbacks cannot replace a newer session's code. Android logs include code/session only, no raw exceptions, labels or PCM.

- [x] RED failure mapping/retention tests, typed capture read/consumer/silenced fault injection and resource release. Implement catalogue, reporter, guarded service/Activity integration.
- [x] RED actual Compose recovery tests for retry/permission/cleanup and AI-only errors; verify actual Android reporter/UI log code agreement.
- [ ] Native adapter callback registration/filtering/unregister still needs device validation. The pure atomic availability ordering test passes; it does not replace the unexecuted native callback test. No contention is inferred from PCM.
- [x] Full suite/build/lint, independent read-only review, important fixes and preparation for main-target Draft Closes #30. Watch7 permission changes, competing capture, actual motor stop/resource recovery and predecessor merges remain unverified.

Existing #29/PR65 belongs ChatGPT/chlgusrbs0. Read-only validate separately and coordinate; do not modify that branch or duplicate onboarding. #30 is based on #66 including live settings/graphs/AI; future integration must preserve both.
