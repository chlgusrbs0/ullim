# 울림 기술 문서

[프로젝트 소개로 돌아가기](../README.md)

해커톤 최종 MVP의 구현 구조와 개발·검증 기록을 안내합니다. 현재 구현 설명과 과거 계획·실험 기록을 구분합니다.

## 현재 구현과 사용 방법

| 문서 | 내용 |
| --- | --- |
| [아키텍처](architecture.md) | 최종 구현 요약과 초기 설계 계약의 구분 |
| [프로젝트 현황](project-status.md) | 구현·후속 평가·보류 항목의 정본 |
| [개발 및 검증 안내](development.md) | 환경 구성, 로컬 빌드, 시험 방법 |
| [모델](models.md) | YAMNet 출처, LiteRT, 분류 안정화와 보정 |
| [실시간 조절](live-tuning.md) | 워치·선택적 브라우저 설정 |
| [설정 저장](persistent-settings.md) | 기본값 및 저장·복원 |
| [메인 화면](main-listening-ui.md) | 시작·중지 UI |
| [AI 결과 표시](ai-result-display.md) | 후보·점수·실행 상태 |
| [시각화](sound-haptic-visualization.md) | 소리 특징과 진동 명령 그래프 |
| [세션 생명주기](session-lifecycle.md) | 감지 소유권과 종료 |
| [오류와 복구](error-recovery.md) | 오류 상태와 수동 복구 |
| [접근성](wear-accessibility.md) | 원형 화면과 읽기·조작 지원 |

## 검증 자료와 과거 기록

| 문서 | 읽는 방법 |
| --- | --- |
| [촉각 및 지연 검증](watch-haptic-validation.md) | 실제 관찰과 실패, 아직 미완료인 물리 평가 |
| [AI 실기기 검증 절차](ai-runtime-validation.md) | 통제 시험 절차; 이미 확보된 일부 실행 근거와 구분 |
| [최종 MVP 기본값](evidence/final-mvp-defaults.md) | 설정·검사 및 설치 기록 |
| [해커톤 시연 인계](demo-package.md) | 당시 제출 파일 정보와 시연 대본; 시연 영상 또는 실적 증명이 아님 |
| [전체 증거 자료](evidence/) | 날짜·이슈·기기별 실험 및 실패 기록. 서로 다른 빌드의 결과를 합치지 않음 |
| [과거 개발 계획](superpowers/plans/) | 당시 구현 순서와 의사결정. 계획만으로 구현 완료를 판단하지 않음 |

[협업 규칙](../AGENTS.md) · [라이선스](../LICENSE)

문서용 앱 아이콘 `assets/ullim-icon.svg`는 실제 [Android 벡터 아이콘](../app/src/main/res/drawable/ic_launcher.xml)의 경로·색상·선폭을 SVG로 옮긴 것입니다. 발표자료 로고 원본이나 실제 앱 스크린샷을 대신하는 합성 화면이 아닙니다.
