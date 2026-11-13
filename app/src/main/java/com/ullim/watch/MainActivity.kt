package com.ullim.watch

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.ullim.watch.ui.UllimApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { UllimApp(onDiagnostics = { startActivity(Intent(this, DiagnosticsActivity::class.java)) }) }
    }
}
