package com.ullim.watch.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.*
import com.ullim.watch.core.ListeningState
import com.ullim.watch.runtime.LiveLevels
import com.ullim.watch.settings.*
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun TuningContent(
    settings: SettingsState, phase: ListeningState, levels: LiveLevels,
    onChange: (TuningParameter, Float) -> Unit, onReset: () -> Unit,
    onRetry: () -> Unit, onStop: () -> Unit, onBack: () -> Unit,
    onPhone: () -> Unit = {},
) {
    var groupName by rememberSaveable { mutableStateOf<String?>(null) }
    var parameterName by rememberSaveable { mutableStateOf<String?>(null) }
    val group = TuningGroup.entries.firstOrNull { it.name == groupName }
    val parameter = TuningParameter.entries.firstOrNull { it.name == parameterName }
    val scroll = rememberScrollState()
    val back: () -> Unit = {
        when { parameter != null -> parameterName = null; group != null -> groupName = null; else -> onBack() }
    }
    BackHandler(onBack = back)
    LaunchedEffect(groupName, parameterName) { scroll.scrollTo(0) }
    AppScaffold {
        ScreenScaffold(scrollState = scroll) { padding ->
            Column(Modifier.padding(padding).fillMaxWidth().verticalScroll(scroll)
                .padding(horizontal = 26.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(parameter?.label ?: group?.label ?: "실시간 상세 조절",
                    Modifier.semantics { heading() }, textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium)
                if (phase is ListeningState.Listening) {
                    TuningText("${if (levels.detected) "소리 감지" else "무음 판단"}\n입력 ${(levels.rawSound * 100).roundToInt()}% → 보정 후 ${(levels.sound * 100).roundToInt()}%\n변환 ${(levels.normalizedLevel * 100).roundToInt()}% → 진동 요청 ${(levels.commanded * 100).roundToInt()}%")
                    TuningButton("감지 중지", onStop)
                } else if (phase is ListeningState.Starting || phase is ListeningState.Stopping) {
                    TuningText("감지 상태 변경 중")
                    if (phase is ListeningState.Starting) TuningButton("시작 취소", onStop)
                } else TuningText("메인에서 감지를 시작한 뒤 여기서 값을 바꿔보세요.")
                TuningButton(if (parameter != null) "항목 목록으로" else if (group != null) "분류 목록으로" else "메인으로", back)
                if (parameter != null) {
                    ParameterEditor(parameter, settings.values.tuning, settings.ready, onChange)
                } else if (group != null) {
                    TuningParameter.entries.filter { it.group == group }.forEach { p ->
                        TuningButton("${p.label}\n${p.display(settings.values.tuning[p])}", { parameterName = p.name }, settings.ready)
                    }
                } else {
                    TuningButton("휴대폰에서 조절", onPhone)
                    TuningText("값을 바꾸면 감지 중에도 바로 반영돼요. 자동 저장되어 앱·워치를 다시 시작해도 유지돼요.")
                    TuningGroup.entries.forEach { g ->
                        TuningButton(g.label, { groupName = g.name }, settings.ready)
                    }
                    TuningText("강약 지점 사이도 연속으로 계산해요. 약·중·강 세 단계로만 나누지 않아요.")
                    TuningButton("상세 조절 전체 기본값", onReset, settings.ready)
                }
                SettingsSaveStatus(settings, onRetry)
                TuningButton("뒤로", back)
            }
        }
    }
}

@Composable
private fun ParameterEditor(p: TuningParameter, tuning: TuningSettings, enabled: Boolean,
    onChange: (TuningParameter, Float) -> Unit) {
    val value = tuning[p]
    var entered by rememberSaveable(p.name, value) { mutableStateOf(editableValue(p, value)) }
    var error by remember(p.name, value) { mutableStateOf(false) }
    Text(p.display(value), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
    TuningText(p.help)
    TuningText("범위 ${p.display(p.minimum)} ~ ${p.display(p.maximum)}\n기본 ${p.display(MvpDefaults.tuning[p])}")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton({ onChange(p, value - p.step) }, Modifier.weight(1f).heightIn(min = 48.dp), enabled = enabled) { Text("−") }
        OutlinedButton({ onChange(p, value + p.step) }, Modifier.weight(1f).heightIn(min = 48.dp), enabled = enabled) { Text("+") }
    }
    TuningText("숫자 직접 입력${when(p.unit) { TuningUnit.PERCENT -> " (%)"; TuningUnit.MS -> " (ms)"; else -> "" }}")
    BasicTextField(entered, { entered = it; error = false },
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.primary).padding(10.dp)
            .semantics { contentDescription = "${p.label} 숫자 입력" },
        enabled = enabled, singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
    TuningButton("입력값 적용", {
        val number = entered.trim().replace(',', '.').toFloatOrNull()
        if (number == null || !number.isFinite()) error = true
        else {
            val requested = if (p.unit == TuningUnit.PERCENT) number / 100 else number
            val applied = tuning.with(p, requested)[p]
            onChange(p, applied)
            // An out-of-range value can clamp to the unchanged current setting.
            entered = editableValue(p, applied)
        }
    }, enabled)
    if (error) TuningText("숫자를 입력해 주세요.")
    TuningText("범위와 다른 항목의 순서에 맞게 적용돼요. 실제 적용값은 위에 표시돼요.")
    TuningButton("이 항목 기본값", { onChange(p, MvpDefaults.tuning[p]) }, enabled)
}

private fun editableValue(p: TuningParameter, value: Float): String =
    String.format(Locale.ROOT, "%.4f", if (p.unit == TuningUnit.PERCENT) value * 100 else value).trimEnd('0').trimEnd('.')

@Composable
private fun TuningText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
}

@Composable
private fun TuningButton(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    OutlinedButton(onClick, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = enabled) {
        Text(label, textAlign = TextAlign.Center)
    }
}
