# #29 최초 안내·마이크 동의의 현재 앱 통합

2026-11-14, `verify/29-consent-integration`, 기반 `8a9457a8954c4659cf59e9382b7ec0d01fd50c17`.
참조 원본 PR65 head `4bbb89fe33cfffda159a9ea9c8df9acb94544b44`의 동의 절차를 현재 PR73 통합 코드에 이식한다. 원본 브랜치의 전체 UI로 덮어쓰지 않으며, 이 문서의 결과를 원본4bbb 검증으로 간주하지 않는다.

## 변경 동작

- 최초 실행에서 목적, 직접 시작한 감지/모터 보정의 마이크 사용, 원음 비저장·미전송, 로컬 수치/설정 보관, 화면 OFF/중지, 모터·기능 한계를 한국어로 설명한다.
- 사용자의 ‘마이크 권한 허용’만 RECORD_AUDIO를 요청한다. 허용 결과는 화면 상태만 갱신하며 서비스·보정을 시작하지 않는다. 이미 권한이 있으면 ‘메인으로’를 제공한다.
- ‘나중에’와 뒤로 가기도 안내를 완료하고 메인으로 이동한다. 완료 여부와 요청 이력은 원본과 같은 `ullim_first_run`의 `onboarding_complete` / `microphone_permission_requested` 키에 저장한다.
- 미허용 상태의 시작 버튼은 비활성화한다. 거절 후 재요청 또는 앱 권한 설정으로 이동하며, 설정 복귀는 자동 감지를 시작하지 않는다.
- 알림 권한은 안내와 분리하여 이후 명시적인 ‘소리 감지 시작’에서만 요청한다. 권한 결과로 이어지는 시작도 보이는 화면, 필수 보정, 기존 세션 장벽을 통과해야 한다. 안내가 열려 있으면 복원된 pendingStart를 무시한다.
- DataStore/AI 표시/시각화/오류 복구/모터 프로필 형식/DSP·출력 코드는 유지한다. 다른 기존 Activity 테스트는 안내 완료 상태를 명시하여 본래의 검사 목적을 유지한다.
- Main/Diagnostics/AiControls의 task affinity를 `${applicationId}.main`으로 치환한다. 일반 앱은 기존 `com.ullim.watch.main` 그대로이고, opt-in `.nativeprobe` 앱만 별도 작업으로 분리한다. 검증 전용 두 Activity는 기존 별도 affinity를 유지한다.

## 실제 로컬 실행

Windows / JBR21 / SDK36 / Gradle8.13. 원본 마이크·음성 파일이나 실제 워치 권한 변경을 사용하지 않았다.

1. 새 Activity 통합 테스트4개를 먼저 실행해 **4개 모두 의미 있는 실패**를 확인했다(33초). 누적 앱에 안내·권한 복구 흐름이 없었다. 구현 후 같은4개 통과(24초).
2. 즉시 메인 제목 표시, 거절 뒤 재요청, 뒤로, 별도 알림 요청을 추가 확인했다. 총7개 통과(22초). 추가3개는 이미 구현된 동작의 특성 확인이며 새 실패→수정 증거로 주장하지 않는다. 테스트 도구 API 위치 오류는 컴파일 오류로 구분하고 바로잡았다.
3. opt-in APK task 검사에서 `.nativeprobe` 패키지인데 제품3개 Activity가 원본 앱 affinity를 공유하는 실패를 확인했다(기대3, 실제0). placeholder 수정 후 일반/검증 APK를 각각 검사한다.
4. 일반 전체 `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:assembleRelease :app:compileReleaseUnitTestKotlin`: **243 tests, failures/errors/skipped0**, BUILD SUCCESSFUL 61초. 일반 debug/release manifest의 제품 affinity3개 일치, 두 검증 Activity의 manifest/DEX 제외를 확인했다. release 테스트는 컴파일한 것이며 실행했다고 주장하지 않는다.

| 산출물 | bytes | SHA256 |
|---|---:|---|
| normal debug APK | 52859839 | `998d2e1c219918003dab0e1a493604940da6d833db318e7a532c711a7e8afa52` |
| unsigned release APK | 45552287 | `7bfb176338665627f20ad108f7716b8293f0bf3083fc8649931cb588ca7a388e` |

5. opt-in 전체 `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:compileReleaseUnitTestKotlin -PnativeCaptureProbe=true`: **251 tests, failures/errors/skipped0**, BUILD SUCCESSFUL 49초. 제품3개 Activity의 affinity는 모두 `com.ullim.watch.nativeprobe.main`, 검증 전용2개는 별도 affinity다. APK52909182bytes/SHA256 `815239cfd77c962173b81c0447b77a5c802bf23bd1242b83b0f2a3ef0046f5bb`. 설치·실행하지 않았다.

위 산출물의 제품 코드 커밋은 `332fb0c3355a5bca836c855390e1685aa30bc184`다. 이후 변경은 검토 결과 기록뿐이며 APK를 재설치하지 않았다.

## 독립 검토

별도 읽기 전용 검토가 `8a9457a..332fb0c`를 확인했다. Critical/Important 없음, opt-in 결과 XML251/실패·오류·skip0을 독립적으로 읽었다. 일반243/build/lint/release는 위 실행 기록 근거이며 검토자가 재실행한 것이 아니다. 수정 요구가 없어 추가 구현·재검토를 하지 않았다.

Minor2건을 후속 검사로 남긴다. (1) 알림 권한 요청을 확인하는 테스트는 결과 callback/Activity 재생성의 pendingStart 복원을 직접 끝까지 검사하지 않는다. 코드는 안내/전경/보정/세션 장벽으로 보호하지만 회귀 범위를 더 넓힐 여지가 있다. (2) 기본 글씨의 새 안내는 확인했으나 기존2× 글씨 테스트에 이 안내를 추가하지 않아 큰 글씨에서의 버튼 도달/넘침은 직접 검사하지 않았다. 관찰된 제품 실패라는 뜻은 아니며 접근성 완료로 간주하지 않는다.

검토에서 판단을 유보한 실제 OS 권한 창·영구 거절·복귀 시점·원형 화면·스크린리더와 물리 소리/진동/백그라운드 인수는 아래 실기기 게이트로 유지한다. 이 판단으로 Draft 통합만 진행하며 main 머지·#29 완료는 하지 않는다.

## 남은 완료 조건

워치7에서 새 설치/권한 허용·거절·영구 거절·설정 복귀·재실행과 원형 화면 사용성을 이 변경으로 확인하지 않았다. 사용자가 지금 기기를 확인할 수 없어 워치 조작·설치를 하지 않는다. 기존 원본 앱/보정은 유지한다. 이전 화면 OFF 시험과 별도 probe의 보정 실패는 이 안내 변경의 검증이 아니다.

#24는 main 완료, #6/#25와 누적 런타임의 필수 실기기 조건은 미완료다. **PR65와 PR73은 Draft, #29는 open**을 유지한다. 자동 시작 방지의 호스트 검사는 실제 OS 권한·마이크 검증을 대체하지 않는다. GitHub Actions/원본 PCM 저장·전송/시스템 설정 쓰기는 없다.
