# 환경음 분류 안정화 구현 계획 (#20)

> 과거 작업 계획입니다. 당시의 요구사항·체크 상태를 보존하며, 최종 구현 및 검증 상태는 현재 기술 문서를 기준으로 합니다.

> 작업자 지침: superpowers:executing-plans 단계별 적용.

**목표:** YAMNet 상위 5개 점수를 짧게 유효한 안정 문맥과 정확한 한국어 표시로 변환한다.
**구조:** 작업자 소유 순수 Kotlin 안정화 로직은 Classification만 소비하며 오디오·DSP 락을 사용하지 않는다. 제한된 점수 이력과 후보 확인은 감지 단조 시간 기준이다. 빈 점수는 불확실, 만료된 값은 제거. UI·작업자 상태 연결은 #22/#27.
**기술:** 기존 Kotlin/JUnit과 Classification 계약.
**명세:** #20, docs/models.md, architecture.md, 사용자 승인 로컬 우선 접근.

## 공통 제약
- 소리별 고정 진동, PCM 파일, 네트워크, Actions, 합격 기준 변경 금지.
- 모델 원문 및 창 종료·만료 시간 보존. 점수는 보정된 확률이 아니다.
- main 대상 Draft PR Closes #20; 실제 음원 정확도와 Watch7 동작은 미검증.

## 검토 중점
- 같은 결과 반복 읽기는 새 증거나 만료 연장이 아님.
- 순서 역전, 미래, 만료, 다른 세션은 현재 문맥을 대체하지 못함.
- 모호·낮은·무신호 결과에 이전 확정 레이블을 유지하지 않음.
- 재시작 및 긴 입력 공백에서 이력 초기화.
- 영문 원문 대체 표시에서 악기 horn과 차량 경보를 혼동하지 않음.

## 작업 1. 분류 안정화
파일: ai/ClassificationSmoother.kt, ai/SoundLabels.kt, ai/ClassificationSmootherTest.kt, docs/models.md.
입력: Classification(sessionId, timestampNs, validUntilNs, scores).
출력: ClassificationSmoother.accept(result: Classification, sessionId: Long, nowNs: Long): Classification?, current(sessionId: Long, nowNs: Long): Classification?, reset(); SoundLabels.display(label: String): String.
- [ ] 500ms 간격 두 관찰, 교대 레이블, 낮거나 모호하거나 빈 점수, 중복 시각, 만료, 세션 재시작, 공백, 정확한 레이블 시험 추가.
- [ ] 기능 누락 실패 확인.
- [ ] 상위 5개 제한 EMA(새 값 .6, 이전 .4), 최고 점수 >=.35, 차순위 차이 >=.08. 400ms 이상에 걸친 두 개 이상 서로 다른 창에서 후보 확인, 확인 전·불확실 시 빈 점수. 만료 공백·재시작 시 이력 초기화, 입력 만료값 그대로 유지.
- [ ] 전체 testDebugUnitTest/assembleDebug/lintDebug 실행과 결과 검토.
- [ ] 읽기 전용 검토, 독립 브랜치 커밋·푸시, 로컬 근거·기기 미검증을 포함한 Draft PR.

문턱은 초기 개발 선택이며 측정 정확도가 아니다. 실제 음성·경적·노크·잡음 정확성은 표준 음원 및 Watch7 평가에 남는다.
