# 실제 앱의 AI 연결 수정 이식·설치

검증된 PR76/b1375ed의 ClassificationSmoother/HybridHapticMapper와 해당 테스트만 현재 작동 앱 abc51a4에 이식했다. 새 소스 `ee25d9d242037ff9db2febdb6a240d499e56f2a5`,4파일78추가/10삭제다. 모터 보정·프로필 키/형식·진동 주기·렌더러·UI·설정 코드는 변경하지 않았다.

- 전체221 tests,failures/errors/skips0,로컬 test/assemble/lint 성공35초.
- 독립 읽기 전용 검토에서 actionable finding 없음.4개 파일이 검증된 PR76과 일치하고 기존 AiWorker→LatestClassification→ListeningService/HybridHapticMapper 계약이 유지됨을 확인했다.
- 동일 점수의 EMA 반올림 경계와 실제 Static 등 관련 잡음 레이블의 정책 연결을 수정한다. 점수 합산/고정 소리별 진동 패턴을 만들지 않는다.
- GitHub Actions 미사용. 별도 장시간 시험이 끝난 뒤,실제 서비스/마이크가 중지된 것을 확인하고 기존 앱에 install-r했다. 앱/데이터를 삭제하지 않았다.

## 실제 설치 확인

SM-L300/API37/L300XXU2ZZK1. APK53294025bytes/SHA256 `064dd0d12ca19dc53a9ac7bceb9e3b0be0dca0f43732dee21ed7e7925da08919`,설치된 base.apk 해시 일치. MainActivity cold launch Status=ok,5429ms.

기존 정상 APK6a2e6e16…3006c는 별도 백업으로 보존했다. 설치 뒤 숫자 보정 파일의 SHA256 `47a0c31418640333f4624240f37690f410d70423750ad7bb70675866b751f06c` 일치,사용자 화면 유지 값1,마이크 미실행을 확인했다. 설정 저장 파일은 설치 전후 모두 없었고 기본 AI OFF/민감도1의 소스 기본값을 바꾸지 않았다. AI 토글이나 감지를 자동으로 켜지 않았고 시작 화면을 열어 두었다.

별도 #22 시험의5분 ON/599추론/모델 close는 원본0278 AiWorker와 합성 입력의 컴포넌트 시험이다. 현재 전체 앱의 추가 UI 메타데이터·마이크·서비스 경로 전체를 같은 조건으로 재시험한 증거가 아니다. [AI 정책 근거 PR76](https://github.com/chlgusrbs0/ullim/pull/76).

아침에 실제 AI ON/OFF 촉감·실제 감지 서비스·보정 후 단발/무음·강약/지속/리듬을 확인해야 한다. 잘게 끊기는 현재 진동 질감과 물리 지연/강약 구별은 여전히 미해결/미측정이다. PR73/#17을 이 이식만으로 머지하거나 닫지 않는다.
