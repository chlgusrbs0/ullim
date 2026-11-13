# Classification stability implementation plan (#20)

> For agentic workers: use superpowers:executing-plans task by task.

**Goal:** Convert YAMNet top-five scores into short-lived stable context and honest Korean display names.
**Architecture:** A worker-owned pure Kotlin smoother consumes Classification values, never audio or DSP locks. Its bounded score history and candidate confirmation use the capture monotonic timestamp. Empty scores mean uncertain; expired values disappear. UI/worker status wiring follows in #22/#27.
**Tech stack:** Existing Kotlin/JUnit and unchanged core Classification contract.
**Spec:** GitHub #20; docs/models.md and architecture.md; user-approved implementation/local-verification-first approach.

## Global constraints
- No fixed sound-name vibration patterns, PCM files, network, Actions or acceptance changes.
- Preserve raw model labels and original window end/expiry; scores are not calibrated probabilities.
- Draft PR targeting main with Closes #20. Real audio accuracy and Watch7 behavior remain unverified.

## Review focus
- Repeated reads of one result cannot count as new evidence or extend expiry.
- Out-of-order, future, expired or other-session results cannot replace active context.
- Ambiguous/low-score/no-signal results must not retain a confident old label.
- Session restart and long input gaps clear smoothing history.
- English label fallback must not confuse musical horns with vehicle warnings.

### Task 1: Stabilize classifications
Create ai/ClassificationSmoother.kt, ai/SoundLabels.kt and ai/ClassificationSmootherTest.kt under existing source roots. Extend docs/models.md with thresholds and validation boundary.
Consumes: Classification(sessionId, timestampNs, validUntilNs, scores).
Produces: ClassificationSmoother.accept(result: Classification, sessionId: Long, nowNs: Long): Classification?, current(sessionId: Long, nowNs: Long): Classification?, reset(); SoundLabels.display(label: String): String.
- [ ] Add tests for two observations 500ms apart, alternating labels, low/ambiguous/empty scores, duplicate timestamp reads, expiry, session restart, gaps and exact label mapping.
- [ ] Run the new tests and observe missing-feature failure.
- [ ] Implement bounded top-five score EMA (new=.6, old=.4); require top score >=.35 and runner-up margin >=.08. Confirm a candidate on at least two distinct windows spanning >=400ms; emit empty scores before confirmation or on uncertainty. Reset history after expiry gaps/restart. Preserve input expiry exactly.
- [ ] Run complete testDebugUnitTest/assembleDebug/lintDebug and inspect outputs.
- [ ] Obtain read-only review, commit/push independent branch and open Draft PR with local evidence and device gaps.

Thresholds are initial development choices, not measured accuracy. Actual label correctness on speech/horns/knocks/noise belongs to the pending standard-audio and Watch7 checks.
