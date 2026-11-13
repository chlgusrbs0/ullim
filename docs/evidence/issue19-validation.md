# #19 YAMNet 실제 워치 실행 검증

## 독립 변경과 로컬 검사

기존 PR56의 모델 커밋 `e1524b7`과 LF 보존 `2d0716d`만 main `432fafc` 위에 이식했다. 원 브랜치/커밋은 보존했고 후속 PR74로 연결한 뒤 기존 Draft PR56만 대체 처리했다. 미검증 서비스·촉각 변경은 이 PR에 포함하지 않는다. 모델 생산 코드는 이식 후 변경하지 않았다. 최종 native 실행 소스 `ceeb0de301fc73ad42ede70a83ddbce0b9374715`.

- 일반 debug 전체67 tests, failures/errors/skips0, test/assemble/lint 및 unsigned release 성공23초.
- 최종 opt-in 전체68 tests, failures/errors/skips0, test/assemble/lint 성공25초. 모델 엔진 성공/실패 모두 입력 작업 버퍼가0으로 지워지는 회귀를 추가했다.
- 독립 읽기 전용 검토: 모델/외부·내장521레이블 바이트, SHA256, FLOAT32 `[15600] → [1,521]`, 내장 Apache2.0 및 자원/결과 소유권 검증에 추가 결함 없음.
- 검토 P2(일정 시각과 총량만으로 동시 진행을 판정)는 실제 DSP 진행 증가 조건, 호출 중 진행 횟수, 생산자 전달 간격·지각 측정으로 보완했다. 합성20ms 패킷은 마지막 샘플 시점 뒤 전달한다. 최종 후보로 재실행했다.
- 일반 debug/release manifest·DEX에 NativeYamnetProbeActivity 없음, opt-in에만 있음. 세 APK의 모델/레이블 해시는 동일하며 모델은 Stored(비압축)다. 일반 앱의 INTERNET/Bluetooth 권한 없음.
- 모델4126810 bytes/SHA256 `10c95ea3eb9a7bb4cb8bddf6feb023250381008177ac162ce169694d05c317de`;521레이블6230 bytes/SHA256 `8e1267a120c1932b7273c0d0e0c5529edbb9a35512b437b1c8982baa59047051`.
- [출처·버전·라이선스](../models.md) 및 APK의 Apache2.0/YAMNet 고지를 유지했다. GitHub Actions 미사용.

## 실제 워치 실행

SM-L300 / Android17 API37 / L300XXU2ZZK1, 2026-11-14 KST. 별도 패키지 `com.ullim.watch.nativeprobe`, 최종 APK50065950 bytes / SHA256 `de8b7843853ab23bbe0808504a071edd8ffa25964fafbb70ba7fa96ec58d5d46`. 설치된 APK 해시도 일치했다.

시험 앱은 네트워크/폰 권한 없이 번들 모델을 직접 로드했다. ADB WiFi는 관측용으로 유지했으므로 기기를 물리적으로 네트워크에서 분리했다고 주장하지 않는다. 실제 마이크·모터·원음 파일·서버 호출은 없다. 아래 분류 레이블은 **고정된 합성 시험 입력**의 점수이며 사용자의 주변 소리/음성을 수집한 결과가 아니다.

| 합성 입력 | 실제 상위 모델 점수 | 추론 시간 |
| --- | --- | --- |
| 디지털 무음 |Silence0.800781, Sound effect0.007813, Speech0.003906 |221ms |
|1kHz 순음(진폭0.3) |Beep/bleep0.941406, Sine wave0.082031, Music0.042969 |254ms |
| 시드7331의 균등 백색잡음(±0.2) |Static0.667969, Noise0.585938, White noise0.500000 |158ms |

모델 점수는 보정된 확률/정확도가 아니다. 이 세 입력으로 실제 말소리·노크·경고음 정확도나 AI의 지각 개선을 판정하지 않는다.

| 실행·동시 진행 | 실제 관측 |
| --- | --- |
| 모델 로드 |253ms, FLOAT32 입력15600/출력521 검증 통과; 로드 중 DSP0→12프레임 |
| 독립 DSP와 최신 AI 소비 |10초 동안 DSP500프레임/유효 native 추론22회. 추론 단계 DSP14→500,22개 추론 구간 모두 DSP 진행 관측 |
| 추론/소프트웨어 DSP |최대 추론304ms, DSP extract P95 4931µs/최대8277µs |
| 실제 생산자 전달 |간격 P95 21525µs/최대25848µs, 예정 완료 시점 대비 지각 P95 6524µs/최대9184µs. 처리 총량만으로 cadence를 추정하지 않음 |
| 종료 |close540µs, 중복 close 안전, 최종 result=PASS. 전체10276ms/프로세스CPU9062ms |

| 시점 | PSS(API kB) | 사용 Java heap(bytes) | 배터리 온도 |
| --- | --- | --- | --- |
| 전 |63460 |3650968 |36.1°C |
| 로드 후 |75437 |4249336 |36.1°C |
| 스트림 종료 |80388 |3768944 |39.2°C |
| close/GC 요청 후 |75658 |3166832 |39.2°C |

프로세스CPU는 여러 스레드의 합이고 전체 워치 CPU 사용률이 아니다. 배터리 온도는 CPU/피부 온도와 다르다.10초 관측에서 온도가 상승했으며 충전 조건도 포함한다. close 뒤 PSS가 초기값으로 즉시 복귀하지 않은 사실을 숨기지 않는다.5분 열·CPU·메모리 안정성은 이 시험으로 통과 처리하지 않는다. [선별 native 로그](issue19-watch7-native.txt).

## 재현·복원·남은 범위

```text
./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
./gradlew.bat :app:assembleDebug -PnativeCaptureProbe=true -PnativeProbePackage=true
adb -s WATCH_SERIAL install app/build/outputs/apk/debug/app-debug.apk
adb -s WATCH_SERIAL shell am start -W -a com.ullim.watch.PROBE_YAMNET \
  -n com.ullim.watch.nativeprobe/com.ullim.watch.probe.NativeYamnetProbeActivity
```

일반 debug APK SHA256 `ccb6628760054c5f82ba14f163ec596bcce1ce770753cc71239e887b303bae14`; unsigned release `b4e95a25e6d80e7df2c38c9472c1bfec7fa0203e7683aeaba631a93889f36a9b`. 최종 보완은 opt-in 계측 코드만 변경했고 생산 모델 API/자산은 동일하다.

시험 뒤 별도 패키지만 제거했다(`Success`). 기존 울림 APK `6a2e6e16…3006c`와 보정값 `47a0c314…f06c`의 SHA256 일치, AppOps의 마이크 미실행 및 사용자 화면 유지 값1을 다시 확인했다. 기존 앱을 교체하거나 AI 토글을 변경하지 않고 시작 화면을 열어 두었다.

이 기록은 모델 컴포넌트와 독립 소비 경로 검증이다. 실제 감지 서비스/사용자 AI 토글 연결, 결과 평활화/만료,5분 이상 실제 AI+DSP/발열 및 촉각 A/B 유용성은 #20~#22/#36/#37에서 완료해야 한다. 파일 포함·추론 성공만으로 최종 AI 기능/출품 완료를 선언하지 않는다.
