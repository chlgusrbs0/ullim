# #5 워치7 단일 마이크 실제 검증

## 실행 대상

- 소스 `1d5cefead363a7ddae0fd550200a2ae72edbfc5b`, main `43be302` 통합. 선행 #3 병합 완료.
- SM-L300 / Galaxy Watch7, Android 17 API37 / One UI Watch 베타 `L300XXU2ZZK1`, 2026-11-14 KST.
- 실기기 시험은 이 PR의 정확한 `MicrophoneCapture`와 `AndroidPcmInput`을 직접 호출했다. 누적 #17 앱의 결과를 대신 사용하지 않았다.
- 시험 APK SHA-256 `2ba6d85715a4d2663653441301fd7a98fa5d5041aea283d50c7be4627a168e86` (25,932,956 bytes). 기기 내 설치 APK의 해시도 일치했다.

## 재현과 제품 빌드 경계

```text
./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug -PnativeCaptureProbe=true
adb -s WATCH_SERIAL install -r app/build/outputs/apk/debug/app-debug.apk
adb -s WATCH_SERIAL shell am start -W -a com.ullim.watch.PROBE_CAPTURE \
  -n com.ullim.watch/.probe.NativeCaptureProbeActivity --el durationMs 60000
```

원래 허용돼 있던 RECORD_AUDIO를 `pm revoke`하여 거부 케이스를 확인하고, 원래 허용 상태로 `pm grant`한 뒤 같은 후보로 60초 케이스를 수행했다. 거부 세션의 종료/미실행을 확인한 뒤 프로세스를 정리해 두 번째 세션을 시작했다. 이 정리를 정상 캡처 중지의 증거로 사용하지 않았다.

프로브는 명시적 실행 요청과 실제 포커스/전경 상태가 모두 있어야 시작한다. 1~60초로 제한하며 화면 가림·pause·버튼·타이머는 모두 같은 stop 경로로 들어간다. 서비스/진동 출력은 추가하지 않았다. 원본 PCM은 기존 컴포넌트의 메모리 처리만 하고, 프로브가 보관하는 값은 프레임/샘플 수·RMS 최소/최대·시각 연속성 카운터다. 원음 파일/배열 로그/네트워크 전송은 없다.

기본 debug와 release는 이 추가 소스·manifest를 포함하지 않는다. **일반 debug의 merged manifest와 DEX에서 프로브가 없음**, 시험 옵션을 켠 release의 manifest와 DEX에서도 없음, 시험 APK의 DEX에는 있음으로 양·음성 대조했다. 일반 debug 해시 `eeaf7a151cc63f044b3949aee3e7dbdfa4222ab5353197d2afb39a9e2ec669bb`; unsigned release 해시 `e4f6e996ad4c21a6ee6ff48ba8be4b692b5a04e5c1bf7331de1cef3dbbce7730`.

## 로컬 검증 및 검토

- 초기 프로브 전체 기존 30 tests/assemble/lint 성공34초.
- 독립 읽기 전용 검토: 생산 캡처/PCM 개인정보 경로의 추가 코드 지적 없음. 시험 화면의 포커스 상실 중지 누락 Important 1건은 수정했다.
- pause 없이 포커스만 잃는 회귀: 수정 전 실제 1 test 실패17초 → 수정 후 프로브 포함 **31 tests, 실패/오류/skip 0**, assemble/lint 성공46초.
- 최종 일반 debug 전체 **30 tests, 실패/오류/skip 0**, assemble/lint 성공33초. 프로브 전용 테스트는 시험 옵션에서만 포함한다.
- 시험 옵션을 켠 release assemble 및 기존 전체 검증 성공44초. release에는 시험 코드가 없다. GitHub Actions 미사용.
- 기존 초기화 실패·음수 read·권한 회수 경계·중지/해제 블로킹·해제 실패 소유권 유지·이전 종료 상태 순서·소스 폴백의 실패 주입은 [이전 기록](issue5-cleanup-validation.md)을 따른다. 실제 장치를 강제로 고장내어 초기화/read 실패를 만들었다고 주장하지 않는다.

## 실제 결과

| DoD/사건 | 관측 |
| --- | --- |
| 권한 거부 | 03:12:17 `Starting → Failed(PERMISSION)`. 0 frames/0 samples. native 객체 생성 전에 거부돼 opens(시도)=1/releases=0, done=true/leaseReleased=true. AppOps ignore, running 없음. |
| 권한 허용·형식 | 03:12:41 Recording. 프레임워크 client format 16000Hz / channels=1 / encoding=2(PCM16), source=6(VOICE_RECOGNITION), silenced=false. 이는 client 형식이며 하드웨어 원래 sample rate와 혼동하지 않는다. |
| 실제 주변음 | 첫 205ms에는 RMS 최대0, 10.219초 표본에는 최대0.3482424로 변함. 사용자가 시험 중 가까이에서 박수 친 것을 확인했다. 정밀한 박수 발생 시각이나 물리 지연을 측정한 것은 아니다. |
| 60초 수집 | 타이머 요청 elapsed=60007ms, 2995 frames / 958400 samples(59.9초 분량). 10초마다 client silenced=false 및 프레임 증가, 마지막 Stopped. |
| 정상 중지·해제 | 03:13:41.078 stop(timer), .131 Stopped, .138 done=true/leaseReleased=true, opens=1/releases=1. 이후 프레임워크 AppOps running 없음, 사용 시간60.067초. force-stop으로 종료하지 않음. |
| 중복 시작 | Recording 상태에서 같은 캡처에 두 번째 start를 요청해 duplicateAccepted=false. |
| 같은 프로세스에서 재시작 | Activity 종료 후 동일 PID의 새 Activity에서 1초 수집. 45 frames/14400 samples 후 Stopped, opens=1/releases=1, done/leaseReleased=true, AppOps running 없음(1.052초). |

`timestampGaps=0`은 샘플 기반 프레임 시각 계약 검사다. 독립적인 하드웨어 timestamp/드롭 검출이 아니며 모든 물리 오디오 무손실이나 촉각 지연 합격을 뜻하지 않는다. 실제 경과·샘플 총량·프레임워크의 입력 및 해제 관측을 함께 기록했다. 60초 시작 직후의 무음 샘플을 통제된 무음 환경으로 오해하지 않는다.

선별된 수치 로그는 [native 로그](issue5-watch7-native.txt)에 있다. 기기 연결 주소, 원음, 다른 앱 로그는 포함하지 않는다.

## 원래 상태 복원

시험 뒤 기존 개선 APK `6a2e6e16a634fd59d5dbce19ce72856c3dd5ff632df2f4677fe14474e853006c`를 재설치하고 기기 내 해시 일치를 확인했다. 보정 파일은 4,262 bytes, SHA-256 `47a0c31418640333f4624240f37690f410d70423750ad7bb70675866b751f06c`로 시험 전과 같다. 기존 RECORD_AUDIO/POST_NOTIFICATIONS 허용·USER_SET 상태를 복원했다. global setting_vibration_intensity는 읽기만 했고3 그대로다. 마이크 AppOps가 실행 중이 아니고 ListeningService도 없는 상태로 메인을 열었다.

#5 캡처/해제 DoD를 충족한 결과이며, #17/#18의 매끈한 강약·지속시간 촉각화나 물리 지연 및 최종 인수는 별도다.
