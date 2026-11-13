# Classification native verification (#20)

> Execute inline; one fresh read-only review before integration.

Goal: isolate the existing #20 component on merged #19 and observe actual model scores through confirmation, uncertainty and expiry on Watch7.

Architecture: retain the pure Kotlin smoother and its core Classification contract. Add only an opt-in separate-package Activity using generated signals and controlled score scenarios. No microphone, vibrator, network or original app changes.

Spec: GitHub #20 and docs/models.md. Original PR57/commit2eaa296 is preserved; replacement branch starts at main ab730a6.

- [x] Read issue/all comments/PR57 and preserve ownership; transplant only2eaa296, preserving current model evidence during documentation conflict resolution.
- [x] Exercise exact threshold/confirmation boundaries with host tests; add action-gated native validation and generated silence, voice-like, horn-like, knock-like, noise and sine inputs. Names describe synthesis, never presumed recognition.
- [x] Native controlled scores cover Speech/horn/Knock, ambiguity, low scores, duplicates, stopped/delayed expiry, reset and other sessions. Log original label, Korean mapping, scores and monotonic time/expiry separately from actual inference results. Execution-error state wiring remains #22.
- [x] Run normal/opt-in test/build/lint, verify normal/release exclusion. Run the final candidate on Watch7; preserve original app/profile/settings and remove only the temporary probe afterwards. [Evidence](../../evidence/issue20-validation.md).
- [ ] Fresh read-only review, address required findings once, document exact evidence/limits; replace PR57 without deleting its branch. Merge only if issue DoD is met; no accuracy or final AI usefulness claim.

Review focus: stale results without new callbacks, duplicate observations, raw/EMA disagreement, delayed results, session leakage, exact ontology translation and no accidental normal-app probe.

Ruling: use generated test audio only, with known synthesis and observed model labels recorded separately. This preserves the repository's no-user-audio policy; these fixtures do not establish real speech/horn/knock recognition accuracy, which remains an explicit integration/acceptance limit.

Boundary regression exposed Float EMA drift: repeated .42 became .42000002, changing a raw .08000001 margin into .07999998. Use the algebraically equivalent previous + .6*(incoming-previous), preserving constant inputs without weakening thresholds. The exact margin regression failed before this fix.
