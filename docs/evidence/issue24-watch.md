# #24 Wear UI 기반 검증

## 대상과 범위

- 실행 소스: `1153348a126fcf89afd9aa54518e1010d8e2f47a` (현재 main `24a233d` 통합).
- APK: 25,847,264 bytes, SHA-256 `7d29e92c9f0c3008cc926daf7cc771b84c5929d6114497e0f563ed7e7377f3ec`.
- Galaxy Watch7 SM-L300, Android 17 / API 37, One UI Watch 베타 `L300XXU2ZZK1`, 기본 글씨 배율 1.0. 2026-11-14 KST 실행.
- #2·#3은 main 병합 완료. 메인/설정/안내/문제 해결 화면과 기존 #4 진동 진단 진입을 검증했다. 감지 제어·설정 저장·확대 글씨·촉각 품질은 후속 이슈 범위다.

## 로컬 검증과 검토

`./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --console=plain` 성공(35초). JBR 21, SDK 36, 고정된 기존 Gradle 의존성 사용. 13 tests, failures/errors/skips 0. GitHub Actions는 사용하지 않았다.

기존 진단 진입이 사라지는 회귀를 먼저 테스트로 확인(RED)한 뒤 복원했다. UI 테스트는 네 화면 전환, 시스템 Back 경로, 진단 진입 시 서비스 미시작을 검사한다. Robolectric 192dp 스크린샷은 호스트 레이아웃 참고 자료이며 원형 실기기 검증을 대체하지 않는다.

별도 읽기 전용 검토자는 `24a233d..1153348a`에서 Critical/Important/Minor 코드 지적 없음으로 보고했다. 실기기 검증은 아래 별도 수행했다.

## 실제 워치 결과

직접 설치한 위 APK에서 앱 소유 화면을 확인한 뒤 스크린샷을 얻었다. 임의의 다른 앱 화면은 수집하지 않았다.

| 확인 항목 | 결과와 근거 |
| --- | --- |
| 메인과 기본 조작 | [메인](issue24-watch/home.png), [하단 스크롤](issue24-watch/home-bottom.png). 설정·안내·진단 버튼에 도달하며 겹침 없음. 모든 기본 버튼은 최소 48dp 높이. |
| 설정과 터치 복귀 | [설정](issue24-watch/settings.png). 한글과 뒤로 버튼이 읽히며 터치로 메인 복귀. 짧은 화면에 불필요한 스크롤 없음. |
| 긴 안내와 스크롤 | [안내](issue24-watch/guide.png), [안내 하단](issue24-watch/guide-scrolled.png). 전체 문장과 하단 버튼에 스크롤로 도달. |
| 오류 안내와 복귀 | [문제 해결](issue24-watch/error.png), [하단](issue24-watch/error-scrolled.png). 전체 문장과 뒤로 버튼에 도달. 시스템 Back으로 [안내](issue24-watch/back-to-guide.png) → [메인](issue24-watch/back-to-home.png) 복귀 확인. |
| 기존 진단 유지 | [진단](issue24-watch/diagnostics.png), [복귀](issue24-watch/diagnostics-back.png). 기존 Activity가 열리고 Back으로 UI에 복귀. 진동 버튼은 누르지 않음. |
| 실제 손가락 검사 | 사용자가 설정 → 뒤로, 안내 → 문제 해결 → 뒤로를 직접 눌러 보고 **“글자와 버튼 모두 괜찮고 이동도 정상”**으로 확인함. |
| Material 3 / 단독 UI | Wear Compose Material 3 1.5.0 사용, 휴대폰 Material3 혼용 없음. 시스템 dynamic palette와 기본 ColorScheme 폴백. 진단도 공통 테마 사용. 별도 폰/웹 화면 없음. |

스크롤 중에는 이미 지나간 문장/버튼의 일부가 화면 위·아래에 보일 수 있다. 정지한 해당 콘텐츠의 읽기 위치에서 실제 한국어 글리프 손실·겹침이 없고 각 버튼에 도달함을 확인했다. 확대 글씨 검증(#31) 및 동적 색상 환경별 검증은 이 기록으로 통과 처리하지 않는다.

시험 전 사용 중이던 개선 APK와 보정값을 별도로 보존했다. UI 후보는 감지 서비스를 포함하지 않으며 이 시험에서 마이크를 활성화하지 않았다. 검증 종료 후 기존 개선 APK(SHA-256 `6a2e6e16a634fd59d5dbce19ce72856c3dd5ff632df2f4677fe14474e853006c`)의 설치와 기기 내 해시를 확인했다. 기존 마이크·알림 허용 및 USER_SET 상태를 복원했다. 보정값 4,262 bytes와 SHA-256 `47a0c31418640333f4624240f37690f410d70423750ad7bb70675866b751f06c`가 시험 전과 같고, 마이크 AppOps가 실행 중이 아니며 ListeningService도 없음을 확인했다. 글씨 배율 1.0, 충전 중 강제 화면 켜짐 0을 유지했다.
