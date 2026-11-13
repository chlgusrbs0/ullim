# Main listening screen (#25)

`MainActivity` collects the controller's runtime and separate retry-eligibility flows with lifecycle-aware Compose collectors. `ui/UllimApp` receives those observed values and callbacks. Only navigation is saved in the screen; no remembered boolean represents microphone activity. Returning from a guide/settings screen or recreating the Activity does not issue a capture start.

The primary control reads Start, Starting, Stop or Stopping from the real session phase. Starting and Stopping disable the primary control; Starting also offers a separate cancellation action. The Activity passes the current session token in each stop command. The service ignores old/missing stop tokens and can cancel a matching pending start before microphone ownership attaches. The foreground notification carries the active token and uses a token-specific Intent data URI: updating a new notification cannot replace the token on a retained older PendingIntent handle.

An error retains its text while the controller waits for native ownership and terminal cleanup. `startAllowed` can change independently of that unchanged error value; both the start button and diagnostic entry update from it. The command handlers check the controller again before starting capture or opening vibration diagnostics.

Permission flags refresh on creation, resume and permission results. Missing permissions and errors offer an Android app-settings entry. Returning from settings refreshes permission notices without starting capture. The settings page opens the existing optional AI controls and exposes vibration diagnostics only when capture can safely start. Sensitivity persistence and the AI label screen remain #28 and #27 work.

The home screen distinguishes input sound level from **commanded** vibration level, and displays the capability limitation when amplitude control is unavailable. These numbers do not measure physical vibration.

## Verification boundary

The JVM suite uses Robolectric SDK34 with native graphics and 192dp round-watch qualifiers. `MainActivityUiTest` renders the actual Activity and Wear Compose controls, taps them, records Android service/activity intents through shadows, and drives the real controller with an unstarted fake microphone owner. It checks pending cancellation, token-bearing stop, disabled transitions, retry after unchanged-error cleanup, navigation/recreation, permission settings/return, AI and diagnostic entry, and commanded levels. AudioRecord and the physical motor never run in these tests.

Run `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug` locally (Windows: `gradlew.bat`). Watch7 installation, leaving/returning to the app, rapid taps, actual permissions/service behavior, round-screen touch/scroll/back gestures and microphone/vibration stop video remain required device evidence. Keep the PR Draft and issue open until its device evidence and predecessor merges are complete. No workflows or raw microphone recordings are used.
