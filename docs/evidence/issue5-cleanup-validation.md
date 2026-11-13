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
