# Haptic mapping verification (#12)

> Execute inline; one fresh read-only review before merging.

Goal: verify the original PR50 mapper on current merged DSP, preserving the existing branch and PR.
Spec: GitHub #12, dependencies #3/#10 merged; original mapper commit4f8322e.

Scope: pure DSP features to20ms HapticFrame commands. This does not certify physical motor amplitude, smoothness or latency. Preserve original app/calibration/settings; no microphone/audio files/network/Actions.

- [x] Read issue/all comments/PR50, record user-authorized ownership transfer and merge current main4902ca7 into the original branch. Only mapper and its tests differ from main before validation changes.
- [ ] Verify raw synthetic PCM strength order, sensitivity order,3-knock gaps,5-second duration and silence; retain existing feature-level upper-bound/session/release tests.
- [ ] Add opt-in native mapping scenario and observe numerical command outputs on Watch7 without operating its motor.
- [ ] Full normal/opt-in test/build/lint; fresh read-only review, document exact results, update PR50, merge with expected head only after all #12 command-level DoD pass.

Review focus: constant loudness/history comparisons under identical conditions, sensitivity through DSP, quiet-gap preservation, duration and timestamps, session/reset leakage and truthful distinction between commanded and physically felt vibration.
