# 울림 (Ullim)

**소리를 듣는 또 하나의 방법.**

울림은 **갤럭시 워치7 단독**으로 주변 소리의 **크기·지속시간·리듬**을 분석하여 손목 진동의 시간적 변화로 전달하는 청각 접근성 프로토타입입니다. 특정 소리를 감지해 고정 횟수로 울리는 알림 앱이 아니라, **실제 소리 변화에 따라 촉각 표현도 달라지는 것**이 핵심입니다.

> **상태: 해커톤 MVP 개발 중.** 워치7 설치·실행과 사용자 시작→마이크·DSP·진동→중지, 별도 시험의 [실제 AI 모델 실행](docs/evidence/issue19-validation.md)을 확인했습니다. 화면을 계속 확인하지 않고 강약·지속·리듬을 느끼는 것이 목표이며 펄스 진동을 사용합니다. 최신 통합본의 착용 품질·AI 효과·물리 지연은 워치에서 확인해야 합니다. 현재 범위는 [핵심 MVP 계획](docs/superpowers/plans/2026-11-14-hackathon-core-mvp.md)을 따릅니다.

## 제품 목표

- 워치7에서 사용자가 시작한 마이크 입력을 감지하며, 화면이 꺼져도 사용자 중지 전까지 가능한 범위에서 처리합니다.
- DSP가 상대적 음량, 에너지 엔벌로프, 노크·말소리의 리듬을 분석하고 이를 진동 강약·길이·간격으로 표현합니다.
- YAMNet/LiteRT 기반 AI가 음향 문맥에 맞춰 **강약 변환 곡선을 조절**합니다. 신규 기본값은 AI ON이며, 첫 진동은 AI 분석을 기다리지 않습니다. AI 지연·실패 시 기본 DSP 촉각화를 계속합니다.
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
       └───────────────► LiteRT / YAMNet ──► asynchronous AI context ──┘
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

Windows에서는 `./gradlew.bat`를 사용합니다. APK 경로는 `app/build/outputs/apk/debug/app-debug.apk`입니다. 단위 테스트는 합성 PCM·가짜 하드웨어로 실행하며 워치7의 물리 검증을 대신하지 않습니다.

워치에서 **소리 감지 시작**을 누르고 마이크/알림 권한을 허용합니다. 감지 중에는 워치 안에서 PCM을 처리해 DSP 기반 진동을 요청하고 입력 소리/요청한 진동의 크기를 표시합니다. 앱의 중지 버튼 또는 작동 알림의 중지 액션으로 멈춥니다. 알림 권한을 거부하면 알림 표시가 제한될 수 있습니다. 인터넷·스마트폰은 필요하지 않으며 원본 음성을 파일로 저장하지 않습니다. 진동 진단은 감지를 중지한 상태에서만 엽니다.

**AI 보조** 화면에서 AI를 켜거나 끌 수 있습니다(신규 기본 ON). AI를 켜는 것만으로 마이크를 시작하지 않으며, 사용자가 감지를 시작한 세션에서만 번들 YAMNet을 별도 작업자로 구동합니다. AI가 지연/실패/만료되어도 기본 DSP 경로는 AI 결과를 기다리지 않습니다. 일시적인 추론 시간 초과는 작업 정리가 끝난 뒤 5초 이후 세션당 한 번 자동 복구합니다. 반복 실패나 다른 오류는 AI OFF→ON으로 재시도합니다. 진행 중인 네이티브 작업은 안전하게 끝난 뒤 모델을 닫으므로 정리가 남았으면 표시합니다. 민감도(기본 100%, 범위 75~125%)와 AI 선택(신규 기본 ON)은 로컬 DataStore에 저장합니다. 감지 중 변경은 즉시 적용하며 저장 중/실패는 화면에 표시합니다. 자세한 동작과 검증 경계는 [설정 안내](docs/persistent-settings.md)를 참고하세요. 모델·조절 정책과 검증 경계는 [모델 안내](docs/models.md), 실제 워치 측정 절차는 [AI 성능 검증](docs/ai-runtime-validation.md)을 따릅니다. 실제 모델/AI ON·OFF·물리 지연·촉각 품질은 아직 워치7에서 검증하지 않았습니다.

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
- [docs/session-lifecycle.md](docs/session-lifecycle.md) — 세션 상태·마이크 소유권·중지 및 오류 복구 경계
- [docs/main-listening-ui.md](docs/main-listening-ui.md) — 실제 감지 상태를 표시하는 메인 화면과 로컬 화면 검증 경계
- [docs/sound-haptic-visualization.md](docs/sound-haptic-visualization.md) — 공통 소리 시간축의 입력·진동 명령 그래프와 물리 측정 구분
- [docs/ai-result-display.md](docs/ai-result-display.md) — AI 명칭·모델 점수와 불확실/만료/실패 표시의 검증 경계
- [docs/persistent-settings.md](docs/persistent-settings.md) — 민감도·AI 로컬 저장, 즉시 적용 및 저장 실패 처리
- [docs/error-recovery.md](docs/error-recovery.md) — 오류 코드, 수동 회복 행동과 자원 정리 경계
- [docs/wear-accessibility.md](docs/wear-accessibility.md) — 큰 글씨·화면 읽기 의미·대비·터치 및 실제 워치 검증 체크리스트
- [docs/watch-haptic-validation.md](docs/watch-haptic-validation.md) — 워치7 실제 관찰, 연속 출력 통제 비교와 물리 지연 측정 절차
