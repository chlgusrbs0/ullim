package com.ullim.watch.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.ullim.watch.ui.theme.UllimTheme

@Preview(device = "id:wearos_small_round", showSystemUi = true, name = "울림 홈")
@Composable
private fun HomeScreenPreview() {
    UllimTheme { HomeScreen(onSettings = {}, onGuide = {}) }
}

@Preview(device = "id:wearos_small_round", showSystemUi = true, name = "설정")
@Composable
private fun SettingsScreenPreview() {
    UllimTheme { SettingsScreen(onBack = {}) }
}

@Preview(device = "id:wearos_small_round", showSystemUi = true, name = "사용 안내")
@Composable
private fun GuideScreenPreview() {
    UllimTheme { GuideScreen(onErrorGuide = {}, onBack = {}) }
}

@Preview(device = "id:wearos_small_round", showSystemUi = true, name = "문제 해결")
@Composable
private fun ErrorGuideScreenPreview() {
    UllimTheme { ErrorGuideScreen(onBack = {}) }
}
