# #21 실제 모델→평활화→제한된 촉각 정책

기존 PR58의 단일 커밋1651f85를 선행 #12/#20이 머지된 main8398b0a 위에 이식했다. 기존 브랜치/커밋을 보존한다. 최종 검증 소스87bd0719cdc442953dbaaf89d3004c13e87fa3df.

## 실제 연결 공백과 수정

고정 모델은 합성 백색잡음에 Static0.667969/Noise0.585938/White noise0.5를 출력한다. 기존 정책은 Noise 단일 이름만 지원하고 관련 클래스끼리도0.15 간격을 요구해, 평활화가 확인한 Static을 무시했다. 정확한 측정 점수를 평활화→정책에 통과시키는 회귀가 실제 실패했다.

Static/White noise/Pink noise/Wind noise (microphone)를 기존 NOISE gain0.94에 연결했다. 최고 개별 점수0.65와 다른 정책/미지원 클래스에 대한 간격0.15는 유지한다. 같은 정책을 가리키는 관련 레이블끼리는 경쟁으로 간주하지 않으며 **점수를 합산하지 않는다**. 원본 표시 레이블도 바꾸지 않는다. 무음·이력·시간·길이·출력0.85 상한·기본 대비10% 범위와 OFF 즉시 baseline 복원은 유지한다.

- 최종 일반92 tests, failures/errors/skips0. 로컬 test/build/lint/unsigned release 성공28초.
- 최종 opt-in96 tests, failures/errors/skips0. test/build/lint 성공24초. 일반/release manifest·DEX에는 시험 Activity/HybridPolicyVerification이 없고 opt-in에만 존재한다.
- 독립 읽기 전용 전체 변경 검토의 P2: 초기 native 만료 검사는 모든 만료 프레임이 무음이어서 검증력이 부족했다. 별도 비영 입력100프레임을 추가했고, 만료를 무시하는 잘못된 매퍼를 실제로 거부하는 오류 주입 검사와 정상 매퍼 검사가 통과했다. 생산 만료 로직은 원래 호스트 검사에 포함돼 있었으며 이 지적 때문에 변경하지 않았다.
- 다른 검토 지적 없음. GitHub Actions 미사용.

## 실제 Watch7 수치 A/B

SM-L300/API37/L300XXU2ZZK1,2026-11-14 KST. 별도 패키지 APK SHA256 `6e4e79c734adaa6544643d999ab6ad1e6fb97f73d5ef7cc50575c0859243e033`; 설치 APK 해시 일치,최종 PID2616 result=PASS. [선별 native 로그](issue21-watch7-native.txt).

번들 YAMNet이 코드로 만든 잡음/무음/순음을 각2번 실제 추론했다. 두 창의 실제 단조 시각을500ms 이상 분리해 실제 평활화로 문맥을 확인했다. 다음 명령 비교는 같은 PCM을 같은 DSP에 한 번 통과시켜 baseline/hybrid에 동시에 전달한 **빠른 논리 시각 재생**이며,실시간 생산자 간격/마이크/모터 시험이 아니다. 모델 문맥을 임의로 Noise라고 주입하지 않았다.

| 실제 모델 문맥 |100명령 A/B(앞50 AI,뒤50 OFF) | 명령 MAE |
| --- | --- | --- |
| Static0.667969/Noise0.585938/White noise0.5 |앞50개 실제 변경,뒤50개 baseline과 정확히 같음 |0.01085649460554123 |
| Silence0.800781 |변경0,무음 유지 |0 |
| Beep, bleep0.941406 |미지원 정책으로 변경0,기본 DSP 유지 |0 |

모든 프레임의 무음 여부·세션·시각·길이와 출력 상한을 유지했다. 동일한 실제 잡음 문맥을 일부러 노크에 잘못 적용한 재생에서도 선두5/20/45와300/500ms 간격이 유지되고 레이블만으로 새 진동 명령을 만들지 않았다. 별도 비영 잡음 입력100프레임은 만료 전75개 보정,만료 시각 이후25개 **소리가 계속 있어도 baseline과 정확히 같음**을 확인했다. 모델 close 완료.

MAE는100개 명령의 절대 차이 평균이며 뒤50개 OFF도 분모에 포함한다. 실제 모델이 출력을 바꿨다는 근거이지,잡음 인식 정확도·손목에서 느끼는 개선·매끈한 연속 진동 합격의 근거가 아니다.

## 아직 남은 게이트

#21은 실제 AI ON/OFF **촉감 비교가 미실시**여서 Draft/이슈 open을 유지한다.5분 이상 독립 실행기/DSP·발열·메모리·토글 정리는 #22,실제 촉각 유용성은 #37에서 검증해야 한다. 현재 사용자 앱에 생산 통합본을 배포했다거나 AI 토글을 켰다고 주장하지 않는다.

시험 앱만 제거한 뒤 기존 울림 APK6a2e6e16…3006c/보정값47a0c314…f06c의 SHA256 유지,마이크 미실행 및 사용자 화면 유지 값1을 확인하고 기존 시작 화면으로 복귀했다.

일반 APK SHA256 `9a4777790de74ac01bdf323d7d56191fd279b298b09438e31132f15023810ce0`; unsigned release `73e9305bd4aa6d2c800f31d3af562155dad173b46359e622e9f4a5147c006432`. 마지막 검토 수정은 opt-in 검사만 바꿨으며 생산 소스/일반 APK는 동일하다.

재현: 일반 `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug`; 별도 앱은 `-PnativeCaptureProbe=true -PnativeProbePackage=true`로 빌드/설치하고 NativeClassificationProbeActivity를 `com.ullim.watch.PROBE_HYBRID` action으로 실행한다. 원본 마이크·모터·사용자 음원 저장/전송은 없다.
