# 개인정보 처리·보관 점검 (#33)

상태: **코드·APK 감사 완료, 실기기 오프라인/AI 및 최초 동의 통합 검증은 남음**. 이 문서는 현재 누적 구현과 실제 검사 범위를 구분한다. 원본 PCM·주변 음성 파일을 검증 자료로 수집하지 않는다.

## 사용자가 알아야 할 동작

울림은 사용자가 감지를 시작한 동안 워치 마이크의 주변 소리를 메모리에서 분석해 진동으로 표현한다. 원본 음성 파일, 대화 자막, 녹음 목록은 만들지 않는다. 선택적 AI 모델과 레이블도 APK에 포함되어 있고 서버·로그인·휴대전화 연결을 처리 경로에 요구하지 않는다. **인터넷 없이 실제 AI까지 작동하는지는 아직 워치7에서 검증하지 않았다.**

민감도와 AI 사용 선택은 워치 안에 저장한다. AI 선택을 켜거나 저장된 ON을 읽는 것만으로 마이크를 시작하지 않는다. 현재 누적 앱의 최초 권한 흐름은 **감지 시작 버튼 → OS 마이크 권한 허용 → 감지 시작**이다. 중지 버튼과 작동 알림의 중지 동작은 마이크·진동 종료를 요청한다. 정상 종료의 실제 한 세션은 확인했지만 권한 회수·화면 꺼짐·잠금·시스템 종료 등 전체 경우는 각 이슈에서 검증 중이다.

#29/PR #65는 다른 담당자의 최초 안내·동의 구현으로 **아직 통합하지 않았다**. 해당 구현은 권한 허용만으로 감지를 시작하지 않고 별도 시작을 요구하며 `ullim_first_run` SharedPreferences를 사용한다. 현재 누적 앱이 이미 이 동작을 갖췄다고 안내하지 않는다. #29 통합 후 최초 안내/설정/권한/시작·중지 설명과 실제 동작을 다시 대조해야 #33을 완료할 수 있다.

## 데이터 흐름과 보관 범위

| 정보 | 처리·보관 위치 | 종료·삭제 범위 |
|---|---|---|
| 마이크 PCM | AudioRecord와 Kotlin 메모리. DSP320샘플(20ms), AI15,600샘플(975ms) 창 | 팬아웃 stop에서 ring/partial 배열을0으로 채우고 최신 참조를 비움. 일시적 복사본의 JVM 즉시 완전 소거를 보장하지 않음 |
| AI 입력 | 단일 작업자의 FloatArray/직접 ByteBuffer | 추론 finally에서 입력을0으로 채움. 네이티브 작업 진행 중 OFF/중지 시 안전한 종료까지 메모리가 남을 수 있음 |
| AI 결과 | 최신 유효 분류 스냅샷과 UI 메모리 | 만료 시 결과 사용·노출을 중단함. 평활화용 제한된 메모리 문맥은 다음 결과·OFF·세션 종료 때 갱신/정리함. 분류 이력 파일이나 분류명 로그 없음 |
| 입력 수준·진동 명령 그래프 | 세션별256슬롯, 최근5초 수치 메모리 | 원본 PCM이 아닌 RMS/엔벌로프/명령값. 세션 종료 때 비움. 그래프 파일 자동 생성 없음 |
| 설정 | 앱 전용 `files/datastore/ullim_settings.preferences_pb` | schema1, sensitivity, ai_enabled. 앱 내 기본값 복원은 이 설정들을 초기화하며 OS 마이크 권한 취소나 전체 파일 삭제를 뜻하지 않음 |
| 최초 안내/권한 요청 이력 | #29 통합 후 `shared_prefs/ullim_first_run.xml` 예정 | 현재 누적 브랜치에는 없음. 설정 기본값 복원과 별개이며 통합 뒤 파일/삭제 동작 재검증 필요 |
| 모델·레이블·고지 | APK의 읽기 전용 assets | 사용자 녹음이 아님. 모델 다운로드/업데이트/분석 요청을 런타임 네트워크로 보내지 않음 |
| OS/라이브러리 파일 | 프로파일·UI ID 등 프레임워크 관리 파일 | 아래 실제 파일명 검사 범위를 참고. 오디오 파일이라고 간주하지 않음 |

`AndroidPcmInput`은 AudioRecord로 메모리 배열을 읽는다. `MicrophoneCapture`/`AudioFanOut`은 파일·네트워크 sink가 없으며 팬아웃은 세션 종료 때 버퍼를 정리한다. `AiWorker`는 대기 입력1개와 실행 중 입력만 유지하고 OFF/종료 시 대기·문맥 참조를 비운다. `YamnetClassifier` 및 `AndroidYamnet.InterpreterEngine`은 입력 버퍼를 finally에서 지운다. 이미 시작된 네이티브 호출을 강제로 해제해서 다른 마이크/추론 작업을 겹쳐 실행하지 않는다.

이 검토는 앱이 사용하는 경로의 소스 감사이며 JVM/운영체제 전체 메모리의 물리적 소거 증명이나 모든 외부 네이티브 코드의 감사는 아니다. 디버거로 힙·원본 PCM을 추출하는 절차는 검증에 사용하지 않는다.

## 최종 APK의 권한과 외부 진입점

Debug와 unsigned Release APK를 각각 AAPT로 검사했다([실제 권한 출력](evidence/issue33-apk-permissions.txt)). 두 APK의 권한은 같다.

| 권한 | 용도 |
|---|---|
| RECORD_AUDIO | 사용자 시작 세션의 마이크 수집 |
| VIBRATE | 진동 출력·사용자 진단 |
| FOREGROUND_SERVICE / FOREGROUND_SERVICE_MICROPHONE | 사용자 시작 마이크 서비스 |
| POST_NOTIFICATIONS | 작동 알림과 중지 동작 |
| 앱 자체 DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION | AndroidX가 추가한 signature 권한. 인터넷 권한이 아님 |

**INTERNET, ACCESS_NETWORK_STATE, 외부 저장소·Bluetooth 연결 권한은 두 최종 APK에 없다.** 앱에 원본 PCM을 외부 Intent/공유 URI/파일로 내보내는 경로도 확인되지 않았다. 이러한 권한·소스 검사가 실제 네트워크 단절 상태의 모델 실행을 대신하지는 않는다.

ListeningService, DiagnosticsActivity, AiControlsActivity 및 AndroidX InitializationProvider는 최종 Manifest에서 exported=false다. MainActivity는 런처 진입점이다. Debug에는 Compose PreviewActivity와 UI 테스트용 ComponentActivity가 추가로 exported=true이며, 검사한 앱 코드에서 이 진입점을 마이크 자동 시작/PCM 외부 제공에 연결하지 않는다. unsigned Release에는 이 두 개발용 Activity가 없다. ProfileInstallReceiver는 DUMP 권한을 요구하는 프로파일 관리 진입점이다. Debug는 디버깅 가능한 빌드이며 일반 Release의 접근 경계와 동일하다고 주장하지 않는다.

## 의존성과 로그

`./gradlew.bat :app:dependencies --configuration debugRuntimeClasspath`를 실제 실행하고 직접 선언 및 전이 그래프를 검토했다. Compose/Wear/AndroidX, Kotlin·coroutines·DataStore, 로컬 LiteRT1.4.2 및 디버그 UI 도구가 포함된다. OkHttp/Retrofit/Firebase/Crashlytics/Sentry/Ktor/gRPC/Volley/analytics 이름의 전송·분석 SDK는 나타나지 않았다. 이름 검색만으로 모든 바이너리에 어떤 I/O도 없음을 증명하지 않으며, 최종 권한과 실제 소스의 데이터 sink 검사도 함께 수행했다. 오픈소스 고지 전체 검증은 #34에서 별도로 한다.

| 앱 로그 태그 | 현재 기록하는 값 | 기록하지 않는 값 |
|---|---|---|
| UllimError | 세션 번호와 고정 오류 코드 | PCM, 원본 음성, 예외 메시지·Throwable 스택, 분류명 |
| UllimAI (debuggable만) | 단계·오류 코드, 로드/추론 시간, 시도·결과·드롭 개수, 정리 여부 | 입력 샘플, 분류명·음성 내용 |
| UllimHaptics | 사용자가 누른 합성 비교 패턴·상태·명령 수·앱 일정 지연 | 마이크 샘플·분류명. 이 수치는 물리 지연이 아님 |

모델 로드는 번들 파일의 SHA-256/형식/차원과 스레드 수를 검사하며, 원본 PCM을 파일로 내보내는 모델 디버그 옵션을 설정하지 않는다. 모델 파일은 읽기 전용 매핑이다. 앱에서 허용한 메타데이터 로그와 OS/네이티브 라이브러리가 자체 생성하는 진단의 범위는 구분한다. 아직 실제 모델 구동·오류 상황에서 전체 시스템 로그를 점검하지 않았으므로 그 실기기 항목은 미완료다.

## 백업·기기 이전·앱 삭제

`allowBackup=false`만으로 일부 Android12+ 제조사의 기기 간 이전까지 금지되지 않을 수 있다. 이번에 `android:dataExtractionRules`를 지정하고 cloud-backup과 device-transfer 양쪽에서 다음9개 도메인의 `.` 전체를 명시적으로 제외했다.

`root`, `file`, `database`, `sharedpref`, `external`, `device_root`, `device_file`, `device_database`, `device_sharedpref`.

정책은 설정·동의 이력을 포함한 앱 데이터의 **클라우드 백업 및 Android 기기 간 이전 제외**다. minSdk34이므로 Android11 이하용 legacy 규칙은 추가하지 않았다. iOS 대응 앱·식별자·cross-platform-transfer 규칙을 구성하지 않았으며 지원하지 않는 대응 앱 정보를 만들어 넣지 않았다. 새로운 전송 모드나 플랫폼을 도입할 때는 그 경로를 별도로 검증해야 한다.

실제 APK에서 Manifest의 allowBackup=false와 dataExtractionRules 리소스 참조를 확인했고, Debug와 Release 양쪽의 패키징된 규칙에서9×2개 제외를 확인했다. Release는 리소스 경로가 최적화되어 `aapt2 dump resources`로 `xml/data_extraction_rules → res/4j.xml`을 확인한 뒤 그 실제 파일을 디코딩했다. 처음 원래 파일명으로 찾지 못했던 것을 규칙 누락으로 오인하지 않았다. [Debug 규칙](evidence/issue33-backup-rules-debug.txt), [Release 규칙](evidence/issue33-backup-rules-release-unsigned.txt).

일반적인 앱 삭제는 APK와 앱 전용 설정/캐시/데이터를 제거한다. 이 점검에서 사용자의 설정을 지우거나 앱 삭제를 실행하지는 않았다. `adb uninstall -k`처럼 데이터를 보존하는 개발 명령은 일반 삭제와 다르며 사용하지 않는다. 앱 삭제가 운영체제의 메타데이터 로그나 개발 PC/GitHub에 이미 남긴 비음성 검증 기록을 모두 지우는 동작은 아니다. 설정 기본값 복원은 앱 삭제나 OS 권한 취소의 대체 기능이 아니다.

## 실제 검사 결과와 남은 조건

2026-11-13, 연결된 Samsung Watch7 SM-L300/API37에서 기존 비교 APK(소스6fd0358, SHA2567d9d9066…)의 앱 전용 디렉터리를 읽기 전용으로 검사했다. 파일 **내용은 읽지 않고 이름만** 확인했다. `files/shared_prefs/databases/no_backup` 범위에서 `files/profileInstalled`와 `shared_prefs/android.app.ActivityThread.IDS.xml`만 관찰했다. 이 스냅샷에서는 설정 변경을 저장하지 않았고 마이크가 중지된 상태였다. 모든 시나리오/디렉터리/새 #33 APK에서 파일 부재를 증명한 것은 아니다.

새 #33 APK는 사용자의 E/F 비교를 방해하지 않도록 워치에 교체 설치하지 않았다. 실제 워치의 인터넷 연결을 끊거나 사용자 설정/권한을 변경하지 않았다. 다음 조건이 남아 있어 Draft를 유지한다.

- [ ] #29 최초 동의 통합 후 안내·권한·명시적 시작/중지 동작 일치.
- [ ] 최종 후보 APK를 워치7에 설치해 변경된 안내와 설정 보관 확인.
- [ ] 휴대전화·인터넷 없이 AI OFF/ON, 실제 네이티브 추론·DSP·진동/중지 실행.
- [ ] 그 세션 전후의 앱 파일명/로그·모델 오류 경로 점검. 원본 오디오 파일/덤프 없이 메타데이터만 수집.
- [ ] 실제 플랫폼 백업/이전 제외 동작 검사. 정책 XML의 포함은 확인했으나 실기기 이전을 실행한 것은 아님.

오프라인 시험 때는 먼저 기기·후보 APK와 현재 연결 설정을 기록하고 사용자가 명시적으로 감지를 시작한다. 네트워크를 끊은 후의 상태/AI 시도·성공·실패 수를 앱 메타데이터로 확인한다. 연결 복구 뒤 동일 세션 로그와 앱 파일명만 확인하고 원래 연결 설정을 복원한다. 설치된 모델·권한이 필요하며, 무선 디버깅이 끊기는 것을 앱 처리 실패로 오인하지 않는다. 지금 열린 촉각 비교가 끝난 뒤 수행한다.

## 로컬 검증 재현

Windows/JBR21, SDK36/Build Tools35.0.0에서 실제 실행했다.

```text
./gradlew.bat :app:dependencies --configuration debugRuntimeClasspath
BUILD SUCCESSFUL in 1s

./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:lintDebug
BUILD SUCCESSFUL in 1m54s
168 tests / 0 failures / 0 errors
```

Lint는 오류0, 경고11, hint1이며 기존 DataExtractionRules 경고는 없어졌다. 경고 억제나 Actions를 추가하지 않았다. XML을 그대로 따라 쓰는 테스트는 만들지 않고 실제 컴파일·Lint·최종 APK 검사로 검증했다.

| 산출물 | 크기(bytes) | SHA-256 |
|---|---:|---|
| Debug | 56,232,548 | `5754a59ac312705e4393474c8dc8de1dc4dd8365295cc20aa81697775ba26633` |
| unsigned Release | 45,424,835 | `0482296a5a4532ff54eb62ba154307a9bd8e003ded576233ccff739e47d72116` |

Release는 **미서명 감사 산출물**이며 설치 가능한 최종 배포본으로 제공하지 않는다. APK/서명키는 Git에 추가하지 않았다.

재현 명령(이름이 최적화된 Release의 실제 XML 경로는 리소스 표에서 다시 조회한다):

```text
aapt dump permissions APK_PATH
aapt dump xmltree APK_PATH AndroidManifest.xml
aapt2 dump resources APK_PATH
aapt dump xmltree APK_PATH PACKAGED_RULE_XML_PATH
adb -s WATCH_SERIAL shell run-as com.ullim.watch find . -type f
```

참조: [Android Auto Backup·제외 규칙](https://developer.android.com/identity/data/autobackup), [로컬 설정](persistent-settings.md), [AI 동작/안전한 정리](models.md), [오류 복구](error-recovery.md), [실기기 촉각 검증](watch-haptic-validation.md).
