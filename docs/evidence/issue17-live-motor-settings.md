# 감지 중 진동 설정 변경과 보정 유효성 (#17)

소스 `76d2c81b68fb78135d25faa5d340bc7c75379414` (구현 `edfe8bf31d92973337e99b5340c37d6bfbdb126b` 및 독립 검토 수정). PR73의 기존 모터 보정 후보에 설정 감시를 추가했다. 자연스러운 연속 진동이나 실제 물리 지연의 합격 증거는 아니다.

## 동작과 검토

- 기존 프로필 키에 포함된 System4/Global1의 정확한 URI만 제어 스레드에서 관찰한다. 프로필 로드에 쓴 키로 등록 직후 재검사하며 실패하면 마이크 생성 전에 중지한다. 오디오 스레드에서 설정을 읽지 않는다.
- 감지 중 키 불일치, 읽기 예외 또는 변경 통지가 온 정확한 설정을 확인하지 못하면 기존 감지/AI/진동 종료 경로로 먼저 중지하고 MOTOR_CALIBRATION을 보고한다. 새 세션에 이전 콜백이 영향을 주지 않는다.
- 수치 프로필을 지우거나 형식을 바꾸지 않는다. 별도 no_backup 무효화 표시와 메모리 상태가 이후 로드를 막고, 새 보정 저장과 표시 제거가 성공해야 다시 사용할 수 있다. 표시 저장 실패는 성공으로 취급하지 않는다. 저장 실패 상태의 프로세스 재시작까지 영구 차단을 보장한다는 의미는 아니다.
- 독립 읽기 전용 검토의 P2 두 건을 한 차례 보완했다. SDK getter 예외를 기존 '-' 값으로 숨기던 경로는 checked reader로 분리했다. 예외와 실제 null을 구분하며 기존 정상 키 직렬화는 그대로다. 검증 앱은 옵션 하나만 켜도 반드시 .nativeprobe로 분리되고, 수치 fixture를 쓰기 전 실제 패키지도 검사한다.
- 실제 기기 설정은 읽기만 했다. Global/System 쓰기, 보호 설정 변경, hidden API, root, 원본 PCM/음성 저장·전송, GitHub Actions 사용은 없다.

## 로컬 검증

처음 getter 수준의 두 회귀 테스트가 실제 실패했다(10개 중2실패). 분리 옵션을 생략한 검증 빌드가 기존 패키지명을 사용함도 APK 검사로 재현했으며, 그 APK는 설치하지 않았다.

수정 후 실행:

```text
:app:testDebugUnitTest :app:assembleDebug :app:lintDebug -PnativeCaptureProbe=true
236 tests, failures/errors/skips=0, BUILD SUCCESSFUL 61s

:app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:assembleRelease
236 tests, failures/errors/skips=0, BUILD SUCCESSFUL 71s
```

서비스 테스트에는 실제 서비스의 가짜 PCM 공급 후 취소/해제/재시작, 사전 검사 실패 시 마이크 미생성, 오래된 콜백의 다음 세션 보호를 포함한다. 이는 실제 AudioRecord·물리 모터 검증과 구분한다. 정상 debug/release 패키지는 com.ullim.watch이며 manifest와 DEX에서 NativeMotorSettingsProbeActivity 부재를 확인했다.

## Watch7 읽기 전용 검증

SM-L300/API37/L300XXU2ZZK1. 별도 검증 APK SHA256 `ee1c69b8c937e626afa427c9e4cc71ec18aa1217cbae26f9e0bbf2805e4ff87c`; 설치 base.apk 해시 일치. PID4634, 2026-11-14 06:43:29–34 KST. [숫자 결과 로그](issue17-settings-watch7.txt).

- 다섯 URI 등록 및 초기 키 재검사 성공. 5초 안정 상태 뒤 해제 반환 성공, result=PASS/5286ms.
- 현재 키 SHA256 `5e9c1f9694026d1e53d973352e916e5b101d67b879361222c61b507533dd4775`가 원래 수치 보정 파일의 헤더 키 해시와 일치한다. 기기 식별 문자열 자체는 로그에 기록하지 않았다.
- 검증 앱 자체 공간의 생성한4262-byte 수치 fixture로 무효화→로드 거부→기존 수치 바이트 유지→새 보정 저장 후 로드 복구를 확인했다. 원래 앱 보정은 사용하거나 덮어쓰지 않았다.
- 마이크·모터 미사용, 설정 쓰기 없음. RECORD_AUDIO는 ignore였다. 최종 PASS 확인 후 검증 앱만 제거했다.
- 원래 앱 보정4262bytes/SHA256 `47a0c31418640333f4624240f37690f410d70423750ad7bb70675866b751f06c`, 화면 유지1, 글씨1.0 유지. 원래 앱의 무효화 표시는 생기지 않았다.

## 정상 앱 업데이트 설치

기존064dd0d APK 백업과 설치 해시, 마이크/ListeningService 중지, 원래 보정 해시를 먼저 확인했다. 정상 debug APK52838087bytes/SHA256 `4b206f345b6660cdb89e23d57bc1b3965ac2970993f8ecc8abe6d08153a3efaa`를 install-r했고 Success 및 설치 base.apk 해시 일치를 확인했다. 원래 앱 삭제/데이터 초기화는 하지 않았다. 기존064d와6a2e APK는 계속 별도 보존한다.

MainActivity 시작은 Status=ok/WaitTime2019ms였으며 cold launch로 해석하지 않는다. 보정47a0c314…f06c/무효화 표시 없음/화면 유지1/글씨1.0을 재확인했다. 마이크 사용 시각은 여전히3시간 전, 활성 ListeningService 없음, 설정 저장 파일 없이 files/profileInstalled만 유지했다. AI 토글·민감도 변경·감지 자동 시작은 하지 않았다. 실제 착용 회귀는 아직이다.

## 남은 실제 검증

사용자가 직접 진동 설정을 바꿨을 때 활성 감지가 즉시 끝나고 재보정 전 재시작을 거부하는 양성 시험은 아직 수행하지 않았다. 위 워치 시험은 등록/안정 재검사/해제와 분리된 수치 저장소 시험이다. 실제 강약·지속·리듬, 연속 진동 질감, AI ON/OFF 착용 비교와 물리 P95 지연은 여전히 남는다. 선행 #16도 미병합이므로 이 단위만으로 PR73/#17을 머지·종료하지 않는다.
