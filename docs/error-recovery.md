# Error codes and manual recovery (#30)

CaptureFailure is a stable code catalogue beside the existing RuntimeState contract. The controller still owns session admission and native cleanup. CaptureFailureReporter selects the Korean message and emits `session=<id> code=<code>` under `UllimError` only if the current-session error is accepted. UI shows that same code. Logs contain no PCM, labels, source speech, exception payloads or audio files.

| Code | Meaning and next action |
| --- | --- |
| MIC_PERMISSION | Permission denied/revoked; open app permission settings, then manually start. Active revocation requests a token-matched stop. |
| MIC_SILENCED | Framework reports this input silenced. Check competing recording/system microphone access, then manually retry. It does not prove which other app is responsible. |
| MIC_INITIALIZATION / MIC_READ | Recorder setup/input failed. Stop competing recording or check device/permission state, wait for release, retry manually. |
| PROCESSING | DSP/consumer failed. Stop processing/output, release input, then manual retry. |
| HAPTIC_UNSUPPORTED / HAPTIC_OUTPUT | No supported motor or output failed. Capture stops; diagnostic entry can inspect support when cleanup permits it. |
| FOREGROUND_START | Foreground promotion/start failed. Return to the visible app and retry manually. |
| SERVICE_COMMAND | Stop could not be delivered and fallback stop failed. Open app settings for force-stop; never claim Activity recreation frees native resources. |
| CLEANUP | Native release cannot be confirmed. Retain ownership and disable start/diagnostics; open app settings for force-stop and reopen. |
| UNKNOWN | Legacy/unclassified error; retained explanation and manual start guard still apply. |

Error state retains its session's stop token while resources are owned/unsettled, even though core ListeningState.Error contains only a reason. A terminal cleanup failure may replace an earlier error for that same active session; old-session and unrelated UI failures cannot overwrite it. After both release and terminal completion, manual start clears the old code. No automatic restart is added.

AndroidPcmInput registers AudioRecord configuration callbacks and initializes its availability from the recorder's own active configuration. An atomic metadata guard checks silencing around reads without per-frame Binder queries. A callback beats an older initial query. PCM zeros are normal silence, never evidence of microphone contention. Typed capture exceptions preserve input/output cause through cleanup; a failed native release overrides the cause with CLEANUP and keeps the existing ownership barrier.

Service motor setup/preparation is guarded before attaching microphone ownership. Rendering failure stops the session with an output-specific code. AI errors remain independent, use `AI_<fault>` in UI and `UllimError` metadata, retain baseline DSP, and offer the existing details/OFF/ON route. AiRuntime owns a separate process-scope fault observer, so closing a model after the service stops still logs cleanup/executor failures; session/fault deduplication prevents heartbeat log spam. OFF and retries use the local-settings main/control authority; no automatic native retry or model interruption is claimed.

## Local verification limits

Pure tests cover cause/code mapping, stale rejection, active-error cleanup escalation, stop-token retention, typed input/consumer failures, native-release ownership, and metadata silencing/initial-query ordering. Actual MainActivity Compose tests cover error code/manual retry after release, unsafe cleanup→system settings with start disabled, and permission revocation→current token stop. AI Flow UI tests check its independent error code.

The service integration test runs real ListeningService lifecycle/Handler/controller/renderer orchestration with an injected fake PcmInput and Robolectric vibrator. Three paced synthetic frames play a command before a read failure; the test checks that output was active, then cancelled, with the matching code/log, one release and manual retry. A real worker with an injected close failure separately proves process-scope reporting after capture Stop, matching the displayed AI_CLEANUP code exactly once. Neither test initializes a real AudioRecord, native model or physical motor. The unmodified Android adapter could not initialize under the initial Robolectric shadow attempt, so that attempt did not demonstrate read-failure handling. AudioRecord callback registration/delivery, competing apps, actual permission revocation, device native release, notification teardown and physical motor stop remain Watch7 checks.

Run `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug` locally. Keep #30/its PR open/Draft until predecessor main merges and device evidence satisfy the issue. Separate owner #29/PR65 onboarding is not modified by this branch; its eventual integration must retain explicit consent and these recovery paths. No workflows or raw microphone recordings are used.

References: [Android input-sharing policy](https://developer.android.com/media/platform/sharing-audio-input), [AudioRecord callbacks](https://developer.android.com/reference/android/media/AudioRecord#registerAudioRecordingCallback(java.util.concurrent.Executor,%20android.media.AudioManager.AudioRecordingCallback)).
