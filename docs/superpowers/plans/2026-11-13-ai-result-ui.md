# AI classification display (#27)

Bind the actual AiWorker StateFlow and process AI choice to a pure display projection shared by home and AI controls. Core contracts and inference/haptic policy stay fixed. Home puts the compact AI status/result after both sound/command graphs; the scrollable AI controls show the same result and optional original label. Wear text wraps without truncation. No automatic microphone start, warnings implying danger or calibrated confidence, or native inference claims.

Projection gates: OFF clears all recognition immediately; no active capture, wrong worker/result session, loading/delayed/failed execution cannot show a recognition. A fresh result must pass current-session monotonic freshness and conservative score/margin gates (.35/.08, duplicate labels normalized); empty/low/ambiguous scores mean uncertain. Exact Korean labels take priority; untranslated classes keep original text marked as model original. Unknown/unsupported sentinel gets an explicit unsupported result, never a warning. Numeric score is labeled model score, not correctness probability. Expired/future results cannot display label or score.

AiSnapshot adds resultExpired=false metadata so the real heartbeat clearing an expired result remains distinguishable from first-result waiting. Reset on clearContext/new result/session/OFF; no raw label/PCM logging added. Record cross-issue scope on #22 before modification. Screen consumers independently check freshness at display time, in addition to worker expiry.

- [x] RED pure projection tests: all phases, session mismatch, uncertainty/duplicate/tie, exact expiry/future, Korean/original/Unknown and conservative model score.
- [x] RED worker expiry metadata test, then implement flag and projection.
- [x] Actual Compose tests driven by MutableStateFlow classification/runtime/enable/clock: fresh label/score, replacement, ambiguous/expired/failed/OFF clearing and current controls remaining usable. Verify original-label wrapping on a small host surface; Watch7 text screenshots remain pending.
- [x] Connect real MainActivity and AI controls through lifecycle-aware flow collection. Test/build/lint and one independent read-only review; fix important findings and prepare main-target Draft publication Closes #27. Predecessor main merges, native model accuracy and Watch7 readability remain required unverified evidence.

