# Bundled YAMNet classification model (#19)

- Author: Google / TensorFlow authors. Model: `google/lite-model/yamnet/classification/tflite/1` (version 1, unchanged binary).
- Download: https://storage.googleapis.com/download.tensorflow.org/models/tflite/task_library/audio_classification/android/lite-model_yamnet_classification_tflite_1.tflite
- Official usage reference: https://developers.google.com/codelabs/tflite-audio-classification-basic-android
- Model implementation/ontology: https://github.com/tensorflow/models/tree/master/research/audioset/yamnet
- Retrieved 2026-11-13. Asset `yamnet.tflite`: 4,126,810 bytes; SHA-256 `10c95ea3eb9a7bb4cb8bddf6feb023250381008177ac162ce169694d05c317de`.
- `yamnet-labels.txt` was extracted unchanged from the model's embedded ZIP entry `yamnet_label_list.txt`: 6,230 bytes, 521 lines; SHA-256 `8e1267a120c1932b7273c0d0e0c5529edbb9a35512b437b1c8982baa59047051`.
- Embedded model metadata declares Apache License 2.0. The upstream license with copyright is retained in `app/src/main/assets/notices/Apache-2.0.txt` (from https://github.com/tensorflow/models/blob/master/LICENSE). The model and labels are unmodified; app adapter code is independently written.
- Interpreter runtime: official `com.google.ai.edge.litert:litert:1.4.2`, Apache-2.0. CPU, one thread; no Play Services, server or runtime download. Runtime guide: https://developers.google.com/edge/litert/android

The binary's FlatBuffer declares float32 input `[15600]` and float32 output `[1,521]`; its internal weights are quantized. Input is 975ms of 16kHz mono audio normalized by 32768. No extra resampling or spectrogram preprocessing is performed outside the model. The Android loader verifies asset hashes and both tensor shapes/types. Model failures throw to the independent AI worker (#22), which leaves the DSP path running without AI context.

Classification uses the window end in the capture monotonic clock domain and expires 1.5s later. Scores are model scores, not calibrated probabilities or guaranteed recognition confidence. Top five original labels retain exact output indices. Low-score/unstable/expired interpretation and Korean display labels belong to #20. PCM stays in memory; inference input buffers are zeroed after each call. The adapter is serialized and close is idempotent.

## Verification boundary

Local contract tests use an injected engine to verify normalization, invalid input/output rejection, labels, timestamps and closure. They do **not** execute the native Android interpreter. #19 remains incomplete until a Watch7 actually loads this bundled model and performs inference offline. The service connection is implemented in #22; real standard-audio scores, load/inference duration, memory and temperature are not yet measured. Device execution remains tracked in #19/#22/#36. No accuracy or improvement claim is made.

## Result stability (#20)

`ClassificationSmoother` keeps at most five smoothed scores (EMA: new 0.6, old 0.4). Both the raw and smoothed winner must score at least 0.35, lead the second candidate by at least 0.08 and agree on the label. Two distinct windows spanning at least 400ms must confirm that candidate. These are development thresholds; they are not calibrated accuracy measurements. Ambiguous/low scores clear the confirmed label immediately; empty input clears score history. Before confirmation, the returned classification has empty scores (UI: uncertain, policy: baseline DSP).

Out-of-order, future, expired and other-session results cannot replace current context. A new session or a gap reaching the previous result's expiry clears history. Repeated reads do not count as additional windows or extend expiry. Display and policy consumers must check `isFresh` at use time, even if no new inference arrives. Worker failure/disabled/loading are separate execution states, not classifications. #22 connects worker status to runtime and an AI control screen; #27 adds the [shared result presentation](ai-result-display.md) on home and AI controls.

`SoundLabels` translates a limited set of exact ontology labels, retaining the original string for untranslated classes. It does not interpret a musical “French horn” as a vehicle warning. The full 521-class original map remains bundled. Real speech/horn/knock/noise class correctness and false positives have not yet been evaluated with standard audio or on Watch7.

## Conservative hybrid policy (#21)

`HybridHapticMapper` always computes a normal DSP command first using an independent baseline mapper history. A missing, expired, future, wrong-session, low/ambiguous score or unmapped label returns that exact baseline command immediately; past AI adjustments never change the baseline state. Only fresh scores >=0.65 with a lead >=0.15 are eligible. Initial targets are gain 1.02 for Speech/Conversation, gain 0.94 for Noise, or onset emphasis 0.08 for Knock/Tap/Clapping. Parameters approach their targets by 0.1 per 20ms frame; session changes/input gaps reset only the policy. OFF/invalid context resets the adjustment immediately.

The output is `baseline * (gain + onset*focus*(1-baseline))`, bounded by 0.85. This keeps adjustments within 10% of baseline, keeps every zero silent and preserves session/timestamp/duration. A known label incorrectly classified with high score can still produce a small undesirable adjustment; it cannot substitute a fixed alert or mute all sound. These targets are development choices, not established improvements. The service now reads fresh context from the independent worker and applies this mapper in #22.

Synthetic PCM A/B tests inject deterministic Speech/Knock/Noise context into the same real DSP features and compare baseline/hybrid commands. They assert identical event leading edges, silence and time fields, output bounds, and exact OFF recovery even after ON. They print changed-frame counts and mean absolute command differences for reproduction. These tests do not run YAMNet, establish classification accuracy, simulate a wrist or prove a benefit. The Watch7 same-input AI ON/OFF comparison and #37 perception evaluation remain open.

## Independent runtime (#22)

`AiRuntime` owns one process-wide asynchronous lane. The worker has at most one native operation and one latest pending audio window, with 500ms input cadence. DSP reads an atomic result and never waits for model load/inference/close. The capture consumer offers AI windows only after DSP fan-out. AI defaults OFF; enabling it does not start the microphone. The AI control screen shares #28's [local persistent settings](persistent-settings.md); restoring ON does not start the microphone.

`AiCaptureFeed` separates audio delivery from mode reconciliation. Its audio-thread `accept` only sends PCM through fan-out and offers the latest window. `start`/`refreshMode` and the AI control activity reconcile the setting on the service/UI main thread. A stale audio-thread preference read therefore cannot turn AI back on after a user's OFF. Future settings integration must keep this single control-thread authority.

Load/inference/close execute outside the worker state lock. OFF, session changes and errors clear context immediately and invalidate old callbacks. Native close waits on the same lane for an ongoing operation to return; the runtime never calls close concurrently or claims it killed a blocked native call. The UI exposes pending cleanup. One idle lane remains after OFF; its model reference is released after successful closure, while actual Android resident memory/GC behavior still needs measurement.

Development deadlines are 500ms for delayed inference, 1s for inference failure and 10s for load/previous-operation failure. A service heartbeat checks deadlines and result expiry even when no model result arrives. Failure remains latched across microphone restarts until explicit AI OFF/ON; there is no automatic retry. Old-generation outputs/errors cannot alter a new session. A failed close poisons further model loads until process restart. The process executor is not shut down by individual services; rejection is reported honestly, including retained-model cleanup if closure cannot be scheduled.

`AiSnapshot` contains phases/faults, loaded/busy/cleanup flags, operation age, load duration, completed inference attempts (including failed/cancelled attempts within the same capture session), valid-contract native results, last/max duration, latched faults/timeouts and dropped windows. These are monotonic command/runtime measurements, not physical haptic latency. Per-session metrics reset on start; an ongoing old operation is visible through busy/cleanup/operation age rather than counted as a new session's inference. Debug builds log these metadata fields on state changes or every 5s under `UllimAI`. Logs omit PCM, source speech, audio files and classification labels. The [device validation procedure](ai-runtime-validation.md) is still unexecuted on Watch7.
