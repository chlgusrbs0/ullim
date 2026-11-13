# #7 오디오 팬아웃 검증 진행

선행 #3·#5는 main에 병합됐다. 사용자 재배정에 따라 기존 PR45를 인계해 main `7353dcfe9ce015b9317098459ac428bd73d60b12`를 통합했다. 통합 소스 `ab62db07b14a2bb1418e3d3c2684e36949c61208`에서 기존 오디오 팬아웃 코드는 변경하지 않았다.

2026-11-14 KST Windows/JBR21, SDK36, 저장소 Gradle Wrapper로 실행:

```text
./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --console=plain
BUILD SUCCESSFUL in 33s
36 tests, failures/errors/skips 0
AudioFanOutTest: 6 tests
10-minute synthetic PCM: DSP frames=30000, desktop processing=132ms
```

합성 입력의 10분 분량은 가속 처리한 것이며 실제 경과 10분이나 워치 처리 속도를 뜻하지 않는다. 기존 테스트는 partial 조립, 오래된 세션/중복 시각 거부, 시간 불연속/재시작 초기화, AI 소비 스레드가 스냅샷을 쓰는 동안 DSP 진행, 소비하지 않는 AI의 최신 창 교체를 확인한다. 고정 배열과 단일 최신 슬롯은 소스에서 확인할 수 있지만 이 로그만으로 실제 워치 heap 증가 없음이나 2초 내내 AI를 막은 실기기 시험을 통과했다고 주장하지 않는다.

위 기록은 최초 통합 시점의 미완료 상태다. 이후 다음 검증을 추가 완료했다. GitHub Actions·원음 저장/전송은 사용하지 않았다.

## 최종 검증 — d5368e0

생산 AudioFanOut은 기존 구현을 유지했다. 검증 코드를 보완해 AI 스냅샷을 실제 2초 이상 보유한 뒤 패턴·세션·시각이 그대로인지 검사하고, 자식 스레드의 실패를 호출자에게 전달한다. 메모리는 stop/최신 창 제거 전에 같은 활성 세션 안에서 측정한다.

- 일반 debug 전체 test/assemble/lint: 성공21초, 36 tests/실패·오류·skip 0.
- 별도 설치형 opt-in debug: 전체 37 tests/실패·오류·skip 0, assemble/lint 성공29초. 호스트 AI 보유2016ms.
- release 최초 전체 빌드 성공32초, 최종 시험 옵션을 켠 release 재조립 성공2초.
- 일반 debug/release의 manifest와 DEX에서 NativeFanOutProbeActivity 부재, 시험 APK에는 존재함을 대조했다. 두 옵션 `-PnativeCaptureProbe=true -PnativeProbePackage=true`에서만 별도 패키지 `com.ullim.watch.nativeprobe`로 빌드했다.
- 시험 APK: 25,844,420 bytes / SHA-256 `c249ac52a85a856b6b92aee949acb1d2a9697d50c6f61c6b5d8fbe2d122b19d1`. 워치 설치 파일의 해시도 일치했다.
- 일반 debug SHA-256 `8ad4e91f8227ec51ab4b085a7a256fe48ef0aa61cd80f848a475c7937406249a`; unsigned release `b01c9290c9b1974ba41cf47bb5292bb5a2def8e5c8976e20674418532994738f`.

독립 코드 검토에서 생산 경로의 차단 결함은 확인되지 않았다. 검증 도구 검토의 Important 2건(보유 뒤 스냅샷 검사 및 종료 전 메모리 측정)을 한 번의 보완 과정에서 반영한 후 위 최종 후보로 재검증했다. 초기 도구의 종료 후 heap 측정/보유 전 검사 결과는 최종 근거로 사용하지 않았다. 콜백 안에서 start/stop을 동기 재진입하는 조건부 보완점은 현재 서비스 호출 경로에서 사용하지 않는 것으로 검토됐다.

## Galaxy Watch7 자동 검증

SM-L300 / Android17 API37 / L300XXU2ZZK1, 2026-11-14 KST. 별도 시험 앱은 합성 PCM만 생성하며 실제 마이크와 진동을 호출하지 않는다. 사용자 착용/음향 정확도 시험을 대신하지 않는다.

| 측정 | 실제 결과 |
| --- | --- |
| 활성 세션의 연속 합성 입력 | 누적30000/60000/90000/120000 DSP frames, 제출한 프레임 누락 없음. 10분 분량씩4회 가속 처리. |
| 활성 세션 Java heap | 강제 GC 요청 후 네 체크포인트 모두3,649,888 bytes, 첫→마지막 증가0 bytes. 최신 AI 창을 소비하지 않고 세션을 유지하며 측정. |
| 처리 경과 | 첫 burst3846ms(초기 클래스/JIT 준비 포함), 이후1417/1468/1421ms. GC 대기 포함, 물리 촉각 지연이 아님. |
| 실제2초 AI 정체 | 같은 patterned snapshot을2093ms 보유·보유 후 내용/세션/시각 검사 통과. 그동안 DSP96프레임 진행, 제출 수와 출력 수 일치. |
| 큐·재시작 | 최신 창15600샘플 하나만 소비 가능, 다음 소비null. 이전 세션 입력 거부, 부분/AI 데이터 혼입 없음, 중지 후 창null. |
| 종료 | result=PASS. 시험 패키지 마이크 AppOps ignore, 마이크 미사용. |

메모리 증가4MiB 미만은 이 고정된 합성 시험의 개발 검증 한계다. 실제 관측은0이며 장시간 실제 마이크/AI 모델/PSS/발열 검증의 합격을 뜻하지 않는다. 40분은 합성 입력의 길이이고 벽시계40분 시험이 아니다. 수치 로그: [issue7-watch7-native.txt](issue7-watch7-native.txt).

별도 시험 패키지는 검증 뒤 제거했다. 기존 앱 APK `6a2e6e16…3006c` 및 보정값 `47a0c314…f06c`의 해시가 그대로이고 마이크도 실행 중이 아님을 확인했다. 사용자가 바꾼 충전 중 화면 유지 값1을 시험 전후 읽기만 하고 보존했다. 기존 울림 메인 화면으로 복귀했다.
