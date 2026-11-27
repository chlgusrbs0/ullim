# AI runtime device validation (#19/#22)

Status: **controlled evaluation procedure; full matrix not completed**. Native model execution and some applied haptic commands were observed on Watch7; see [live inspection](evidence/live-detailed-tuning.md) and [project status](project-status.md). Those records do not establish accuracy, physical motor latency, thermal stability or controlled AI benefit. Local injected engines do not prove those outcomes either. Keep unverified acceptance gates open. Do not use GitHub Actions.

## Record the environment

Record Watch7 model, OS/API, APK commit/hash, bundled model hash, battery level, power/thermal mode, sensitivity and room conditions. Use controlled synthetic sounds or properly licensed standard evaluation audio played externally; do not record or retain microphone PCM. Audio sources used for model accuracy must have known expected classes and provenance. Keep command timing separate from physical sound→motor response (#18/#37).

Build locally, install to an explicitly selected watch, and start the app using the commands in development.md. With the device offline (disconnect internet/phone after installation), start capture manually. The shipped default is AI ON; explicitly select AI OFF before the baseline trial, preserving the distinction between the procedure and the shipped default. Verify DSP/haptic commands and user stop first, then repeat with AI ON under the same sound/volume/sensitivity conditions for at least five minutes each. Never treat a desktop synthetic stream as that five-minute device test.

## Metadata-only collection

Use the actual watch serial instead of `WATCH_SERIAL`:

```text
adb -s WATCH_SERIAL shell getprop ro.product.model
adb -s WATCH_SERIAL shell getprop ro.build.version.sdk
adb -s WATCH_SERIAL shell dumpsys meminfo com.ullim.watch
adb -s WATCH_SERIAL shell dumpsys cpuinfo
adb -s WATCH_SERIAL shell dumpsys thermalservice
adb -s WATCH_SERIAL logcat -v monotonic -s UllimAI:I
```

Capture meminfo/CPU/thermal snapshots before AI, after load, each minute, after OFF/cleanup and after stop. If a command is unavailable on the device, record that limitation rather than inventing a number. Keep CPU/thermal source, timestamps, sample interval and foreground/background/screen-off state. Avoid recording logs from unrelated apps. `UllimAI` exists only for debuggable apps and emits aggregate metadata on phase/fault changes or every five seconds: no PCM, recognized class labels or speech content.

| Measure | OFF baseline | ON steady | ON failure/delay | OFF after ON |
|---|---|---|---|---|
| Valid model result count | not expected | must increase to prove native inference | distinguish valid results from completed attempts | must stop increasing after in-flight work ends |
| Load and last/max inference duration | N/A | report real numbers/distribution evidence | include timeouts and current operation age | pending cleanup honest |
| Process memory/CPU/thermal | measured, not assumed | compare same conditions | identify pressure or sustained degradation | measure recovery; do not assume immediate GC |
| DSP cadence/command path | confirm | confirm while AI runs | must continue without waiting for AI | confirm exact baseline policy |
| Runtime state/UI | OFF | loading/listening | delayed or failed with baseline available | OFF/pending cleanup reflect actual work |

The current summary reports last/max duration rather than a full latency distribution. Collect per-operation controlled measurements or instrumentation in the device test if a percentile is needed; do not infer P95 from the maximum or a five-second sample. #18/#37 separately require measured physical reaction, not `UllimAI` timestamps.

## Failure, toggle and lifecycle scenarios

- AI ON before capture: model must not load until the user starts microphone capture.
- Rapid OFF/ON during load/inference: at most one process lane, one native operation and one pending window; old results must not reach the new generation.
- Delay beyond 500ms: show delayed, clear AI context and keep DSP moving. Beyond 1s inference or 10s load/previous operation: latch AI failure. These defaults are not measured Watch7 limits.
- Restart microphone capture after AI failure: AI must not retry automatically. Explicit AI OFF→ON authorizes retry; a close failure requires process restart.
- OFF during inference: context disappears immediately, model closes only after inference returns. If native code never returns, record one occupied lane and pending cleanup; do not claim native cancellation or memory release.
- Stop, permission revoke, service destruction and new session: microphone/haptic teardown remains independent; late AI callbacks cannot resurrect status or previous audio.
- Model missing/corrupt/unsupported native library and inference failure: report AI-only fault, no secret retries, no DSP stop. Host injection tests cover control logic; actual native/device behavior still requires evidence.

Attach timestamped metadata, APK/model hashes, tables, screenshots and actual pass/fail for every case. No measurements exist yet. If YAMNet fails the device constraints, keep #22 open and choose a smaller model only through the existing SoundClassifier contract with new provenance and measured comparisons. Do not change acceptance criteria or declare improvement without #37 evidence.
