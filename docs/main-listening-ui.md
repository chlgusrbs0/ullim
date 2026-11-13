# 최초 실행 안내와 마이크 동의 (#29)

## 최초 실행

`MainActivity` reads the onboarding-complete flag from the private `ullim_first_run` preferences. A fresh install opens the Korean onboarding screen. The user can either request microphone permission explicitly or continue without granting it. Both choices finish onboarding and persist the completion flag, so a normal relaunch does not force the guide again.

The onboarding permission action requests only `RECORD_AUDIO`. A grant returns to the idle home screen; it does not create a session, start the foreground service, or open the microphone. Capture requires a separate tap on the home screen's start control.

## Disclosure

The guide describes the demo as mapping nearby sound level, duration and rhythm changes to haptic changes. It explicitly does not claim to understand all sounds, languages or conversation meaning, and does not present itself as a safety alert or medical device. It explains that microphone access is used for live on-device analysis after the user starts detection, that raw voice is not saved or sent to a server, and that a foreground service/ongoing notification keeps detection active when the display is off. The app and notification both expose a stop action. Haptic intensity/rhythm distinction depends on watch hardware and is not guaranteed.

## Permission recovery

The first-run permission action and the home retry action request `RECORD_AUDIO` only after an explicit user tap. The request-attempt flag is stored separately from the onboarding-complete flag. When Android reports that the permission can no longer be requested through the normal dialog, the home screen offers the app's system settings instead. Returning from settings refreshes the permission state but never starts capture.

Notification permission is requested separately when the user starts detection on Android 13 or later. Denying notifications does not turn microphone permission into a denial; the app can continue only after the user has explicitly started capture.

## Verification boundary

`FirstRunOnboardingTest` checks that the permission-consent action completes the guide and requests permission without invoking capture, and that choosing “later” completes the guide without requesting permission. `MainActivityUiTest` continues to exercise the real Activity/controller command path with a fake microphone owner. These host tests do not exercise Android's real permission sheet, a physical microphone, a foreground notification on Wear OS, screen-off behavior, or a Galaxy Watch7. Those remain manual device checks; do not claim them as passed from Robolectric tests.


## Existing main screen behavior

The existing controller-backed main screen remains the only place where capture starts. The first-run guide is an additional navigation state; it does not replace or simulate the runtime's listening state.
