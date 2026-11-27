# 메인 감지 화면 구현 계획 (#25)

> 과거 작업 계획입니다. 당시의 요구사항·체크 상태를 보존하며, 최종 구현 및 검증 상태는 현재 기술 문서를 기준으로 합니다.

> 작업자 지침: superpowers:executing-plans 단계별 적용.

**목표:** Wear 메인 시작·중지 화면을 실제 컨트롤러·서비스 상태에 연결하고 내비게이션·재생성 후 복원한다.
**구조:** #59 Wear 테마·화면·내비게이션을 #61 런타임과 통합한다. MainActivity는 권한·서비스 명령을 유지하고 ui/UllimApp은 불변 실제 상태와 콜백을 받는다. 낙관적 감지 플래그를 사용하지 않는다. 별도 읽기 전용 시작 가능 상태 흐름으로 오류 값이 같아도 재시도 가능성을 갱신한다. 설정·권한은 메인 제어 스레드, 오디오·AI는 독립 실행.
**기술:** Wear Material3/Compose, lifecycle-runtime-compose 2.9.2(Kotlin2.0 호환), Compose UI 시험, Robolectric 4.17 SDK34 원형 192dp, 공식 JBR21 플래그. 앱 네트워크 의존성 없음.
**명세:** #25, #23 session-lifecycle.md, #24 PR59, 사용자 승인 코드·로컬 우선. 기기 인수 조건 유지.

## 공통 제약
- 가짜 remembered 감지 상태, 중복 마이크 세션, 재생성 시 자동 마이크 재시작 금지.
- 공통 계약, 실제 오류 문구, 개인정보, 출력 제한 및 권한·서비스 제약 유지.
- 시작/중지 중심, 진행 중 비활성 상태와 시작 취소 제공. 오류·권한 대응 진입점 제공.
- Wear Material3 컨트롤만 사용하고 원형 스크롤·뒤로 가기·제목 의미 유지.
- Actions 금지. Robolectric은 호스트 근거이며 Watch7 설치·터치·물리 동작 증거가 아니다.

## 검토 중점
- 시작·중지 조작은 현재 토큰의 Intent를 한 번 전달하며 이전 토큰이 새 세션을 취소하지 않음.
- 재생성 및 안내·설정 복귀 시 실제 감지 상태 유지, 새 서비스 시작 없음.
- Error 값이 같아도 재시도 가능성이 변경되면 UI 재구성.
- 정리 대기 오류는 시작·진단 비활성화, 활성 감지는 중지 가능.
- Android 설정 복귀 후 권한 갱신, 감지 상태 추정 금지.

## 작업 1. 관찰 가능한 시작 가능 상태와 명령 경계
파일: runtime/ListeningController.kt, runtime/CaptureRuntime.kt 및 시험. 같은 컨트롤러 락에서 startAllowed: StateFlow<Boolean>을 제공하고 canStart() 명령 방어 유지. MainActivity 명령·알림에 중지 토큰을 추가하고 서비스에서 현재·대기 토큰 일치를 요구한다. 기존 공통 상태 타입 보존.
- [x] Error → finish → 소유자 해제 시 Error가 같아도 재시도 가능해지는 실패 시험.
- [x] Robolectric Android 모의 환경의 실제 MainActivity/Controller 시작·중지·대기 취소·이전 토큰 시험. 실제 AudioRecord 미생성.
- [x] 누락·오동작 실패 확인 후 흐름 및 토큰 명령 구현.

## 작업 2. 실제 Wear 화면 통합
파일: MainActivity.kt, ui/UllimApp.kt 수정, ui/ListeningUiState.kt 생성, 한국어 문자열 확장. #24 ui/theme/UllimTheme.kt 재사용.
출력: UllimApp(state: ListeningUiState, onStart, onStop, onAiSettings, onDiagnostics, onAppSettings); 기본 정적 미리보기 인자만 허용.
- [x] Android 리소스 JVM 시험·Compose 시험 매니페스트/JUnit4·Robolectric 설정. MainActivity 생명주기 구독과 생성·복귀·권한 결과 갱신.
- [x] 실제 단계·동작·상태·수치·제한·startAllowed 연결. 기본 제어 우선, 시작·중지 진행 및 취소 표시. AI 제어, 대기 상태 진단, 도움말·오류에서 권한 설정 연결. 유효하지 않은 초기 UI 임시 표시 제거.
- [x] 실제 Compose 의미·클릭 검증: 시작→시작 중 비활성→감지 중 중지→중지 중 비활성→대기, 동일 오류 재시도, 권한 진입, 설정·안내 이동 및 재생성 중 중복 서비스 명령 없음.
- [x] 전체 단위 시험, assembleDebug, lintDebug, 독립 읽기 전용 검토 및 중요 회귀 수정. 로컬 검증 후 main 대상 Draft Closes #25, 선행·기기 미검증 기록.

네이티브 마이크·서비스와 Watch7 원형 화면·터치·복귀·빠른 연속 조작은 미검증이었다. 당시 후속 범위는 #26 그래프, #27 AI 상세, #28 설정 저장, #29/#30 최초 안내·복구이며 임시 표시로 완료를 대체하지 않는다.
