# 하이브리드 촉각 정책 구현 계획 (#21)

> 과거 작업 계획입니다. 당시의 요구사항·체크 상태를 보존하며, 최종 구현 및 검증 상태는 현재 기술 문서를 기준으로 합니다.

> 작업자 지침: superpowers:executing-plans 단계별 적용. 아래는 **종류별 이득과 85% 상한을 포함한 초기 정책**이며 최종 MVP에서는 동일 문맥의 제한적 평활화와 전체 요청 범위로 대체되었다.

**목표:** 신뢰 가능한 AI 문맥으로 DSP 신호를 대체하지 않고 명령을 미세 조정한다.
**구조:** HybridHapticMapper는 항상 진행하는 EnvelopeHapticMapper를 감싸고 기본 명령 계산 뒤 제한된 이득·온셋 강조만 적용한다. 누락·무효·미지원 문맥은 이전 AI 상태에 관계없이 정확한 기본값을 반환한다. 새 펄스, 시간·지속 변경 없음.
**기술:** Kotlin, HapticMapper/Classification 계약, JUnit 합성 PCM.
**명세:** #21, architecture.md/models.md, 사용자 승인 로컬 검증 우선.

## 공통 제약
- AI 대기, PCM 저장, 네트워크, Actions 금지. 작업자 연결은 #22.
- 무음·세션·시간·지속 및 당시 출력 상한 0.85 유지.
- 문맥 누락·만료·미래·다른 세션·저점수·모호·미지원 시 기본값과 비트 단위 동일.
- 로컬 A/B는 명령 비교이며 지각·AI 개선을 증명하지 않는다.

## 검토 중점
- ON 이후 OFF는 수정된 매퍼 이력을 상속하지 않는다.
- 잘못됐지만 신뢰도 높은 알려진 레이블이 실제 사건을 모두 억제하지 않는다.
- 무음에서 레이블 기반 펄스 생성 금지.
- 세션·초기화·공백에서 이전 AI 파라미터 미보존.
- 수치 이득·강조는 강도 순서를 뒤집거나 상한을 넘지 않는다.

## 작업 1. 제한 문맥 정책과 A/B 합성 입력
파일: haptics/HybridHapticMapper.kt, haptics/HybridHapticMapperTest.kt, docs/models.md.
입력: AudioFeatures, 선택적 유효 Classification, 기본 HapticMapper.
출력: HybridHapticMapper(baseline: HapticMapper = EnvelopeHapticMapper()): HapticMapper.
- [ ] OFF 정확 복원·무효 문맥·초기화/세션·단조 강도 및 동일 PCM 3종 A/B(발화 변조, 노크 3회, 잡음) 실패 시험 추가.
- [ ] 기능 누락 실패 확인.
- [ ] 기본값 우선 계산. 당시 알려진 정확한 레이블은 점수 >=0.65, 차이 >=0.15일 때 허용. Speech/Conversation 이득 1.02, Noise 0.94, Knock/Tap/Clapping 온셋 강조 0.08 및 이득 1. 20ms마다 파라미터가 목표로 0.1 접근, 무효 문맥 즉시 초기화. `base * (gain + onset*focus*(1-base))`, 0~0.85 제한. 무음·식별 필드 보존, 세션/공백 시 정책 초기화, 기본 매퍼는 자체 생명주기로만 초기화.
- [ ] 전체 시험/assemble/lint. 합성 입력의 명령 MAE·변경 횟수 및 같은 시간·무음·0.9~1.1 상대 명령 범위 확인.
- [ ] 읽기 전용 검토, 커밋, main 대상 Draft PR Closes #21 및 기기 미검증 기록.

초기 보수적 목표값이며 오분류가 작은 원치 않는 변화를 일으킬 수 있다. 실제 이득은 #37 AI ON/OFF 평가 필요. 지각적 동등성을 가정하지 않는다.
