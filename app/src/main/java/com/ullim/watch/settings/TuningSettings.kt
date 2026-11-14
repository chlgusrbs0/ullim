package com.ullim.watch.settings

import java.util.Locale
import kotlin.math.roundToInt

enum class TuningGroup(val label: String) { INPUT("입력·무음 판단"), TIMING("반응·끊김"), OUTPUT("진동 강약 곡선"), PULSE("펄스 길이·전환"), MOTOR("모터 소음 보정"), AI("AI 미세 조정") }
enum class TuningUnit { PERCENT, MS, NUMBER }

enum class TuningParameter(val label: String, val help: String, val group: TuningGroup,
    val default: Float, val minimum: Float, val maximum: Float, val step: Float, val unit: TuningUnit = TuningUnit.NUMBER) {
    REFERENCE_RMS("강한 입력 기준", "낮출수록 같은 소리를 크게 판단해요. 실제 음압 수치는 아니에요.", TuningGroup.INPUT, .5f,.03f,1f,.01f,TuningUnit.PERCENT),
    LOUDNESS_KNEE("작은 소리 확대 곡선", "낮출수록 작은 소리도 커져요. 1은 이전 변환이에요.", TuningGroup.INPUT,.3f,.1f,2f,.05f),
    MIN_RMS("최소 입력 크기", "이보다 작은 입력은 무음으로 판단해요.",TuningGroup.INPUT,.002f,.0001f,.02f,.0005f,TuningUnit.PERCENT),
    NOISE_MARGIN("배경 소리 여유", "높이면 배경 소리를 더 많이 제외해요.",TuningGroup.INPUT,1.1f,.2f,2f,.1f),
    OPEN_OFFSET("감지 시작 추가 기준", "높이면 감지를 시작하기 어려워져요.",TuningGroup.INPUT,.008f,.001f,.04f,.001f,TuningUnit.PERCENT),
    CLOSE_OFFSET("감지 유지 추가 기준", "낮추면 약해진 소리도 더 잘 유지해요.",TuningGroup.INPUT,.004f,0f,.03f,.001f,TuningUnit.PERCENT),
    OPEN_RATIO("배경 대비 시작 배율", "배경보다 얼마나 커야 감지를 시작할지 정해요.",TuningGroup.INPUT,1.7f,.5f,4f,.1f),
    CLOSE_RATIO("배경 대비 유지 배율", "낮추면 말소리의 약한 부분에서도 감지를 유지해요.",TuningGroup.INPUT,1.25f,.3f,3f,.05f),
    NOISE_RISE("배경 상승 학습 속도", "무음 구간에서만 적용돼요. 높으면 배경 상승을 빨리 따라가요.",TuningGroup.INPUT,.005f,.001f,.05f,.001f,TuningUnit.PERCENT),
    NOISE_FALL("배경 하강 학습 속도", "높으면 조용해진 환경을 빨리 반영해요.",TuningGroup.INPUT,.05f,.005f,.5f,.005f,TuningUnit.PERCENT),
    ATTACK_MS("입력 반응 시간", "짧을수록 소리 시작과 커짐을 빨리 따라가요.",TuningGroup.TIMING,20f,5f,100f,5f,TuningUnit.MS),
    RELEASE_MS("입력 감쇠 시간", "길면 입력 크기의 내려감을 천천히 따라가요.",TuningGroup.TIMING,120f,20f,400f,20f,TuningUnit.MS),
    OUTPUT_ATTACK("진동 증가 반응", "높을수록 진동이 커지는 속도가 빨라져요.",TuningGroup.TIMING,.7f,.05f,1f,.05f,TuningUnit.PERCENT),
    OUTPUT_RELEASE("진동 감소 반응", "높을수록 진동이 약해지는 속도가 빨라져요.",TuningGroup.TIMING,.45f,.05f,1f,.05f,TuningUnit.PERCENT),
    GAP_HOLD_MS("짧은 무음 이어주기", "0은 즉시 중지예요. 늘리면 짧은 쉼을 잇지만 종료도 조금 늦어져요.",TuningGroup.TIMING,0f,0f,100f,20f,TuningUnit.MS),
    OUTPUT_GAIN("전체 진동 배율", "모든 유음의 요청값을 같은 배율로 조절해요.",TuningGroup.OUTPUT,1f,.1f,2f,.1f),
    CONTRAST("강약 대비", "1보다 높이면 작은 소리는 낮추고 큰 소리와 차이를 벌려요.",TuningGroup.OUTPUT,1f,.4f,3f,.1f),
    ONSET_ACCENT("소리 시작 강조", "갑자기 시작한 소리를 더 또렷하게 표현해요.",TuningGroup.OUTPUT,.1f,0f,.4f,.02f,TuningUnit.PERCENT),
    WEAK_OUTPUT("소리 25% 지점의 진동", "정규화된 소리 크기 25%에 대응하는 요청값이에요.",TuningGroup.OUTPUT,.25f,0f,1f,.02f,TuningUnit.PERCENT),
    MIDDLE_OUTPUT("소리 50% 지점의 진동", "중간 소리의 요청값을 직접 조절해요.",TuningGroup.OUTPUT,.5f,0f,1f,.02f,TuningUnit.PERCENT),
    STRONG_OUTPUT("소리 80% 지점의 진동", "큰 소리의 요청값을 직접 조절해요.",TuningGroup.OUTPUT,.8f,0f,1f,.02f,TuningUnit.PERCENT),
    OUTPUT_FLOOR("감지 중 최소 진동", "소리가 감지될 때만 적용해요. 무음에서 새 진동을 만들지 않아요.",TuningGroup.OUTPUT,0f,0f,.5f,.02f,TuningUnit.PERCENT),
    OUTPUT_CEILING("최대 진동 요청", "전체 출력의 상한이에요. 실제 모터 힘의 측정값은 아니에요.",TuningGroup.OUTPUT,1f,.2f,1f,.02f,TuningUnit.PERCENT),
    LONG_START("긴 펄스 전환 시작", "이 요청값부터 뒤 펄스를 첫 펄스로 합치기 시작해요.",TuningGroup.PULSE,.6f,.25f,.9f,.01f,TuningUnit.PERCENT),
    LONG_END("긴 펄스 전환 완료", "이 요청값부터 한 번의 긴 펄스를 사용해요.",TuningGroup.PULSE,.75f,.3f,1f,.01f,TuningUnit.PERCENT),
    GROUP_MS("펄스 묶음 길이", "20은 이전의 짧은 펄스예요. 40·60은 더 길게 합칠 수 있어요.",TuningGroup.PULSE,60f,20f,60f,20f,TuningUnit.MS),
    MIN_ON_MS("짧은 펄스 최소 시간", "낮추면 아주 작은 요청을 더 짧게 표현해요.",TuningGroup.PULSE,4f,1f,4f,1f,TuningUnit.MS),
    SIMILARITY_MS("묶을 수 있는 세기 차이", "높이면 서로 다른 세기도 더 많이 묶어요.",TuningGroup.PULSE,2f,0f,10f,1f,TuningUnit.MS),
    MOTOR_SUBTRACTION("모터 소음 제거 세기", "낮추면 외부음이 더 남지만 워치 진동이 재감지될 수 있어요.",TuningGroup.MOTOR,1.25f,0f,3f,.05f),
    MOTOR_TAIL_MS("모터 잔향 보정 시간", "진동이 끝난 뒤 보정을 유지하는 시간이에요.",TuningGroup.MOTOR,120f,0f,300f,10f,TuningUnit.MS),
    AI_CONFIDENCE("AI 적용 최소 점수", "종류별 강도를 정하지 않고 문맥 안정화에만 사용해요.",TuningGroup.AI,.65f,.35f,.95f,.05f,TuningUnit.PERCENT),
    AI_MARGIN("AI 후보 간 최소 차이", "높이면 확실한 문맥에만 미세 조정을 적용해요.",TuningGroup.AI,.08f,0f,.3f,.01f,TuningUnit.PERCENT),
    AI_SMOOTHING("AI 변화 반영 속도", "높으면 현재 요청값을 더 빨리 따라가요.",TuningGroup.AI,.35f,.1f,1f,.05f,TuningUnit.PERCENT),
    AI_LIMIT("AI 조정 최대 폭", "0은 강약 미세 조정을 끄고 분류만 유지해요.",TuningGroup.AI,.03f,0f,.1f,.01f,TuningUnit.PERCENT),
    AI_WAIT_MS("늦은 AI 적용 대기", "늦게 도착한 문맥이 짧은 소리를 바꾸지 않도록 기다려요.",TuningGroup.AI,500f,0f,1000f,100f,TuningUnit.MS);

    val key: String get() = "tuning_${name.lowercase(Locale.ROOT)}"
    fun display(value: Float): String = when(unit) {
        TuningUnit.PERCENT -> String.format(Locale.getDefault(), if(step < .01f) "%.2f%%" else "%.0f%%", value*100)
        TuningUnit.MS -> "${value.roundToInt()} ms"
        TuningUnit.NUMBER -> String.format(Locale.getDefault(), "%.2f", value)
    }
}

/** Immutable, atomically published frame-side settings. No file access in signal processing. */
@ConsistentCopyVisibility
data class TuningSettings private constructor(private val overrides: Map<TuningParameter,Float>) {
    constructor(): this(emptyMap())
    operator fun get(parameter: TuningParameter): Float = overrides[parameter] ?: parameter.default
    fun with(parameter: TuningParameter, requested: Float): TuningSettings {
        require(requested.isFinite())
        var low=parameter.minimum; var high=parameter.maximum
        when(parameter) {
            TuningParameter.WEAK_OUTPUT -> high=minOf(high,this[TuningParameter.MIDDLE_OUTPUT])
            TuningParameter.MIDDLE_OUTPUT -> {low=maxOf(low,this[TuningParameter.WEAK_OUTPUT]);high=minOf(high,this[TuningParameter.STRONG_OUTPUT])}
            TuningParameter.STRONG_OUTPUT -> low=maxOf(low,this[TuningParameter.MIDDLE_OUTPUT])
            TuningParameter.OUTPUT_FLOOR -> high=minOf(high,this[TuningParameter.OUTPUT_CEILING])
            TuningParameter.OUTPUT_CEILING -> low=maxOf(low,this[TuningParameter.OUTPUT_FLOOR])
            TuningParameter.LONG_START -> high=minOf(high,this[TuningParameter.LONG_END]-.05f)
            TuningParameter.LONG_END -> low=maxOf(low,this[TuningParameter.LONG_START]+.05f)
            TuningParameter.CLOSE_OFFSET -> high=minOf(high,this[TuningParameter.OPEN_OFFSET])
            TuningParameter.OPEN_OFFSET -> low=maxOf(low,this[TuningParameter.CLOSE_OFFSET])
            TuningParameter.CLOSE_RATIO -> high=minOf(high,this[TuningParameter.OPEN_RATIO])
            TuningParameter.OPEN_RATIO -> low=maxOf(low,this[TuningParameter.CLOSE_RATIO])
            else -> Unit
        }
        var value=requested.coerceIn(low,high)
        if(parameter==TuningParameter.GROUP_MS) value=(value/20).roundToInt()*20f
        val next=overrides.toMutableMap()
        if(kotlin.math.abs(value-parameter.default)<.0000001f) next.remove(parameter) else next[parameter]=value
        return TuningSettings(next.toMap())
    }
    companion object {
        // Signal-processing baseline. Shipped app defaults are MvpDefaults.tuning.
        val DEFAULT=TuningSettings()
        fun decode(fallback: TuningSettings = DEFAULT, read: (TuningParameter)->Float?): TuningSettings {
            val values=TuningParameter.entries.mapNotNull { p -> read(p)?.takeIf { it.isFinite() && it in p.minimum..p.maximum }
                ?.let { p to if(p==TuningParameter.GROUP_MS) (it/20).roundToInt()*20f else it } }.toMap()
            var result=TuningSettings(values)
            val pairs=listOf(TuningParameter.WEAK_OUTPUT to TuningParameter.MIDDLE_OUTPUT,
                TuningParameter.MIDDLE_OUTPUT to TuningParameter.STRONG_OUTPUT,
                TuningParameter.OUTPUT_FLOOR to TuningParameter.OUTPUT_CEILING,
                TuningParameter.CLOSE_OFFSET to TuningParameter.OPEN_OFFSET,
                TuningParameter.CLOSE_RATIO to TuningParameter.OPEN_RATIO,
                TuningParameter.LONG_START to TuningParameter.LONG_END)
            if(pairs.any { (a,b)-> result[a]+(if(a==TuningParameter.LONG_START) .04999f else 0f)>result[b] }) return fallback
            result=TuningSettings(values.filter { (p,v)->kotlin.math.abs(v-p.default)>=.0000001f })
            return result
        }
    }
}
