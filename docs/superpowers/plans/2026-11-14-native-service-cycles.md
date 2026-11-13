# Native service lifecycle verification plan (#23)

> Execute inline with superpowers:executing-plans; one fresh-context read-only review before native execution. The user authorized autonomous issue/PR work and Watch7 tests while asleep, including preserving their installed app and settings.

**Goal:** Exercise the current integrated service with real AudioRecord, DSP, AI OFF/ON/OFF, duplicate commands, normal stop and repeated restart on Watch7. Keep incomplete issue gates open.

**Architecture:** An opt-in Activity in mandatory com.ullim.watch.nativeprobe runs bounded foreground-only checks against unchanged production ListeningService/CaptureRuntime/AiRuntime. It performs real numeric motor calibration in its own storage, then three capture cycles with OFF3s→ON8s→OFF2s. Each transition is checked against actual state/model/active recording configuration counts. Request duplicate starts and stale stop commands; await real terminal cleanup before the next token. No original PCM, labels, audio files, network or actual system-setting writes.

**Base/spec:** 1ccb5fc9185040ee9b798945d4f642e8216f6b33, issue23 and docs/session-lifecycle.md. This measures the current integration, not the original e615 PR source. Native app manifest/source/test additions only; normal debug/release exclude probes. Existing normal installed4b206f/profile47a remain untouched. Own probe permissions may be granted for the test; original permissions/settings are preserved.

**Constraints:** three capture cycles; calibration timeout10s; start5s; AI load/inference8s budget; stop/AI cleanup8s; each watch wait is bounded and failure triggers stop rather than a retry. On Activity pause/focus loss/destroy cancel the run and request native cleanup; record requested result separately from verified cleanup. Require zero active recording configurations before calibration/start and one while capturing; public AudioManager counts are device-wide observations, not privileged per-UID attribution. Abort if baseline has another recorder. Do not log configuration IDs or classification labels.

## Work

- [x] Add opt-in test source set. RED Activity boundary tests: absent action no effects, missing microphone permission fails before calibration/capture, wrong runtime package refuses any storage/mic operation.
- [x] Implement bounded orchestration Activity and declaration, leaving production service/controller/mapper unchanged. Success requires calibration saved,3 cycles, actual AI successes, zero terminal recording configurations and AI unloaded/not busy/no pending cleanup.
- [x] Run opt-in full tests/build/lint and normal tests/build/release; assert probe exclusion in normal manifest/DEX. Commit source.
- [x] One independent read-only review of this unit. One required fix pass with RED→GREEN and full suite; no repeat reviewer.
- [x] If watch remains reachable, install only own probe, grant own microphone/notification permissions, run once and poll the same process. Inspect service/AppOps/numeric logs and original app/profile hashes. Remove own probe only after observed terminal result and cleanup. Reopen original idle screen.
- [ ] Record exact source/APK/native evidence and limits, update PR61. Do not merge/close23 while predecessors/remaining OS scenarios or physical gates are missing.

## Review focus

Package isolation before any data/mic access; false PASS on lifecycle cancellation; timeout while a native resource remains owned; real OFF/ON inference evidence; cleanup before next session; device-wide public recording counts correctly described. No raw audio, hidden APIs, global settings writes or production behavior changes. Watch unavailability leaves native evidence pending and does not stop code work.

Official public recording-count reference: https://developer.android.com/reference/android/media/AudioManager#getActiveRecordingConfigurations(). Empty means no active recording when queried; it does not prove unallocated native objects were released.

Use the actual SettingsRuntime controller for AI toggles in the isolated app, including save completion/error checks; do not bypass it with only process-local values. Require notification permission and one active app notification during capture, zero after stop. Missing notification permission was a real semantic RED(1 failed/10s), then added to preflight. Initial three Activity boundary tests passed22s after the expected missing-class compile RED23s; implementation compile fixes used the existing public state flow and a test Activity subclass for unsafe package simulation (Robolectric Config has no packageName option).

One fresh read-only review of24afe91 found3 Important items: each mode's save error can be erased by a later toggle; calibration cancellation can start an orphan ListeningService; a valid in-flight inference may finish during OFF cleanup and increase its counter. Also re-graded the reviewer's Minor error/cleanup mislabel as Important for verification integrity: it incorrectly records a clean retryable error as unreleased resources and replaces the actual failure code. All4 have concrete semantic RED16s: expected SETTINGS_ERROR versus null; unexpected STOP service Intent for a non-service microphone lease; OFF_NOT_VERIFIED from the real worker completing a blocked inference; settled Error incorrectly rejected. One required fix pass awaits each settings save, stops only the service-cycle token, freezes inference count after cleanup (including that completion in totals), and accepts settled Error only for FAIL/CANCELLED while PASS still requiresIdle. No re-review. No deferred minors.

Final: Ruling: physical tactile response, end-to-end latency and remaining OS scenarios remain unproven and open; this verifier measures actual capture/AI/service cycle cleanup only. Treating it as final acceptance could hide hardware/OS failures, so no such merge/closure is authorized by this evidence.

Final: fixed all4 findings — corresponding four regression tests RED16s→GREEN, full244/244; opt-in test/build/lint/releaseUnitTest compilation42s. Normal236/236 test/build/lint/release58s before probe-only review fixes; normal manifest/DEX exclude the Session Activity. Test sources are debug-only even when the probe property is enabled, release test compilation passed. No production runtime files changed.

Watch outcome: native installed/hashed source0eec917, terminal FAIL CALIBRATION_FAILED/UNSTABLE_PHASE_0, cycles0/inferences0/cleanupVerified=true/3664ms. Audio use2s890ms then inactive, service records absent. Own probe removed and original4b206f/profile47a/stay-on1/font1.0 preserved. Do not retry unchanged until the placement/measurement condition is deliberately controlled; no threshold weakening or fixture substitution. Service-cycle success remains unproven.
