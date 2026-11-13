# #20 분류 결과 평활화·만료 검증

main `ab730a6`(검증된 #19) 위에 기존 PR57의 `2eaa296`만 이식했다. 기존 브랜치/커밋은 보존한다. 검증 소스 `d1bddbe97c352f0342ed96827856ed911c24a496`.

## 로컬 검사와 수치 수정

- 일반 앱 전체77 tests, failures/errors/skips0. test/assemble/lint/unsigned release 성공36초.
- opt-in 별도 앱 전체79 tests, failures/errors/skips0. test/assemble/lint 성공21초.
- 확인 기간400ms의 직전/정확한 경계, 점수0.35와 승자 간격0.08의 통과/실패를 추가했다. 중복·역순·다른 세션·미래·만료·모호함·무신호·원본/EMA 승자 불일치·Top5 상한·정확한 한국어 매핑 검사도 통과했다.
- 반복0.42가 기존 EMA 계산에서0.42000002가 되어0.5와의 간격이0.08000001→0.07999998로 변하는 회귀를 실제 실패로 관측했다. 동등한 `previous + 0.6 * (incoming - previous)`로 바꿔 동일 입력을 보존했다. 임계값을 완화하지 않았다.
- 검증용 Activity 미구현 상태의 컴파일 실패를 관측한 뒤 구현했다. 명시적 실행 action 없이 화면만 열 때 모델 worker를 시작하지 않는 회귀도 통과했다.
- 독립 읽기 전용 전체 변경 검토에서 actionable finding 없음. 모든 번역 키20개가 실제 번들521레이블에 정확히 존재함을 확인했다.
- 일반/release manifest와 DEX에 검증 Activity가 없고 opt-in에만 포함됨을 확인했다. GitHub Actions 미사용.

## 실제 Watch7

SM-L300/API37/L300XXU2ZZK1, 2026-11-14 KST. 최종 별도 APK SHA256 `41051a6dcf76d92dfebb48d6c064a739c2c404c8ec89153f1947c0ba7dd34bff`; 설치된 APK 해시 일치.

마이크·진동·파일 녹음·네트워크 사용 없이 두 종류의 시험을 분리했다. `source=controlled_scores`는 인위적으로 지정한 점수의 상태 전환 검사이며 모델 인식 결과가 아니다. `source=generated_pcm`만 코드로 합성한 오디오의 실제 모델 출력이다. [native 로그](issue20-watch7-native.txt).

제어 점수 시험은 Speech/차량 경적/Knock/Noise 각각의 확인·중복 거부·만료·다른 세션·reset, 교대/모호함/낮은 점수/빈 결과/미래 결과 거부를 실제 워치에서 통과했다.

| 합성 입력(녹음 아님) | 실제 원본 Top 점수 | 첫 창/확인 뒤 표시 |
| --- | --- | --- |
| 디지털 무음 | Silence0.800781 | 불확실→무음 |
| 발성 형태를 흉내 낸120Hz 변조 배음 | Music0.800781, Synthesizer0.261719 | 불확실→음악 |
| 경적 형태를 흉내 낸350+440Hz 이중톤 | Telephone0.992188, Alarm0.968750, Dial tone0.941406 | 승자 간격 부족으로 계속 불확실 |
| 노크 형태를 흉내 낸 감쇠 잡음 | Sound effect0.148438, Explosion0.109375 | 낮은 점수로 계속 불확실 |
| 시드7331 균등 백색잡음 | Static0.667969, Noise0.585938 | 불확실→Static(미번역 원문) |
|1kHz 순음 | Beep/bleep0.941406 | 불확실→Beep, bleep(미번역 원문) |

각 합성 입력마다 실제 단조 시계로500ms 이상 떨어진3개 창, 총18회 추론했다. 반환한 창 종료 시각·점수·한국어 이름·유효 종료 시각을 함께 관측했고, 원본 만료 시각을 연장하지 않았다. 마지막 실제 확인 결과의 만료 시각14938835040814ns 뒤14938837551044ns에 **새 콜백 없이 null**이 됨을 확인했다. 모델 close/context reset 뒤 최종 `result=PASS`.

위 표의 합성 이름은 실제 분류의 정답을 뜻하지 않는다. 배음이 Speech가 아니라 Music으로 분류되고, 이중톤/감쇠 잡음이 경적/Knock으로 인식되지 않은 사실을 그대로 남겼다. 이 검사는 실제 말소리·경적·노크의 인식 정확도나 촉각 개선 합격을 뜻하지 않는다. 점수도 보정된 확률이 아니다.

## DoD와 복원

| #20 완료 조건 | 근거 |
| --- | --- |
| 성공/지연/중지 시 오래된 레이블 영구 표시 방지 | 호스트 만료/반복/긴 공백/reset 검사, 실제 워치 제어 시나리오 및 실제 모델 결과의 새 콜백 없는 만료 |
| 분류 기준값·상태 전환 단위 검사 | 전체77/79개 통과, 경계 수치 회귀 수정 |
| 타임스탬프·점수 UI 제공 계약 | 기존 Classification의 원본 timestampNs/validUntilNs 및 Top5 점수 보존, SoundLabels 정확한 매핑; 실제 워치 로그 |

실제 실행기 loading/disabled/error 상태와 UI 연결은 #22/#27의 범위다. 소비자는 매번 `current`/`isFresh`를 호출해야 하며, 이 컴포넌트는 화면 타이머나 감지 서비스를 대신 구현하지 않는다. 실제 인식 정확도·AI 촉각 A/B·5분 발열/성능 및 최종 출품 검증도 계속 남는다.

시험 패키지만 제거한 뒤 기존 울림 APK `6a2e6e16…3006c`/보정값 `47a0c314…f06c`의 SHA256 유지, 마이크 미실행, 사용자 화면 유지 값1을 확인하고 시작 화면을 열어 두었다. 기존 앱이나 AI 토글은 교체/변경하지 않았다.

재현: 일반 `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug`; 별도 시험은 `-PnativeCaptureProbe=true -PnativeProbePackage=true`로 빌드해 별도 패키지를 설치하고 `com.ullim.watch.PROBE_CLASSIFICATION` action으로 `com.ullim.watch.probe.NativeClassificationProbeActivity`를 실행한다.

일반 debug SHA256 `901c2c66a933eea650b660d8acfafea1eb847b90ef1f376a3755f2668b30cfe1`; unsigned release `a75f02729ce9155c27105524626ef99071d9bd97b7793082fa4906bc396caa55`.
