package com.ullim.watch

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.*
import com.ullim.watch.runtime.CaptureFailure
import com.ullim.watch.runtime.CaptureRuntime
import com.ullim.watch.settings.LocalTuningServer
import com.ullim.watch.settings.SettingsRuntime
import com.ullim.watch.settings.TuningWebBridge
import com.ullim.watch.ui.theme.UllimTheme
import java.io.File

class PhoneTuningActivity : ComponentActivity() {
    private var server: LocalTuningServer? = null
    private var address by mutableStateOf("연결 준비 중")
    private val addressFile get() = File(filesDir, "phone-tuning-address.txt")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bridge = TuningWebBridge(SettingsRuntime.get(this), ::stopCapture)
        server = LocalTuningServer(assets.open("phone-tuning.html").use { it.readBytes() }, bridge::snapshot, bridge::command)
        setContent {
            val scroll = rememberScrollState()
            UllimTheme { AppScaffold { ScreenScaffold(scrollState = scroll) { padding ->
                Column(Modifier.padding(padding).fillMaxWidth().verticalScroll(scroll).padding(horizontal = 24.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("휴대폰에서 조절", textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
                    Text("같은 핫스팟의 휴대폰 브라우저에서 아래 주소를 열어 주세요.", textAlign = TextAlign.Center)
                    Text(address, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
                    Text("조절 중에는 이 화면을 열어 두세요. 값은 워치에 자동 저장돼요. 감지는 워치 메인에서 먼저 시작해 주세요.", textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(::stopCapture, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("감지 중지") }
                    OutlinedButton({ finish() }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("연결 닫기") }
                }
            } } }
        }
    }

    override fun onStart() {
        super.onStart()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        address = runCatching {
            server!!.start()
            server!!.addresses().joinToString("\n\n").ifEmpty { "Wi-Fi 연결을 확인해 주세요." }
        }.getOrElse { "연결을 열지 못했어요. 다시 열어 주세요." }
        addressFile.writeText(address)
    }
    override fun onStop() {
        server?.close()
        addressFile.delete()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onStop()
    }

    private fun stopCapture() {
        val token = CaptureRuntime.stopToken() ?: return
        if (runCatching { startService(Intent(this, ListeningService::class.java).setAction(ListeningService.ACTION_STOP)
                .putExtra(ListeningService.EXTRA_SESSION, token)) }.isFailure &&
            !runCatching { stopService(Intent(this, ListeningService::class.java)) }.getOrDefault(false))
            reportCaptureFailure(this, token, CaptureFailure.SERVICE_COMMAND)
    }
}
