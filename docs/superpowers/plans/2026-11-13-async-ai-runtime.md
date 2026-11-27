# 비동기 AI 실행 구현 계획 (#22)

> 과거 작업 계획입니다. 당시의 요구사항·체크 상태를 보존하며, 최종 구현 및 검증 상태는 현재 기술 문서를 기준으로 합니다.

> 작업자 지침: superpowers:executing-plans를 단계별 적용.

**목표:** DSP 및 사용자 중지를 막지 않는 선택적 오프라인 AI 소비자를 연결한다.

**구조:** 프로세스 소유 AiWorker는 비동기 실행 경로 하나와 최신 대기 PCM 창 하나를 사용한다. 네이티브 로딩·추론·닫기는 짧은 상태 락 밖의 해당 경로에서만 실행한다. 세대 검사로 이전 세션·토글 결과를 무효화한다. 시간 제한은 추론과 독립적으로 확인하며 당시 계획에서는 오류 후 수동 OFF/ON 전 자동 재시도하지 않는다. 서비스는 오디오 분배, 하이브리드 매핑, 주기 검사 및 AI 상태를 연결한다. 별도 화면에서 #28 저장 구현 전까지 프로세스 내 ON/OFF를 제어한다.

**기술:** 기존 Kotlin/Wear Material3, Executor, StateFlow, AndroidYamnet, ClassificationSmoother.

**명세:** #22, architecture.md/models.md, 사용자 승인 로컬 우선 검증. #24 PR59의 UI 기반은 재생성하지 않는다.

## 공통 제약
- 기존 공통 계약, DSP 우선, 명시적 감지 시작 및 원음 개인정보 원칙 유지.
- 추론과 동시에 인터프리터를 닫지 않으며 DSP/UI에서 동기 대기하지 않는다.
- 프로세스 작업자·실행 경로 하나, 대기 창 하나. OFF/새 세션은 결과를 무효화하고 비동기 정리를 예약한다.
- Actions 금지. 필수 근거 없이 기기·지각·성능 성공 또는 main 병합 금지.
- OFF/로딩/감지/지연/실패를 레이블과 구별하고 추론 중지 시에도 만료 검사.

## 검토 중점
- 반환하지 않는 네이티브 호출이 오디오를 막거나 토글 시 대체 작업자를 누적시키지 않아야 한다.
- 이전 세대 결과·오류는 새 세션에 영향 금지.
- OFF는 문맥 즉시 제거; 진행 중 추론 후 정리 대기를 정직하게 표시.
- 시간 초과·생성·추론 실패는 AI에만 영향; 당시 자동 재시도 금지.
- 종료·비활성 요청·잘못된 세션·이전 창은 PCM을 보존하거나 AI를 되살리지 않는다.

## 작업 1. 순수 작업자와 실패 주입
파일: ai/AiWorker.kt, ai/AiWorkerTest.kt.
입력: SoundClassifier, AudioFrame, ClassificationSmoother, 비동기 Executor, 단조 시계.
출력: AiWorker(factory: () -> SoundClassifier, executor: Executor, clockNs: () -> Long), state: StateFlow<AiSnapshot>; start(sessionId, enabled): Boolean; setEnabled(sessionId, enabled); offer(frame): Boolean; tick(); current(sessionId, nowNs): Classification?; stop(sessionId).
AiSnapshot은 세션·단계·오류·분류, 로딩/작업/정리 상태, 로딩·추론 시간/횟수, 폐기 창 수를 제공하고 PCM은 제공하지 않는다.
- [ ] 실제 실행기·래치로 차단 추론, 최신 창만 유지, 독립 DSP, OFF 종료 순서, 빠른 토글, 이전 세대 및 세션 중지, 시간 초과·자동 재시도 없음, 생성·추론 실패, 지연·만료, 닫기/실행 거부 정리를 시험한다.
- [ ] 대상 시험을 실행하여 기능 누락 실패 확인.
- [ ] 직렬 실행 경로와 작업자 단독 네이티브 소유, 짧은 동기 상태 전이 구현. 대기 창 하나, 오래되거나 순서/크기가 다른 프레임 폐기. 입력 간격 500ms, 지연 표시 500ms, 추론 기한 1초, 로딩/이전 작업 10초. 반환하지 않는 작업은 점유·정리 대기로 남기고 강제 취소나 새 작업자 생성 금지. 닫기 실패는 프로세스 재시작 전 모델 로딩 차단.
- [ ] 통합 전 전체 단위 시험 실행.

## 작업 2. 서비스·상태·사용자 제어
파일: ai/AiRuntime.kt, AiControlsActivity.kt 생성; ListeningService.kt, runtime/CaptureRuntime.kt, MainActivity.kt, 매니페스트와 문자열 수정. #59 UI 패키지는 유지.
입력: 작업자 API, fanout.takeLatestAi(), HybridHapticMapper, 기존 세션 생명주기.
출력: 선택적 모델 소비, 프로세스 AI 토글, 실제 상태 및 메타데이터만의 기록.
- [ ] 감지 콜백에서 먼저 오디오 분배 후 최신 AI 창 제공; DSP 매핑은 원자적 문맥 읽기. 토글·주기 검사는 네이티브 대기 없이 처리.
- [ ] 사용자 시작 활성 세션에서만 작업자 시작; 정확한 토큰으로 중지하고 모든 종료·오류에서 문맥 제거. 감지 차단 없이 상태 관찰, RuntimeState 의미 유지.
- [ ] exported=false AI 제어 Activity 및 명시적 진입 버튼. 지연·실패·정리 상태 표시. #28 전 저장 주장 금지.
- [ ] testDebugUnitTest/assembleDebug/lintDebug, 매니페스트·자산·권한 검토. 원음 없이 Watch7에서 5분 ON/OFF CPU·메모리·발열·시간 수집 방법 문서화.
- [ ] 읽기 전용 검토, 중요 발견 사항을 실패 회귀 시험으로 수정, 커밋·푸시, main 대상 Draft PR Closes #22 및 미측정 범위 기록.

Watch7 모델 실행, 안정적 발열·메모리 및 네이티브 취소 한계는 실제 기기 검증 조건이다. 측정 전 더 작은 모델 변경을 정당화하지 않는다.
