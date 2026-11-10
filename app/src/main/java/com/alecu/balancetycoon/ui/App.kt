package com.alecu.balancetycoon.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alecu.balancetycoon.GameViewModel
import com.alecu.balancetycoon.R
import com.alecu.balancetycoon.core.format.Fmt
import com.alecu.balancetycoon.core.game.GameEngine
import com.alecu.balancetycoon.core.game.OfflineReport
import com.alecu.balancetycoon.core.i18n.Lang
import com.alecu.balancetycoon.ui.theme.Amounts
import com.alecu.balancetycoon.ui.theme.Gold
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BalanceTycoonApp(vm: GameViewModel) {
    val tick by vm.tick.collectAsStateWithLifecycle()
    val lang = rememberLang()
    val engine = vm.engine
    if (engine == null) {
        LoadingScreen()
        return
    }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Avisos de los sucesos nuevos (liquidaciones, cierres, contrataciones...).
    var lastSeenSeq by remember(engine) { mutableLongStateOf(engine.state.eventSeq) }
    LaunchedEffect(tick) {
        val latest = engine.state.events.lastOrNull() ?: return@LaunchedEffect
        if (latest.seq > lastSeenSeq) {
            lastSeenSeq = latest.seq
            if (vm.offlineReport == null && !vm.catchingUp) {
                scope.launch { snackbar.showSnackbar(latest.text.get(lang)) }
            }
        }
    }
    val message = vm.message
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            vm.message = null
        }
    }

    val docs = engine.state.documents.size
    val pending = engine.pendingObligations().size + (if (engine.state.pendingDistribution != null) 1 else 0)
    val tabs = listOf(
        "🏭" to stringResource(R.string.tab_business),
        "📥" to stringResource(R.string.tab_inbox),
        "📒" to stringResource(R.string.tab_books),
        "🏛️" to stringResource(R.string.tab_tax),
    )

    Scaffold(
        topBar = { HeaderBar(engine, lang, tick) { showSettings = true } },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, (emoji, label) ->
                    val badge = when (i) {
                        1 -> docs
                        3 -> pending
                        else -> 0
                    }
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = {
                            if (badge > 0) {
                                BadgedBox(badge = { Badge { Text("$badge") } }) { Text(emoji, fontSize = 20.sp) }
                            } else {
                                Text(emoji, fontSize = 20.sp)
                            }
                        },
                        label = { Text(label, maxLines = 1) },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                0 -> BusinessScreen(vm, engine, lang, tick)
                1 -> InboxScreen(vm, engine, lang, tick)
                2 -> BooksScreen(engine, lang, tick)
                else -> TaxScreen(vm, engine, lang, tick)
            }
            if (vm.catchingUp) CatchUpOverlay()
        }
    }

    vm.offlineReport?.let { report ->
        OfflineDialog(
            report = report,
            lang = lang,
            onClose = { vm.offlineReport = null },
            onBoost = {
                vm.offlineReport = null
                vm.watchAdForBoost()
            },
        )
    }
    if (showSettings) {
        SettingsDialog(engine, onReset = { vm.resetGame() }, onClose = { showSettings = false })
    }
}

@Composable
private fun HeaderBar(engine: GameEngine, lang: Lang, tick: Long, onSettings: () -> Unit) {
    val onHeader = Color.White
    Surface(color = Amounts.headerBackground, contentColor = onHeader) {
        Column(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(engine.state.companyName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(Fmt.dateLong(engine.date(), lang), style = MaterialTheme.typography.bodySmall, color = onHeader.copy(alpha = 0.75f))
                }
                TextButton(onClick = onSettings) { Text("⚙️", fontSize = 20.sp) }
            }
            LinearProgressIndicator(
                progress = { engine.dayProgress().toFloat() },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = Gold,
                trackColor = onHeader.copy(alpha = 0.15f),
            )
            Row {
                HeaderStat(stringResource(R.string.cash), money(engine.cash(), lang), Modifier.weight(1f))
                HeaderStat(stringResource(R.string.public_debt), money(engine.publicDebtEstimate(), lang), Modifier.weight(1f))
            }
            if (engine.creditDrawn() > 0) {
                Text(
                    stringResource(R.string.credit_line, money(engine.creditDrawn(), lang)),
                    style = MaterialTheme.typography.bodySmall,
                    color = Gold,
                )
            }
        }
    }
}

@Composable
private fun HeaderStat(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator()
            Text(stringResource(R.string.loading))
        }
    }
}

@Composable
private fun CatchUpOverlay() {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 6.dp) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.loading))
            }
        }
    }
}

@Composable
private fun OfflineDialog(report: OfflineReport, lang: Lang, onClose: () -> Unit, onBoost: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.offline_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.offline_body, durationLabel(report.seconds), report.days.toInt()))
                Text(stringResource(R.string.offline_sales, money(report.sales, lang)), fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.offline_cash, money(report.cashChange, lang)))
                Text(stringResource(R.string.offline_entries, report.entries.toInt()))
                if (report.capped) Hint(stringResource(R.string.offline_capped))
                if (report.events.isNotEmpty()) {
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    for (ev in report.events.takeLast(5)) {
                        Text("• " + ev.text.get(lang), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.continue_btn)) } },
        dismissButton = { TextButton(onClick = onBoost) { Text("📺 " + stringResource(R.string.boost_watch)) } },
    )
}

@Composable
private fun SettingsDialog(engine: GameEngine, onReset: () -> Unit, onClose: () -> Unit) {
    var confirmReset by remember { mutableStateOf(false) }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.settings_reset)) },
            text = { Text(stringResource(R.string.settings_reset_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    onClose()
                    onReset()
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.cancel)) } },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.settings)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.settings_company), style = MaterialTheme.typography.labelMedium)
                Text(engine.state.companyName, fontWeight = FontWeight.SemiBold)
                OutlinedButton(onClick = { confirmReset = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.settings_reset))
                }
                HorizontalDivider()
                Text(stringResource(R.string.about_title), style = MaterialTheme.typography.labelMedium)
                Hint(stringResource(R.string.about_body))
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.close)) } },
    )
}
