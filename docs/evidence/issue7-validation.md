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

현재 워치는 #17의 개선 APK와 기존 보정값으로 복원돼 있다. #7 후보는 아직 워치에 설치하지 않았고, 별도의 native 부하·메모리 증거 및 독립 코드 검토 결과를 완료 판정에 반영해야 한다. PR45는 이슈를 닫지 않고 Draft로 유지한다. GitHub Actions·원음 저장/전송은 사용하지 않았다.
