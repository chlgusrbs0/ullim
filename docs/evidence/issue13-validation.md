# #13 표준 출력 백엔드 — 로컬 통합 완료/물리 검증 대기

선행 #4/#12가 머지된 main8398b0a를 기존 PR51/브랜치에 통합했다. 원래14239e9 구현을 보존하며,통합 후 main과의 코드 차이는 AndroidWaveformDevice/WaveformRenderer/테스트3개 파일139줄뿐이다. 검증 소스998897fe301ccc1f03502c6fda904bd017a631dc.

- Windows/JBR21/SDK36,일반 전체90 tests,failures/errors/skips0. 로컬 test/assemble/lint 성공27초.
- 상한217/255와 유한한 길이,미지원 모터,기본 강도 폴백,오래된 세션/중복 시각/중지 후 명령,무음 및 오류 취소 검사 통과.
- 독립 읽기 전용 전체 변경 검토에서 actionable finding 없음. 공통 HapticFrame이 강도 유한성0..1,길이1..1000ms,유효 식별자를 검증하고,표준 effect는 repeat=-1로 생성한다. SENT는 제출 결과이며 실제 모터 출력 보장이 아니다.
- GitHub Actions 미사용. 실제 마이크·진동·사용자 설정 변경 없음.

AndroidWaveformDevice Git blob `e725c99df4511c041164b3d48a51c3a8b0901e27`은 현재 설치된 개선본3abd46b와 정확히 같다. 그러나 후속 개선본의 WaveformRenderer에는 배치/강제 폴백 확장이 있으므로,그 개선본에서 얻은 과거 사용자 체감을 PR51 전체의 새 검증으로 대신하지 않았다.

현재 Watch7은 강도 API 지원을 보고하지만,같은600ms의 값1/255가 비슷하게 느껴지고 짧은 명령 갱신은 거칠었다는 기존 결과가 있다. [기기 한계 기록](issue4-watch7.md)을 유지한다. 이 원시 표준 백엔드가 강약·연속성을 해결했다고 주장하지 않는다.

현재 별도 AI 장시간 시험이 워치를 사용 중이므로 이 후보의 새 물리 출력/중지 시험은 아직 수행하지 않았다. 약/중/강/리듬 명령 로그,실제 중지 뒤 잔여 진동,지원 제한 표시와 사용자 확인이 남아 있다. PR51은 Draft,#13은 open을 유지한다. #17/#18/#37의 실제 출력 품질·지연·사용자 평가도 별도 게이트다.

재현: `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug`. 워치 시험은 기존 앱/보정값을 보존한 별도 후보로 진행하고,명령 제출·OS 종료·손목의 실제 종료를 구분해 기록해야 한다.
