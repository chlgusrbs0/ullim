# YAMNet native verification plan

> Execute inline; use one fresh read-only review before integration.

Goal: verify the existing pinned YAMNet component on Watch7, including real model inference while a separate DSP producer continues. User requires actual AI participation before hackathon acceptance.

Scope: preserve original PR56 branch and transplant only its model/LF commits to current main. Add an opt-in separate-package synthetic probe; normal debug/release must exclude it. No microphone, motor, network, raw audio files or original app data changes.

- [x] Inspect issue19/all comments/PR56; record isolation in PR56 comment; preserve model hashes/license/521labels.
- [x] Transplant LF fix and model commit, resolve only Gradle context preserving current tests/native guards.
- [x] Add regression for zeroing inference input after success and failure.
- [x] Add bounded native test: canonical silence/sine/noise class scores, real model load/inference/close timing and memory; separate 20ms DSP producer and latest-window AI consumer for10 seconds; no production AI enablement until lifecycle/performance evidence.
- [x] Run full local tests/build/lint, verify packaged hashes, tensor contract and normal/release exclusion.
- [x] Read-only review and required fixes; execute final native candidate. Observed actual cadence and DSP/AI overlap, not scheduled-frame counts alone. See [evidence](../../evidence/issue19-validation.md).
- [x] Create replacement PR74 and link/close superseded PR56; preserve its branch and keep issue19 open until final gate/merge.
- [ ] Merge/close issue19 only when its component gates pass. Production AI usefulness and five-minute integration remain separate required issues.

Native PSS and battery temperature are observations, not a five-minute thermal or perception pass. No-INTERNET/Bluetooth permissions plus bundled local inference demonstrates app independence while ADB WiFi remains connected; do not claim a physically disconnected watch. Production service/AI toggles, smoothing, meaningful haptic A/B and five-minute AI/DSP behavior remain #20–#22/#36/#37.
