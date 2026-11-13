# Live motor-setting calibration validity (#17)

> Execute inline; one fresh read-only review of this unit before integration.

Goal: prevent an active capture from continuing with motor-noise bounds calibrated for different vibration settings. Spec #17 and the existing PR73 remaining gate. Basebec3444/current installed064dd0d; user authorized all ownership, continued fixes, device verification and eligible merges.

Architecture: keep the existing profile key/file bytes. Reuse its exact System4/Global1 keys for a main/control-thread ContentObserver. Capture one expected key before loading the profile, register then re-read before microphone/output startup. A later mismatch/read failure reports MOTOR_CALIBRATION and uses existing capture/renderer/AI shutdown. Closing or stale callbacks cannot affect another session. No settings reads in the audio thread and no setting writes.

Files: MotorProfileStore.kt (shared key names/URIs, identical key representation); new MotorSettingsWatcher.kt (read-only observation lifecycle); ListeningService.kt (start/stop ownership); focused watcher/store/service tests. An opt-in separate-package native registration probe may be added after host verification; normal/release must exclude it.

- [x] Read current issue17/all comments/PR73, actual service/store/controller/tests and official ContentResolver/Settings.Global API references. Existing load already accepts explicit firmwareKey, so no load API change is necessary.
- [ ] RED tests: global/system changes, same/unrelated notifications, registration recheck mismatch, read failure, queued callbacks after close/new observer; exact legacy key representation; real Service cancellation/release/restart, observation preflight failure without microphone creation, stale callback after user stop/new token.
- [ ] Implement watcher and service lifecycle hooks. Registration failure/preflight mismatch must prevent microphone creation. Known startup failures, normal stop, asynchronous read failure and destroy close the observer; closed callbacks have no effect. Preserve existing error/ownership barriers.
- [ ] Full test/build/lint; one read-only review and one required fix pass. No new approval request for already authorized implementation.
- [ ] Native read-only registration/recheck/close on Watch7, same profile-key hash where possible; do not change actual Global/System settings. Full positive external-change/physical stop test waits for the user in the morning. Keep17Draft/open.
- [ ] Preserve installed candidate/profile/settings/stay-on1 and back up064d before any later installation. Record exact APK/install/cleanup evidence; no Actions/raw audio/hidden API/root/protected-setting writes.

Review focus: registration-to-start race, previous callbacks versus new session, errors that could block microphone/renderer cleanup, key-format compatibility, no duplicate callbacks/retry/secret restarts, and truthful native scope.

Additional observed RED: an explicit notification for an unverifiable/missing setting was incorrectly ignored because the legacy key uses '-' for missing values. Exact watched URI notifications now invalidate when that specific value cannot be read; unrelated/parent notifications with an unchanged identity remain ignored. The registration race test also inspects the real host ContentResolver registration before changing the key inside the post-registration read.

Initial opt-in verification exposed an invalid hyphenated taskAffinity in the new probe manifest (52 host tests failed at package parsing, not silently skipped). Corrected the affinity to a valid package-style name before any device installation and rerun the full suite.

The next opt-in run exposed an existing permission-navigation test hardcoding the normal package name. Production already uses packageName correctly; updated the assertion to the actual application.packageName so it verifies both normal and separate probe settings targets instead of weakening the behavior check.

Further real RED in service/store tests: stopping alone could reuse the same old key after an unverifiable change. Preserve the numeric profile bytes but persist a one-byte no_backup invalidation marker and an in-process latch. Even an empty marker blocks load after restart; only successful save plus marker removal restores validity. Stop microphone/output before this control-thread persistence. The native probe validates this lifecycle with a generated numeric fixture in its own separate package; it never rewrites the user's calibration or actual system settings.

API references: https://developer.android.com/reference/android/content/ContentResolver#registerContentObserver(android.net.Uri,%20boolean,%20android.database.ContentObserver) and https://developer.android.com/reference/android/provider/Settings.Global#getUriFor(java.lang.String).
