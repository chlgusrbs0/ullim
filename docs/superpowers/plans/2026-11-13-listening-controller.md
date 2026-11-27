# 감지 컨트롤러 구현 계획 (#23)

> 과거 작업 계획입니다. 당시의 요구사항·체크 상태를 보존하며, 최종 구현 및 검증 상태는 현재 기술 문서를 기준으로 합니다.

> 작업자 지침: superpowers:executing-plans 단계별 적용.

**목표:** 관찰 가능한 단일 세션 상태 머신, 배타적 마이크 소유권, 안전한 롤백 및 즉각적인 중지.
**구조:** ListeningController를 CaptureRuntime에서 분리한다. CaptureRuntime은 입력·출력 수준 및 설정 전달을 담당한다. 토큰으로 상태·소유권을 관리하고 소유자 해제 전까지 중지 중 상태를 유지한다. 해제 이후에만 오류 재시도를 허용한다. ForegroundSession은 마이크 중지 요청 전 DSP·AI·진동을 취소하며 네이티브 시작/중지/해제를 직렬화하되 호출자와 분리한다.
**기술:** 기존 Kotlin, RuntimeState/ListeningState/AiStatus, StateFlow, AudioRecord 어댑터, JUnit 래치.
**명세:** #23, architecture.md, 사용자 승인 #60 이후 코드·로컬 우선 검증.

## 공통 제약
- 공통 데이터 계약과 단일 마이크 소유자 유지.
- 메인/UI 시작·중지는 차단된 네이티브 초기화·중지를 기다리지 않는다.
- 이전 네이티브 자원 소유 중 새 감지 금지. 늦은 이벤트가 종료 상태를 되살리거나 새 토큰을 변경하지 않는다.
- DSP·AI·진동 취소는 모델 완료 및 마이크 정리와 독립적이다.
- Actions·PCM 저장·기기 성공 주장 금지. main 대상 Draft Closes #23.

## 검토 중점
- 동시 시작 요청에서 토큰 하나만 허용.
- 실제 해제 전 종료 통지가 대기 상태를 노출하거나 새 마이크를 허용하지 않음.
- 이전 소유자·토큰·상태·오류 콜백은 새 세션에 영향 없음.
- 네이티브 시작·중지 차단은 호출자 반환과 진동 취소를 늦추지 않음.
- 반복 중지·롤백이 새 세션에 이전 정리를 재실행하지 않음.

## 작업 1. 상태와 소유권
파일: runtime/ListeningController.kt, ListeningControllerTest.kt 생성, runtime/CaptureRuntime.kt 및 서비스 연결 수정. 공개 공통 타입 유지.
입력: RuntimeState/ListeningState/AiStatus.
출력: ListeningController.state, requestStart(): Long?, isStarting(id), isListening(id), attach(id, owner: Any): Boolean, detach(owner), listening(id), stopping(id), finished(id), fail(id: Long?, message), aiStatus(id,status). 변경 메서드는 토큰·전이 수락 여부를 반환한다.
- [ ] 동시 시작, 연결 전 중지, 종료·해제 장벽, 오류·재시도, 이전 콜백, 출처 없는 UI 오류 실패 시험.
- [ ] 기능 누락 실패 확인 후 소유자 동일성 및 단조 증가 토큰을 사용한 동기 전이 구현.
- [ ] 표시 수준과 설정은 CaptureRuntime 유지, 상태 조작 위임. attach에 명시 토큰 전달, 모든 호출자·시험 갱신. PCM·진동 공통 계약 변경 금지.

## 작업 2. 즉각적이며 순서가 보장된 정리
파일: audio/MicrophoneCapture.kt, runtime/ForegroundSession.kt, 실제 가짜 입력 생명주기 시험 확장.
- [ ] 차단된 네이티브 시작·중지가 호출자 반환과 출력 취소를 늦추는 문제 재현.
- [ ] 마이크 중지를 멱등 원자 요청 및 실행별 짧은 중지 제어 스레드 하나로 구현. 실행 모니터에서 네이티브 시작·중지·해제 직렬화, 호출자 join 금지. 정리 완료까지 소유권 유지.
- [ ] ForegroundSession에서 DSP·AI·진동 취소 후 감지 중지 요청·알림 제거. 승격 실패 롤백 포함 멱등성 보장.
- [ ] 차단 입력 시험에서 입력 해제 전 출력 취소·중지 반환, 정리 전 새 세션 거부, 해제 1회, 중지 후 늦은 Recording 없음 확인.
- [ ] 전체 testDebugUnitTest/assembleDebug/lintDebug, 읽기 전용 검토, 중요 회귀 수정, 독립 브랜치 및 로컬 근거 포함 Draft PR.

실제 기기 ADB 반복, OS 경쟁 중 자원 해제, 서비스 종료 후 화면 복귀, 물리 출력 중지는 Watch7 연결 전 미검증이다.
