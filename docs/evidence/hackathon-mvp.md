# 해커톤 MVP 통합 빌드

사용자 지시에 따라 핵심 앱 통합·배포를 먼저 진행한다. 기존 작동 브랜치09ae702와 main8398b0a를 합쳤다. 충돌8개를 정리한 뒤 `app/src/main`과 `app/build.gradle.kts`는 작동 브랜치와 동일하다. 최신 main의 DSP/모델/마이크 회귀와 실기기 근거는 유지하고 opt-in 검증 화면7개를 합쳤다. 별도 개인정보 작업은 포함하지 않는다.

- 포함: 실제 마이크→DSP 강약/지속/리듬→모터 간섭 보정→진동, 비동기 번들 YAMNet AI 보조, 입력/명령 그래프, 민감도/AI 설정, 명시적 시작/중지.
- 일반 전체256 tests/실패·오류·skip0, `testDebugUnitTest assembleDebug lintDebug` 성공62초. 최초 실행에서 옛 UI를 기대하던 main의2개 검사를 실제 안내 완료/설정/진단 경로로 맞춘 뒤 해당2개 통과24초, 전체 재검사 통과다. 제품 로직을 테스트에 맞춰 약화하지 않았다.
- 정상 APK `com.ullim.watch`, min34/target35,52859839bytes/SHA256 `6412a2d2f711d1ce0d089ced8348c5d3857ba3968541f93952a320914b28c973`. `artifacts/ullim-mvp-debug.apk`에 같은 파일을 준비했다(APK는 Git에 넣지 않음).
- 실제 APK 모델4126810bytes/SHA256 `10c95ea3eb9a7bb4cb8bddf6feb023250381008177ac162ce169694d05c317de`, 레이블6230bytes/`8e1267a120c1932b7273c0d0e0c5529edbb9a35512b437b1c8982baa59047051`. arm64 LiteRT/DataStore/graphics native 포함. 검증 전용7개 Activity는 정상 manifest/DEX에서 제외됨.
- APK 서명 검증 성공. 인증서SHA256 `c23fcde811ce18637d6c40ed2ba05c69b9d02501e67db6ed7ed841d8bfd3cc3b`로 기존 설치본 백업과 같아 일반적인 데이터 유지 업데이트가 가능한 서명이다. 실제 업데이트 성공을 새로 확인한 것은 아니다.

기기 연결 없이 개발 중이므로 새 설치/착용 시험은 하지 않았다. 기존 모터 재입력 보정과 강약 개선의 사용자 관찰은 유지하지만 잘게 끊기는 질감은 남는다. 이 빌드를 새 매끈함 해결·물리 P95·전체 앱 AI ON/OFF 인수 통과로 부르지 않는다. AI 모델 자체의 실제 워치 실행은 [main의 #19 근거](issue19-validation.md)와 구분한다.

MVP 통합 PR은 `Refs #17`로 연결하며 머지만으로 미해결 하드웨어 이슈를 닫지 않는다. 시연은 안내/권한 → 필요 시 직접3초 보정 → 설정에서 AI ON → 메인 감지 시작 → 작은/큰·짧은/긴 소리 → 중지 순서다. 원본 음성을 저장하지 않는다. 워치 연결 가능 시 같은 APK의 실제 AI 상태·추론 수와 촉감을 이어서 확인한다.

독립 읽기 전용 검토가 `09ae702..d0482af`의 제품 코드 보존, 최신 main의 DSP/분류기 보존, 실제 APK 모델/레이블/arm64와 검증 화면 제외를 직접 확인했다. Critical/Important/Minor 없음이다.

비배포 opt-in 검증 도구 회귀는10개 중9개 통과, `NativeSessionProbeReviewTest.failedOnSaveMustBeObservedBeforeAnotherToggleCanClearItsError`가3초 대기 시간 초과로 실패했다(전체 명령50초, 성공으로 기록하지 않음). 이 도구는 정상 APK에 포함되지 않고 제품 코드는 기존 작동본과 같으며 일반 전체256개가 통과했다. 사용자의 MVP 우선 지시에 따라 이 별도 검증 도구 실패로 제품 통합을 막지 않고 #23의 후속 검사로 남긴다. 전체 opt-in 검증 통과나 실제 서비스 반복 통과를 주장하지 않는다.
