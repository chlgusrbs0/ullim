# #5 마이크 정리 회귀 검증

2026-11-13 KST. PR #44의 기존 마이크 컴포넌트에 #23에서 확인한 정리 보장을 반영했다. `PcmInput`, 16kHz mono PCM16, `AudioFrame` 및 공개 start/stop/awaitStopped 시그니처는 바꾸지 않았다.

## 재현과 수정

기존 `stop()`은 네이티브 start/stop이 사용하는 같은 모니터를 호출자 스레드에서 기다렸다. 네이티브 처리가 막히면 화면의 중지 요청도 기다릴 수 있었다. 또한 release 예외를 CLEANUP으로 보고하면서도 active 소유권을 비워 새 녹음을 허용했다.

실제 `MicrophoneCapture`에 제어 가능한 `PcmInput`을 넣어 다음을 재현했다. 원본 오디오 대신 정해진 가짜 입력/오류만 사용했다.

- `stopReturnsWhileNativeStartIsBlocked`: start가 막힌 동안 호출자의 stop 반환, 새 소유자 거부, 정리 뒤 read 없음과 release 1회.
- `stopReturnsWhileNativeStopIsBlockedAndReleaseStillOwnsRecorder`: stop이 막힌 동안 호출자 반환, 정리 완료 전 새 소유자 거부, release 1회.
- `failedNativeReleaseRetainsRecorderOwnership`: CLEANUP 반환 뒤에도 다음 start를 거부하고 추가 소스를 열지 않음.

수정 전 타깃 테스트 실행은 **10개 중 신규 3개 실패**(각각 네이티브 start/stop 대기 및 새 녹음 허용)로 예상한 결함을 재현했다. 중지 요청은 atomic으로 한 번만 표시하고, 네이티브 정리는 별도 스레드에서 기존 모니터로 직렬화했다. release 실패 시 입력과 active 소유권을 유지한다. 정리가 실패한 컴포넌트는 재시작 가능하다고 주장하지 않는다.

500ms의 테스트 대기는 호출자가 차단되는지 확인하기 위한 호스트 타임아웃이다. 소리→물리 진동 지연 또는 실제 마이크 해제 소요시간의 실측/합격 기준이 아니다.

## 실제 로컬 실행

Windows/JBR21, Android SDK36/Build Tools35.0.0, 저장소 Gradle Wrapper:

```text
수정 전 baseline: testDebugUnitTest / assembleDebug / lintDebug 성공18s
RED: testDebugUnitTest --tests com.ullim.watch.audio.MicrophoneCaptureTest
     10 tests completed, 3 failed; BUILD FAILED in 7s
GREEN: testDebugUnitTest / assembleDebug / lintDebug
       BUILD SUCCESSFUL in 7s, 53 tasks: 15 executed, 38 up-to-date
       MicrophoneCaptureTest 10 + ContractsTest 6 + CapabilitiesTest 5
       총21 tests, failures0, errors0, skipped0
       Lint0 errors, 4 warnings, 1 hint
```

main의 #4 merge `24a233dd06eb12446197d66070d6572d86c60dd6`를 추가 통합한 뒤 같은 전체 명령도 성공2s(53 tasks, 1 executed/52 up-to-date). 이번 main 통합은 문서/이미지만 추가했으며 수정한 마이크 소스에 차이가 없다. #3 선행 이슈와 함께 #4도 실제 main에 병합되어 있다. GitHub Actions는 사용하지 않았다.

## 실기기 경계

워치에는 누적 #35 앱이 설치되어 있다. 이 PR의 단독 앱은 마이크 자동 시작이나 서비스/UI 연결을 추가하지 않으며 #6의 연결 흐름과 구분한다. 기존 5s756ms의 누적 앱 캡처/중지 기록을 #5의 60초 검증으로 대체하지 않는다. **60초 캡처, 실제 권한 허용/거부·오류 상태 및 정리 확인은 아직 별도 수행할 항목이다.** 소스/호스트 회귀 성공만으로 #5 DoD를 충족했다고 쓰거나 PR을 머지하지 않는다.

## 독립 검토 후 수정

읽기 전용 검토에서 기존 #5 컴포넌트의 추가 Important 3건을 확인하고 한 번의 수정 과정에 반영했다.

1. `AudioRecord.Builder.build()`는 [공식 문서](https://developer.android.com/reference/android/media/AudioRecord.Builder#build())에 따라 미지원 설정/장치 또는 권한 부족에 `UnsupportedOperationException`을 던질 수 있다. 기존 `IllegalArgumentException`만 잡는 경로는 UNPROCESSED 실패 후 VOICE_RECOGNITION을 시도하지 않았다. 실제 Android 어댑터가 사용하는 `openPcmInput` 경계에 소스 선택을 분리하고 두 초기화 예외에서 대체 소스를 시도한다.
2. 권한을 어댑터 진입 시와 각 소스 생성 전에 확인하고, 초기화 예외 뒤에도 다시 확인한다. 권한이 없거나 도중에 회수되면 PERMISSION, 권한이 있지만 양 소스 모두 실패하면 INITIALIZATION을 유지한다. 보통의 장치 실패를 권한 거부로 추측하지 않는다.
3. 이전 세션의 종료 상태 콜백을 전달하기 전에 다음 세션을 허용하던 순서를 고쳤다. 콜백이 반환할 때까지 active를 유지하고, 소유권 해제와 완료 신호를 같은 lock에서 처리한다.

종료 콜백을 latch로 막는 신규 테스트는 수정 전 실제 1개 실패(5s)를 재현했다. 소스 선택을 기존 동작 그대로 분리한 상태에서는 6개 테스트 중 4개가 미지원 예외/권한/대체 소스 누락으로 실패(4s)했다. 이후 전체 테스트·assemble·lint가 성공14s, **28 tests/실패0/오류0/skip0**(Capture11/Factory6/Contracts6/Capabilities5), Lint 오류0/경고4/힌트1. Android 프레임워크 자체를 모킹해 성공을 가정한 검증은 아니다. 소스 선택/권한 콜백 경계의 테스트이며 실제 기기 권한 회수 시험은 여전히 별도다.

검토의 Minor: 기존 factory-stop 테스트의 무제한 latch와 반복 세션 테스트의 무제한 spin은 실패 시 정리 개선이 남아 있다. 이번 생산 코드 수정의 통과 근거로 그 취약한 실패 정리 경로를 사용하지 않았다.

## 누적 앱의 60초 파일럿 한계

현재 설치 소스 `de71ba7`, APK SHA-256 `952d2a0f823b16f2f728bb7bd7a8dfb12dc215478dcec92fd885910e683b84bb`, SM-L300/ZZK1에서 감지를 시작했다. 호스트 경과 3.0/13.1/23.6/32.9/43.3/53.1/63.3초의 각 표본에서 UI는 `소리 감지 중`, AppOps는 RECORD_AUDIO running이었다. 표본 시점의 화면 입력/요청 수치는 모두 0%였다. 사이의 소리 사건이나 연속 PCM을 보관하지 않았으며, 표본만으로 외부 소리 캡처 성공이나 전체 무중단을 단정하지 않는다.

자동 조작의 중지 버튼 이름을 잘못 지정한 탓에 첫 파일럿은 정상 중지 버튼 시험에 실패했고, 안전 정리 경로의 앱 force-stop으로 마이크를 끝냈다. 그 뒤 ListeningService 없음과 AppOps 실행 중 기록 없음, 재실행된 시작 화면을 확인했다. 이는 앱 중지 버튼 결함의 증거가 아니다. 도구의 버튼 이름을 바로잡았으며 정상 버튼 중지·권한 시험은 다시 해야 한다. 이 PR의 새 초기화/상태 순서 수정은 이 설치 앱에 아직 반영되지 않았으므로 파일럿을 해당 수정의 네이티브 통과로 쓰지 않는다.

사용자는 작은/큰 소리 모두 비슷한 짧은 진동이 끊겨 반복되며 소리 유무만 알리는 듯하다고 보고했다. #18의 강도/연속성 결함이 지속된다는 결과로 추적한다. #5의 캡처/상태 테스트 통과와 구분하며 출력 품질을 합격으로 처리하지 않는다.
