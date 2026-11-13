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

Local contract tests use an injected engine to verify normalization, invalid input/output rejection, labels, timestamps and closure. They do **not** execute the native Android interpreter. #19 remains incomplete until a Watch7 actually loads this bundled model and performs inference offline. Real standard-audio scores, load/inference duration, memory and temperature are not yet measured; device execution and the independent consumer connection are tracked in #19/#22/#36. No accuracy or improvement claim is made.

## Result stability (#20)

`ClassificationSmoother` keeps at most five smoothed scores (EMA: new 0.6, old 0.4). Both the raw and smoothed winner must score at least 0.35, lead the second candidate by at least 0.08 and agree on the label. Two distinct windows spanning at least 400ms must confirm that candidate. These are development thresholds; they are not calibrated accuracy measurements. Ambiguous/low scores clear the confirmed label immediately; empty input clears score history. Before confirmation, the returned classification has empty scores (UI: uncertain, policy: baseline DSP).

Out-of-order, future, expired and other-session results cannot replace current context. A new session or a gap reaching the previous result's expiry clears history. Repeated reads do not count as additional windows or extend expiry. Display and policy consumers must check `isFresh` at use time, even if no new inference arrives. Worker failure/disabled/loading are separate execution states, not classifications, and are connected in #22/#27.

`SoundLabels` translates a limited set of exact ontology labels, retaining the original string for untranslated classes. It does not interpret a musical “French horn” as a vehicle warning. The full 521-class original map remains bundled. Real speech/horn/knock/noise class correctness and false positives have not yet been evaluated with standard audio or on Watch7.
