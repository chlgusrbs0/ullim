# Bundled YAMNet classification model (#19)

- Author: Google / TensorFlow authors. Model: `google/lite-model/yamnet/classification/tflite/1` (version 1, unchanged binary).
- Download: https://storage.googleapis.com/download.tensorflow.org/models/tflite/task_library/audio_classification/android/lite-model_yamnet_classification_tflite_1.tflite
- Official usage reference: https://developers.google.com/codelabs/tflite-audio-classification-basic-android
- Model implementation/ontology: https://github.com/tensorflow/models/tree/master/research/audioset/yamnet
- Retrieved 2026-11-13. Asset `yamnet.tflite`: 4,126,810 bytes; SHA-256 `10c95ea3eb9a7bb4cb8bddf6feb023250381008177ac162ce169694d05c317de`.
- `yamnet-labels.txt` was extracted unchanged from the model's embedded ZIP entry `yamnet_label_list.txt`: 6,230 bytes, 521 lines; SHA-256 `8e1267a120c1932b7273c0d0e0c5529edbb9a35512b437b1c8982baa59047051`.
- Embedded model metadata declares Apache License 2.0. The upstream license with copyright is retained in `app/src/main/assets/notices/Apache-2.0.txt` (from https://github.com/tensorflow/models/blob/master/LICENSE). The model and labels are unmodified; app adapter code is independently written.
- Interpreter runtime: official `com.google.ai.edge.litert:litert:1.4.2`, Apache-2.0. CPU, one thread; no Play Services, server or runtime download. Runtime guide: https://developers.google.com/edge/litert/android

The binary's FlatBuffer declares float32 input `[15600]` and float32 output `[1,521]`; its internal weights are quantized. Input is 975ms of 16kHz mono audio normalized by 32768. No extra resampling or spectrogram preprocessing is performed outside the model. The Android loader verifies asset hashes and both tensor shapes/types. Model failures throw to the independent AI worker (#22), which must leave DSP running.

Classification uses the window end in the capture monotonic clock domain and expires 1.5s later. Scores are model scores, not calibrated probabilities or guaranteed recognition confidence. Top five original labels retain exact output indices. Low-score/unstable/expired interpretation and Korean display labels belong to #20. PCM stays in memory; inference input buffers are zeroed after each call. The adapter is serialized and close is idempotent.

## Verification boundary

Local contract tests use an injected engine to verify normalization, invalid input/output rejection, labels, timestamps, input erasure and closure. They do **not** execute the native Android interpreter. The separate native test now executes the actual bundled interpreter on Watch7 with canonical generated silence, a 1kHz sine and seeded noise, plus a latest-window AI consumer alongside an independent DSP producer. See [device evidence](evidence/issue19-validation.md) for exact APK/model hashes, class scores, timing, actual producer cadence, memory and battery-temperature observations.

The test app has no Internet/Bluetooth permission and uses bundled assets; ADB WiFi remains connected for observation. It does not open a microphone or vibrator. This component test is not production service/toggle integration, five-minute thermal validation, real microphone recognition accuracy, or useful haptic A/B evidence. Those remain #20–#22/#36/#37. No accuracy or improvement claim is made merely from successful model execution.

## Result stability (#20)

`ClassificationSmoother` keeps at most five smoothed scores (EMA: new 0.6, old 0.4). Both the raw and smoothed winner must score at least 0.35, lead the second candidate by at least 0.08 and agree on the label. Two distinct windows spanning at least 400ms must confirm that candidate. These are development thresholds; they are not calibrated accuracy measurements. Ambiguous/low scores clear the confirmed label immediately; empty input clears score history. Before confirmation, the returned classification has empty scores (UI: uncertain, policy: baseline DSP).

Out-of-order, future, expired and other-session results cannot replace current context. A new session or a gap reaching the previous result's expiry clears history. Repeated reads do not count as additional windows or extend expiry. Display and policy consumers must check `isFresh` at use time, even if no new inference arrives. Worker failure/disabled/loading are separate execution states, not classifications, and are connected in #22/#27.

`SoundLabels` translates a limited set of exact ontology labels, retaining the original string for untranslated classes. It does not interpret a musical “French horn” as a vehicle warning. The full 521-class original map remains bundled. Watch7 controlled-score scenarios and actual model outputs from generated test signals now verify confirmation, uncertainty, metadata and expiry: see [#20 evidence](evidence/issue20-validation.md). Synthesis names are separated from observed labels; these fixtures do not establish accuracy on real speech, horns, knocks or environmental recordings.

## Conservative hybrid policy (#21)

`HybridHapticMapper` always computes a normal DSP command first using an independent baseline mapper history. A missing, expired, future, wrong-session, low/ambiguous score or unmapped label returns that exact baseline command immediately; past AI adjustments never change the baseline state. Only fresh scores >=0.65 with a lead >=0.15 are eligible. Initial targets are gain 1.02 for Speech/Conversation, gain 0.94 for Noise, or onset emphasis 0.08 for Knock/Tap/Clapping. Parameters approach their targets by 0.1 per 20ms frame; session changes/input gaps reset only the policy. OFF/invalid context resets the adjustment immediately.

The output is `baseline * (gain + onset*focus*(1-baseline))`, bounded by 0.85. This keeps adjustments within 10% of baseline, keeps every zero silent and preserves session/timestamp/duration. A known label incorrectly classified with high score can still produce a small undesirable adjustment; it cannot substitute a fixed alert or mute all sound. These targets are development choices, not established improvements. The class is connected to the independent worker in #22.

Synthetic PCM A/B tests inject deterministic Speech/Knock/Noise context into the same real DSP features and compare baseline/hybrid commands. They assert identical event leading edges, silence and time fields, output bounds, and exact OFF recovery even after ON. They print changed-frame counts and mean absolute command differences for reproduction. These tests do not run YAMNet, establish classification accuracy, simulate a wrist or prove a benefit. The Watch7 same-input AI ON/OFF comparison and #37 perception evaluation remain open.
