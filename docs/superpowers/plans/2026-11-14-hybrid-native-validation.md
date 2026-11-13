# Actual model to bounded policy verification (#21)

> Execute inline; one fresh read-only whole-branch review before integration.

Goal: make real model context participate in the existing bounded noise policy while preserving DSP rhythm, exact OFF recovery and conservative fallback. Spec GitHub #21, existing approved architecture/plan; #12/#20 merged in main8398b0a.

The prior single-label Noise policy ignores actual pinned-model white-noise output Static0.667969/Noise0.585938/White noise0.5. The smoother correctly confirms Static, but the policy rejects its name and treats related noise scores as competing policies. This is an implementation gap within the existing noise adjustment, not a new feature or model accuracy claim.

Decision: map exact bundled Noise/Static/White noise/Pink noise/Wind noise (microphone) to the same existing NOISE gain0.94. Require the actual highest class score >=0.65 and margin>=0.15 against candidates from other policies or unsupported labels. Never sum class scores or relabel the displayed winner. Other supported label sets/gains, confidence thresholds, silence, timing, limit and baseline history stay unchanged.

- [x] Read issue/all comments/PR58, preserve old branch and transplant only1651f85 onto main8398b0a; document existing user transfer/local-first authorization.
- [ ] Add a failing regression using exact native19/20 noise scores through the real smoother into policy, plus competing-policy/unknown/low-score cases; fix exact label grouping only after failure.
- [ ] Add opt-in same-input command A/B using actual YAMNet classifications from generated PCM, not injected pretend model labels. Record original score list, accepted context, changed commands/MAE, unchanged timing/silence, and exact OFF/expired recovery. No microphone or motor.
- [ ] Run normal/opt-in tests/build/lint, verify packaged isolation, execute final Watch7 candidate, clean up only temporary package/preserve original app/profile/settings.
- [ ] Fresh read-only review; document exact evidence and remaining tactile A/B/production5min gates; open replacement Draft PR, link/close old Draft preserving branch. Physical tactile comparison waits for morning; do not close21 on numerical evidence alone.

Review focus: correlated ontology outputs do not become summed probabilities; genuinely competing supported/unsupported labels still force baseline; no history contamination; bounded change/zero/onset/time invariants; actual native classification provenance and no fabricated usefulness.

Final review P2: initial native expiry comparisons only saw silent frames after the last knock, making that assertion vacuous. Added a separate always-nonzero100-frame replay with actual model context: adjustment before expiry and exact baseline after expiry. A negative-control unit test supplies a mapper that ignores expiry and must be rejected with EXPIRED_NOT_BASELINE; positive control uses the real hybrid mapper. Observed missing-verifier RED before implementing this check. Production expiry logic was already covered by host tests and did not change for this finding.
