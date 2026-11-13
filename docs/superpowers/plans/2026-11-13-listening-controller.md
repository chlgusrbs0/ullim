# Listening controller implementation plan (#23)

> For agentic workers: use superpowers:executing-plans task by task.

**Goal:** One observable session state machine with exclusive microphone ownership, safe rollback and responsive stop.
**Architecture:** Extract ListeningController from CaptureRuntime; CaptureRuntime remains the app's levels/settings bridge. Controller tokens govern state and ownership. A completion barrier keeps Stopping until the owner releases; errors remain retryable only after release. ForegroundSession cancels DSP/AI/haptics before requesting microphone shutdown, and microphone stop dispatches native work off the caller while serializing it with initialization/release.
**Tech stack:** Existing Kotlin, RuntimeState/ListeningState/AiStatus, StateFlow, AudioRecord adapter and JUnit latches.
**Spec:** GitHub #23; architecture.md; user-authorized code/local-check-first continuation from #60.

## Global constraints
- Preserve shared core data contracts and the single microphone owner.
- Main/UI start/stop never awaits blocked native microphone initialization/stop.
- No new capture while old native resources remain owned; late events cannot resurrect finished state or alter a new token.
- DSP/AI/haptic cancellation remains independent of model completion and microphone cleanup.
- No Actions/PCM persistence, no device success claims; Draft/main target Closes #23.

## Review focus
- Concurrent start requests admit exactly one token.
- Completion before actual release cannot expose Idle or permit another microphone.
- Old owner/token/status/error callbacks cannot affect a new session.
- Blocking native start/stop cannot delay caller return or haptic cancellation.
- Repeated stop/rollback cannot replay old cleanup against a new session.

### Task 1: State and ownership
Create runtime/ListeningController.kt and ListeningControllerTest.kt. Adapt runtime/CaptureRuntime.kt and service attach call; preserve public core types.
Consumes: RuntimeState/ListeningState/AiStatus.
Produces: ListeningController.state, requestStart(): Long?, isStarting(id), isListening(id), attach(id, owner: Any): Boolean, detach(owner), listening(id), stopping(id), finished(id), fail(id: Long?, message), aiStatus(id,status). Mutation methods return whether their token/transition was accepted.
- [ ] Add failing tests for concurrent starts, stop-before-attach, completion/release barrier, errors/retry, stale callbacks and unattributed UI errors.
- [ ] Observe missing-feature failure, then implement synchronized transitions with identity-checked owner and monotonically increasing tokens.
- [ ] Keep display levels/settings in CaptureRuntime and delegate state operations. Pass an explicit token to attach, updating all callers/tests; do not change core PCM/haptic contracts.

### Task 2: Responsive and ordered cleanup
Modify audio/MicrophoneCapture.kt and runtime/ForegroundSession.kt; extend real fake-input lifecycle tests.
- [ ] Reproduce blocked native start/stop holding the caller and delaying output cancellation.
- [ ] Make microphone stop an idempotent atomic request plus one short-lived stop-control thread per active run. Serialize native start/stop/release under the run monitor; no join on caller. Keep ownership until capture cleanup completes.
- [ ] Cancel DSP/AI/haptics first in ForegroundSession, then request capture stop and remove notification; make cleanup idempotent, including failed promotion rollback.
- [ ] Verify with blocked native input that output is cancelled and stop returns before releasing the input gate, while controller refuses a new session until cleanup finishes. Confirm one release and no late Recording after stop.
- [ ] Run full testDebugUnitTest/assembleDebug/lintDebug. Obtain read-only review, fix important findings with regressions, commit/push independent branch and open Draft PR with actual local evidence.

Device adb repetition, microphone resource release under real OS contention, UI return after service destruction and physical output cessation remain unverified until Watch7 is connected.
