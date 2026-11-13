# Third-party provenance and packaged notices plan (#34)

> **For agentic workers:** Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Make every redistributed component/model and referenced external implementation traceable, retaining required notices in both the source tree and APK.

**Architecture:** Inventory actual Debug/Release resolved artifacts using a local Gradle init script; compare cached Maven metadata and embedded notices with primary upstream sources pinned by version/commit. Keep original notices, add a readable THIRD_PARTY_NOTICES.md and bundled notices without declaring a license for Ullim itself. Distinguish source copying, model redistribution, runtime libraries, build tooling and idea-only references.

**Tech Stack:** Gradle8.13 local metadata/ZIP inspection, Markdown/text assets, existing Kotlin/Wear app. No Actions or new runtime service.

**Spec:** GitHub #34 and AGENTS.md. Claim6851811604 posted after current comments/open PR inspection, immediately rechecked sole active claim. Cumulative base2087228; prerequisites #8/#19/#24 not yet merged. This does not waive native DoD.

## Constraints and review focus

- Do not add a project-wide LICENSE or invent author/permission claims.
- Preserve YAMNet binary/label bytes and independently recheck their hashes, embedded metadata/map and upstream terms.
- Record exact runtime artifact versions and actual notice sources, including native bundled dependencies; a blanket Apache assertion from a Maven coordinate is insufficient.
- Compare DSP/haptic history and relevant upstream code; report the scope of GPL/source trace checks without claiming universal originality from a string search.
- Verify required notices actually reach both APK variants and links/README commands resolve. Unsigned Release is an audit artifact, not an installable final release.
- Preserve the current Watch7 E/F comparison APK and connection settings. Local build only while user feedback is pending.

## Task: inventory and notices

- [x] Collect actual resolved artifact inventory, cached POM/license/NOTICE contents and known native-library provenance; exact native revisions remain unresolved.
- [x] Pin upstream reference commits/licenses; compare copied versus independently written DSP/haptic code and trace relevant history.
- [x] Write source THIRD_PARTY_NOTICES.md and bundled complete attribution/license texts plus exact model metadata/hashes.
- [x] Update README provenance/reproduction links and check local/external links using appropriate authenticated sources.
- [x] Assemble/inspect Debug and Release APK notice entries and unchanged model bytes; run appropriate local validation.
- [x] Read-only review; fix the Google Maven POM mapping and add known native notices. Commit/push Draft PR71 to main with Closes #34, retaining the unresolved native provenance and predecessor gates.

## Remaining gates

- [ ] Resolve the exact LiteRT-linked native source revisions, complete the native inventory and provide Eigen/MPL Source Form correspondence before final distribution.
- [ ] Complete predecessor issues #8/#19/#24 on main; packaging checks do not waive their DoD.
