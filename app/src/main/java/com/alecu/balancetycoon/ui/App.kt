package com.alecu.balancetycoon.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alecu.balancetycoon.GameViewModel
import com.alecu.balancetycoon.R
import com.alecu.balancetycoon.core.format.Fmt
import com.alecu.balancetycoon.core.game.GameEngine
import com.alecu.balancetycoon.core.game.Offices
import com.alecu.balancetycoon.core.game.OfflineReport
import com.alecu.balancetycoon.core.i18n.Lang
import com.alecu.balancetycoon.ui.theme.Palette
import kotlinx.coroutines.launch

@Composable
fun BalanceTycoonApp(vm: GameViewModel) {
    val tick by vm.tick.collectAsStateWithLifecycle()
    val lang = rememberLang()
    val engine = vm.engine
    if (engine == null) {
        LoadingScreen()
        return
    }

    var showSettings by remember { mutableStateOf(false) }
    var showOffices by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Avisos de los sucesos nuevos (contrataciones, mudanzas, liquidaciones, cierres...).
    var lastSeenSeq by remember(engine) { mutableLongStateOf(engine.state.eventSeq) }
    LaunchedEffect(tick) {
        val latest = engine.state.events.lastOrNull() ?: return@LaunchedEffect
        if (latest.seq > lastSeenSeq) {
            lastSeenSeq = latest.seq
            if (vm.offlineReport == null && !vm.catchingUp) {
                snackbar.currentSnackbarData?.dismiss()
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

    Scaffold(
        containerColor = Palette.PanelDark,
        topBar = { TopBar(engine, lang, onReputation = { vm.tab = 3 }, onOffices = { showOffices = true }, onSettings = { showSettings = true }) },
        bottomBar = { BottomNav(vm, engine) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (vm.tab) {
                0 -> OfficeScreen(vm, engine, lang, tick)
                1 -> StaffScreen(vm, engine, lang, tick)
                2 -> EquipmentScreen(vm, engine, lang, tick)
                3 -> ClientsScreen(vm, engine, lang, tick)
                else -> BooksScreen(vm, engine, lang, tick)
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
    if (vm.startedFresh) {
        AlertDialog(
            onDismissRequest = { vm.startedFresh = false },
            title = { Text(stringResource(R.string.fresh_title)) },
            text = { Text(stringResource(R.string.fresh_body)) },
            confirmButton = { TextButton(onClick = { vm.startedFresh = false }) { Text(stringResource(R.string.continue_btn)) } },
        )
    }
    if (showOffices) OfficesDialog(vm, engine, lang) { showOffices = false }
    if (showSettings) SettingsDialog(engine, onReset = { vm.resetGame() }, onClose = { showSettings = false })
}

@Composable
private fun TopBar(engine: GameEngine, lang: Lang, onReputation: () -> Unit, onOffices: () -> Unit, onSettings: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Palette.PanelDark)
            .statusBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Reputación: tipo de cliente actual en estrellas.
            GameButton(onClick = onReputation, color = Palette.PanelLight, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp)) {
                PixelText("★ " + (engine.tier().index + 1), size = 16.sp, color = Palette.Gold)
                PixelText(engine.state.reputation.toString(), size = 11.sp, color = Palette.Muted)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                PixelText("🪙 " + moneyShort(engine.cash(), lang), size = 24.sp, color = Palette.Gold)
                val credit = engine.creditDrawn()
                if (credit > 0) {
                    PixelText(stringResource(R.string.credit_line, moneyShort(credit, lang)), size = 11.sp, color = Palette.Red)
                } else {
                    PixelText(Fmt.dateLong(engine.date(), lang), size = 11.sp, color = Palette.Muted)
                }
            }
            Box {
                GameButton(onClick = onOffices, color = Palette.PanelLight, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                    Text("🏢", fontSize = 18.sp)
                }
                if (engine.canMove()) Badge("!", Modifier.align(Alignment.TopEnd), color = Palette.Red)
            }
            Spacer(Modifier.width(6.dp))
            GameButton(onClick = onSettings, color = Palette.PanelLight, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                Text("⚙️", fontSize = 18.sp)
            }
        }
        // Avance del día contable.
        Box(Modifier.fillMaxWidth().height(3.dp).background(Palette.Stroke)) {
            Box(Modifier.fillMaxWidth(engine.dayProgress().toFloat()).height(3.dp).background(Palette.Gold))
        }
    }
}

@Composable
private fun BottomNav(vm: GameViewModel, engine: GameEngine) {
    val tabs = listOf(
        "🏢" to stringResource(R.string.tab_office),
        "👥" to stringResource(R.string.tab_staff),
        "💻" to stringResource(R.string.tab_equipment),
        "📂" to stringResource(R.string.tab_clients),
        "📒" to stringResource(R.string.tab_accounts),
    )
    Row(
        Modifier
            .fillMaxWidth()
            .background(Palette.PanelDark)
            .border(2.dp, Palette.Stroke)
            .navigationBarsPadding()
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        tabs.forEachIndexed { i, (emoji, label) ->
            val badge = when (i) {
                3 -> engine.state.cases.size
                4 -> if (engine.state.pendingDistribution != null) 1 else 0
                else -> 0
            }
            Box(Modifier.weight(1f)) {
                GameButton(
                    onClick = { vm.tab = i },
                    color = if (vm.tab == i) Palette.Gold else Palette.Mint,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 4.dp, horizontal = 2.dp),
                ) {
                    Text(emoji, fontSize = 20.sp)
                    PixelText(label, size = 10.sp, color = Palette.Stroke, align = TextAlign.Center)
                }
                if (badge > 0) Badge(badge.toString(), Modifier.align(Alignment.TopEnd))
            }
        }
    }
}

@Composable
private fun OfficesDialog(vm: GameViewModel, engine: GameEngine, lang: Lang, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = Palette.Panel,
        title = { PixelText(stringResource(R.string.offices_title), size = 20.sp, color = Palette.Gold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (o in Offices.all) {
                    val current = o.index == engine.state.officeIndex
                    val past = o.index < engine.state.officeIndex
                    val next = o.index == engine.state.officeIndex + 1
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .border(2.dp, if (current) Palette.Gold else Palette.Stroke, MaterialTheme.shapes.small)
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        PixelText(
                            (if (current) "📍 " else if (past) "✔ " else "🏢 ") + o.name.get(lang) + " · " + o.city,
                            size = 15.sp,
                            color = if (current) Palette.Gold else Palette.Cream,
                            maxLines = 2,
                        )
                        Hint(stringResource(R.string.office_specs, o.floors, o.desksPerFloor, moneyShort(o.rent, lang)))
                        if (!current && !past) {
                            val tier = com.alecu.balancetycoon.core.game.ClientTiers.get(o.minTier)
                            Hint(stringResource(R.string.office_requires, tier.name.get(lang), moneyShort(o.fitOut, lang), moneyShort(o.deposit, lang)),
                                if (engine.tier().index >= o.minTier) Palette.Mint else Palette.Muted)
                            if (o.landlordIsIndividual) Hint(stringResource(R.string.office_withholding))
                        }
                        if (next) {
                            PriceButton(
                                title = stringResource(R.string.move_btn),
                                price = moneyShort(engine.moveCashNeeded(), lang),
                                onClick = {
                                    vm.moveOffice()
                                    onClose()
                                },
                                enabled = engine.canMove(),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                Hint(stringResource(R.string.offices_note))
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize().background(Palette.PanelDark), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(color = Palette.Gold)
            PixelText(stringResource(R.string.loading), size = 16.sp)
        }
    }
}

@Composable
private fun CatchUpOverlay() {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Surface(shape = MaterialTheme.shapes.large, color = Palette.Panel, tonalElevation = 6.dp) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Palette.Gold)
                Spacer(Modifier.height(12.dp))
                PixelText(stringResource(R.string.loading), size = 15.sp)
            }
        }
    }
}

@Composable
private fun OfflineDialog(report: OfflineReport, lang: Lang, onClose: () -> Unit, onBoost: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = Palette.Panel,
        title = { PixelText(stringResource(R.string.offline_title), size = 20.sp, color = Palette.Gold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.offline_body, durationLabel(report.seconds), report.days.toInt()))
                Text(stringResource(R.string.offline_sales, money(report.sales, lang)), fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.offline_cash, money(report.cashChange, lang)))
                Text(stringResource(R.string.offline_cases, report.casesSolved, report.reputation.toInt()))
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
            containerColor = Palette.Panel,
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
        containerColor = Palette.Panel,
        title = { PixelText(stringResource(R.string.settings), size = 20.sp, color = Palette.Gold) },
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
