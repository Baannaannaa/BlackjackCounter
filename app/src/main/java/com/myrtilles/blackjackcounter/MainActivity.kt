package com.myrtilles.blackjackcounter

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.myrtilles.blackjackcounter.ui.theme.Typography
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// ───────────────────────────────────────────────
// MAIN ACTIVITY
// ───────────────────────────────────────────────

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Notification Permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        enableEdgeToEdge()

        // Immersive Mode
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())

        // Initializations
        SoundManager.init(applicationContext)
        ReminderWorker.schedule(applicationContext)

        lifecycleScope.launch {
            StatsManager.checkAndResetStreak(applicationContext)
        }

        setContent {
            val context = LocalContext.current
            val darkMode by StatsManager.darkModeFlow(context).collectAsState(initial = false)

            MaterialTheme(
                colorScheme = if (darkMode) DarkCasinoColors else LightCasinoColors,
                typography = Typography
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigator()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        SoundManager.release()
    }
}

// ───────────────────────────────────────────────
// DATA MODELS & SETTINGS
// ───────────────────────────────────────────────

data class DrillSettings(
    val numCards: Int = 20,
    val speedMs: Int = 900,
    val showSuits: Boolean = true,
    val numDecks: Int = 6
)

data class BlackjackSettings(
    val numDecks: Int = 6
)

data class Card(val rank: Int, val suit: String)

// ───────────────────────────────────────────────
// NAVIGATION
// ───────────────────────────────────────────────

@Composable
fun AppNavigator() {
    var screen by rememberSaveable { mutableStateOf("home") }
    var drillSettings by remember { mutableStateOf(DrillSettings()) }
    var gameSettings by remember { mutableStateOf(BlackjackSettings()) }
    var finalCount by rememberSaveable { mutableIntStateOf(0) }
    var cardsDrawn by rememberSaveable { mutableIntStateOf(0) }

    when (screen) {
        "home" -> HomeScreen(
            onSelectDrill = { screen = "drill_config" },
            onSelectGame = { screen = "game_config" },
            onShowRules = { screen = "rules" },
            onShowStats = { screen = "stats" },
            onShowAppSettings = { screen = "app_settings" }
        )
        "drill_config" -> {
            BackHandler { screen = "home" }
            DrillConfigScreen(
                settings = drillSettings,
                onSettingsChange = { drillSettings = it },
                onStart = { screen = "drill" },
                onBack = { screen = "home" }
            )
        }
        "game_config" -> {
            BackHandler { screen = "home" }
            BlackjackConfigScreen(
                settings = gameSettings,
                onSettingsChange = { gameSettings = it },
                onStart = { screen = "game" },
                onBack = { screen = "home" }
            )
        }
        "game" -> {
            BackHandler { screen = "home" }
            BlackjackGameScreen(
                settings = gameSettings,
                onBack = { screen = "home" }
            )
        }
        "app_settings" -> {
            BackHandler { screen = "home" }
            AppSettingsScreen(onBack = { screen = "home" })
        }
        "rules" -> {
            BackHandler { screen = "home" }
            RulesScreen(onBack = { screen = "home" })
        }
        "stats" -> {
            BackHandler { screen = "home" }
            StatsScreen(onBack = { screen = "home" })
        }
        "drill" -> {
            BackHandler { screen = "drill_config" }
            DrillScreen(
                settings = drillSettings,
                onFinish = { count, drawn ->
                    finalCount = count
                    cardsDrawn = drawn
                    screen = "result"
                }
            )
        }
        "result" -> {
            BackHandler { screen = "home" }
            ResultScreen(
                settings = drillSettings,
                runningCount = finalCount,
                cardsDrawn = cardsDrawn,
                totalDecks = drillSettings.numDecks,
                onRestart = { screen = "home" }
            )
        }
    }
}

// ───────────────────────────────────────────────
// SCREENS
// ───────────────────────────────────────────────

@Composable
fun HomeScreen(
    onSelectDrill: () -> Unit,
    onSelectGame: () -> Unit,
    onShowRules: () -> Unit,
    onShowStats: () -> Unit,
    onShowAppSettings: () -> Unit
) {
    val context = LocalContext.current
    val streak by StatsManager.streakFlow(context).collectAsState(initial = 0)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))

        // Streak Display (Duolingo style)
        if (streak > 0) {
            Card(
                modifier = Modifier
                    .wrapContentWidth()
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = CasinoGold.copy(alpha = 0.15f),
                    contentColor = CasinoGold
                ),
                border = BorderStroke(2.dp, CasinoGold)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Text("🔥", fontSize = 24.sp)
                    Spacer(Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(
                            streak.toString(),
                            fontWeight = FontWeight.Black,
                            fontSize = 22.sp,
                            lineHeight = 22.sp
                        )
                        Text(
                            "JOURS",
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            lineHeight = 10.sp
                        )
                    }
                }
            }
        } else {
            Spacer(Modifier.height(16.dp))
        }

        Spacer(Modifier.height(16.dp))

        // Adaptive Title
        var fontSize by remember { mutableStateOf(32.sp) }
        Text(
            text = stringResource(R.string.home_title),
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.fillMaxWidth(),
            onTextLayout = { textLayoutResult ->
                if (textLayoutResult.didOverflowWidth) {
                    fontSize = (fontSize.value * 0.9f).sp
                }
            }
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.home_subtitle),
            fontSize = 18.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(64.dp))

        // Main Actions
        Button(
            onClick = onSelectDrill,
            modifier = Modifier.fillMaxWidth().height(64.dp)
        ) {
            Text(stringResource(R.string.btn_start), fontSize = 22.sp)
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onSelectGame,
            modifier = Modifier.fillMaxWidth().height(64.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = Color.Black
            )
        ) {
            Text(stringResource(R.string.btn_play_game), fontSize = 22.sp)
        }

        Spacer(Modifier.height(48.dp))
        HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))
        Spacer(Modifier.height(32.dp))

        // Secondary Options
        OutlinedButton(
            onClick = onShowRules,
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text(stringResource(R.string.btn_rules))
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onShowStats,
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text(stringResource(R.string.btn_stats))
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onShowAppSettings,
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text(stringResource(R.string.btn_settings))
        }
    }
}

@Composable
fun DrillConfigScreen(
    settings: DrillSettings,
    onSettingsChange: (DrillSettings) -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))
        Text(stringResource(R.string.btn_start), fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(32.dp))

        Text(stringResource(R.string.settings_num_cards, settings.numCards), fontSize = 18.sp)
        Slider(
            value = settings.numCards.toFloat(),
            onValueChange = { onSettingsChange(settings.copy(numCards = it.roundToInt())) },
            valueRange = 10f..100f
        )
        Spacer(Modifier.height(16.dp))

        Text(stringResource(R.string.settings_speed, settings.speedMs), fontSize = 18.sp)
        Slider(
            value = settings.speedMs.toFloat(),
            onValueChange = { onSettingsChange(settings.copy(speedMs = it.roundToInt())) },
            valueRange = 300f..2000f,
            steps = 33
        )
        Spacer(Modifier.height(16.dp))

        Text(stringResource(R.string.settings_decks, settings.numDecks), fontSize = 18.sp)
        Slider(
            value = settings.numDecks.toFloat(),
            onValueChange = { onSettingsChange(settings.copy(numDecks = it.roundToInt())) },
            valueRange = 1f..8f,
            steps = 6
        )

        val maxCards = settings.numDecks * 52
        if (settings.numCards > maxCards) {
            Text(
                stringResource(R.string.settings_max_cards_warning, maxCards, maxCards),
                fontSize = 15.sp,
                color = Color(0xFFE65100),
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(32.dp))

        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text(stringResource(R.string.btn_validate), fontSize = 20.sp)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text(stringResource(R.string.btn_back))
        }
    }
}

@Composable
fun BlackjackConfigScreen(
    settings: BlackjackSettings,
    onSettingsChange: (BlackjackSettings) -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))
        Text(stringResource(R.string.btn_play_game), fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(32.dp))

        Text(stringResource(R.string.settings_decks, settings.numDecks), fontSize = 18.sp)
        Slider(
            value = settings.numDecks.toFloat(),
            onValueChange = { onSettingsChange(settings.copy(numDecks = it.roundToInt())) },
            valueRange = 1f..8f,
            steps = 6
        )

        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(32.dp))

        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text(stringResource(R.string.btn_validate), fontSize = 20.sp)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text(stringResource(R.string.btn_back))
        }
    }
}

@Composable
fun AppSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val darkMode by StatsManager.darkModeFlow(context).collectAsState(initial = false)
    val soundOn by StatsManager.soundFlow(context).collectAsState(initial = true)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(32.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.settings_title),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.settings_dark_mode), fontSize = 18.sp, modifier = Modifier.weight(1f))
                Switch(
                    checked = darkMode,
                    onCheckedChange = { scope.launch { StatsManager.setDarkMode(context, it) } }
                )
            }
            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.settings_sounds), fontSize = 18.sp, modifier = Modifier.weight(1f))
                Switch(
                    checked = soundOn,
                    onCheckedChange = { scope.launch { StatsManager.setSound(context, it) } }
                )
            }
            Spacer(Modifier.height(24.dp))

            Text(stringResource(R.string.settings_language_title), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            val languages = listOf(
                "Français" to "fr",
                "English" to "en",
                "Español" to "es",
                "Italiano" to "it",
                "Português" to "pt"
            )

            languages.forEach { (name, code) ->
                OutlinedButton(
                    onClick = {
                        androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
                            androidx.core.os.LocaleListCompat.forLanguageTags(code)
                        )
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Text(name)
                }
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))
            Spacer(Modifier.height(32.dp))
        }

        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text(stringResource(R.string.btn_back))
        }
    }
}

@Composable
fun RulesScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Spacer(Modifier.height(32.dp))
        Text(stringResource(R.string.rules_title), fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        Text(stringResource(R.string.rules_principle_title), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.rules_principle_desc),
            fontSize = 17.sp
        )
        Spacer(Modifier.height(20.dp))

        Text(stringResource(R.string.rules_values_title), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        RuleRow("2, 3, 4, 5, 6", "+1", Color(0xFF2E7D32))
        RuleRow("7, 8, 9", "0", Color.Gray)
        RuleRow("10, J, Q, K, A", "−1", Color(0xFFC62828))
        Spacer(Modifier.height(20.dp))

        Text(stringResource(R.string.rules_rc_title), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.rules_rc_desc),
            fontSize = 17.sp
        )
        Spacer(Modifier.height(20.dp))

        Text(stringResource(R.string.rules_tc_title), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.rules_tc_desc),
            fontSize = 17.sp
        )
        Spacer(Modifier.height(20.dp))

        Text(stringResource(R.string.rules_advice_title), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.rules_advice_desc),
            fontSize = 17.sp
        )
        Spacer(Modifier.height(32.dp))

        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text(stringResource(R.string.btn_back))
        }
    }
}

@Composable
fun RuleRow(cards: String, value: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(cards, fontSize = 18.sp, modifier = Modifier.weight(1f))
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
fun DrillScreen(
    settings: DrillSettings,
    onFinish: (count: Int, drawn: Int) -> Unit
) {
    var currentCard by remember { mutableIntStateOf(2) }
    var currentSuitString by remember { mutableStateOf("♠") }
    var runningCount by remember { mutableIntStateOf(0) }
    var cardsShown by remember { mutableIntStateOf(0) }
    var animKey by remember { mutableIntStateOf(0) }

    val context = LocalContext.current
    val soundOn by StatsManager.soundFlow(context).collectAsState(initial = true)

    LaunchedEffect(Unit) {
        val shoe = createShoe(settings.numDecks)
        val cardsToDraw = minOf(settings.numCards, shoe.size)

        repeat(cardsToDraw) {
            val card = shoe.removeAt(shoe.size - 1)
            currentCard = card.rank
            currentSuitString = card.suit
            runningCount += hiLoValue(card.rank)
            cardsShown++
            animKey++
            if (soundOn) SoundManager.playCard()
            delay(settings.speedMs.toLong())
        }
        onFinish(runningCount, cardsToDraw)
    }

    val animProgress = remember { Animatable(0f) }
    LaunchedEffect(animKey) {
        animProgress.snapTo(0f)
        animProgress.animateTo(
            1f,
            animationSpec = tween(
                durationMillis = (settings.speedMs * 0.6f).toInt().coerceAtMost(400),
                easing = FastOutSlowInEasing
            )
        )
    }

    val progress = animProgress.value
    val offsetX = (1f - progress) * -300f
    val rotationY = (1f - progress) * 90f

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("$cardsShown / ${settings.numCards}", fontSize = 18.sp, color = Color.Gray)
        Spacer(Modifier.height(24.dp))

        val rank = cardToString(currentCard)

        Box(
            modifier = Modifier.graphicsLayer(
                translationX = offsetX,
                rotationY = rotationY,
                alpha = progress,
                cameraDistance = 12f * LocalDensity.current.density
            )
        ) {
            PlayingCard(
                rank = rank,
                suit = currentSuitString,
                showSuit = settings.showSuits
            )
        }
    }
}

@Composable
fun ResultScreen(
    settings: DrillSettings,
    runningCount: Int,
    cardsDrawn: Int,
    totalDecks: Int,
    onRestart: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var rcAnswer by remember { mutableStateOf("") }
    var tcAnswer by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }

    val totalCards = totalDecks * 52
    val cardsRemaining = totalCards - cardsDrawn
    val decksRemainingExact = cardsRemaining / 52.0
    val decksRemaining = ((decksRemainingExact * 4).roundToInt() / 4.0).coerceAtLeast(0.25)

    val trueTC = runningCount / decksRemaining
    val trueTCRounded = (trueTC * 2).roundToInt() / 2.0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))
        Text(stringResource(R.string.result_title), fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.result_info, totalDecks, cardsDrawn),
            fontSize = 15.sp, color = Color.Gray
        )
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = rcAnswer,
            onValueChange = { input ->
                val filtered = buildString {
                    var seenDot = false
                    input.forEachIndexed { i, c ->
                        when {
                            c.isDigit() -> append(c)
                            c == '-' && i == 0 && isEmpty() -> append(c)
                            (c == '.' || c == ',') && !seenDot -> {
                                append('.')
                                seenDot = true
                            }
                        }
                    }
                }
                rcAnswer = filtered
            },
            label = { Text(stringResource(R.string.label_rc)) },
            enabled = !submitted,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = tcAnswer,
            onValueChange = { input ->
                val filtered = buildString {
                    var seenDot = false
                    input.forEachIndexed { i, c ->
                        when {
                            c.isDigit() -> append(c)
                            c == '-' && i == 0 && isEmpty() -> append(c)
                            (c == '.' || c == ',') && !seenDot -> {
                                append('.')
                                seenDot = true
                            }
                        }
                    }
                }
                tcAnswer = filtered
            },
            label = { Text(stringResource(R.string.label_tc)) },
            enabled = !submitted,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(24.dp))

        if (!submitted) {
            Button(
                onClick = {
                    submitted = true
                    val userRC = rcAnswer.toIntOrNull()
                    val userTC = tcAnswer.replace(',', '.').toDoubleOrNull()
                    val rcOk = userRC == runningCount
                    val tcOk = userTC != null && Math.abs(userTC - trueTCRounded) <= 0.5

                    val session = SessionStat(
                        date = System.currentTimeMillis(),
                        numCards = settings.numCards,
                        speedMs = settings.speedMs,
                        numDecks = settings.numDecks,
                        trueRC = runningCount,
                        userRC = userRC,
                        trueTC = trueTCRounded,
                        userTC = userTC,
                        rcOk = rcOk,
                        tcOk = tcOk
                    )
                    scope.launch {
                        StatsManager.addSession(context, session)
                        StatsManager.updateStreak(context)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text(stringResource(R.string.btn_validate))
            }
        } else {
            val userRC = rcAnswer.toIntOrNull()
            val userTC = tcAnswer.replace(',', '.').toDoubleOrNull()
            val rcOk = userRC == runningCount
            val tcOk = userTC != null && Math.abs(userTC - trueTCRounded) <= 0.5

            ResultLine(
                stringResource(R.string.result_rc), rcOk,
                stringResource(R.string.result_truth_rc, runningCount),
                stringResource(R.string.result_user_answer, rcAnswer)
            )
            Spacer(Modifier.height(12.dp))
            ResultLine(
                stringResource(R.string.result_tc), tcOk,
                stringResource(R.string.result_truth_tc, trueTCRounded, decksRemaining, decksRemainingExact),
                stringResource(R.string.result_user_answer, tcAnswer)
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onRestart,
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text(stringResource(R.string.btn_menu))
            }
        }
    }
}

@Composable
fun ResultLine(label: String, ok: Boolean, truth: String, user: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "${if (ok) "✅" else "❌"} $label",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = if (ok) Color(0xFF2E7D32) else Color(0xFFC62828)
        )
        Text(truth, fontSize = 16.sp)
        Text(user, fontSize = 15.sp, color = Color.Gray)
    }
}

@Composable
fun StatsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sessions by StatsManager.sessionsFlow(context).collectAsState(initial = emptyList())
    val streak by StatsManager.streakFlow(context).collectAsState(initial = 0)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Spacer(Modifier.height(32.dp))
        Text(stringResource(R.string.stats_title), fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        if (sessions.isEmpty()) {
            Text(
                stringResource(R.string.stats_empty),
                fontSize = 17.sp,
                color = Color.Gray
            )
            Spacer(Modifier.weight(1f))
        } else {
            val total = sessions.size
            val rcWins = sessions.count { it.rcOk }
            val tcWins = sessions.count { it.tcOk }
            val rcPct = rcWins * 100.0 / total
            val tcPct = tcWins * 100.0 / total
            val avgSpeed = sessions.asSequence().map { it.speedMs }.average()
            val avgCards = sessions.asSequence().map { it.numCards }.average()

            StatBlock("Streak 🔥", "$streak jours")
            StatBlock(stringResource(R.string.stats_sessions_played), total.toString())
            StatBlock(stringResource(R.string.stats_rc_accuracy), "${"%.0f".format(rcPct)} % ($rcWins/$total)")
            StatBlock(stringResource(R.string.stats_tc_accuracy), "${"%.0f".format(tcPct)} % ($tcWins/$total)")
            StatBlock(stringResource(R.string.stats_avg_speed), "${avgSpeed.roundToInt()} ms/carte")
            StatBlock(stringResource(R.string.stats_avg_cards), "${avgCards.roundToInt()} par session")

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            Text(stringResource(R.string.stats_history), fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                items(sessions.take(50)) { s ->
                    SessionRow(s)
                    HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))
                }
            }

            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = {
                    scope.launch { StatsManager.clearAll(context) }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.btn_clear_history), color = Color(0xFFC62828))
            }
        }

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text(stringResource(R.string.btn_back))
        }
    }
}

@Composable
fun StatBlock(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 17.sp, modifier = Modifier.weight(1f))
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SessionRow(s: SessionStat) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(s.formattedDate(), fontSize = 15.sp, color = Color.Gray)
            Text(
                stringResource(R.string.stat_row_info, s.numCards, s.speedMs, s.numDecks),
                fontSize = 15.sp
            )
            Text(
                stringResource(
                    R.string.stat_row_result,
                    if (s.rcOk) "✅" else "❌",
                    s.userRC ?: "?",
                    s.trueRC,
                    if (s.tcOk) "✅" else "❌",
                    s.userTC?.let { "%.1Format".format(it) } ?: "?",
                    "%.1Format".format(s.trueTC)
                ),
                fontSize = 15.sp
            )
        }
    }
}

@Composable
fun PlayingCard(
    rank: String,
    suit: String,
    showSuit: Boolean,
    modifier: Modifier = Modifier,
    showCornerSuits: Boolean = true,
    centerIconSize: Dp = 70.dp
) {
    val isRed = suit == "♥" || suit == "♦"
    val textColor = when {
        !showSuit -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        isRed -> Color(0xFFD32F2F)
        else -> Color(0xFF111111)
    }

    val iconRes = when (suit) {
        "♠" -> R.drawable.suit_spades
        "♥" -> R.drawable.suit_hearts
        "♦" -> R.drawable.suit_diamonds
        "♣" -> R.drawable.suit_clubs
        else -> R.drawable.suit_spades
    }

    Box(
        modifier = modifier
            .size(width = 260.dp, height = 370.dp)
            .background(Color.White, RoundedCornerShape(22.dp))
            .border(2.dp, Color(0xFF333333), RoundedCornerShape(22.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 18.dp, top = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(rank, fontSize = 42.sp, fontWeight = FontWeight.Bold, color = textColor)
            if (showSuit && showCornerSuits) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = textColor
                )
            } else if (!showSuit) {
                Text("?", fontSize = 36.sp, color = textColor)
            }
        }

        if (showSuit) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(centerIconSize),
                tint = textColor
            )
        } else {
            Text("?", fontSize = centerIconSize.value.sp, color = textColor)
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 18.dp, bottom = 14.dp)
                .graphicsLayer(rotationZ = 180f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(rank, fontSize = 42.sp, fontWeight = FontWeight.Bold, color = textColor)
            if (showSuit && showCornerSuits) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = textColor
                )
            } else if (!showSuit) {
                Text("?", fontSize = 36.sp, color = textColor)
            }
        }
    }
}

@Composable
fun BlackjackGameScreen(settings: BlackjackSettings, onBack: () -> Unit) {
    val context = LocalContext.current
    val soundOn by StatsManager.soundFlow(context).collectAsState(initial = true)

    var bankroll by rememberSaveable { mutableIntStateOf(1000) }
    var currentBet by rememberSaveable { mutableIntStateOf(10) }
    val shoe = remember(settings) { mutableStateListOf<Card>().apply { addAll(createShoe(settings.numDecks)) } }
    
    val playerHand = remember(settings) { mutableStateListOf<Card>() }
    val dealerHand = remember(settings) { mutableStateListOf<Card>() }
    
    var runningCount by rememberSaveable(settings) { mutableIntStateOf(0) }
    var showHelper by rememberSaveable { mutableStateOf(false) }

    var gameState by rememberSaveable { mutableStateOf("betting") }
    var gameResultResId by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(bankroll) {
        if (currentBet > bankroll) {
            currentBet = bankroll.coerceAtLeast(10)
        }
    }

    val playCardSound = { if (soundOn) SoundManager.playCard() }

    fun calculateScore(hand: List<Card>): Int {
        var score = 0
        var aces = 0
        for (card in hand) {
            when (card.rank) {
                14 -> { score += 11; aces++ }
                in 11..13 -> score += 10
                else -> score += card.rank
            }
        }
        while (score > 21 && aces > 0) {
            score -= 10
            aces--
        }
        return score
    }

    fun dealCard(): Card {
        if (shoe.size < 12) {
            shoe.clear()
            shoe.addAll(createShoe(settings.numDecks))
            runningCount = 0
        }
        val card = shoe.removeAt(shoe.size - 1)
        runningCount += hiLoValue(card.rank)
        playCardSound()
        return card
    }

    val decksRemainingExact = shoe.size / 52.0
    val decksRemainingRounded = ((decksRemainingExact * 4).roundToInt() / 4.0).coerceAtLeast(0.25)
    val trueCount = runningCount / decksRemainingRounded

    fun startHand() {
        if (bankroll < currentBet) return
        bankroll -= currentBet
        playerHand.clear()
        dealerHand.clear()
        
        repeat(2) {
            playerHand.add(dealCard())
            dealerHand.add(dealCard())
        }

        if (calculateScore(playerHand) == 21) {
            gameState = "dealer_turn"
        } else {
            gameState = "playing"
        }
    }

    LaunchedEffect(gameState) {
        if (gameState == "dealer_turn") {
            delay(600)
            while (calculateScore(dealerHand) < 17) {
                dealerHand.add(dealCard())
                delay(600)
            }
            
            val pScore = calculateScore(playerHand)
            val dScore = calculateScore(dealerHand)
            
            when {
                pScore > 21 -> gameResultResId = R.string.game_result_lose
                dScore > 21 -> { gameResultResId = R.string.game_result_win; bankroll += currentBet * 2 }
                pScore > dScore -> { gameResultResId = R.string.game_result_win; bankroll += currentBet * 2 }
                pScore < dScore -> gameResultResId = R.string.game_result_lose
                else -> { gameResultResId = R.string.game_result_push; bankroll += currentBet }
            }
            gameState = "result"
        }

        if (gameState == "result" && bankroll < 10) {
            delay(2000)
            gameState = "game_over"
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.game_bankroll, bankroll), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 18.sp)
                IconButton(onClick = onBack) { Icon(Icons.Default.Close, null) }
            }
            
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = showHelper, onCheckedChange = { showHelper = it }, modifier = Modifier.size(32.dp))
                    Text(stringResource(R.string.game_show_count), fontSize = 13.sp)
                    if (showHelper) {
                        Spacer(Modifier.width(8.dp))
                        Text("RC: $runningCount | TC: ${"%.1f".format(trueCount)}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.game_dealer), fontSize = 16.sp, color = Color.Gray)
                    if (gameState != "betting" && gameState != "playing") {
                        Text("Score: ${calculateScore(dealerHand)}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    HandDisplay(hand = dealerHand, isDealer = true, gameState = gameState, modifier = Modifier.fillMaxWidth().height(160.dp), scale = 0.45f)
                }
                Spacer(Modifier.height(24.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.game_player), fontSize = 14.sp, color = Color.Gray)
                    Text("Score: ${calculateScore(playerHand)}", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(), 
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                HandDisplay(
                    hand = playerHand, 
                    isDealer = false, 
                    gameState = gameState, 
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                    scale = 0.5f
                )

                if (gameState == "betting" || gameState == "result") {
                    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(10, 100, 500).forEach { amount ->
                                Button(onClick = { currentBet = (currentBet + amount).coerceAtMost(bankroll) }, modifier = Modifier.weight(1f).requiredHeight(36.dp), colors = ButtonDefaults.buttonColors(containerColor = CasinoGreenLight)) { Text("+$amount$", fontSize = 12.sp) }
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(10, 100, 500).forEach { amount ->
                                OutlinedButton(onClick = { currentBet = (currentBet - amount).coerceAtLeast(10) }, modifier = Modifier.weight(1f).requiredHeight(36.dp)) { Text("-$amount$", fontSize = 12.sp) }
                            }
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Row(modifier = Modifier.weight(1f).requiredHeight(56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                            Text(stringResource(R.string.game_bet, currentBet), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(onClick = { startHand() }, modifier = Modifier.weight(1f).requiredHeight(56.dp)) { Text(stringResource(R.string.game_btn_deal)) }
                    }
                } else if (gameState == "playing") {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { gameState = "dealer_turn" }, modifier = Modifier.weight(1f).requiredHeight(56.dp)) { Text(stringResource(R.string.game_btn_stand)) }
                        Button(onClick = { playerHand.add(dealCard()); if (calculateScore(playerHand) > 21) { gameState = "dealer_turn"; gameResultResId = R.string.game_result_bust } }, modifier = Modifier.weight(1f).requiredHeight(56.dp)) { Text(stringResource(R.string.game_btn_hit)) }
                    }
                }
            }
        }

        if (gameState == "result" && gameResultResId != 0) {
            Card(modifier = Modifier.align(Alignment.Center).padding(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f))) {
                Text(stringResource(gameResultResId), modifier = Modifier.padding(horizontal = 32.dp, vertical = 20.dp), fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
            }
        }

        if (gameState == "game_over") {
            Surface(modifier = Modifier.fillMaxSize(), color = Color.Black.copy(alpha = 0.85f)) {
                Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text("💸", fontSize = 80.sp)
                    Text(stringResource(R.string.game_over_title), fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Text(stringResource(R.string.game_over_desc), fontSize = 18.sp, color = Color.LightGray, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(48.dp))
                    Button(onClick = { bankroll = 1000; currentBet = 10; gameState = "betting" }, modifier = Modifier.fillMaxWidth().height(60.dp), colors = ButtonDefaults.buttonColors(containerColor = CasinoGold, contentColor = Color.Black)) { Text(stringResource(R.string.game_btn_restart), fontSize = 20.sp) }
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().height(50.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White), border = BorderStroke(1.dp, Color.White)) { Text(stringResource(R.string.btn_menu)) }
                }
            }
        }
    }
}

@Composable
fun HandDisplay(
    hand: List<Card>, 
    isDealer: Boolean, 
    gameState: String,
    modifier: Modifier = Modifier.fillMaxWidth().height(200.dp),
    scale: Float = 0.52f,
    centerIconSize: Dp = 70.dp
) {
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = if (isDealer) Alignment.TopCenter else Alignment.BottomCenter
    ) {
        val availableWidth = maxWidth
        val cardWidth = (260 * scale).dp
        val n = hand.size
        val spacing = if (n <= 1) 0.dp else {
            val totalWidthNoOverlap = cardWidth * n + (8.dp * (n - 1))
            if (totalWidthNoOverlap <= availableWidth) {
                8.dp
            } else {
                val calculatedSpacing = (availableWidth - cardWidth) / (n - 1) - cardWidth
                calculatedSpacing.coerceAtLeast(-(cardWidth - 20.dp))
            }
        }

        Row(
            modifier = Modifier.wrapContentWidth(unbounded = true),
            horizontalArrangement = Arrangement.spacedBy(spacing, Alignment.CenterHorizontally)
        ) {
            hand.forEachIndexed { index, card ->
                val isHidden = isDealer && (gameState == "playing" || gameState == "dealer_turn") && index == 1
                CardContainer(scale = scale) {
                    if (isHidden) {
                        CardBack()
                    } else {
                        PlayingCard(
                            rank = cardToString(card.rank),
                            suit = card.suit,
                            showSuit = true,
                            showCornerSuits = false,
                            centerIconSize = centerIconSize
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CardContainer(scale: Float, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .requiredSize(width = (260 * scale).dp, height = (370 * scale).dp)
    ) {
        Box(modifier = Modifier.graphicsLayer(scaleX = scale, scaleY = scale, transformOrigin = TransformOrigin(0f, 0f))) {
            content()
        }
    }
}

@Composable
fun CardBack() {
    Box(
        modifier = Modifier
            .size(width = 260.dp, height = 370.dp)
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(22.dp))
            .border(4.dp, Color.White, RoundedCornerShape(22.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text("♣♦\n♥♠", fontSize = 40.sp, color = Color.White.copy(alpha = 0.3f))
    }
}

fun hiLoValue(card: Int): Int = when (card) {
    in 2..6 -> 1
    in 7..9 -> 0
    else -> -1
}

fun cardToString(card: Int): String = when (card) {
    11 -> "J"
    12 -> "Q"
    13 -> "K"
    14 -> "A"
    else -> card.toString()
}

fun createShoe(numDecks: Int): MutableList<Card> {
    val suits = listOf("♠", "♥", "♦", "♣")
    val shoe = mutableListOf<Card>()
    repeat(numDecks) {
        for (value in 2..14) {
            for (suit in suits) {
                shoe.add(Card(value, suit))
            }
        }
    }
    shoe.shuffle()
    return shoe
}
