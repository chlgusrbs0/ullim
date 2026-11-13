# 실제 서비스 반복 검증 도구와 첫 실행 (#23)

**이번 Watch7 실행은 실패다.** 새 보정이 UNSTABLE_PHASE_0으로 거절돼 서비스 반복0회/AI추론0회로 끝났다. 도구 구현과 로컬 검사를 마쳤지만 실제 마이크→서비스→AI ON/OFF 반복 성공을 입증하지 못했다. #23/PR61 및 관련 #17 게이트를 유지한다.

## 범위와 로컬 검증

현재 통합 후보1ccb5fc에 opt-in 검증 도구만 추가했다. 소스 `24afe911c22954f23ef2051b7817ddb8d431db4f` 및 검토 보완 `0eec9176ae71632d5d8c6b25473d48281921d244`. 원본 PR61/e615를 이 소스로 교체하거나 그 원본 전체가 실기기 통과했다고 주장하지 않는다. 기본 ListeningService/AudioRecord/DSP/AI/렌더러/설정/프로필의 제품 코드는 변경하지 않았다.

별도 .nativeprobe Activity에서 새 보정 후3회 실제 서비스 사이클을 요청하도록 구현했다. 각 사이클은 AI OFF3초→ON8초→OFF2초, 중복 시작 요청8개 거절/동일 START3개 추가/오래된 STOP 거절/STOP3개 추가와 종료 장벽을 검사한다. 실제 SettingsRuntime 변경과 저장, DSP 시각화 수치 진행, 네이티브 AI 성공 횟수, 모델 정리, 녹음 구성/알림 수를 확인한다. 공개 [AudioManager.getActiveRecordingConfigurations](https://developer.android.com/reference/android/media/AudioManager#getActiveRecordingConfigurations())의 수는 기기 전체 관찰이며 특권 per-UID 계측이 아니다. 시작 전0, 감지 중1, 끝난 뒤0을 요구하고 다른 녹음이 있으면 중단한다. 이 수가0이라고 미사용 객체의 release까지 증명하는 것은 아니다.

새 Activity의 action/패키지/권한 경계를 검사한다. 마이크·알림 권한이 없으면 수집 전에 실패하고, com.ullim.watch.nativeprobe 외의 패키지는 저장소·마이크 접근 전에 거절한다. 화면 이탈·초점 상실·시간 초과·오류는 취소와 실제 종료 확인을 거친다. 원본 PCM/음성/분류명은 로그나 파일로 보존하지 않는다.

- 최초 누락 구현 컴파일 RED23초, 경계3개 테스트/build/lint GREEN22초.
- 알림 권한 누락이 검증을 우회할 수 있는 의미적 RED1건10초 뒤 수정.
- 전체240 tests, failures/errors/skips0 및 test/build/lint 성공37초. 검증 테스트를 testDebug에만 두고 opt-in releaseUnitTest 컴파일도 성공23초.
- 정상 앱 전체236 tests, failures/errors/skips0 및 test/build/lint/release 성공58초. 정상 debug/release manifest/DEX에 Session 검증 Activity 부재를 확인했다.
- 독립 읽기 전용 검토의4경로를 각각 의미적 RED로 재현했다(4tests/4failures,16초). 설정 ON 저장 실패가 뒤의 OFF 성공에 가려짐, 보정 lease에 서비스 STOP이 발송됨, 정상 진행 중 추론의 OFF 정리 완료를 실패로 취급함, 정리된 재시도 가능 Error를 정리 실패로 기록함이었다.
- 한 차례 보완 뒤 **244tests/실패·오류·skip0, test/build/lint/opt-in releaseUnitTest compile 성공42초**. 각 설정 변경의 저장을 확인하고, 서비스 사이클 토큰만 STOP하며, 정리 뒤 추론 횟수를 고정하고, FAIL/CANCELLED에는 정리된 Error를 허용하되 PASS에는 Idle을 요구한다. 제품 코드 변경이 없는 probe 전용 보완이며 재검토를 반복하지 않았다. 미룬 경미 사항은 없다.

## Watch7 첫 실행

SM-L300/API37/L300XXU2ZZK1. APK52887418bytes, SHA256 `1d5a6b6317d3aefb6a2486e8b1db98d88578138d7652c336ce7f357980fe7efd`; 설치된 base.apk 해시 일치. PID5548,2026-11-14 07:09:23–27 KST. 마이크/알림 권한은 이 임시 패키지에만 부여했다. [수치 로그](issue23-service-watch7.txt).

실제 마이크를 열고 기존 MotorCalibrationRunner를 실행했다. 기준18프레임/각 출력17프레임/안정성 표본각5개를 얻었으나, 첫 출력 구간 안정성 검사가 실패해 saved=false였다. 사용자 착용 확인 없이 실행한 현재 조건의 결과이며, 그 원인을 착용 상태나 주변음으로 단정하지 않는다. 기존 사용자 착용 보정값의 실패를 뜻하지도 않는다. 임계값을 낮추거나 생성 fixture로 바꿔 성공 처리하지 않았다.

최종 result=FAIL/code=CALIBRATION_FAILED/cycles=0/totalInferences=0/cleanupVerified=true/3664ms. 실제 RECORD_AUDIO 사용2s890ms 뒤 running 아님, 임시 앱 서비스 목록nothing을 확인했다. 이 실행으로3회 서비스/AI 반복, 중복 명령의 OS 처리, 실제 AI 전환/모델 정리를 통과했다고 기록하지 않는다.

최종 결과와 정리를 확인한 뒤 임시 앱만 제거했다. 기존 울림 설치 APK는 SHA256 `4b206f345b6660cdb89e23d57bc1b3965ac2970993f8ecc8abe6d08153a3efaa`, 숫자 보정4262bytes/SHA256 `47a0c31418640333f4624240f37690f410d70423750ad7bb70675866b751f06c` 그대로다. 무효화 표시 없음/화면 유지1/글씨1.0/기존 마이크 미실행과 서비스 없음도 확인했다. 원래 시작 화면으로 복귀했고 사용자 설정을 바꾸지 않았다.

## 남은 게이트

착용 상태에서 명시적 보정의 안정성을 재확인하고 실제 서비스/AI 반복을 실행해야 한다. 권한 회수·서비스 파괴·화면 꺼짐/잠금·오류 재시작·물리 진동 중지의 다른 OS 시나리오도 남는다. 실제 강약·매끈함·물리 지연·블라인드 정확도는 이 검증 도구의 성공 여부와 별개다. 선행 #6/#16/#22도 아직 main 완료가 아니다. 자동 검증만으로 실제 하드웨어/OS 실패를 놓칠 수 있으므로 해당 인수 기준을 완화하거나 #23을 머지·종료하지 않는다.
