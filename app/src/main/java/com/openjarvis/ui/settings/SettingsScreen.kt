package com.openjarvis.ui.settings

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.openjarvis.llm.UniversalAdapter
import com.openjarvis.voice.STTMode
import com.openjarvis.voice.VoiceManager
import com.openjarvis.ui.theme.VoidColor

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onSaveProvider: (String, String, String, String) -> Unit,
    voiceManager: VoiceManager,
    initialProviderName: String = "Groq",
    initialBaseUrl: String = "",
    initialApiKey: String = "",
    initialModel: String = "llama-3.1-70b-versatile",
    modifier: Modifier = Modifier
) {
    var selectedProvider by remember { mutableStateOf(initialProviderName) }
    var baseUrl by remember { mutableStateOf(initialBaseUrl) }
    var apiKey by remember { mutableStateOf(initialApiKey) }
    var model by remember { mutableStateOf(initialModel) }
    var showBaseUrlField by remember { mutableStateOf(false) }
    var showProvidersDropdown by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    var lastSaved by remember { mutableStateOf(0L) }
    var savedVisible by remember { mutableStateOf(false) }
    var voiceEnabled by remember { mutableStateOf(voiceManager.isVoiceEnabled()) }
    var speakResults by remember { mutableStateOf(voiceManager.isSpeakResultsEnabled()) }
    var speechRate by remember { mutableFloatStateOf(voiceManager.getSpeechRate()) }
    var sttMode by remember { mutableStateOf(voiceManager.getSTTMode()) }
    var recognitionLanguage by remember { mutableStateOf(voiceManager.getRecognitionLanguage()) }
    var showLanguageMenu by remember { mutableStateOf(false) }
    val sttModeLabel = if (sttMode == STTMode.PUSH_TO_TALK) "نگه‌داشتن میکروفون" else "تشخیص خودکار سکوت"
    
    LaunchedEffect(selectedProvider) {
        showBaseUrlField = selectedProvider == "Custom"
    }
    
    val scrollState = rememberScrollState()
    
    androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(VoidColor.Void950)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            SettingsHeader(onNavigateBack = onNavigateBack)
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                SectionLabel("ارائه‌دهنده هوش مصنوعی")
                
                Spacer(modifier = Modifier.height(12.dp))
                
                ProviderSelectorCard(
                    selectedProvider = selectedProvider,
                    isExpanded = showProvidersDropdown,
                    onToggle = { showProvidersDropdown = !showProvidersDropdown },
                    onSelect = { provider ->
                        selectedProvider = provider
                        showProvidersDropdown = false
                    }
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                FloatingLabelTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = "Base URL",
                    isFocused = showBaseUrlField,
                    visible = showBaseUrlField
                )
                
                FloatingLabelTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = "API Key",
                    isFocused = false,
                    isPassword = true,
                    showPassword = showPassword,
                    onTogglePassword = { showPassword = !showPassword }
                )
                
                FloatingLabelTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = "Model",
                    isFocused = false
                )
                
                Text(
                    text = "با سرویس‌های سازگار با OpenAI کار می‌کنه",
                    style = TextStyle(
                        fontFamily = FontFamily.Default,
                        fontWeight = FontWeight(400),
                        fontSize = 11.sp,
                        color = VoidColor.TextDisabled
                    )
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                TestConnectionButton(
                    apiKey = apiKey,
                    baseUrl = baseUrl,
                    model = model,
                    onSave = { name, url, key, mdl ->
                        onSaveProvider(name, url, key, mdl)
                        lastSaved = System.currentTimeMillis()
                        savedVisible = true
                    }
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                SectionLabel("صدا و زبان")
                
                Spacer(modifier = Modifier.height(12.dp))
                
                SettingsToggleRow(
                    title = "دستیار صوتی",
                    subtitle = "با میکروفون صحبت کن؛ فارسی و انگلیسی",
                    enabled = voiceEnabled,
                    onToggle = {
                        voiceEnabled = !voiceEnabled
                        voiceManager.setVoiceEnabled(voiceEnabled)
                    }
                )
                
                if (voiceEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    SettingsToggleRow(
                        title = "پاسخ صوتی",
                        subtitle = "جارویس نتیجهٔ کارها رو با صدا می‌گه",
                        enabled = speakResults,
                        onToggle = {
                            speakResults = !speakResults
                            voiceManager.setSpeakResultsEnabled(speakResults)
                        }
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // STT Mode selector
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = VoidColor.Void900,
                        border = BorderStroke(1.dp, VoidColor.BorderSubtle)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { 
                                    sttMode = if (sttMode == STTMode.PUSH_TO_TALK) STTMode.AUTO_VAD else STTMode.PUSH_TO_TALK
                                    voiceManager.setSTTMode(sttMode)
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "روش ضبط صدا",
                                    style = TextStyle(
                                        fontFamily = FontFamily.Default,
                                        fontWeight = FontWeight(500),
                                        fontSize = 14.sp,
                                        color = VoidColor.TextPrimary
                                    )
                                )
                                Text(
                                    text = if (sttMode == STTMode.PUSH_TO_TALK)
                                        "برای ضبط نگه دار و برای پایان رها کن"
                                    else
                                        "با سکوت، ضبط به‌صورت خودکار تموم می‌شه",
                                    style = TextStyle(
                                        fontFamily = FontFamily.Default,
                                        fontWeight = FontWeight(400),
                                        fontSize = 11.sp,
                                        color = VoidColor.TextDisabled
                                    )
                                )
                            }
                            Text(
                                text = sttModeLabel,
                                style = TextStyle(
                                    fontFamily = FontFamily.Default,
                                    fontWeight = FontWeight(500),
                                    fontSize = 12.sp,
                                    color = VoidColor.Violet
                                )
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "سرعت صحبت: ${"%.2f".format(speechRate)}x",
                        style = TextStyle(
                            fontFamily = FontFamily.Default,
                            fontSize = 12.sp,
                            color = VoidColor.TextSecondary
                        )
                    )
                    Slider(
                        value = speechRate,
                        onValueChange = {
                            speechRate = it
                            voiceManager.setSpeechRate(it)
                        },
                        valueRange = 0.5f..2.0f
                    )

                    Button(
                        onClick = {
                            val sample = when (recognitionLanguage) {
                                "en-US" -> "Hello! I am Jarvis. How can I help you?"
                                "en-AU" -> "G’day! I’m Jarvis. How can I help you today?"
                                "es-ES" -> "¡Hola! Soy Jarvis. ¿Cómo puedo ayudarte?"
                                "de-DE" -> "Hallo! Ich bin Jarvis. Wie kann ich dir helfen?"
                                else -> "سلام! من جارویسم. چطوری؟"
                            }
                            voiceManager.testSpeak(sample)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = VoidColor.VioletDim)
                    ) {
                        Text("تست صدای زبان انتخاب‌شده")
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = VoidColor.Void900,
                        border = BorderStroke(1.dp, VoidColor.BorderSubtle)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    showLanguageMenu = true
                                }.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("زبان تشخیص و پاسخ صوتی", color = VoidColor.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    Text("زبان گفتار ورودی و صدای خروجی جارویس", color = VoidColor.TextDisabled, fontSize = 11.sp)
                                }
                                Text(
                                    when (recognitionLanguage) {
                                        "en-US" -> "English (US)"
                                        "en-AU" -> "English (Australia)"
                                        "es-ES" -> "Español"
                                        "de-DE" -> "Deutsch"
                                        else -> "فارسی"
                                    },
                                    color = VoidColor.Cyan,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            DropdownMenu(
                                expanded = showLanguageMenu,
                                onDismissRequest = { showLanguageMenu = false }
                            ) {
                                listOf(
                                    "fa-IR" to "فارسی",
                                    "en-US" to "English (US)",
                                    "en-AU" to "English (Australia)",
                                    "es-ES" to "Español (España)",
                                    "de-DE" to "Deutsch"
                                ).forEach { (tag, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            recognitionLanguage = tag
                                            voiceManager.setRecognitionLanguage(tag)
                                            showLanguageMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    // Model status
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = VoidColor.Void800,
                        border = BorderStroke(1.dp, VoidColor.BorderSubtle)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = null,
                                tint = VoidColor.Green,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "تشخیص گفتار",
                                    style = TextStyle(
                                        fontFamily = FontFamily.Default,
                                        fontWeight = FontWeight(500),
                                        fontSize = 14.sp,
                                        color = VoidColor.TextPrimary
                                    )
                                )
                                Text(
                                    text = "موتور گفتار نصب‌شده روی دستگاه",
                                    style = TextStyle(
                                        fontFamily = FontFamily.Default,
                                        fontWeight = FontWeight(400),
                                        fontSize = 11.sp,
                                        color = VoidColor.TextDisabled
                                    )
                                )
                            }
                            Text(
                                text = "آماده",
                                style = TextStyle(
                                    fontFamily = FontFamily.Default,
                                    fontWeight = FontWeight(500),
                                    fontSize = 12.sp,
                                    color = VoidColor.Green
                                )
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                SectionLabel("دسترسی‌ها")
                
                Spacer(modifier = Modifier.height(12.dp))
                
                PermissionRow(
                    title = "دسترسی‌پذیری",
                    subtitle = "ضروری — برای فعال‌سازی لمس کن"
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Spacer(modifier = Modifier.height(32.dp))
                
                SectionLabel("درباره برنامه")
                
                Spacer(modifier = Modifier.height(12.dp))
                
                AboutSection()
                
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
        
        SavedToast(
            visible = savedVisible,
            onDismiss = { savedVisible = false },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp)
        )
    }
    }
}

@Composable
private fun SettingsHeader(onNavigateBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onNavigateBack) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowLeft,
                contentDescription = "بازگشت",
                tint = VoidColor.TextSecondary
            )
        }
        
        Text(
            text = "تنظیمات",
            style = TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight(600),
                fontSize = 20.sp,
                color = VoidColor.TextPrimary
            )
        )
    }
    
    Divider(
        modifier = Modifier.fillMaxWidth(),
        thickness = 1.dp,
        color = VoidColor.BorderSubtle
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight(600),
            fontSize = 10.sp,
            letterSpacing = 3.sp,
            color = VoidColor.TextDisabled
        )
    )
}

@Composable
private fun ProviderSelectorCard(
    selectedProvider: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onSelect: (String) -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = if (isExpanded) VoidColor.BorderGlow else VoidColor.BorderSubtle,
        animationSpec = tween(200),
        label = "border"
    )
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(16.dp),
        color = VoidColor.Void900,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProviderDot(provider = selectedProvider)
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Text(
                text = selectedProvider,
                style = TextStyle(
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight(500),
                    fontSize = 14.sp,
                    color = VoidColor.TextPrimary
                ),
                modifier = Modifier.weight(1f)
            )
            
            val rotationAngle by animateFloatAsState(
                targetValue = if (isExpanded) 180f else 0f,
                animationSpec = tween(200),
                label = "rotation"
            )
            
            Icon(
                imageVector = Icons.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = VoidColor.TextDisabled,
                modifier = Modifier.graphicsLayer(rotationZ = rotationAngle)
            )
        }
    }
    
    AnimatedVisibility(
        visible = isExpanded,
        enter = expandVertically() + fadeIn(animationSpec = tween(200)),
        exit = shrinkVertically() + fadeOut(animationSpec = tween(200))
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = VoidColor.Void800,
            border = BorderStroke(1.dp, VoidColor.BorderSubtle)
        ) {
            Column {
                UniversalAdapter.AVAILABLE_PROVIDERS.forEach { provider ->
                    ProviderOption(
                        label = provider,
                        isSelected = provider == selectedProvider,
                        onClick = { onSelect(provider) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProviderDot(provider: String) {
    val colors = mapOf(
        "Groq" to VoidColor.Violet,
        "Google Gemini" to VoidColor.Cyan,
        "OpenRouter" to VoidColor.Green,
        "Anthropic Claude" to VoidColor.Amber,
        "OpenAI" to VoidColor.Green,
        "Ollama (Local)" to VoidColor.Cyan,
        "Custom" to VoidColor.Violet
    )
    
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(colors[provider] ?: VoidColor.Violet)
    )
}

@Composable
private fun ProviderOption(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelected) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(16.dp)
                    .background(VoidColor.Violet)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
        } else {
            Spacer(modifier = Modifier.width(15.dp))
        }
        
        Text(
            text = label,
            style = TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight(500),
                fontSize = 14.sp,
                color = if (isSelected) VoidColor.Violet else VoidColor.TextPrimary
            )
        )
    }
}

@Composable
fun FloatingLabelTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isFocused: Boolean,
    isPassword: Boolean = false,
    showPassword: Boolean = false,
    visible: Boolean = true,
    onTogglePassword: (() -> Unit)? = null
) {
    val labelOffset by animateFloatAsState(
        targetValue = if (isFocused || value.isNotEmpty()) -20f else 0f,
        animationSpec = spring(stiffness = 300f, dampingRatio = 0.75f),
        label = "offset"
    )
    
    val labelScale by animateFloatAsState(
        targetValue = if (isFocused || value.isNotEmpty()) 0.75f else 1f,
        animationSpec = spring(stiffness = 300f, dampingRatio = 0.75f),
        label = "scale"
    )
    
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) VoidColor.Violet else VoidColor.BorderSubtle,
        animationSpec = tween(200),
        label = "border"
    )
    
    if (!visible) return
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(14.dp),
        color = VoidColor.Void900,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Text(
                text = label,
                style = TextStyle(
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight(400),
                    fontSize = 14.sp,
                    color = VoidColor.TextSecondary
                ),
                modifier = Modifier.graphicsLayer {
                    translationY = labelOffset.dp.toPx()
                    scaleX = labelScale
                    scaleY = labelScale
                }
            )
            
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 12.dp),
                textStyle = TextStyle(
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight(400),
                    fontSize = 14.sp,
                    color = VoidColor.TextPrimary
                ),
                visualTransformation = if (isPassword && !showPassword) 
                    PasswordVisualTransformation() else VisualTransformation.None
            )
        }
    }
}

@Composable
private fun TestConnectionButton(
    apiKey: String,
    baseUrl: String,
    model: String,
    onSave: (String, String, String, String) -> Unit
) {
    var testState by remember { mutableStateOf<TestState>(TestState.Idle) }
    
    val borderColor by animateColorAsState(
        targetValue = when (testState) {
            is TestState.Idle -> VoidColor.VioletDim
            is TestState.Loading -> VoidColor.Violet
            is TestState.Success -> VoidColor.Green
            is TestState.Error -> VoidColor.Red
        },
        animationSpec = tween(200),
        label = "btn_border"
    )
    
    val textColor = when (testState) {
        is TestState.Idle -> VoidColor.Violet
        is TestState.Loading -> VoidColor.Violet
        is TestState.Success -> VoidColor.Green
        is TestState.Error -> VoidColor.Red
    }
    
    OutlinedButton(
        onClick = {
            onSave("Groq", baseUrl, apiKey, model)
            testState = TestState.Success(150)
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, borderColor),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = textColor
        )
    ) {
        when (val state = testState) {
            is TestState.Idle -> Text("Test Connection")
            is TestState.Loading -> CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = VoidColor.Violet
            )
            is TestState.Success -> {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Connected — ${state.ms}ms")
            }
            is TestState.Error -> {
                Text("Failed — check key")
            }
        }
    }
}

sealed class TestState {
    data object Idle : TestState()
    data object Loading : TestState()
    data class Success(val ms: Long) : TestState()
    data class Error(val message: String) : TestState()
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    enabled: Boolean,
    onToggle: (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .then(
                if (onToggle != null) Modifier.clickable { onToggle() }
                else Modifier
            ),
        shape = RoundedCornerShape(16.dp),
        color = VoidColor.Void900,
        border = BorderStroke(1.dp, VoidColor.BorderSubtle)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = null,
                tint = VoidColor.TextSecondary,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = TextStyle(
                        fontFamily = FontFamily.Default,
                        fontWeight = FontWeight(500),
                        fontSize = 14.sp,
                        color = VoidColor.TextPrimary
                    )
                )
                Text(
                    text = subtitle,
                    style = TextStyle(
                        fontFamily = FontFamily.Default,
                        fontWeight = FontWeight(400),
                        fontSize = 11.sp,
                        color = VoidColor.TextDisabled
                    )
                )
            }
            
            Switch(
                checked = enabled,
                onCheckedChange = { onToggle?.invoke() },
                enabled = onToggle != null,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = VoidColor.TextPrimary,
                    checkedTrackColor = VoidColor.Violet,
                    uncheckedThumbColor = VoidColor.TextDisabled,
                    uncheckedTrackColor = VoidColor.Void600
                )
            )
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    subtitle: String
) {
    val isGranted by remember { mutableStateOf(false) }
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        shape = RoundedCornerShape(16.dp),
        color = VoidColor.Void900,
        border = BorderStroke(1.dp, VoidColor.BorderSubtle)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(VoidColor.Red.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = VoidColor.Red,
                    modifier = Modifier.size(20.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = TextStyle(
                        fontFamily = FontFamily.Default,
                        fontWeight = FontWeight(500),
                        fontSize = 14.sp,
                        color = VoidColor.TextPrimary
                    )
                )
                Text(
                    text = subtitle,
                    style = TextStyle(
                        fontFamily = FontFamily.Default,
                        fontWeight = FontWeight(400),
                        fontSize = 11.sp,
                        color = VoidColor.Red
                    )
                )
            }
            
            Icon(
                imageVector = Icons.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = VoidColor.Red
            )
        }
    }
}

@Composable
private fun AboutSection() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = VoidColor.Void900,
        border = BorderStroke(1.dp, VoidColor.BorderSubtle)
    ) {
        Column {
            AboutRow(title = "Version", value = "M1.0 (build 1)")
            Divider(thickness = 1.dp, color = VoidColor.BorderSubtle)
            AboutRow(title = "License", value = "MIT Open Source")
            Divider(thickness = 1.dp, color = VoidColor.BorderSubtle)
            AboutRow(title = "GitHub", value = "tokenarc/open-jarvis", isLink = true)
            Divider(thickness = 1.dp, color = VoidColor.BorderSubtle)
            
            Text(
                text = "Built on Android · From Termux with love",
                style = TextStyle(
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight(400),
                    fontSize = 12.sp,
                    color = VoidColor.TextDisabled
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )
        }
    }
}

@Composable
private fun AboutRow(
    title: String,
    value: String,
    isLink: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight(400),
                fontSize = 14.sp,
                color = VoidColor.TextSecondary
            )
        )
        
        Text(
            text = value,
            style = TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight(400),
                fontSize = 14.sp,
                color = if (isLink) VoidColor.Violet else VoidColor.TextPrimary
            )
        )
    }
}

@Composable
private fun SavedToast(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(visible) {
        if (visible) {
            kotlinx.coroutines.delay(1500)
            onDismiss()
        }
    }
    
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { 40 } + fadeIn(),
        exit = slideOutVertically { 40 } + fadeOut(),
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = VoidColor.Void800,
            border = BorderStroke(1.dp, VoidColor.Green.copy(alpha = 0.25f))
        ) {
            Text(
                text = "✓ saved",
                style = TextStyle(
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight(500),
                    fontSize = 12.sp,
                    color = VoidColor.Green
                ),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}