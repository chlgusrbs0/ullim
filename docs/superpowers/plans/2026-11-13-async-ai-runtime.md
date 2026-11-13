# Async AI runtime implementation plan (#22)

> For agentic workers: use superpowers:executing-plans task by task.

**Goal:** Connect offline AI as an optional independent consumer without blocking DSP or user stop.
**Architecture:** A process-owned AiWorker uses one asynchronous execution lane and one latest pending PCM window. Native load/inference/close occur only on that lane, outside its short state lock. Generation checks invalidate old session/toggle results. Deadline checks run independently of native inference; failure latches until manual OFF/ON, with no automatic retry. The service connects fan-out, hybrid mapping, heartbeat and AI state. A separate AI control screen provides process-local ON/OFF until #28 persistence.
**Tech stack:** Existing Kotlin/Wear Material3, Executor, StateFlow, AndroidYamnet and ClassificationSmoother.
**Spec:** GitHub #22; architecture.md/models.md; user-authorized implementation/local-check-first approach. #24 PR59 already supplies independent UI foundations and must not be recreated.

## Global constraints
- Preserve existing core contracts, DSP priority, explicit microphone start and all raw PCM privacy rules.
- No native interpreter close concurrent with inference, no synchronous waiting for AI from DSP/UI.
- At most one process AI worker/pump and one pending window; OFF/new sessions invalidate queued results and arrange safe asynchronous closure.
- No Actions, device/perception/performance success claims or main merge before required evidence.
- Surface OFF/loading/listening/delayed/failed states separately from labels; expiry is checked even when inference stops.

## Review focus
- A blocked native call cannot stall audio or spawn accumulating replacement workers on OFF/ON.
- Results/errors from old generations cannot alter a new session.
- OFF immediately removes AI context; closure after in-flight inference must remain honest about pending cleanup.
- Timeout/factory/inference failures affect only AI and never automatically retry.
- Shutdown, disabled-state offers, wrong sessions and old windows cannot retain PCM or resurrect AI.

### Task 1: Pure worker and failure injection
Create ai/AiWorker.kt and ai/AiWorkerTest.kt.
Consumes: SoundClassifier, AudioFrame, ClassificationSmoother, asynchronous Executor, monotonic clock.
Produces: AiWorker(factory: () -> SoundClassifier, executor: Executor, clockNs: () -> Long), state: StateFlow<AiSnapshot>; start(sessionId, enabled): Boolean; setEnabled(sessionId, enabled); offer(frame): Boolean; tick(); current(sessionId, nowNs): Classification?; stop(sessionId).
AiSnapshot exposes session, phase, fault, classification, loaded/busy/cleanup-pending flags, load/inference durations/counts and dropped-window count; never PCM.
- [ ] Add deterministic real-executor/latch tests for blocked inference + latest-only buffer + independent DSP; OFF closure ordering; rapid OFF/ON and stale generation results; late old-session stop; timeout/no auto retry; factory/inference failure; delayed state/expiry; model closure/rejection cleanup.
- [ ] Run targeted tests and observe missing-feature failure.
- [ ] Implement one serialized pump, worker-only native ownership and short synchronized state transitions. Keep pending snapshots at one and discard stale/reordered/wrong-sized frames. Use 500ms input cadence, 500ms delayed indicator, 1s inference deadline and 10s load/previous-operation deadline as development defaults. A native call that never returns remains one occupied lane with cleanup pending, rather than falsely claiming it was interrupted or starting new workers. Close failure disables further model loads until process restart.
- [ ] Run full unit suite before integration.

### Task 2: Service, state and user control
Create ai/AiRuntime.kt and AiControlsActivity.kt; modify ListeningService.kt, runtime/CaptureRuntime.kt, MainActivity.kt, manifest and strings. Keep #59 ui-package files intact for later UI integration.
Consumes: worker API, fanout.takeLatestAi(), HybridHapticMapper and existing session lifecycle.
Produces: actual optional AI model consumption, process-local AI toggle, effective AI status and metadata-only telemetry.
- [ ] In the capture callback, feed fanout first then offer its latest AI snapshot; read current context atomically inside DSP mapping. Apply toggle/heartbeat without waiting for worker/native operations.
- [ ] Start the worker only for the active user-started capture session; stop with exact session token and clear context on all teardown/error paths. Observe worker status without blocking capture and preserve core RuntimeState meaning.
- [ ] Add exported=false AI control activity and explicit entry button; show honest delayed/failure/cleanup states. No persistent settings claims until #28.
- [ ] Run testDebugUnitTest/assembleDebug/lintDebug and inspect manifest/assets/permission boundaries. Document reproducible Watch7 5min ON/OFF CPU/memory/thermal/latency collection without recording audio.
- [ ] Obtain read-only review, fix important findings with failing regressions, commit/push and open main-target Draft PR with Closes #22 and unmeasured device evidence.

Model load/inference on Watch7, steady thermal/memory behavior and native cancellation limits remain device gates; host fakes cannot prove them. No smaller-model swap is justified before those measurements.
