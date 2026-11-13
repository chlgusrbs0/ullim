# 울림 (Ullim)

**소리를 듣는 또 하나의 방법.**

울림은 **갤럭시 워치7 단독**으로 주변 소리의 **크기·지속시간·리듬**을 분석하여 손목 진동의 시간적 변화로 전달하는 청각 접근성 프로토타입입니다. 특정 소리를 감지해 고정 횟수로 울리는 알림 앱이 아니라, **실제 소리 변화에 따라 촉각 표현도 달라지는 것**이 핵심입니다.

> **상태: 앱 기초 구현 단계.** 단일 Wear OS 앱의 기본 화면을 워치7에 설치·실행했습니다([실기기 기록](docs/evidence/issue2-watch7.md)). 이 기초 앱에는 소리 감지·DSP·진동·AI 기능이 없으며 성능은 미검증입니다. 후속 기능 검증은 연계된 GitHub 이슈와 PR 결과에 따릅니다.

## 제품 목표

- 워치7에서 사용자가 시작한 마이크 입력을 감지하며, 화면이 꺼져도 사용자 중지 전까지 가능한 범위에서 처리합니다.
- DSP가 상대적 음량, 에너지 엔벌로프, 노크·말소리의 리듬을 분석하고 이를 진동 강약·길이·간격으로 표현합니다.
- YAMNet/LiteRT 기반 AI가 음향 문맥을 **선택적으로 보조**합니다. AI가 느리거나 실패해도 기본 DSP 촉각화가 계속 작동하는 것이 목표입니다.
- Wear Material 3 Expressive로 워치 원형 화면에 입력 소리와 **요청한 진동 명령**을 시각화합니다. 이 그래프는 모터의 물리 가속도를 직접 측정한 것이 아닙니다.
- 민감도, AI 사용 여부를 워치 안에 저장합니다. 원본 음성 파일의 저장·전송은 목표로 하지 않습니다.

## 범위에서 제외하는 것 (v1.0)

- 필수 스마트폰 앱, 노트북/웹 대시보드, 서버·로그인·계정, 인터넷 서비스
- Google Play 공개 출시 및 상용 수준의 배터리 최적화
- 음성 인식/자막/의미 번역, 완전한 청력 복원, 모든 소리를 정확하게 구분하거나 위험을 보장하여 경고하는 기능
- 각 소리의 이름마다 정해진 횟수만 진동하는 단순 알림 서비스

## 예정 기술 구조

`Kotlin · Wear OS · Wear Material 3 Expressive · AudioRecord · DSP(RMS/에너지 엔벌로프/Onset) · Android VibrationEffect · LiteRT/YAMNet`

```text
Watch7 microphone
       │
   AudioRecord
       │
 Bounded PCM buffer
       ├───────────────► DSP ───────────────► HapticMapper ─────► HapticRenderer ─► Watch vibration
       └───────────────► LiteRT / YAMNet ──► optional AI context ──┘
                                                     │
                                              Wear OS visualization
```

기본 DSP 처리 경로는 AI 추론을 기다리지 않습니다. 진동 출력은 워치7에서 확인되는 기능에 따라 표준 파형·지원되는 고급 엔벌로프·기능 제한 폴백을 사용할 수 있지만, 제한된 폴백이 원래 촉각화의 품질을 보장하지는 않습니다.

자세한 경계와 데이터 계약은 [아키텍처](docs/architecture.md)를 참고하세요.

## GitHub 이슈 기반 개발

- [전체 이슈](https://github.com/chlgusrbs0/ullim/issues) #1~#39가 v1.0의 작업 범위입니다.
- #1은 협업 규칙과 기초 문서, #2는 Kotlin/Wear OS 초기화, #3은 공통 타입과 계약을 정의합니다.
- 이후 오디오·DSP·진동·AI·UI·통합 테스트를 선행 이슈에 맞춰 구현합니다.
- **#39 최종 인수**는 #1~#38의 완료와 실기기·성능·APK 검증이 끝난 뒤에만 닫습니다.
- 다른 AI와 중복 작업을 방지하기 위해 각 작업의 **이슈 댓글 선점**이 필수입니다. 자세한 규칙은 [AGENTS.md](AGENTS.md)를 읽으세요.
- 작업 PR 본문에는 정확한 이슈 번호를 넣은 `Closes #N`을 작성하여 **main 병합 뒤 자동 종료**되게 합니다.
- **GitHub Actions를 사용하지 않습니다.** 로컬 빌드와 워치7 실기기 검증을 기본으로 합니다.

## 로컬 개발·검증

JDK 17, Android SDK Platform 36 및 Build Tools 35.0.0을 준비합니다. Android Studio에서 저장소를 열고 SDK 경로를 설정하거나, `ANDROID_HOME`을 지정합니다. Gradle은 저장소의 Wrapper로 실행합니다.

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

Windows에서는 `./gradlew.bat`를 사용합니다. APK 경로는 `app/build/outputs/apk/debug/app-debug.apk`입니다. 현재 기초 화면에는 마이크·인터넷 권한이나 녹음·진동 동작이 없습니다. 아직 단위 테스트 소스가 없어 `testDebugUnitTest`의 `NO-SOURCE` 결과를 기능 테스트 통과로 해석하면 안 됩니다.

워치7에서 설치·실행하려면 실제 기기와 디버깅 가능한 연결이 필요합니다. 연결·설치 명령과 실기기 증거 기준은 [개발·검증 안내](docs/development.md)를 참고하세요.

## 검증 및 안전성에 관한 주의

- 연구·해커톤용 청각 보조 프로토타입이며 **공인된 긴급 경보 장치를 대체하지 않습니다.**
- 사용자가 느끼는 진동 구별과 실제 물리 반응 지연은 **워치7 실측 전까지 검증되지 않았습니다.**
- AI 분류는 불확실할 수 있고 음원 분리·음성 내용 인식을 보장하지 않습니다.
- 외부 라이브러리·모델을 직접 재사용할 때는 해당 저작권·라이선스 고지를 보존합니다. 프로젝트 자체 라이선스는 사용자 승인 없이 추가하지 않습니다.

## 협업 문서

- [AGENTS.md](AGENTS.md) — 작업 선점·중복 방지·PR 자동 종료 규칙
- [docs/architecture.md](docs/architecture.md) — 기능 범위, 데이터 흐름 및 모듈 경계
- [docs/development.md](docs/development.md) — 로컬 검증/실기기 증거 및 완료 판단
