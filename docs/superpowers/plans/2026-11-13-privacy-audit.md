# Offline processing and storage audit plan (#33)

> **For agentic workers:** Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Audit the shipped APK and data paths against no raw audio persistence/transmission, and explicitly exclude app data from cloud backup and device transfer.

**Architecture:** Keep AudioRecord→memory fan-out→DSP/optional native AI unchanged. Add Android12+ data extraction exclusions alongside allowBackup=false. Document actual app-private settings, bounded memory and metadata logs, deletion boundaries and required offline hardware evidence. Review foreign #29/PR65 read-only; do not adopt its branch or falsely claim integrated consent.

**Tech Stack:** Android Manifest/XML, Gradle local artifact inspection, existing Kotlin/Wear app.

**Spec:** GitHub issue #33, AGENTS.md, docs/development.md and docs/architecture.md. Claim6850632618 is sole active claim after current comments/open PR recheck. Base6fd0358 includes cumulative unmerged predecessors; main has #1–#3 only.

## Constraints and review focus

- No raw PCM/voice files, dumps, network transfer, exception payloads or classification-label logging.
- No GitHub Actions, server, account, mobile module, INTERNET permission, or third-party telemetry.
- SDK minimum34; no legacy pre31 backup XML needed. Android12+ allowBackup=false alone is insufficient for some manufacturers' device-to-device transfers (official Android guide).
- Explicitly cover root/file/database/sharedpref/external and device-protected counterparts in cloud and D2D rules. Do not invent an iOS counterpart's identifiers for cross-platform transfer configuration.
- Audit final merged manifest and dependency graph, not only handwritten manifest. Do not claim arbitrary native code, filesystem or OS backup behavior is proven by text search.
- Preserve current device E/F comparison APK while user feedback is pending. Do not install another build, erase settings, revoke permissions, or stop the user's trial.

## Task: audit and concrete privacy policy

- [x] Inspect actual runtime source/assets/dependencies/final APK permissions and storage/log paths; record scope and findings.
- [x] Add explicit XML cloud/D2D exclusions and manifest reference; no test that simply mirrors XML.
- [x] Build/lint/test locally, inspect packaged XML/manifest and dependency report; document exact commands/results and remaining gaps.
- [x] Perform read-only device file-name inspection without recording/reading source audio, while preserving current trial state. Native model/system failure logs remain unverified.
- [x] Document user-visible privacy behavior and settings/uninstall/OS-log boundaries; record #29 integration as pending.
- [x] Read-only review: no Critical/Important findings. Corrected minor expiry wording: published result stops being used, while bounded smoother history remains until update/reset.
- [ ] Commit/push Draft PR with Closes #33. Keep issue open until offline native AI+capture+haptics and consent integration DoD are actually verified.
