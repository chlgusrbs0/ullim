# 실험 및 검증 기록 안내

[기술 문서 안내](../README.md)

이곳은 해커톤 개발 당시의 실험·실패·설치·로컬 검사 원본이다. 각각의 시험 대상과 빌드, 날짜, 측정 경계를 구분한다. 현재의 구현 및 미완료 상태는 [프로젝트 현황](../project-status.md)을 따른다. 기존 이슈는 진행 계획 없음으로 종료했으며 시험 실패나 미측정을 통과로 변경하지 않았다. 원본 텍스트 로그, JSON, 이미지, 해시 및 기술 식별자는 원형 그대로 보존한다.

## 최종 결과 및 설정

- [최종 사용자 설정과 설치 검증](final-mvp-defaults.md)
- [해커톤 MVP 통합 빌드](hackathon-mvp.md)
- [해커톤 핵심 촉각·AI 개선](hackathon-core-mvp.md)
- [실시간 상세 조절 및 영구 저장](live-detailed-tuning.md)
- [선택적 휴대폰 브라우저 조절](phone-browser-tuning.md)
- [실제 AI 분석·전체 출력](live-ai-full-output.md)

## 오디오와 DSP

- [기초 앱 설치·실행](issue2-watch7.md)
- [마이크 정리 회귀 검증](issue5-cleanup-validation.md)
- [단일 마이크 실제 검증](issue5-watch7-native.md)
- [설치본 화면 꺼짐 감지](issue6-production-background.md)
- [오디오 분배 검증](issue7-validation.md)
- [RMS·peak·엔벌로프 검증](issue8-validation.md)
- [적응형 소음 억제](issue9-validation.md)
- [리듬·온셋 검증](issue10-validation.md)
- [DSP 경계·성능 검증](issue11-validation.md)

## 진동 출력과 모터 보정

- [Watch7 진동 기능 조사](issue4-watch7.md)
- [DSP에서 촉각 명령으로의 검증](issue12-validation.md)
- [자기 진동 재입력 수정 및 관찰](issue17-local-feedback-candidate.md)
- [모터 설정과 보정 유효성](issue17-live-motor-settings.md)
- [입력 크기와 진동 요청 환산](input-loudness-scale.md)
- [소리 크기별 짧은·긴 펄스](sound-driven-haptic-range.md)
- [실시간 펄스 ON 시간 묶기](grouped-live-pulses.md)
- [긴 펄스 전환 기준 조정](earlier-strong-pulse.md)

## AI·서비스·UI

- [YAMNet 실제 워치 실행](issue19-validation.md)
- [분류 결과 안정화 및 만료](issue20-validation.md)
- [실제 앱의 AI 연결 수정·설치](issue17-ai-backport-validation.md)
- [추론 시간 초과의 제한 복구](issue22-transient-timeout-recovery.md)
- [서비스 반복 검증 도구와 실패 결과](issue23-native-service-cycles.md)
- [Wear UI 기반 검증](issue24-watch.md)
- [최초 안내·마이크 동의 통합](issue29-consent-integration.md)
