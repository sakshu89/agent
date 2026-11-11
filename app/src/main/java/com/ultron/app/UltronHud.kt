package com.ultron.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.ultron.app.performance.PerformanceMode
import com.ultron.app.ai.CloudModelConfig
import com.ultron.core.ai.ModelMode
import com.ultron.app.diagnostics.DiagnosticManager
import com.ultron.app.performance.PerformanceProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private val Bg = Color(0xFF010202)
private val Glass = Color(0xFF0B1011).copy(alpha = .76f)
private val Glass2 = Color(0xFF12191A).copy(alpha = .58f)
private val White = Color(0xFFEAF4F1)
private val Dim = Color(0xFF91A39F)
private val Green = Color(0xFF9DFFB0)
private val Cyan = Color(0xFF65EFFF)
private val Violet = Color(0xFFB88CFF)
private val Gold = Color(0xFFFFD27A)
private val Red = Color(0xFFFF6D7A)

private val AccentPalette = listOf(Cyan, Violet, Gold, Green)

private fun blendColors(a: Color, b: Color, fraction: Float): Color {
    val t = fraction.coerceIn(0f, 1f)
    return Color(
        red = a.red + (b.red - a.red) * t,
        green = a.green + (b.green - a.green) * t,
        blue = a.blue + (b.blue - a.blue) * t,
        alpha = a.alpha + (b.alpha - a.alpha) * t
    )
}

@Composable
fun UltronHud(
    modifier: Modifier = Modifier,
    onRequestMicrophonePermission: () -> Unit = {},
    onOpenAccessibilitySettings: () -> Unit = {},
    onPickVoskModel: () -> Unit = {},
    onRequestLocationPermission: () -> Unit = {},
    onOpenSessionFolder: () -> Unit = {},
    onSetLocalServer: (Boolean) -> Unit = {}
) {
    val c = LocalContext.current
    var screen by remember { mutableStateOf(0) }
    var input by remember { mutableStateOf("") }
    var perf by remember { mutableStateOf(PerformanceProfile.getMode(c)) }

    LaunchedEffect(Unit) {
        AssistantController.init(c)
        UltronSettings.load(c)
        LogStore.i("ITRA", "ITRA 5.0 interface online.")
        AssistantController.resumeIfEnabled()
    }

    val status = AssistantController.status
    val listening = AssistantController.listening
    val processing = AssistantController.processing
    val mode = ModeManager.mode
    val visual = VisualIntensity.profile(mode)
    val accentTransition = rememberInfiniteTransition(label = "dynamicAccent")
    val accentPhase by accentTransition.animateFloat(
        initialValue = 0f,
        targetValue = AccentPalette.size.toFloat(),
        animationSpec = infiniteRepeatable(tween((18000 / visual.animationSpeed).toInt(), easing = LinearEasing)),
        label = "accentPhase"
    )
    val accentIndex = accentPhase.toInt().coerceIn(0, AccentPalette.lastIndex)
    val accentNext = (accentIndex + 1) % AccentPalette.size
    val accentFraction = accentPhase - accentIndex
    val baseAccent = blendColors(AccentPalette[accentIndex], AccentPalette[accentNext], accentFraction)
    val accent = VisualIntensity.amplify(baseAccent, mode)

    Box(
        modifier.fillMaxSize().background(
            Brush.radialGradient(
                colors = listOf(accent.copy(.055f * visual.glow), Bg, Bg),
                radius = 1000f
            )
        )
    ) {
        ParticleField(accent.copy(alpha = (accent.alpha * visual.glow).coerceIn(0f, 1f)))
        Column(
            Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            GlassTopBar(status, mode, accent)
            Spacer(Modifier.height(10.dp))

            when (screen) {
                0 -> {
                    GlassNav(screen, accent) { screen = it }
                    Spacer(Modifier.height(8.dp))
                    CoreScreen(
                        accent, status, listening, processing, input,
                        { input = it },
                        { val x = input; input = ""; if (x.isNotBlank()) AssistantController.handleUserInput(x) }
                    )
                }
                1 -> {
                    GlassNav(screen, accent) { screen = it }
                    VoiceScreen(c, accent, onRequestMicrophonePermission, onRequestLocationPermission)
                }
                2 -> {
                    GlassNav(screen, accent) { screen = it }
                    LogsScreen(accent)
                }
                3 -> {
                    GlassNav(screen, accent) { screen = it }
                    StatusScreen(c, accent)
                }
                else -> {
                    GlassNav(screen, accent) { screen = it }
                    SettingsScreen(
                        c, accent, onOpenAccessibilitySettings, onPickVoskModel,
                        onOpenSessionFolder, perf, onSetLocalServer
                    ) { perf = it; PerformanceProfile.setMode(c, it) }
                }
            }

            Spacer(Modifier.height(8.dp))
            BottomControls(
                AssistantController.handsFree,
                {
                    if (AssistantController.handsFree) AssistantController.setHandsFree(false)
                    else if (!AssistantController.setHandsFree(true)) onRequestMicrophonePermission()
                },
                listening, processing
            ) {
                if (listening) AssistantController.stopTapListening()
                else AssistantController.tapToTalk(onRequestMicrophonePermission)
            }
        }
    }
}

@Composable
private fun GlassTopBar(status: String, mode: ModeManager.Mode, accent: Color) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(Glass2)
            .border(1.dp, accent.copy(.20f), RoundedCornerShape(24.dp))
            .padding(horizontal = 15.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("ITRA", color = White, fontSize = 23.sp, fontWeight = FontWeight.Bold, letterSpacing = 5.sp, fontFamily = FontFamily.Monospace)
            Text("PERSONAL AI // ANDROID", color = accent, fontSize = 8.sp, letterSpacing = 2.sp, fontFamily = FontFamily.Monospace)
        }
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(if (status.equals("ERROR", true)) Red else Green))
                Spacer(Modifier.width(7.dp))
                Text(mode.name, color = accent, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
            }
            Text(status.uppercase(), color = Dim, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun GlassNav(selected: Int, accent: Color, onSelect: (Int) -> Unit) {
    val names = listOf("CORE", "VOICE", "DIAG", "STATUS", "SETTINGS")
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(Glass2).border(1.dp, White.copy(.06f), RoundedCornerShape(20.dp))
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        names.forEachIndexed { i, name ->
            val active = selected == i
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(15.dp))
                    .background(if (active) accent.copy(.12f) else Color.Transparent)
                    .clickable { onSelect(i) }.padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(name, color = if (active) accent else Dim, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
private fun ColumnScope.CoreScreen(
    accent: Color, status: String, listening: Boolean, processing: Boolean,
    input: String, onInput: (String) -> Unit, onSubmit: () -> Unit
) {
    Column(Modifier.fillMaxWidth().weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(28.dp))
                .background(Brush.radialGradient(listOf(accent.copy(.055f), Color.Transparent)))
                .border(1.dp, accent.copy(.10f), RoundedCornerShape(28.dp)),
            contentAlignment = Alignment.Center
        ) {
            CoreAnimation(active = listening || processing, thinking = processing, accent = accent)
        }

        Spacer(Modifier.height(8.dp))
        GlassConversation(accent)
        Spacer(Modifier.height(7.dp))

        OutlinedTextField(
            value = input,
            onValueChange = onInput,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Talk to ITRA…", color = Dim, fontSize = 11.sp) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = accent,
                unfocusedBorderColor = White.copy(.10f),
                focusedTextColor = White,
                unfocusedTextColor = White,
                cursorColor = accent,
                focusedContainerColor = Glass,
                unfocusedContainerColor = Glass
            ),
            shape = RoundedCornerShape(18.dp),
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                imeAction = androidx.compose.ui.text.input.ImeAction.Send
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSend = { onSubmit() })
        )
    }
}

@Composable
private fun CoreAnimation(active: Boolean, thinking: Boolean, accent: Color) {
    val transition = rememberInfiniteTransition(label = "ultronCore")
    val speed = if (thinking) 1.65f else if (active) 1.25f else .65f
    val rotation by transition.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween((10500 / speed).toInt(), easing = LinearEasing)),
        label = "rotation"
    )
    val reverseRotation by transition.animateFloat(
        360f, 0f,
        infiniteRepeatable(tween((13500 / speed).toInt(), easing = LinearEasing)),
        label = "reverseRotation"
    )
    val pulse by transition.animateFloat(
        .88f, 1.12f,
        infiniteRepeatable(tween((if (active) 720 else 1500), easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    Canvas(Modifier.size(310.dp)) {
        val cx = size.width / 2
        val cy = size.height / 2
        val base = size.minDimension * .25f
        val p = pulse

        drawCircle(
            Brush.radialGradient(listOf(accent.copy(.28f), accent.copy(.08f), Color.Transparent)),
            radius = base * 2.5f * p,
            center = Offset(cx, cy)
        )

        for (i in 0..5) {
            val radius = base * (1.05f + i * .29f)
            drawCircle(accent.copy(alpha = .035f + i * .012f), radius, Offset(cx, cy), style = Stroke(1.1f))
        }

        // Layered fluid orbital arcs: these remain vector-rendered and scale cleanly on Android.
        val colors = listOf(accent, Cyan, Violet, Gold)
        for (i in 0..3) {
            val radius = base * (1.05f + i * .29f)
            val box = Size(radius * 2, radius * 2)
            val top = Offset(cx - radius, cy - radius)
            drawArc(
                colors[i].copy(alpha = .70f - i * .09f),
                rotation + i * 72f,
                220f - i * 24f,
                false, top, box, style = Stroke(width = 2.4f - i * .25f)
            )
            drawArc(
                colors[(i + 1) % colors.size].copy(alpha = .48f),
                reverseRotation - i * 55f,
                130f + i * 16f,
                false, top, box, style = Stroke(width = 1.6f)
            )
        }

        // Small orbiting energy nodes.
        for (i in 0 until 10) {
            val angle = Math.toRadians((rotation + i * 36f).toDouble())
            val radius = base * (1.18f + (i % 3) * .30f)
            val x = cx + cos(angle).toFloat() * radius
            val y = cy + sin(angle).toFloat() * radius
            val r = if (i % 3 == 0) 3.8f else 2.2f
            drawCircle(colors[i % colors.size].copy(.65f), r, Offset(x, y))
        }

        // Inner vortex.
        val inner = base * .76f * p
        drawCircle(accent.copy(.10f), inner * 1.65f)
        drawCircle(Cyan.copy(.25f), inner, style = Stroke(3f))
        drawCircle(Violet.copy(.22f), inner * .72f, style = Stroke(2f))
        drawCircle(
            Brush.radialGradient(listOf(White.copy(.95f), accent.copy(.9f), accent.copy(.15f), Color.Transparent)),
            radius = inner * .48f,
            center = Offset(cx, cy)
        )
        drawCircle(White.copy(.88f), inner * .11f)
    }
}

@Composable
private fun ParticleField(accent: Color) {
    val particles = remember {
        List(42) {
            Triple(
                Random.nextFloat(),
                Random.nextFloat(),
                Random.nextFloat()
            )
        }
    }
    val t by rememberInfiniteTransition(label = "particles").animateFloat(
        0f, 1f, infiniteRepeatable(tween(16000, easing = LinearEasing)), label = "particleTime"
    )
    Canvas(Modifier.fillMaxSize()) {
        particles.forEachIndexed { i, (x0, y0, phase) ->
            val x = ((x0 + sin((t + phase) * 6.283f) * .025f + 1f) % 1f) * size.width
            val y = ((y0 + cos((t * .8f + phase) * 6.283f) * .018f + 1f) % 1f) * size.height
            val radius = 1f + (i % 3) * .7f
            drawCircle(accent.copy(alpha = .10f + (i % 4) * .035f), radius, Offset(x, y))
        }
    }
}

@Composable
private fun GlassConversation(accent: Color) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(Glass).border(1.dp, accent.copy(.12f), RoundedCornerShape(18.dp)).padding(12.dp)
    ) {
        val u = AssistantController.userText
        val a = AssistantController.assistantText
        if (u.isBlank() && a.isBlank()) {
            Text("ITRA READY // AWAITING COMMAND", color = Dim, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        } else {
            if (u.isNotBlank()) Text("YOU  $u", color = Dim, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            if (a.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text("ITRA  $a", color = White, fontSize = 12.sp, lineHeight = 18.sp)
            }
        }
    }
}

@Composable
private fun ColumnScope.VoiceScreen(
    c: Context, accent: Color, onMic: () -> Unit, onLoc: () -> Unit
) {
    Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
        GlassCard("VOICE CONTROL", "Wake word, conversation and speech pipeline", accent) {
            Info("WAKE WORD", AssistantController.wakeAliases)
            Info("MODE", if (AssistantController.handsFree) "ACTIVE CONVERSATION / WAKE WORD" else "STANDBY")
            Info("VOSK", if (VoskModelManager.shouldUse(c)) "OFFLINE MODEL ACTIVE" else "ANDROID SPEECH FALLBACK")
            GlassButton("MICROPHONE PERMISSION", accent, onClick = onMic)
            GlassButton("ENABLE LOCATION FOR WEATHER", accent, filled = false, onClick = onLoc)
        }
        GlassCard("OWNER VOICE", "Optional speaker verification is experimental.", accent) {
            Info("VOICE AUTHENTICATION", "NOT ENABLED — no speaker-verification model is installed")
            Text("Voice matching is never treated as perfect identity proof.", color = Dim, fontSize = 10.sp)
        }
    }
}

@Composable
private fun ColumnScope.LogsScreen(accent: Color) {
    // Keep diagnostics available without turning the primary experience into a raw trace.
    // Routine INFO entries and full assistant answers stay out of the visible log feed.
    val visibleEntries = LogStore.entries.filter { it.level == LogStore.Level.ERROR || it.level == LogStore.Level.WARN }
    LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
        item {
            Text("DIAGNOSTICS · warnings and errors only", color = Dim, fontSize = 9.sp,
                fontFamily = FontFamily.Monospace, modifier = Modifier.padding(vertical = 6.dp))
        }
        items(visibleEntries.takeLast(80).asReversed()) { e ->
            Text(
                LogStore.format(e),
                color = when (e.level) {
                    LogStore.Level.ERROR -> Red
                    LogStore.Level.WARN -> Gold
                    LogStore.Level.ACTION -> accent
                    else -> Dim
                },
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(vertical = 3.dp)
            )
        }
    }
}

@Composable
private fun ColumnScope.StatusScreen(c: Context, accent: Color) {
    Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
        GlassCard("SYSTEM STATUS", "Runtime health and capability report", accent) {
            Info("ANDROID", Build.VERSION.RELEASE)
            Info("SDK", Build.VERSION.SDK_INT.toString())
            Info("MODEL", Build.MODEL)
            Info("MIC", if (ContextCompat.checkSelfPermission(c, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) "GRANTED" else "MISSING")
            Info("NOTIFICATIONS", if (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) "GRANTED" else "MISSING")
            Info("ACCESSIBILITY", if (UltronAccessibilityService.getInstance() != null) "CONNECTED" else "NOT CONNECTED")
            Info("LOCAL LLM", "${LocalServerConfig.getBaseUrl(c)} · ${LocalServerConfig.getModel(c)}")
            Info("AI ROUTING", "${CloudModelConfig.mode(c)} · cloud ${if (CloudModelConfig.hasKey(c)) "configured" else "not configured"}")
            Info("WHISPER", if (WhisperLocalConfig.enabled(c)) "LOCAL ADAPTER ENABLED" else "VOSK / ANDROID FALLBACK")
            Info("WEATHER", "Open-Meteo + device location")
        }
        GlassCard("DIAGNOSTICS", "Permission, storage, model and runtime checks", accent) {
            DiagnosticManager.run(c).forEach { check -> Info(check.name, if (check.ok) "OK · ${check.detail}" else "FAIL · ${check.detail}") }
        }
        GlassCard("CAPABILITIES", "Android security boundaries are respected.", accent) {
            Text("Messaging requires confirmation before sending. Accessibility actions report failure when verification cannot confirm success.", color = Dim, fontSize = 10.sp)
            Spacer(Modifier.height(8.dp))
            listOf(
                "Planner / re-plan" to "READY",
                "Observe / verify / recover" to "READY",
                "OCR + Accessibility vision" to if (UltronAccessibilityService.getInstance() != null) "CONNECTED" else "AVAILABLE",
                "Research engine" to "READY",
                "Emergency stop" to "READY"
            ).forEach { Info(it.first, it.second) }
        }
        if (GhostModeManager.active) {
            GlassCard("PRIVILEGED CONTROLS", "Additional Android capabilities are available only when the local privileged gate is active.", accent) {
                GhostModeManager.capabilitySummary().forEach { Info(it.first, it.second) }
                Text("Android permissions and device-owner policy still apply.", color = Dim, fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun ColumnScope.SettingsScreen(
    c: Context, accent: Color, onA: () -> Unit, onV: () -> Unit,
    onFolder: () -> Unit, mode: PerformanceMode, onSetLocalServer: (Boolean) -> Unit, onMode: (PerformanceMode) -> Unit
) {
    val usedMb by produceState(0L) {
        value = withContext(Dispatchers.IO) {
            runCatching { StorageManager.usageBytes() / 1024 / 1024 }.getOrDefault(0L)
        }
    }
    Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
        GlassCard("VOICE SETTINGS", "Changes are persisted.", accent) {
            GlassSwitch("Only respond when I say ITRA", AssistantController.requireWakeWord, accent) { AssistantController.updateRequireWakeWord(it) }
            GlassSwitch("Strict (always say ITRA first)", AssistantController.strictWake, accent) { AssistantController.updateStrictWake(it) }
            SliderRow("Speech speed", (UltronSettings.speechRate - .5f) / 1.5f, accent) {
                UltronSettings.setRate(c, .5f + it * 1.5f)
                runCatching { AssistantController.applyVoiceSettings() }
            }
            Info("WAKE ALIASES", AssistantController.wakeAliases)
        }

        GlassCard("OPERATING MODE", "Capability gates: Light / Medium / Extreme.", accent) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ModeManager.Mode.values().forEach { m ->
                    GlassChoice(m.name, ModeManager.mode == m, accent) { ModeManager.setMode(m) }
                }
            }
        }

        GlassCard("LOCAL AI SERVER", "ITRA uses your local inference server only. Public API endpoints are blocked.", accent) {
            var serverUrl by remember { mutableStateOf(LocalServerConfig.getBaseUrl(c)) }
            var modelName by remember { mutableStateOf(LocalServerConfig.getModel(c)) }
            var serverStatus by remember { mutableStateOf("CURRENT LOCAL ENDPOINT") }
            OutlinedTextField(
                value = serverUrl,
                onValueChange = { serverUrl = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Local server URL") },
                placeholder = { Text("http://192.168.1.10:8080") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = White, unfocusedTextColor = White,
                    focusedBorderColor = accent, unfocusedBorderColor = Dim.copy(.4f),
                    focusedLabelColor = accent, unfocusedLabelColor = Dim
                )
            )
            OutlinedTextField(
                value = modelName,
                onValueChange = { modelName = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Local model identifier") },
                placeholder = { Text("local-model / qwen2.5:7b") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = White, unfocusedTextColor = White,
                    focusedBorderColor = accent, unfocusedBorderColor = Dim.copy(.4f),
                    focusedLabelColor = accent, unfocusedLabelColor = Dim
                )
            )
            GlassButton("SAVE LOCAL SERVER", accent) {
                val urlResult = LocalServerConfig.setBaseUrl(c, serverUrl)
                val modelResult = LocalServerConfig.setModel(c, modelName)
                serverStatus = if (urlResult.isSuccess && modelResult.isSuccess) "SAVED. The next request will use this local server and model." else
                    (urlResult.exceptionOrNull() ?: modelResult.exceptionOrNull())?.message ?: "Invalid local configuration."
            }
            Text(serverStatus, color = accent, fontSize = 9.sp)
        }

        GlassCard("HERMES AGENT ENGINE", "Optional local Hermes 0.23.1 reasoning and tool engine. Android actions remain inside ITRA's permission gates.", accent) {
            var enabled by remember { mutableStateOf(HermesBridgeConfig.isEnabled(c)) }
            var bridgeUrl by remember { mutableStateOf(HermesBridgeConfig.getBaseUrl(c)) }
            var bridgeToken by remember { mutableStateOf(HermesBridgeConfig.getToken(c)) }
            var bridgeStatus by remember { mutableStateOf(if (enabled) "Hermes bridge selected. Requests fall back to local ITRA AI if unavailable." else "Disabled. ITRA uses its configured local model.") }
            GlassSwitch("Use Hermes as ITRA reasoning engine", enabled, accent) { enabled = it }
            OutlinedTextField(value = bridgeUrl, onValueChange = { bridgeUrl = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Hermes bridge URL") }, placeholder = { Text("http://127.0.0.1:8766") }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = White, unfocusedTextColor = White, focusedBorderColor = accent, unfocusedBorderColor = Dim.copy(.4f), focusedLabelColor = accent, unfocusedLabelColor = Dim))
            OutlinedTextField(value = bridgeToken, onValueChange = { bridgeToken = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Bridge bearer token") }, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = White, unfocusedTextColor = White, focusedBorderColor = accent, unfocusedBorderColor = Dim.copy(.4f), focusedLabelColor = accent, unfocusedLabelColor = Dim))
            GlassButton("SAVE HERMES CONNECTION", accent) {
                val saved = HermesBridgeConfig.save(c, bridgeUrl, bridgeToken)
                if (saved.isSuccess) {
                    HermesBridgeConfig.setEnabled(c, enabled)
                    bridgeStatus = if (enabled) "Saved. Hermes will handle eligible reasoning requests; Android actions stay native." else "Saved. Hermes is disabled."
                } else bridgeStatus = saved.exceptionOrNull()?.message ?: "Could not save Hermes settings."
            }
            Text(bridgeStatus, color = accent, fontSize = 9.sp)
            Text("Run the bundled agent-engine/itra_hermes_bridge.py companion with Hermes Agent 0.23.1 installed. On the same phone use 127.0.0.1; for another trusted device use its private LAN IP. Public hosts are rejected.", color = Dim, fontSize = 9.sp, lineHeight = 14.sp)
        }

        GlassCard("HYBRID INTELLIGENCE", "One ITRA brain with local, cloud and local-first hybrid routing.", accent) {
            var selectedMode by remember { mutableStateOf(CloudModelConfig.mode(c)) }
            var cloudEndpoint by remember { mutableStateOf(CloudModelConfig.endpoint(c)) }
            var cloudModel by remember { mutableStateOf(CloudModelConfig.model(c)) }
            var cloudKey by remember { mutableStateOf("") }
            var cloudStatus by remember { mutableStateOf(if (CloudModelConfig.hasKey(c)) "Encrypted cloud key is stored." else "No cloud key configured.") }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf(ModelMode.LOCAL, ModelMode.CLOUD, ModelMode.AUTO).forEach { option ->
                    GlassChoice(option.name, selectedMode == option, accent) { selectedMode = option }
                }
            }
            Info("LOCAL", "Uses your configured private/local inference server.")
            Info("CLOUD", "Uses the configured HTTPS OpenAI-compatible provider.")
            Info("AUTO", "Local first; cloud fallback only for permitted non-private requests.")
            OutlinedTextField(value = cloudEndpoint, onValueChange = { cloudEndpoint = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Cloud API base URL (HTTPS)") }, placeholder = { Text("https://api.openai.com/v1") }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = White, unfocusedTextColor = White, focusedBorderColor = accent, unfocusedBorderColor = Dim.copy(.4f), focusedLabelColor = accent, unfocusedLabelColor = Dim))
            OutlinedTextField(value = cloudModel, onValueChange = { cloudModel = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Cloud model identifier") }, placeholder = { Text("Provider model name") }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = White, unfocusedTextColor = White, focusedBorderColor = accent, unfocusedBorderColor = Dim.copy(.4f), focusedLabelColor = accent, unfocusedLabelColor = Dim))
            OutlinedTextField(value = cloudKey, onValueChange = { cloudKey = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text(if (CloudModelConfig.hasKey(c)) "Replace cloud API key (optional)" else "Cloud API key") }, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = White, unfocusedTextColor = White, focusedBorderColor = accent, unfocusedBorderColor = Dim.copy(.4f), focusedLabelColor = accent, unfocusedLabelColor = Dim))
            GlassButton("SAVE HYBRID AI SETTINGS", accent) {
                val result = CloudModelConfig.save(c, selectedMode, cloudEndpoint, cloudModel, cloudKey)
                cloudStatus = if (result.isSuccess) "Saved. Cloud key encrypted with Android Keystore." else result.exceptionOrNull()?.message ?: "Could not save settings."
                if (result.isSuccess) cloudKey = ""
            }
            Text(cloudStatus, color = accent, fontSize = 9.sp)
            Text("Privacy: ITRA blocks cloud routing when Privacy Mode is enabled or obvious sensitive data is detected. AUTO never sends private context to the cloud. You control provider, model and API key.", color = Dim, fontSize = 9.sp, lineHeight = 14.sp)
        }

        GlassCard("LOCAL INTEGRATION SERVER", "Optional authenticated HTTP boundary for local integrations.", accent) {
            var enabled by remember { mutableStateOf(false) }
            var port by remember { mutableStateOf(LocalServerConfig.getApiPort(c).toString()) }
            GlassSwitch("Run local server", enabled, accent) { enabled = it; if (it) LocalServerConfig.setApiPort(c, port.toIntOrNull() ?: 8765); onSetLocalServer(it) }
            OutlinedTextField(value = port, onValueChange = { port = it.filter(Char::isDigit).take(5) }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Port") }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = White, unfocusedTextColor = White, focusedBorderColor = accent, unfocusedBorderColor = Dim.copy(.4f), focusedLabelColor = accent, unfocusedLabelColor = Dim))
            GlassSwitch("Localhost-only binding", LocalServerConfig.isLocalhostOnly(c), accent) { LocalServerConfig.setLocalhostOnly(c, it) }
            Info("AUTH", if (LocalServerConfig.getApiToken(c).isBlank()) "Token generated on first start" else "Bearer token configured")
        }

        GlassCard("OFFLINE WHISPER", "Optional local multilingual Whisper-compatible transcription endpoint.", accent) {
            GlassSwitch("Enable local Whisper for tap-to-talk", WhisperLocalConfig.enabled(c), accent) { WhisperLocalConfig.setEnabled(c, it) }
            var endpoint by remember { mutableStateOf(WhisperLocalConfig.endpoint(c)) }
            OutlinedTextField(value = endpoint, onValueChange = { endpoint = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Whisper endpoint") }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = White, unfocusedTextColor = White, focusedBorderColor = accent, unfocusedBorderColor = Dim.copy(.4f), focusedLabelColor = accent, unfocusedLabelColor = Dim))
            GlassButton("SAVE WHISPER ENDPOINT", accent) { WhisperLocalConfig.setEndpoint(c, endpoint) }
            Text("The endpoint must be local/private. ITRA sends recorded audio only when this option is enabled. Continuous hands-free remains on Vosk/Android recognition.", color = Dim, fontSize = 9.sp, lineHeight = 14.sp)
        }

        GlassCard("AI SETTINGS", "Personality, language and agent behavior.", accent) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("FRIENDLY", "CONCISE", "TECHNICAL").forEach {
                    GlassChoice(it, UltronSettings.personality == it, accent) { UltronSettings.setPersonality(c, it) }
                }
            }
            GlassSwitch("Compact spoken answers", UltronSettings.compactReplies, accent) { UltronSettings.setCompact(c, it) }
            GlassSwitch("Privacy mode", UltronSettings.privacyMode, accent) { UltronSettings.setPrivacy(c, it) }
            Text("LANGUAGE: AUTO", color = accent, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            Text("ITRA asks its configured AI model to reply in your language. Recognition quality depends on your Android speech service or the imported Vosk language model.", color = Dim, fontSize = 9.sp, lineHeight = 14.sp)
        }

        GlassCard("AUTONOMOUS ACTIONS", "Supported actions are permission-gated and verified where possible.", accent) {
            Info("APP CONTROL", "Launch installed apps")
            Info("SCREEN", "Back / home / scroll / tap named controls")
            Info("WEB", "Google search + app-specific search")
            Info("MESSAGING", "Confirmation required before sending")
            Info("NAVIGATION", "Hand off destinations to Maps")
            Text("ITRA can chain up to four verified tool actions, read screen text with on-device OCR, and store approved step plans in its persistent local queue. Visual scene understanding, browser DOM automation and a task-planning dashboard are still future work.", color = Dim, fontSize = 9.sp, lineHeight = 14.sp)
        }

        GlassCard("ANIMATION", "Performance-aware rendering.", accent) {
            SliderRow("Animation scale", UltronSettings.animationScale / 1.5f, accent) { UltronSettings.setAnimation(c, it * 1.5f) }
            Text("PERFORMANCE PROFILE", color = Dim, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            Row {
                PerformanceMode.values().forEach { GlassChoice(it.name, mode == it, accent) { onMode(it) } }
            }
        }

        GlassCard("SYSTEM INTEGRATIONS", "Android-controlled capabilities.", accent) {
            GlassButton("ACCESSIBILITY SETTINGS", accent, onClick = onA)
            var notificationEnabled by remember { mutableStateOf(com.ultron.app.notifications.UltronNotificationListener.isEnabled(c)) }
            var notificationPackages by remember { mutableStateOf(com.ultron.app.notifications.UltronNotificationListener.allowedPackages(c).joinToString(",")) }
            GlassSwitch("Understand allowlisted notifications", notificationEnabled, accent) { notificationEnabled = it }
            OutlinedTextField(
                value = notificationPackages,
                onValueChange = { notificationPackages = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Allowed app package IDs (comma-separated)") },
                placeholder = { Text("com.whatsapp, org.telegram.messenger") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = White, unfocusedTextColor = White,
                    focusedBorderColor = accent, unfocusedBorderColor = Dim.copy(.4f),
                    focusedLabelColor = accent, unfocusedLabelColor = Dim
                )
            )
            GlassButton("SAVE NOTIFICATION RULES", accent) {
                val packages = notificationPackages.split(",").map { it.trim() }.filter { it.matches(Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")) }.toSet()
                com.ultron.app.notifications.UltronNotificationListener.configure(c, notificationEnabled, packages)
            }
            GlassButton("ANDROID NOTIFICATION ACCESS", accent) {
                runCatching { c.startActivity(android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            GlassButton("IMPORT VOSK MODEL", accent, onClick = onV)
            val offlineModels = VoskModelManager.installedModels(c)
            if (offlineModels.isNotEmpty()) {
                Text("OFFLINE MODELS", color = Dim, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                offlineModels.forEach { model ->
                    GlassChoice(model, VoskModelManager.modelName == model, accent) { VoskModelManager.selectModel(c, model) }
                }
            }
            GlassButton("OPEN SESSION FOLDER", accent, onClick = onFolder)
            Info("MEMORY", "$usedMb MB / 5120 MB")
            Text("Microphone gain, noise suppression and speaker verification require a dedicated audio pipeline/model and are not exposed as functional controls in this build.", color = Dim, fontSize = 9.sp, lineHeight = 14.sp)
        }
    }
}

@Composable
private fun GlassCard(title: String, subtitle: String, accent: Color, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Glass, Glass2, Glass)))
            .border(1.dp, accent.copy(.16f), RoundedCornerShape(24.dp))
            .padding(14.dp)
    ) {
        Text(title, color = White, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp, fontFamily = FontFamily.Monospace)
        Text(subtitle, color = accent.copy(.72f), fontSize = 8.sp)
        Spacer(Modifier.height(11.dp))
        content()
    }
}

@Composable
private fun GlassButton(label: String, accent: Color, filled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(15.dp))
            .background(if (filled) accent.copy(.12f) else Color.Transparent)
            .border(1.dp, accent.copy(if (filled) .24f else .15f), RoundedCornerShape(15.dp))
            .clickable { onClick() }.padding(vertical = 10.dp, horizontal = 13.dp)
    ) {
        Text(label, color = if (filled) accent else White, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun GlassChoice(label: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(13.dp))
            .background(if (selected) accent.copy(.13f) else Color.Transparent)
            .border(1.dp, if (selected) accent.copy(.32f) else White.copy(.08f), RoundedCornerShape(13.dp))
            .clickable { onClick() }.padding(horizontal = 8.dp, vertical = 7.dp)
    ) {
        Text(label, color = if (selected) accent else Dim, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun GlassSwitch(label: String, value: Boolean, accent: Color, onChange: (Boolean) -> Unit) {
    val track by animateColorAsState(if (value) Green.copy(.24f) else Color(0xFF050606), tween(280), label = "switchTrack")
    val border by animateColorAsState(if (value) Green.copy(.72f) else White.copy(.10f), tween(280), label = "switchBorder")
    val thumb by animateColorAsState(if (value) Green else Color(0xFF151A19), tween(280), label = "switchThumb")

    Row(
        Modifier.fillMaxWidth().padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = White, fontSize = 10.sp)
        Box(
            Modifier.width(54.dp).height(30.dp).clip(RoundedCornerShape(18.dp))
                .background(track).border(1.dp, border, RoundedCornerShape(18.dp))
                .clickable { onChange(!value) }.padding(4.dp)
        ) {
            Box(
                Modifier.size(20.dp).align(if (value) Alignment.CenterEnd else Alignment.CenterStart)
                    .clip(CircleShape).background(thumb).shadow(if (value) 9.dp else 0.dp, CircleShape)
            )
        }
    }
}

@Composable
private fun Info(k: String, v: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(k, color = Dim, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
        Text(v, color = White, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun SliderRow(label: String, value: Float, accent: Color, onChange: (Float) -> Unit) {
    Text(label, color = Dim, fontSize = 9.sp)
    Slider(
        value = value.coerceIn(0f, 1f),
        onValueChange = onChange,
        colors = SliderDefaults.colors(
            thumbColor = accent,
            activeTrackColor = accent,
            inactiveTrackColor = White.copy(.12f)
        )
    )
}

@Composable
private fun BottomControls(
    handsFree: Boolean, onHandsFree: () -> Unit,
    listening: Boolean, processing: Boolean, onVoice: () -> Unit
) {
    val active = if (handsFree) Green else Color(0xFF090C0C)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ControlButton(
            if (handsFree) "HANDS-FREE ON" else "HANDS-FREE",
            if (handsFree) Green else Dim,
            Modifier.weight(1f),
            onHandsFree
        )
        ControlButton(
            if (listening) "STOP LISTENING" else if (processing) "THINKING…" else "TALK TO ITRA",
            if (listening) Gold else Cyan,
            Modifier.weight(1f),
            onVoice
        )
        ControlButton("EMERGENCY STOP", Red, Modifier.weight(1f)) { AssistantController.emergencyStop() }
    }
}

@Composable
private fun ControlButton(label: String, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(17.dp)).background(accent.copy(.10f))
            .border(1.dp, accent.copy(.24f), RoundedCornerShape(17.dp))
            .clickable { onClick() }.padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = accent, fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}
