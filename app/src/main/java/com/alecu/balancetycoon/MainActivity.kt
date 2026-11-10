package com.alecu.balancetycoon

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.alecu.balancetycoon.ui.BalanceTycoonApp
import com.alecu.balancetycoon.ui.theme.BalanceTycoonTheme

class MainActivity : ComponentActivity() {
    private val vm: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleDebugExtras(intent)
        setContent {
            BalanceTycoonTheme {
                BalanceTycoonApp(vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDebugExtras(intent)
    }

    /**
     * Solo en la versión de pruebas: «tab» abre una pestaña y «demo_minutes» prepara una partida
     * avanzada con el jugador automático. Se usan para las capturas automáticas de GitHub Actions.
     */
    private fun handleDebugExtras(intent: Intent?) {
        if (intent == null || (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) == 0) return
        val tab = intent.getIntExtra("tab", -1)
        if (tab >= 0) vm.tab = tab
        val demo = intent.getIntExtra("demo_minutes", 0)
        if (demo > 0) vm.requestDemo(demo)
    }

    override fun onStart() {
        super.onStart()
        vm.onForeground()
    }

    override fun onStop() {
        vm.onBackground()
        super.onStop()
    }
}
