# #10 시간적 리듬·onset 검증

## 대상·범위

선행 #8·#9 main 병합 후 `faa003b`를 통합했다. 실행 소스 `06a6b53b0853082a618e8276d3a12d1c364719a4`. Envelope→NoiseGate→Onset 조합과100ms 재검출 억제, 연속 level, 세션/reset 초기화를 검증했다. 소리 종류별 고정 진동 패턴은 추가하지 않았다.

## 로컬·독립 검토

- opt-in 별도 패키지 전체55 tests, failures/errors/skips0, test/assemble/lint 성공24초.
- 일반 debug 전체54 tests, failures/errors/skips0, test/assemble/lint 및 unsigned release 성공24초.
- 노크5/20/45프레임(간격300/500ms) 검출,0~5000ms의251프레임 모두 non-silent·onset1회, 발화 유사 강약 변조와 종료 후 무음, 재검출 억제 및 reset 테스트 통과.
- 기존 지속음 테스트를 첫100프레임 일부만 보는 대신 전체251프레임의 연속 출력을 확인하도록 강화했다.
- 독립 읽기 전용 검토 `faa003b..06a6b53`: actionable findings 없음. 시각 불연속/역순/길이 경계는 #11에 배정된 범위로 구분했다.
- 일반 debug/release의 manifest·DEX에 NativeDspProbeActivity가 없고 opt-in에만 있음을 대조했다. GitHub Actions는 사용하지 않았다.

## 워치7 실제 합성 실행

SM-L300 / Android17 API37 / L300XXU2ZZK1, 2026-11-14 KST. 두 옵션 `-PnativeCaptureProbe=true -PnativeProbePackage=true`로 별도 `com.ullim.watch.nativeprobe`를 설치했다. 기존 울림을 교체하지 않았다. Native temporal 케이스는 합성 PCM과 특징을 사용하며 실제 마이크·모터를 호출하지 않는다.

- 시험 APK25,807,096 bytes / SHA-256 `829411c31e7eb5fa11706a5277b3dec46c37fce0a3d6529e73fce252a0809f8c`, 기기 설치 해시 일치.
- 일반 debug SHA-256 `8f2be13d7ddc99d00074352af958e8f8e612c4d27b5e0449374a24b0f52afce7`; unsigned release `a98d479d70c09b54cb8771823a9c21e6ad97c05e408b6036f0bd68e05d1fca06`.

| 검사 | 실제 결과 |
| --- | --- |
| 서로 다른 노크 간격 |5/20/45프레임 검출,300/500ms 간격 보존 |
| 지속 입력 |251프레임 전체 연속 level>0, onset1회 |
| 발화 유사 변조·종료 | 서로 다른 level94개, 최종 tail0.0 |
| 재검출 억제 |40ms 재검출 차단,100ms 경계에서 허용 |
| 세션 변경 |새 세션의 첫 onset 정상 검출 |
| 종료 |result=PASS, 시험 패키지 RECORD_AUDIO AppOps ignore |

[선별 수치 로그](issue10-watch7-native.txt). 합성 입력의 시간축을 가속 처리한 것이며 실제 경과5초/마이크/사람 손목의 리듬·물리 지연 시험이 아니다. 실제 환경/촉각 통합은 후속 게이트에서 검증한다. 기존 #9의 동일 세기 장기 지속음이 배경으로 학습될 수 있는 한계를 이 검증으로 제거했다고 주장하지 않는다.

첫 자동 실행 도구는 미설치 패키지에 대한 pm path의 정상 exit1을 연결 끊김으로 잘못 처리해 설치 전에 중단했다. get-state로 실제 연결을 분리 확인한 뒤 위 동일 APK로 재실행했다. 앱/알고리즘 실패로 기록하지 않는다.

시험 뒤 직접 만든 별도 패키지만 제거했다. 원래 보정 해시 `47a0c314…f06c`, 마이크 미실행 및 사용자 화면 유지 값1을 확인하고 기존 울림 메인으로 복귀했다. 원음 파일·음성 로그·네트워크 전송은 없다.
