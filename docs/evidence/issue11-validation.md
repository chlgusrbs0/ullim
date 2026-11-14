# #11 DSP 경계·성능 검증

## 대상과 로컬 재현

선행 #10 main 병합 후 `0b2cd56`을 통합했다. 실행 소스 `2613a9e9236eddd65a71f18f6dc886242f3876bb`. 생산 DspProcessor의 기존 길이·시각·세션 방어를 유지하고, 잘못된 입력 이후 다음 정상 프레임의 상태 일치와 partial/stop 경계를 보강했다.

- opt-in 별도 패키지 전체62 tests, failures/errors/skips0, test/assemble/lint 성공28초.
- 일반 debug 전체61 tests, failures/errors/skips0, test/assemble/lint 및 unsigned release 성공24초.
- 경계7 tests에는7종 합성 픽스처, 길이/역순/중복 거부, 입력 공백 초기화, 오류 뒤 정상 reference 일치, 이전 세션 격리, partial 조립·중지 뒤 tail 차단·재시작 및10분 부하가 포함된다.
- 로컬 합성30000프레임:482ms, DSP P95 2000ns/최대1731200ns. JVM heap86699704→72017816 bytes는 GC 시점의 일시 관측이고 retained memory 증거가 아니다.
- 독립 읽기 전용 검토 `0b2cd56..2613a9e`: actionable findings 없음. native timing 배열의 모든30000칸을 각 burst마다 덮고 정렬하며, 같은 활성 세션에서 메모리를 재는 것을 확인했다.
- 일반 debug/release의 manifest·DEX에 시험 진입점/검증 클래스가 없고 opt-in APK에만 있음을 대조했다. GitHub Actions는 사용하지 않았다.

```text
./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
./gradlew.bat :app:testDebugUnitTest --tests '*DspBoundaryTest'
```

## 워치7 실제 합성 실행

SM-L300 / Android17 API37 / L300XXU2ZZK1, 2026-11-14 KST. `-PnativeCaptureProbe=true -PnativeProbePackage=true`로 만든 별도 패키지 `com.ullim.watch.nativeprobe`를 사용했다. 실제 마이크·모터·네트워크를 호출하지 않고 합성 PCM을 메모리에서 생성했다.

APK25,823,480 bytes / SHA-256 `b6c168bac66f4aecc0c7a856f15236307da88fed8f75cede15c79d08a0e9eac6`. 설치된 APK의 해시도 일치했다. 길이1/319/321/640 거부, 역순·중복 거부 뒤 reference 상태 일치, 입력 공백·세션 전환·partial 조립·중지 직후 tail 차단이 모두 통과했다.

같은 활성 fanout/DSP 세션에서 무음·저음량·클리핑·지속톤·대역 제한 잡음·노크·발화 변조7종을100프레임씩 순환하며 가속 처리했다. 메모리 표본은 최신 AI 창을 소비하거나 stop하기 **전** GC 요청 후 Java heap이다.

| 누적 프레임/합성 분량 | burst 실제 경과 | DSP P95/최대 |20ms 초과/연속 초과 | 활성 Java heap |
| --- | --- | --- | --- | --- |
|30000 /10분 |7103ms |105/7985µs |0/0 |3125560 bytes |
|60000 /20분 |5018ms |57/6264µs |0/0 |3133752 bytes |
|90000 /30분 |5144ms |55/5944µs |0/0 |3133752 bytes |
|120000 /40분 |5074ms |54/5727µs |0/0 |3133752 bytes |

총 제출/출력120000프레임 일치. Java heap 증가8192 bytes 후 표본상 유지, 최신 AI 창1개만 존재. 고정 timing 표본30000개를 재사용했다. 최종 result=PASS. [선별 수치 로그](issue11-watch7-native.txt).

소프트웨어 검증기의 개발 한계는 P95<20ms·3회 연속 초과 없음·관측 heap 증가4MiB 미만이며 실제 관측은 **20ms 초과0회**다.40분은 가속 합성 입력 길이이고 벽시계40분 실행이 아니다. burst 경과에는 신호 생성/fanout, DSP timing에는 extract 호출이 포함된다. Java heap은 PSS/네이티브 메모리나 누수 부재 전체의 증명이 아니다. 실제 마이크/AI모델 동시 장시간 동작 및 물리 촉각 지연은 별도 게이트다.

검증 뒤 직접 만든 별도 패키지만 제거하고 기존 보정 해시 `47a0c314…f06c`, 마이크 미실행 및 사용자의 충전 중 화면 유지 값1을 확인했다. 기존 울림 메인으로 복귀했다. 원본 음성·마이크 PCM 저장이나 전송은 없다.
