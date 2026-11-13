# APK provenance drift verification (#34)

> Execute inline with executing-plans, one fresh read-only review at the end. User authorized all issue/PR ownership and continued implementation; preserve Watch7 app/profile/settings.

Goal: reject stale notice metadata when the actual resolved runtime graph or APK native library bytes differ. Base79de7a2/PR71, existing #34 scope and docs/architecture.md. Issue/comments/PR reread; sole existing owner is this chat. #1/#8/#19/#24 are now closed/main, but native complete inventory/revisions and Eigen Source Form still remain unproven. This change cannot close34 or waive those gates.

Architecture: collector emits schema2 catalog with each AAR JNI library's APK path/bytes/SHA256. Packaged verifier requires a fresh Gradle runtime inventory and explicit debug/release variant mapping; compare exact coordinates/artifact names/configurations/current file hashes to the catalog, then the complete APK native entry set and raw library bytes. Keep existing notices/model byte checks. No application dependency/runtime/SDK/model changes or Actions. APK content equality does not establish complete native licenses/source correspondence, and runtime graph input is a local build observation rather than a signed build attestation.

- [x] Build both old PR variants and current runtime inventory. RED negative fixtures: changed/missing/extra/duplicate JNI, changed dependency coordinate and changed artifact file bytes must not pass while notices/models remain identical.
- [x] Implement collector/schema/verifier checks and regenerate against real resolved artifacts. Do not change native completeInventory=false or invent source revisions.
- [x] Verify real Debug/Release plus all six isolated negative fixtures; local full tests/build/lint/release. Test code creates its own temporary files, never modifies source artifacts or installs APKs.
- [x] One fresh independent read-only review. One required fix pass, RED→GREEN/full appropriate checks, no re-review. Record decisions and remaining native source gate.
- [x] Commit/push original PR71 branch, update exact evidence and DoD/predecessor status. Keep Draft/open if provenance gate remains.

Review focus: APK variant versus graph association, unknown/duplicate/missing libraries, metadata hashes versus actual artifacts, stale catalog rejection, unsigned fixture test isolation, no overclaim about DEX origin/native license completeness.

Observed RED: all six invalid fixtures were accepted by the original verifier. After schema2 collector/verifier changes, real baseline first failed because Gradle emits duplicate identical core1.15 artifact rows; the existing collector already merges exact duplicate files/configurations. Canonicalize only identical coordinate/name/configuration/hash records. Positive Debug/Release and all six negative fixtures then passed. Full local test/build/lint/release37s; exact test count is captured in the evidence. JNI parents include DataStore and graphics-path as well as LiteRT; all12 APK entries are byte-checked without claiming their internal inventory/source completeness. Native manifest completeNativeInventory=false and unknown revision fields remain unchanged.

One fresh read-only review of6dbea94 independently confirmed both APKs and all12 catalogued JNI descriptors against the live resolved archives. Important empty-input success reproduced as a real RED (the six drift fixtures still rejected but empty arrays returned success); require at least one pair before checks. One required fix pass and full appropriate validation, no re-review.

Final: minor (deferred): ValidateSet accepts Debug/Release mixed case but case-sensitive configuration matching rejects it; documented lowercase debug/release remains required. Do not include this polish item in the required fix pass.

Final: Ruling: full native source/license inventory and Eigen Source Form remain open and block34; a byte match cannot waive this. Treating it as clearance could omit attribution/source obligations.
Final: Ruling: inventory/variant association is caller-provided local build evidence, not signed attestation or whole-DEX origin proof; wrong caller inputs can mislabel a build, so no stronger claim is made.
Final: Ruling: Watch7 runtime is outside this tooling-only unit and was not exercised or changed; APK/package checks cannot establish hardware behavior, which remains under the existing issue gates.

Final: fixed empty-input success — empty-input fixture RED→GREEN; both real APK checks and all7 negative fixtures pass. Local Gradle test/build/lint/release SUCCESS2s (102 tasks reused because product inputs unchanged), previous full168/168 tests/0 failures/errors/skips and build37s remain valid. Native source completeness remains false; no second review.

Published source/evidence e55194b and updated PR71 title/body with corrected predecessor states, 7 negative checks, current APK hashes, deferred case issue and unresolved native provenance/Source Form. Draft/open retained; no merge or issue closure. Current worktree product files and Watch7 were unchanged.
