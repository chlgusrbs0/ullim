# AI worker real-time native verification (#22)

> Execute inline; fresh read-only review before any integration. Local verification workbench, not a replacement production service PR yet.

Goal: measure the actual original PR60 worker/feed on latest verified DSP/AI policy without importing unfinished old service/UI changes. User explicitly authorizes local-first overnight work despite remaining predecessor tactile gates.

Base: PR76/b1375ed (main8398b0a plus pending21). Original22 source0278a74, AiWorker Git blob36db0a852887f602ed27f317bccb6a40bc9f72ff. AiWorker/AiCaptureFeed/AiWorkerTest copied unchanged. Same existing coroutines-android1.10.2 dependency. #21 physical gate remains open; #22 cannot be merged on this workbench result alone.

- [x] Read issue22/all3comments/PR60, original worker/feed/tests and native procedure. Preserve old production service/UI; no original app replacement or setting changes.
- [ ] Build a separate opt-in action-gated test using real AiWorker+AiCaptureFeed+AudioFanOut+DSP+Hybrid mapper, real model on one independent executor, real20ms paced generated PCM producer. No microphone/motor/files/network.
- [ ] Observe5min OFF baseline and5min ON plus30s OFF/cleanup. Measure actual arrival gaps/lateness/processing, valid inference progression, maximum simultaneous native calls, load/close counts, PSS/Javaheap/processCPU/battery temperature/OS thermal status. Compare commanded baseline/hybrid on the exact same features, never physical vibration.
- [ ] Severe OS thermal status or battery45C are conservative test stop guards, not device safety standards or production acceptance thresholds. Log failure and clean up; never claim5min completion after an early guard/fault/cancellation.
- [ ] Full local tests/build/lint, one fresh read-only review, fix required findings once; final Watch7 candidate only when reachable. Observe terminal outcome before uninstalling own probe. Verify original APK/calibration/micOFF/stay-on1 afterwards.
- [ ] Document exact evidence/limits/source identity on originalPR60; retain Draft for unmerged21/production UI+service integration/human gates. No Actions or threshold/DoD reductions.

Review focus: real elapsed time and measured cadence rather than logical frame counts; synchronous native work never on DSP path; no queue or parallel interpreter growth; truthful post-OFF in-flight completion/closure and memory; instrumentation overhead and charging thermal limits; all exceptions/cancellation clean up independently of the original app.
