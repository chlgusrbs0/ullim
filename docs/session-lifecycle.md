# Session lifecycle (#23)

`ListeningController` is the logical state authority behind CaptureRuntime's read-only RuntimeState flow. Core ListeningState/AiStatus/PCM/haptic contracts remain unchanged. It admits one monotonically numbered session, binds the microphone owner to that starting token, and accepts status transitions only for the active session. Recording requires an attached owner. Unattributed UI errors cannot interrupt an active session.

CaptureRuntime bridges levels, capability indication and process-local AI choice to the controller. Service attachment now passes the session token explicitly. Old tokens and different resource objects cannot overwrite current status or release its owner. Every service capture run uses a new MicrophoneCapture object. UI values remain derived from actual service/controller events; they are not optimistic start/stop toggles.

## Cleanup barrier and errors

- Starting → Listening requires successful microphone recording notification and current owner.
- Starting/Listening → Stopping disables AI context indication and clears displayed levels. Repeated stop and late Recording are ignored.
- Native owner release and terminal DSP/haptic/service cleanup are both required before another start. If `finished` arrives before release, state remains Stopping; if release arrives before `finished`, a new start is still refused.
- Errors retain their reason. Errors before resource acquisition allow retry immediately; errors after acquisition wait for both release and terminal completion. A failed foreground start explicitly completes rollback even if no capture worker ever started.
- A failed native microphone release retains its run and controller lease. The app does not claim the microphone was freed or admit another recorder. The UI asks for full process restart; actual OS recovery must be tested on Watch7. `awaitStopped` only confirms the capture routine terminated, not successful native release in this error case.
- AI native cleanup may still be pending on its independent bounded lane after capture stops. Its context has already been invalidated; this does not permit an old AI callback to affect a new capture. See models.md for those separate flags and deadlines.

ForegroundSession cancels fan-out/DSP/haptic/AI output first, requests microphone stop and removes the foreground notification. Cleanup is idempotent, including rollback after foreground promotion failure. Microphone stop sets an atomic request immediately and dispatches at most one stop-control thread for that run. Native start/stop/release remain serialized under the run monitor, while the UI caller does not wait for that monitor. A blocked native call remains owned and prevents another microphone; it is not described as forcefully cancelled.

## Local and device evidence

Local tests exercise concurrent start admission, stop-before-attach, completion/release ordering, old-token rejection, ordinary error retry and unattributed UI errors. Fake native inputs block start/stop so the tests can verify caller return and output cancellation before releasing the gate; another test injects release failure and verifies no second recorder is admitted. These tests do not prove physical motor cessation, actual AudioRecord behavior or OS service survival.

Reproduce locally with `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug` (Windows: gradlew.bat). Watch7 still needs repeated adb start/stop, rapid taps, service destruction, permission changes and error/retry traces. Record actual session state, owner cleanup and notification/microphone/physical vibration behavior; keep #23 open/Draft until that evidence and predecessor merges exist. No GitHub Actions or microphone audio files are used.
