# #9 적응형 소음 억제 검증

## 대상·범위

선행 #8 main 병합 후 `8cff62a`를 통합했다. 실행 소스 `64137706b7b42869866cbea2244d3283b8ce51bd`. 기존 NoiseGate 생산 구현을 유지하고 실제 PCM 시나리오와 워치 합성 검증을 보완했다. 소음 바닥·히스테리시스·민감도0.5~2·압축 및 세션 초기화를 검증한다.

이 단계는 에너지 기반의 적응형 gate다. 동일 세기의 긴 지속음도 배경으로 학습할 수 있으며 모든 환경음의 의미를 판별하는 잡음 제거/AI 모델은 아니다. 이 한계를 촉각 지속시간·AI 기여·실제 환경 최종 인수 통과로 바꾸어 기록하지 않는다.

## 로컬·독립 검토

- opt-in 별도 패키지: 전체51 tests, failures/errors/skips0, test/assemble/lint 성공30초.
- 일반 debug 전체50 tests, failures/errors/skips0, test/assemble/lint 및 unsigned release 성공25초.
- 기존 gate5개 및 새 PCM 시나리오4개 포함. EnvelopeExtractor→NoiseGate에 합성 팬·노크·발화 유사 변조·클리핑·무음을 통과시켰다.
- 독립 읽기 전용 검토 `8cff62a..6413770`: actionable findings 없음. 실제 마이크/모터 시험과 합성 특징·PCM 시험의 범위를 구분했다.
- 일반 debug/release manifest·DEX에는 NativeDspProbeActivity가 없고 opt-in APK에만 있음을 대조했다. GitHub Actions는 사용하지 않았다.

| 합성 PCM 시나리오 | 실제 관측 |
| --- | --- |
| 학습된 팬 위 노크3회 | 입력/출력 gate 시작 프레임 모두1000/1020/1060, 서로 다른 간격 보존. 종료 후 tail 최대0. |
| 팬 위 발화 유사 변조 | 사건100프레임 중100프레임 응답, peak0.60253143, 최종0. |
| 같은 팬에서 소리 크기 증가 | 입력 진폭0.08/0.16/0.32의 출력 peak0.28843218/0.43663692/0.63946646, 순서 보존. |
| 클리핑 뒤 무음 | 유한하고0~1 범위 유지, 무음 뒤 출력 재증가/재열림 없이0으로 감소. |

시나리오 수치는 JUnit 결과의 system-out에서 재현 가능하다. 실험 입력은 코드로 생성했고 실제 주변 음성/마이크 PCM을 저장하지 않았다.

## 워치7 실제 실행

SM-L300 / Android17 API37 / L300XXU2ZZK1, 2026-11-14 KST. `-PnativeCaptureProbe=true -PnativeProbePackage=true`의 별도 패키지 `com.ullim.watch.nativeprobe`를 사용했다. 기존 울림을 교체하지 않았다. 이번 native noise 케이스는 **합성 AudioFeatures**를 사용하며 실제 마이크·모터는 열지 않는다.

- APK25,807,096 bytes / SHA-256 `e06e2173358658d525e856784b8ce7f85422f2652b08072a0e66fc88f17d01c4`. 기기 내 해시도 일치.
- 배경3000프레임(합성60초) 뒤 level0.0, noiseFloor0.029999815. 다음 순간 변화 level0.72871834로 보존.
- 같은 초기 입력에서 낮은/높은 민감도 level0.31010723/0.62021446. 상한·NaN 민감도 거부·히스테리시스·세션 초기화 모두 통과.
- result=PASS. 시험 패키지 RECORD_AUDIO AppOps ignore. [선별 수치 로그](issue9-watch7-native.txt).
- 일반 debug SHA-256 `bb07da68f2f788b4ef96ff8fc989f0da2b6a1524492596c8b31bb43472648b44`; unsigned release `d49742fdfe5d20864c4459f1a13d63771ba359826804aedb6caf538840469d50`.

합성60초는 가속 입력 길이이며 실제 경과60초 또는 실제 팬/사람 음성 인식 시험이 아니다. 물리 진동 강약·매끈함·지연 합격으로 사용하지 않는다.

시험 뒤 직접 만든 별도 패키지만 제거했다. 기존 APK `6a2e6e16…3006c`, 보정값 `47a0c314…f06c`, 마이크 미실행, 사용자의 충전 중 화면 유지 값1을 확인하고 기존 울림 메인으로 복귀했다. 원음 저장·전송·다른 앱 로그 수집은 없다.
