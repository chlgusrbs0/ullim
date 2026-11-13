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
