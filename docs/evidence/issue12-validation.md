# #12 DSP→촉각 명령 검증

기존 PR50/브랜치와 원본 매퍼 `4f8322e`를 보존하고 검증된 main `4902ca7`을 통합했다. 통합 직후 main과의 차이는 원래 매퍼/테스트2개 파일뿐이었다. 생산 매퍼 변경 없이 최신 DSP와 연결한 검증을 추가했다. 검증 소스 `a9c2e4f2b32b3bc18190e1591b143b8094328028`.

- 일반 전체85 tests, failures/errors/skips0, 로컬 test/assemble/lint 성공23초.
- 별도 opt-in 전체87 tests, failures/errors/skips0, test/assemble/lint 성공26초.
- 기존 특징값 강도 순서/0.85 상한/세션/무음/잔여 엔벌로프 검사에 실제 합성 PCM→DSP→매퍼의 강도·민감도 순서,5초 지속/첫 무음 중지,발화 형태 진폭 변조 검사를 추가했다.
- 독립 읽기 전용 전체 변경 검토에서 actionable finding 없음. 고정된 이전 상태/onset/noise 조건의 입력 증가에 대한 출력 단조성과 시간/길이/세션 보존 확인.
- GitHub Actions 미사용. UI에는 공통 HapticFrame.commandedIntensity와 원본 시각/길이를 제공하며 물리 측정값이라고 부르지 않는다.

## Watch7에서의 수치 검증

SM-L300/API37/L300XXU2ZZK1,2026-11-14 KST. 별도 패키지 `com.ullim.watch.nativeprobe`, APK SHA256 `b5189b35acee65d24ee72b7952f4156126e25298650ee639bbe9e7dda45f4260`; 실제 설치 APK 해시 일치. 같은 프로세스 PID404의 최종 PASS를 관측했다. [native 로그](issue12-watch7-native.txt).

| 검사 | 실제 명령값/결과 |
| --- | --- |
|1kHz 합성 PCM 진폭2000/8000/24000,동일25프레임 이력 |0.26461217 <0.5456316 <0.85 |
| 진폭2000 고정, DSP 민감도0.5/1/2 |0.18710907 <0.26461217 <0.3742181 |
|3개 노크 프레임5/20/45 |출력 선두5/20/45,간격300/500ms 보존 |
| 합성5초 지속음 |20ms 명령250개 모두 비영,최소0.38466227;원본 timestamp/길이 보존 |
| 지속음 뒤 첫 무음 |즉시0,고정 길이 알림/잔여 진동 명령 없음 |
| 발화 형태 진폭 변조100프레임 |서로 다른 강도92개,최대-최소0.8499317 |

이 시험은 워치 CPU에서 합성 PCM의 논리적 시각을 빠르게 주입해 명령 수치를 계산했다. 실제5초 마이크 녹음·모터 출력·물리 지연·매끈한 촉감 측정이 아니다. `motor=false,ai=false`를 로그에 명시했다. 실제 강약/연속성/지연 및 사용자 평가는 #17/#18/#37에서 여전히 완료해야 한다.

## DoD/복원/재현

입력 증가의 단조성은 같은 상태/특징 조건의 호스트 검사와 동일25프레임 PCM 이력 비교로 확인했다. 노크3개 간격·긴 소리 지속은 호스트/native 명령 시퀀스로 확인했다. 모든 검사가 AI 문맥 없이 실제 DSP→매퍼로 수행돼 기본 경로의 독립성을 확인했다. 이는 물리 촉각 합격을 대신하지 않는다.

시험 뒤 별도 앱만 제거했다. 기존 울림 APK `6a2e6e16…3006c`/보정값 `47a0c314…f06c`의 SHA256 유지,마이크 미실행,사용자 화면 유지 값1을 재확인하고 기존 시작 화면으로 복귀했다.

일반 APK SHA256 `3524c1597f76ad0f8518c8b0a180183d94900517098180be07b1aac343ef3e9a`. 검증 코드는 기존 opt-in debug source set에만 있다.

재현: `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug`. 별도 검증은 `-PnativeCaptureProbe=true -PnativeProbePackage=true`로 빌드/설치한 뒤 `NativeDspProbeActivity`를 `com.ullim.watch.PROBE_DSP` action과 `--es scenario mapping`으로 실행한다. 원본 앱 패키지를 교체하지 않는다.
