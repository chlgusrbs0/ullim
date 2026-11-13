package com.ullim.watch.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import com.ullim.watch.R

@Composable
internal fun AiModeControl(checked: Boolean, ready: Boolean, onToggle: () -> Unit) {
    val description = stringResource(if (checked) R.string.ai_enabled_choice else R.string.ai_disabled)
    SwitchButton(
        checked = checked,
        onCheckedChange = { onToggle() },
        enabled = ready,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { stateDescription = description; role = Role.Switch },
        label = { Text(stringResource(R.string.ai_controls)) },
    )
}
