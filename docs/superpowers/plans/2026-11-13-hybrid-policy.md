# Hybrid haptic policy implementation plan (#21)

> For agentic workers: use superpowers:executing-plans task by task.

**Goal:** Let reliable AI context gently adjust commands without replacing the DSP signal.
**Architecture:** HybridHapticMapper wraps an always-advancing EnvelopeHapticMapper. AI only applies a bounded gain/onset emphasis after the baseline command is computed. Missing/invalid/unknown context returns the exact baseline, independent of earlier AI state. No new pulses or changes to timestamps/durations.
**Tech stack:** Existing Kotlin, HapticMapper and Classification contracts, JUnit synthetic PCM fixtures.
**Spec:** GitHub #21; docs/architecture.md and models.md; user-approved implementation/local-validation-first continuation.

## Global constraints
- No AI wait, PCM persistence, network or Actions. Actual worker wiring belongs to #22.
- Preserve silence, session, time, duration and the 0.85 output ceiling.
- No/expired/future/other-session/low-score/ambiguous/unknown context yields bit-for-bit baseline.
- Local A/B results are command comparisons, not proven perception or AI improvements.

## Review focus
- OFF after ON must not inherit modified mapper history.
- A wrong but confident known label cannot suppress all real events.
- Silence never generates a label-driven pulse.
- Session/reset/gaps cannot retain old AI parameters.
- Numeric gain/focus cannot reverse intensity ordering or exceed output limits.

### Task 1: Bounded context policy and A/B fixtures
Create haptics/HybridHapticMapper.kt and haptics/HybridHapticMapperTest.kt; document policy and synthetic comparison in docs/models.md.
Consumes: AudioFeatures, optional fresh Classification, HapticMapper baseline.
Produces: HybridHapticMapper(baseline: HapticMapper = EnvelopeHapticMapper()): HapticMapper.
- [ ] Add failing tests for exact OFF restoration, invalid context, reset/session, monotonic strength, and three same-input PCM A/B scenarios (speech modulation, three knocks, noise).
- [ ] Observe missing-feature failure.
- [ ] Always compute baseline first. Accept exact known label only with score >=0.65 and lead >=0.15. Targets: Speech/Conversation gain1.02, Noise gain0.94, Knock/Tap/Clapping onsetFocus0.08 and gain1. Parameters approach target by 0.1 per20ms frame; invalid context resets them immediately. Apply `base * (gain + onset*focus*(1-base))`, clamp0..0.85. Preserve zeros and identity fields; reset policy on session/gap, baseline only through its own lifecycle.
- [ ] Run full unit suite/assemble/lint. Print command MAE/change counts for synthetic scenarios, asserting identical timing/silence and 0.9..1.1 relative command bound.
- [ ] Read-only review, commit and publish Draft PR targeting main with Closes #21 and device gaps.

The targets are conservative initial choices. Same-class misclassification can still make a small undesirable adjustment; actual benefit requires the #37 AI ON/OFF evaluation. No perceptual equivalence is assumed.
