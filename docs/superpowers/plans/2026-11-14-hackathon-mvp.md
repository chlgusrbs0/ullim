# Hackathon MVP integration

User explicitly prioritizes a rapidly usable hackathon MVP and authorizes PR/merge after essential validation, without waiting for commercial-level surrounding checks. Preserve unresolved hardware truth and keep original acceptance issues open; remove Closes17 from the MVP PR so merge does not falsely close it. Privacy branch remains separate/unpublished.

Base working MVP09ae702; latest main8398b0a. Core is real microphone→DSP strength/duration/rhythm→calibrated motor feedback suppression→haptics, asynchronous bundled YAMNet AI, screen/settings and explicit start/stop. Existing smoothness limitation stays documented. No new feature/permission/Actions/PCM capture or watch UI operation.

- Merge latest main, preserve the previously tested production app, combine both isolated opt-in probe manifests and main AI input-erasure regression, retain both sets of device evidence.
- Run normal tests/build/lint and opt-in compile/regression; inspect normal APK package/entrypoints/bundled model SHA/native ABI. One fresh read-only integration review; only real Critical/Important fix pass. Do not delay shipping for additional nonblocking evidence.
- Publish reviewed integration into PR73, retitle/rewrite as MVP with Refs17 and known limitations; expected-head guarded merge commit. Confirm main merge and issue17 remains open. Build/copy one installable debug APK from exact merged product tree; give a short core demo sequence. No claim of new watch installation or AI ON physical validation.

Review focus: production code preservation across eight conflicts; current main classifier/DSP/microphone fixes retained; opt-in manifest union and forced isolated package; normal APK excludes all probes; bundled assets/ABI/AI wiring; no false automatic issue closure or completed physical acceptance claim.
