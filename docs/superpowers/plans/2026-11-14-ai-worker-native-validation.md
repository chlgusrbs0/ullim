# AI worker real-time native verification (#22)

> Execute inline; fresh read-only review before any integration. Local verification workbench, not a replacement production service PR yet.

Goal: measure the actual original PR60 worker/feed on latest verified DSP/AI policy without importing unfinished old service/UI changes. User explicitly authorizes local-first overnight work despite remaining predecessor tactile gates.

Base: PR76/b1375ed (main8398b0a plus pending21). Original22 source0278a74, AiWorker Git blob36db0a852887f602ed27f317bccb6a40bc9f72ff. AiWorker/AiCaptureFeed/AiWorkerTest copied unchanged. Same existing coroutines-android1.10.2 dependency. #21 physical gate remains open; #22 cannot be merged on this workbench result alone.

- [x] Read issue22/all3comments/PR60, original worker/feed/tests and native procedure. Preserve old production service/UI; no original app replacement or setting changes.
- [x] Build a separate opt-in action-gated test using real AiWorker+AiCaptureFeed+AudioFanOut+DSP+Hybrid mapper, real model on one independent executor, real20ms paced generated PCM producer. No microphone/motor/files/network.
- [x] Observe5min OFF baseline and5min ON plus30s OFF/cleanup. Measure actual arrival gaps/lateness/processing, valid inference progression, maximum simultaneous native calls, load/close counts, PSS/Javaheap/processCPU/battery temperature/OS thermal status. Compare commanded baseline/hybrid on the exact same features, never physical vibration. Actual599inferences/31500DSPframes/1load1close/terminalPASS; [evidence](../../evidence/issue22-worker-native.md).
- [ ] Severe OS thermal status or battery45C are conservative test stop guards, not device safety standards or production acceptance thresholds. Log failure and clean up; never claim5min completion after an early guard/fault/cancellation.
- [x] Full local tests/build/lint, one fresh read-only review, fix required findings once; final Watch7 candidate executed. Observed terminal PASS before uninstalling own probe. Original APK/calibration/micOFF/stay-on1 verified before the separately recorded actual-app AI backport installation.
- [ ] Document exact evidence/limits/source identity on originalPR60; retain Draft for unmerged21/production UI+service integration/human gates. No Actions or threshold/DoD reductions.

Review focus: real elapsed time and measured cadence rather than logical frame counts; synchronous native work never on DSP path; no queue or parallel interpreter growth; truthful post-OFF in-flight completion/closure and memory; instrumentation overhead and charging thermal limits; all exceptions/cancellation clean up independently of the original app.

Final review: three P2 validation findings. Late focus loss/restored interruption during cleanup could still log PASS; verification now rechecks the Activity cancellation gate before PASS. Post-OFF inference allowance was sampled before disabling; it is now sampled after synchronous refreshMode returns. A cumulative100-success check could hide later starvation; every ON minute must now advance successful inference count. Two actual Activity cancellation regressions and an original-worker idle-without-fault regression all failed before these fixes; full suite is required after this single fix pass. Cadence/resources remain measured observations, not substituted physical/perception gates.
