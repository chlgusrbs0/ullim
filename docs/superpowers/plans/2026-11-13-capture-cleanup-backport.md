# 마이크 정리 수정 이식 계획

> 과거 작업 계획입니다. 당시의 요구사항·체크 상태를 보존하며, 최종 구현 및 검증 상태는 현재 기술 문서를 기준으로 합니다.

> 작업자 지침: 기존 이슈 수정은 superpowers:executing-plans로 직접 수행하고 최종 읽기 전용 검토 1회.

**목표:** #23에서 재현한 네이티브 중지 차단·해제 실패 소유권 결함을 독립 #5 PR에 이식한다.
**구조:** PcmInput, AudioFrame, MicrophoneCapture 공개 시그니처 유지. 중지 요청을 한 번 표시하고 호출자를 기다리게 하지 않는 직렬 네이티브 정리 예약. 해제 실패 시 활성 실행을 유지하고 두 번째 녹음기를 거부한다.
**기술:** Kotlin/JUnit4, 기존 Android/Gradle, 새 의존성 없음.
**명세:** #5, AGENTS.md, #23의 #5 조정 댓글.

## 공통 제약
- 16kHz mono PCM16, 단일 소유자, 메모리 내 PCM만 사용.
- Actions 금지; 공통 계약·서비스/DSP/AI/UI 정책·최종 하드웨어 기준 변경 금지.
- 설치된 워치 앱과 예정 리듬 시험 보존; 로컬 검증 후 기기 시험.
- 실제 60초 감지·권한·정리 및 선행 병합 전 PR44 Draft 유지.

## 검토 중점
- 네이티브 시작·중지가 차단되어도 호출자는 반환하고 두 번째 소유자는 거부.
- 해제 실패는 CLEANUP 보고 및 새 감지 거부 유지.
- 반복 중지·시작과 생성·소비자 실패 회귀 보존.
- 호스트 시간 검사는 호출자 차단만 확인하며 물리 지연이 아님.
- 실제 Android 권한·초기화와 60초 감지는 기기 조건.

## 작업 1. #5 정리 보장 이식
파일: audio/MicrophoneCapture.kt, audio/MicrophoneCaptureTest.kt, docs/evidence/issue5-cleanup-validation.md.
입출력: 기존 start(sessionId): Boolean, stop(), awaitStopped(timeoutMs): Boolean, 타입별 감지 상태 유지.

- [x] 깨끗한 독립 복제본 확인, 현재 main 병합, 기준 시험/빌드/린트 성공 18초.
- [x] 네이티브 시작·중지 차단 중 중지와 해제 실패 소유권 유지 회귀 추가. 래치 및 finally 정리로 실제 호출자 반환과 두 번째 녹음기 거부 확인.
- [x] `:app:testDebugUnitTest --tests com.ullim.watch.audio.MicrophoneCaptureTest` 실행. 수정 전 10개 중 3개 실패, 7초.
- [x] #23 원자적 중지·직렬 비동기 네이티브 정리·해제 실패 소유권 유지 이식. 다른 이슈 API 미도입.
- [x] 전체 `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug` 실행, 21개 시험 실패/오류/건너뜀 0개, 7초. 최신 main 문서 병합 재검사 2초 성공.
- [ ] 커밋·읽기 전용 검토 및 기존 Draft PR44 갱신. 예정 리듬 시험 이후 워치 검증. 조기 이슈 종료 금지.

결정: 독립 #5 브랜치의 기존 깨끗한 work/ullim-ui-check 복제본을 사용하여 #35 및 #4 검토 작업공간 보존. 새 worktree·구현 에이전트 불필요.

최종 검토: 정리 이식은 타당. 전체 #5 변경의 중요 입력 대체·권한 원인·종료 상태 순서 결함을 수용했다. 한 차례 수정으로 PcmInputFactory.kt/PcmInputFactoryTest.kt 및 감지 순서 회귀 추가. 수정 전 순서 1/1 실패, 생성 4/6 실패. 최종 28/28 성공, assemble/lint 성공 14초. 새 의존성·공개 계약 변경 없음.

보류한 경미 항목: 이전 factory-stop 시험의 무제한 래치와 반복 세션 대기 루프는 실패 시 수동 정리가 필요할 수 있다. 제품 의미에는 영향 없음.

네이티브 예비 시험: 누적 이전 설치본 35source는 3~63초 감지 유지. 도구의 중지 레이블 오타로 강제 중지 정리. 새 후보·일반 중지·권한 합격 주장 없음. 사용자 체감은 이진적이고 끊기는 진동이므로 검증된 로컬 수정 공개 후 #18 품질 개선 우선. PR44는 당시 Draft 유지.
