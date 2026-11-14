# 해커톤 MVP 현황 — 2026-11-14

런타임은 [PR86](https://github.com/chlgusrbs0/ullim/pull/86)/`6424683`으로 고정했다. 사용자가 승인한35개 수치·민감도125%·AI ON을 앱 기본값으로 통합하고 설치했으며 기존 저장값/보정 파일도 보존했다. 현재 정리는 문서와 GitHub관리 작업이며 새 코드·빌드·테스트·워치 조작을 포함하지 않는다.

이슈가 열려 있다고 해당 기능이 전부 미구현인 것은 아니다. 아래 표는 구현, 후속 평가, 추가 작업 보류를 구분한다. 구현 이슈 종료를 물리 지연·블라인드 구별률·AI 효과의 합격으로 해석하지 않는다. 원래 기준과 실패 기록은 각 이슈/증거 문서에 보존한다.

## 구현 완료로 정리하는 항목

| 이슈 | 현재 상태와 기존 근거 |
|---|---|
| [#9](https://github.com/chlgusrbs0/ullim/issues/9) | NoiseGate·소음 바닥·크기 변환 통합. 활성 지속음을 배경으로 흡수하지 않고 실제 무음에서 출력을 닫는 회귀/사용 관찰. [핵심 변경](evidence/hackathon-core-mvp.md), [크기 변환](evidence/input-loudness-scale.md). |
| [#14](https://github.com/chlgusrbs0/ullim/issues/14) | Watch7 API37에서 엔벌로프 미지원 판정을 기록하고 Waveform폴백을 사용. [기기 능력 기록](evidence/issue4-watch7.md). |
| [#15](https://github.com/chlgusrbs0/ullim/issues/15) | 유한 ON/OFF펄스 폴백과 모의 장치/시간 예산 검사 통합. 실제 진폭 단계와 동일하다고 주장하지 않음. [출력 정책](evidence/sound-driven-haptic-range.md), [최종 설정](evidence/final-mvp-defaults.md). |
| [#25](https://github.com/chlgusrbs0/ullim/issues/25) | 실제 세션 상태를 따르는 메인 시작/중지·설정/오류 진입 통합. 기존 실제 화면 이동/사용자 조작과 로컬 화면 회귀 근거. [화면 기록](evidence/issue24-watch.md), [최종 설치](https://github.com/chlgusrbs0/ullim/pull/86#issuecomment-6874364988). |
| [#28](https://github.com/chlgusrbs0/ullim/issues/28) | DataStore설정 저장·즉시 반영·손상 값 복구·AI ON/OFF기본 경로 통합. 실제35개 조절과 업데이트 전후 파일 보존 확인. [실시간 조절](evidence/live-detailed-tuning.md), [최종 기본값](evidence/final-mvp-defaults.md). 통제된 AI효과 비교는#37에서 별도 추적. |

## 구현을 유지하고 후속 평가로 남기는 항목

| 이슈 | 구현된 범위 | 남은 확인 |
|---|---|---|
| [#6](https://github.com/chlgusrbs0/ullim/issues/6) | 명시적 시작/중지·마이크Foreground Service | 기존5분 OFF기록과 최신 통합본의 전체 종료/재시작 범위를 구분. |
| [#13](https://github.com/chlgusrbs0/ullim/issues/13) | 표준 Waveform/진동 지원 제한 표시 | 최신 출력의 물리 종료·잔여 진동 평가. 과거PR51은 후속 구현으로 대체. |
| [#16](https://github.com/chlgusrbs0/ullim/issues/16) | 유한60ms배치·세션/중지 장벽 | 반복 출력 품질·자원 평가. 사용자는 현재 펄스 질감을 허용했으며 매끈한 연속 진동을 완료 조건으로 새로 요구하지 않음. |
| [#17](https://github.com/chlgusrbs0/ullim/issues/17) | 조용한 착용 보정·모터 재입력 억제 | 외부음 중첩 보존과 물리 지연의 통제 평가. |
| [#21](https://github.com/chlgusrbs0/ullim/issues/21) | 소리 종류별 증폭 대신 연속 문맥의 제한된 미세 보정 | 동일 조건 AI ON/OFF촉각 비교. 발소리 선택적 감쇠는 추가하지 않음. |
| [#22](https://github.com/chlgusrbs0/ullim/issues/22) | 비동기 AI·기본 경로 유지·제한된 시간 초과 복구 | 전체 앱 조건의 성능/자원 측정표와 원인별 평가. |
| [#23](https://github.com/chlgusrbs0/ullim/issues/23) | 중복 세션 차단·정리 장벽 | 빠른 반복/강제 종료/실패 회복의 전체 기기 시나리오. 이전 검증 도구 실패는 통과로 변경하지 않음. |
| [#26](https://github.com/chlgusrbs0/ullim/issues/26) | 공통 시간축의 입력/진동 명령 그래프 | 최종 워치 화면의 동적 대응·화면 OFF범위 기록. |
| [#27](https://github.com/chlgusrbs0/ullim/issues/27) | AI후보·점수·불확실/만료/오류 표시 | 실제 워치의 모든 대표 상태·긴 레이블 확인. |
| [#29](https://github.com/chlgusrbs0/ullim/issues/29) | 최초 안내·권한만으로 마이크를 켜지 않는 흐름 | 최초 허용/거절/영구 거절 기기 시나리오. |
| [#30](https://github.com/chlgusrbs0/ullim/issues/30) | 원인별 오류·다음 행동·수동 회복 경로 | 기기 오류/권한 회수 뒤 자원 정리 전수 확인. |
| [#31](https://github.com/chlgusrbs0/ullim/issues/31) | 스크린리더 의미·큰 글씨/터치 대응 | 실제 TalkBack와 최종 기기 접근성 확인. |
| [#32](https://github.com/chlgusrbs0/ullim/issues/32) | 화면/마이크 가용성·세션 오류 대응 | 잠금·통화 경쟁·권한 회수·강제 종료의 기기 행렬. |

## 추가 정비를 보류하는 항목

| 이슈 | 현재 구분 |
|---|---|
| [#33](https://github.com/chlgusrbs0/ullim/issues/33) | 원음 비저장과 워치 단독 기본 처리는 유지. 추가 백업/이전·개인정보 전수 감사는 이번 MVP 고정 범위 밖으로 보류. 선택적 설정 통신의INTERNET권한은 실제 현황에 반영. |
| [#34](https://github.com/chlgusrbs0/ullim/issues/34) | 모델 출처/기본 고지는 포함. 전체 네이티브 출처·고지 대조의 미확정 항목은 해결로 표시하지 않고 후속 정비로 유지. |
| [#35](https://github.com/chlgusrbs0/ullim/issues/35) | 현재 화면을 고정. 과거PR72의 추가 아이콘/버튼 폴리시 전체를 통합했다고 주장하지 않으며 보류. |

## 산출물과 정식 인수

| 이슈 | 현재 구분 |
|---|---|
| [#18](https://github.com/chlgusrbs0/ullim/issues/18) | 하드웨어 기능/사용자 관찰은 기록되어 있으나 물리 지연·촉각 구별의 정식 합격은 미완료. |
| [#36](https://github.com/chlgusrbs0/ullim/issues/36) | 로컬 회귀와MVP통합은 구현됨. 전체 실기기 시나리오·의존 이슈 인수는 남음. |
| [#37](https://github.com/chlgusrbs0/ullim/issues/37) | 블라인드 구별률·물리P95·통제 AI효과 평가 미실시/미완료. 기준을 낮추거나 통과로 바꾸지 않음. |
| [#38](https://github.com/chlgusrbs0/ullim/issues/38) | 최종 설치용APK·설정 스냅샷·[시연 인계 문서](demo-package.md)는 준비됨. 전체 최종 시연/의존 평가를 완료로 간주하지 않음. |
| [#39](https://github.com/chlgusrbs0/ullim/issues/39) | 해커톤MVP고정과 정식v1.0인수는 구분. 모든 미완료 기준을 통과했다고 표시하지 않음. |

## 종료한 과거 Draft PR

| PR | 종료 이유 |
|---|---|
| [#51](https://github.com/chlgusrbs0/ullim/pull/51) | 과거85%출력 상한의 표준 Waveform초안. 현재 후속 구현으로 대체되어 미병합 종료. |
| [#70](https://github.com/chlgusrbs0/ullim/pull/70) | 추가 데이터 백업/이전·개인정보 감사 보류. 미병합 종료, 원본 브랜치와#33보존. |
| [#71](https://github.com/chlgusrbs0/ullim/pull/71) | 전체 네이티브 출처/고지 감사 보류. 미병합 종료, 미확정 항목과#34보존. |
| [#72](https://github.com/chlgusrbs0/ullim/pull/72) | 추가 아이콘/큰 글씨 폴리시 보류. 미병합 종료, 원본 브랜치와#35보존. |

과거 PR을 닫는 것은 그 PR전체를 머지하거나 미실시 평가를 통과시켰다는 뜻이 아니다. 새 기능과 추가 시험은 사용자 지시에 따라 진행하지 않았다.
