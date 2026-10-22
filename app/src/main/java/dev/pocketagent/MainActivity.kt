package dev.pocketagent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.pocketagent.ui.navigation.AppNavHost
import dev.pocketagent.ui.theme.PocketAgentTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            enableEdgeToEdge()
            setContent {
                PocketAgentTheme {
                    AppNavHost()
                }
            }
        } catch (t: Throwable) {
            android.util.Log.e("MazaAgent", "Error during MainActivity.onCreate", t)
            val intent = android.content.Intent(this, CrashActivity::class.java).apply {
                putExtra(CrashActivity.EXTRA_CRASH_INFO, android.util.Log.getStackTraceString(t))
            }
            startActivity(intent)
            finish()
        }
    }
}
