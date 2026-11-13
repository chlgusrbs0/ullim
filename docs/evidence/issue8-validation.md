# #8 RMS·peak·에너지 엔벌로프 검증

## 대상

선행 #3·#7 main 병합 후 `f94d006`을 통합했다. 실행 소스 `eac9a91abe701bed36cc1461393c1522a938c3e8`. 표준 RMS와 지수 평활식을 독립 Kotlin으로 구현했으며 외부 코드를 복사하지 않았다. AudioFeatures의 임시 List 검증을 여섯 직접 호출로 바꿨고 공통 타입/검증 범위는 유지했다.

## 로컬·독립 검토

- opt-in 별도 패키지: 전체42 tests, failures/errors/skips0, test/assemble/lint 성공25초.
- 일반 debug: 전체41 tests, failures/errors/skips0, test/assemble/lint 및 unsigned release 성공26초.
- 무음, 강도 단조성, PCM16 음수 최솟값, 사인파 해석 RMS,20ms attack/120ms release 시정수, reset/세션 변경 테스트 통과.
- 독립 읽기 전용 검토 `f94d006..eac9a91`: actionable finding 없음. 시각 불연속·잘못된 길이의 통합 경계는 #11 범위로 구분했다.
- 일반 debug/release DEX와 manifest에 NativeDspProbeActivity 없음, opt-in APK에는 존재함을 대조했다. GitHub Actions는 사용하지 않았다.

## 워치7 합성 실행

SM-L300 / Android17 API37 / L300XXU2ZZK1, 2026-11-14 KST. 두 옵션 `-PnativeCaptureProbe=true -PnativeProbePackage=true`를 지정한 별도 `com.ullim.watch.nativeprobe`에 설치했다. 원래 울림을 교체하거나 마이크·진동을 활성화하지 않았다.

- 시험 APK: 25,807,096 bytes / SHA-256 `10a37dbc4af2afd4348b48d5b53c2ea986369bfd2c88de3f3e2c949ed188ee93`. 기기 내 APK 해시도 일치.
- 일반 debug SHA-256 `e203a17ffde99566ed65ba831bfdd43de411cc4ac9b0f16ee7f03553c8ae96b3`; unsigned release `b1aa2e862d9ae96ae97ab9920f767c56ef1721f66f74d7055b4eebf5798d666f`.

| 검사 | 실제 결과 |
| --- | --- |
| 무음·강도 순서·클리핑 |0 RMS/peak 및 무음,100→1000→10000→32767 순서, -32768에서 RMS/peak1.0 검사 통과 |
| 사인파 RMS |0.35353953, 해석 기준0.5/√2와 오차0.0001 미만 |
| 계단 입력 | 첫 attack0.31606027, 무음6프레임 뒤 release0.11627209, 지수 기준 오차0.00001 미만 |
| reset·세션 변경 | 잔여 엔벌로프 제거 검사 통과 |
|10분 분량 합성 스트림 |30000프레임, 실제3761ms, extract 단일 호출 최대5994µs, 유한한 특징값 유지 |
| 결과 |result=PASS. 시험 패키지 RECORD_AUDIO AppOps ignore, 실제 마이크 미사용 |

10분은 가속 합성 입력 길이이며 벽시계10분 시험이 아니다. 최대5994µs는 해당 합성 시험의 소프트웨어 호출 관측이고 물리 소리→모터 지연이 아니다. 실제 마이크와 전체 소음/리듬/촉각 파이프라인은 후속 이슈에서 검증한다. 수치 로그: [issue8-watch7-native.txt](issue8-watch7-native.txt).

시험 뒤 직접 만든 별도 패키지만 제거했다. 원래 보정 파일 SHA-256 `47a0c31418640333f4624240f37690f410d70423750ad7bb70675866b751f06c`, 마이크 미실행, 사용자의 충전 중 화면 유지 값1을 확인했다. 원래 울림 메인으로 복귀했다. 원음 파일/음성 로그/네트워크 전송은 없다.
