# Native service lifecycle verification plan (#23)

> Execute inline with superpowers:executing-plans; one fresh-context read-only review before native execution. The user authorized autonomous issue/PR work and Watch7 tests while asleep, including preserving their installed app and settings.

**Goal:** Exercise the current integrated service with real AudioRecord, DSP, AI OFF/ON/OFF, duplicate commands, normal stop and repeated restart on Watch7. Keep incomplete issue gates open.

**Architecture:** An opt-in Activity in mandatory com.ullim.watch.nativeprobe runs bounded foreground-only checks against unchanged production ListeningService/CaptureRuntime/AiRuntime. It performs real numeric motor calibration in its own storage, then three capture cycles with OFF3s→ON8s→OFF2s. Each transition is checked against actual state/model/active recording configuration counts. Request duplicate starts and stale stop commands; await real terminal cleanup before the next token. No original PCM, labels, audio files, network or actual system-setting writes.

**Base/spec:** 1ccb5fc9185040ee9b798945d4f642e8216f6b33, issue23 and docs/session-lifecycle.md. This measures the current integration, not the original e615 PR source. Native app manifest/source/test additions only; normal debug/release exclude probes. Existing normal installed4b206f/profile47a remain untouched. Own probe permissions may be granted for the test; original permissions/settings are preserved.

**Constraints:** three capture cycles; calibration timeout10s; start5s; AI load/inference8s budget; stop/AI cleanup8s; each watch wait is bounded and failure triggers stop rather than a retry. On Activity pause/focus loss/destroy cancel the run and request native cleanup; record requested result separately from verified cleanup. Require zero active recording configurations before calibration/start and one while capturing; public AudioManager counts are device-wide observations, not privileged per-UID attribution. Abort if baseline has another recorder. Do not log configuration IDs or classification labels.

## Work

- [ ] Add opt-in test source set. RED Activity boundary tests: absent action no effects, missing microphone permission fails before calibration/capture, wrong runtime package refuses any storage/mic operation.
- [ ] Implement bounded orchestration Activity and declaration, leaving production service/controller/mapper unchanged. Success requires calibration saved,3 cycles, actual AI successes, zero terminal recording configurations and AI unloaded/not busy/no pending cleanup.
- [ ] Run opt-in full tests/build/lint and normal tests/build/release; assert probe exclusion in normal manifest/DEX. Commit source.
- [ ] One independent read-only review of this unit. One required fix pass with RED→GREEN and full suite; no repeat reviewer.
- [ ] If watch remains reachable, install only own probe, grant own microphone/notification permissions, run once and poll the same process. Inspect service/AppOps/numeric logs and original app/profile hashes. Remove own probe only after observed terminal result and cleanup. Reopen original idle screen.
- [ ] Record exact source/APK/native evidence and limits, update PR61. Do not merge/close23 while predecessors/remaining OS scenarios or physical gates are missing.

## Review focus

Package isolation before any data/mic access; false PASS on lifecycle cancellation; timeout while a native resource remains owned; real OFF/ON inference evidence; cleanup before next session; device-wide public recording counts correctly described. No raw audio, hidden APIs, global settings writes or production behavior changes. Watch unavailability leaves native evidence pending and does not stop code work.

Official public recording-count reference: https://developer.android.com/reference/android/media/AudioManager#getActiveRecordingConfigurations(). Empty means no active recording when queried; it does not prove unallocated native objects were released.

Use the actual SettingsRuntime controller for AI toggles in the isolated app, including save completion/error checks; do not bypass it with only process-local values. Require notification permission and one active app notification during capture, zero after stop. Missing notification permission was a real semantic RED(1 failed/10s), then added to preflight. Initial three Activity boundary tests passed22s after the expected missing-class compile RED23s; implementation compile fixes used the existing public state flow and a test Activity subclass for unsafe package simulation (Robolectric Config has no packageName option).
