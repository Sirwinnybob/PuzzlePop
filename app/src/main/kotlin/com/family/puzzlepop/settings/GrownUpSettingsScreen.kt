package com.family.puzzlepop.settings

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.family.puzzlepop.BuildConfig
import com.family.puzzlepop.PuzzlePopApp
import com.family.puzzlepop.R
import com.family.puzzlepop.kid.KidShape
import com.family.puzzlepop.kid.PinSetupScreen
import com.family.puzzlepop.kid.PinningGuideSheet
import kotlinx.coroutines.launch

/**
 * The grown-up settings (reached from the library behind the gate). Every change is saved at once,
 * so there is no Save button; the screen reads straight from the stored settings.
 */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun GrownUpSettingsScreen(onDone: () -> Unit, onSpeedTest: () -> Unit = {}) {
    val container = (LocalContext.current.applicationContext as PuzzlePopApp).container
    val repo = container.settings
    val scope = rememberCoroutineScope()
    val s by repo.kidSettings.collectAsState(initial = KidSettings())
    val fast by repo.fastClock.collectAsState(initial = false)
    val bigParty by repo.bigCelebration.collectAsState(initial = true)
    var changingPin by rememberSaveable { mutableStateOf(false) }
    var showGuide by rememberSaveable { mutableStateOf(false) }
    // Hidden: 7 taps on the title reveal the speed test (a hint from the 4th tap on).
    var titleTaps by rememberSaveable { mutableStateOf(0) }
    val toastContext = LocalContext.current
    var toast by remember { mutableStateOf<android.widget.Toast?>(null) }

    fun update(t: (KidSettings) -> KidSettings) {
        scope.launch { repo.updateKidSettings(t) }
    }

    if (changingPin) {
        BackHandler { changingPin = false }
        PinSetupScreen(onDone = { changingPin = false }, changeMode = true, onCancel = { changingPin = false })
        return
    }
    BackHandler(onBack = onDone)

    val activity = checkNotNull(LocalActivity.current)
    val wide = calculateWindowSizeClass(activity).widthSizeClass != WindowWidthSizeClass.Compact

    val timeCard: @Composable () -> Unit = {
        SectionCard {
            SectionTitle(R.string.settings_section_time, R.drawable.ic_timer)
            LimitRow(R.string.settings_sitting, s.sittingLimitMin, 5..120, 5, 20, { v -> update { it.copy(sittingLimitMin = v) } })
            LimitRow(R.string.settings_daily, s.dailyLimitMin, 10..240, 10, 60, { v -> update { it.copy(dailyLimitMin = v) } })
            StepperRow(R.string.settings_break, s.breakMin, 5..120, 5, { stringResource(R.string.settings_minutes, it) }) { v -> update { it.copy(breakMin = v) } }
            StepperRow(
                R.string.settings_finish, s.finishAllowance, 0..20, 1,
                { if (it == 0) stringResource(R.string.settings_finish_off) else stringResource(R.string.settings_pieces_n, it) },
            ) { v -> update { it.copy(finishAllowance = v) } }
            StepperRow(R.string.settings_nap_after, s.napAfterMin, 1..30, 1, { stringResource(R.string.settings_minutes, it) }) { v -> update { it.copy(napAfterMin = v) } }
            StepperRow(R.string.settings_real_sleep, s.realSleepAfterMin, 5..120, 5, { stringResource(R.string.settings_minutes, it) }) { v -> update { it.copy(realSleepAfterMin = v) } }
        }
    }
    val kidCard: @Composable () -> Unit = {
        SectionCard {
            SectionTitle(R.string.settings_section_kid, R.drawable.ic_pieces)
            StepperRow(R.string.settings_min_pieces, s.minPieces, 10..100, 2, { stringResource(R.string.settings_pieces_n, it) }) { v ->
                update { it.copy(minPieces = v, maxPieces = maxOf(it.maxPieces, v)) }
            }
            StepperRow(R.string.settings_max_pieces, s.maxPieces, 10..100, 2, { stringResource(R.string.settings_pieces_n, it) }) { v ->
                update { it.copy(maxPieces = v, minPieces = minOf(it.minPieces, v)) }
            }
            KidShapeRow(s.kidShape) { v -> update { it.copy(kidShape = v) } }
            CelebrationRow(bigParty) { v -> scope.launch { repo.setBigCelebration(v) } }
            SwitchRow(R.string.settings_helper, s.helperOn) { v -> update { it.copy(helperOn = v) } }
            if (s.helperOn) {
                StepperRow(R.string.settings_helper_after, s.helperAfterSec, 10..60, 5, { stringResource(R.string.settings_seconds, it) }) { v -> update { it.copy(helperAfterSec = v) } }
            }
            SwitchRow(R.string.settings_always_kid, s.alwaysOpenInKidMode) { v -> update { it.copy(alwaysOpenInKidMode = v) } }
        }
    }
    val securityCard: @Composable () -> Unit = {
        SectionCard {
            SectionTitle(R.string.settings_section_security, R.drawable.ic_grownup)
            LanguageRow()
            FilledTonalButton(onClick = { changingPin = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)) {
                Text(stringResource(R.string.settings_change_pin), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Text(stringResource(R.string.settings_pin_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FilledTonalButton(onClick = { showGuide = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)) {
                Text(stringResource(R.string.settings_pinning), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    Box(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .widthIn(max = if (wide) 1100.dp else 720.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(painterResource(R.drawable.ic_settings), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(40.dp))
                Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    titleTaps++
                    if (titleTaps >= 7) toast?.cancel()
                    if (titleTaps in 4..6) {
                        toast?.cancel()
                        toast = android.widget.Toast.makeText(toastContext, toastContext.getString(R.string.speedtest_taps, 7 - titleTaps), android.widget.Toast.LENGTH_SHORT).also { it.show() }
                    }
                })
                Button(onClick = onDone, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.settings_done), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) { timeCard() }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        kidCard()
                        securityCard()
                    }
                }
            } else {
                timeCard()
                kidCard()
                securityCard()
            }

            if (titleTaps >= 7) {
                SectionCard {
                    FilledTonalButton(onClick = onSpeedTest, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)) {
                        Text(stringResource(R.string.speedtest_entry), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (BuildConfig.DEBUG) {
                SectionCard { SwitchRow(R.string.settings_fast_clock, fast) { v -> scope.launch { repo.setFastClock(v) } } }
            }
        }
    }

    if (showGuide) PinningGuideSheet(onDismiss = { showGuide = false })
}

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

@Composable
private fun SectionTitle(textRes: Int, iconRes: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp)) {
        Icon(painterResource(iconRes), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(32.dp))
        Text(stringResource(textRes), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

/** Which piece shape kid puzzles are cut in: one icon button per choice. */
@Composable
private fun KidShapeRow(value: KidShape, onChange: (KidShape) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.settings_kid_shape), fontSize = 19.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().selectableGroup()) {
            for (shape in KidShape.entries) {
                val (icon, name) = when (shape) {
                    KidShape.CHUNKY -> R.drawable.ic_style_chunky to R.string.style_chunky
                    KidShape.HEARTS -> R.drawable.ic_style_hearts to R.string.style_hearts
                    KidShape.PARTY -> R.drawable.ic_style_party to R.string.style_party
                    KidShape.SURPRISE -> R.drawable.ic_style_surprise to R.string.kid_shape_surprise
                }
                val label = stringResource(name)
                val selected = shape == value
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    border = if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 88.dp)
                        .selectable(selected = selected, role = Role.RadioButton, onClick = { onChange(shape) }),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                        modifier = Modifier.padding(8.dp),
                    ) {
                        Icon(painterResource(icon), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(40.dp))
                        Text(label, fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

/** The finished-puzzle party for everyone (kid mode or not): a simple chime and confetti, or the big one. */
@Composable
private fun CelebrationRow(big: Boolean, onChange: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.settings_celebration), fontSize = 19.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().selectableGroup()) {
            for ((value, name, note) in listOf(
                Triple(false, R.string.settings_celebration_simple, R.string.settings_celebration_simple_note),
                Triple(true, R.string.settings_celebration_big, R.string.settings_celebration_big_note),
            )) {
                val selected = big == value
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    border = if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 72.dp)
                        .selectable(selected = selected, role = Role.RadioButton, onClick = { onChange(value) }),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                        modifier = Modifier.padding(8.dp),
                    ) {
                        Text(stringResource(name), fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        Text(stringResource(note), fontSize = 14.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** A limit that can be off (null): a switch, and a stepper while it is on. */
@Composable
private fun LimitRow(labelRes: Int, value: Int?, range: IntRange, step: Int, defaultOn: Int, onChange: (Int?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SwitchRow(labelRes, value != null) { on -> onChange(if (on) defaultOn else null) }
        if (value != null) {
            StepperControl(value, range, step, { stringResource(R.string.settings_minutes, it) }, onChange = { onChange(it) })
        }
    }
}

@Composable
private fun SwitchRow(labelRes: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Text(stringResource(labelRes), fontSize = 19.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun StepperRow(labelRes: Int, value: Int, range: IntRange, step: Int, format: @Composable (Int) -> String, onChange: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(labelRes), fontSize = 19.sp)
        StepperControl(value, range, step, format, onChange)
    }
}

/** [-]  value  [+] with big buttons; stays inside [range]. */
@Composable
private fun StepperControl(value: Int, range: IntRange, step: Int, format: @Composable (Int) -> String, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val less = stringResource(R.string.settings_less)
        val more = stringResource(R.string.settings_more)
        FilledTonalButton(
            onClick = { onChange((value - step).coerceAtLeast(range.first)) },
            enabled = value > range.first,
            modifier = Modifier.size(56.dp).semantics { contentDescription = less },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        ) { Text("−", fontSize = 28.sp, fontWeight = FontWeight.Bold) }
        Text(format(value), fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 110.dp))
        FilledTonalButton(
            onClick = { onChange((value + step).coerceAtMost(range.last)) },
            enabled = value < range.last,
            modifier = Modifier.size(56.dp).semantics { contentDescription = more },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        ) { Text("+", fontSize = 28.sp, fontWeight = FontWeight.Bold) }
    }
}

/** App language: English or Korean. Uses the system per-app locale, which also restarts the screen to apply it. */
@Composable
private fun LanguageRow() {
    val context = LocalContext.current
    val lm = context.getSystemService(android.app.LocaleManager::class.java)
    val current = lm.applicationLocales.takeIf { !it.isEmpty }?.get(0)?.language
        ?: context.resources.configuration.locales[0].language
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.settings_language), fontSize = 19.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().selectableGroup()) {
            for ((tag, name) in listOf("en" to "English", "ko" to "한국어")) {
                val selected = current == tag
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    border = if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 60.dp)
                        .selectable(selected = selected, role = Role.RadioButton, onClick = {
                            if (!selected) lm.applicationLocales = android.os.LocaleList.forLanguageTags(tag)
                        }),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(8.dp)) {
                        Text(name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
