# Persistent sensitivity and AI choice (#28)

Use Preferences DataStore 1.1.7 (compatible with existing Kotlin stack), one application-owned store. Defaults: sensitivity1.0, AI OFF. Offer conservative sensitivity steps .75/1/1.25 (existing DSP accepts .5..2). Schema1 tolerates missing keys, invalid types/nonfinite/out-of-range values and unknown future versions by using safe defaults. ReplaceFileCorruptionHandler recovers corrupt preference bytes. No PCM or classification data is stored.

SettingsController loads once asynchronously, disabling edits until ready. It applies current choices immediately on its main/control scope; writes are serialized with one conflated pending request. Revisions prevent old save completions from clearing newer saving/error status. In-memory choices remain applied on I/O failure with explicit unsaved/error text and retry/reset actions. No later disk emission can overwrite a newer explicit choice. Do not block UI/DSP on disk, silently claim durability or add automatic microphone starts.

Application initializes the controller. Applying values updates CaptureRuntime AI/sensitivity and only an existing AI worker on the main control thread; no native worker/model is created by settings loading. Service's live DSP wrapper reads current sensitivity per frame; audio delivery never changes AI mode. Wear settings expose bounded sensitivity, AI toggle, real output limitation, saved/loading/error status and reset; AI detail screen uses the same persistence authority. Existing core contracts and haptic policy remain fixed.

- [x] RED actual DataStore tests: durable reload, typed/schema defaults, corrupt bytes, resets/unknown keys. Implement repository.
- [x] RED controller tests: delayed initial load/edit guard, immediate application before blocked save, latest-write ordering and failed save honesty/retry. Implement process lifecycle and main-thread bridge.
- [x] Prove actual DSP/mapped command response to live sensitivity and exact AI OFF baseline continuity with existing bounded worker tests.
- [x] Add actual Wear Compose setting/toggle/recreation tests. Full unit suite, assemble/lint, independent read-only review and important fixes. Prepare main-target Draft publication Closes #28; retain predecessor/Watch7 UI→physical response/restart DoD as unverified.
