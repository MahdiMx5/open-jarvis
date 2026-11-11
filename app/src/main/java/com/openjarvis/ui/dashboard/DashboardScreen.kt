package com.openjarvis.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.app.Activity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.openjarvis.ui.auth.AuthActivity
import com.openjarvis.agent.AgentCore
import com.openjarvis.agent.AgentState
import com.openjarvis.graphify.GraphifyRepository
import com.openjarvis.ui.chat.ChatMessage
import com.openjarvis.ui.chat.ChatStorage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class ChatColors(
    val background: Color,
    val surface: Color,
    val text: Color,
    val muted: Color,
    val userBubble: Color,
    val assistantBubble: Color,
    val border: Color,
    val accent: Color
)

@Composable
fun DashboardScreen(
    onStartOverlay: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPermissions: () -> Unit,
    graphifyRepo: GraphifyRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("jarvis_chat_history", android.content.Context.MODE_PRIVATE) }
    val agent = remember { AgentCore(context.applicationContext) }
    val agentState by agent.state.collectAsState()
    var darkMode by remember { mutableStateOf(prefs.getBoolean("dark_mode", true)) }
    var tone by remember { mutableStateOf(prefs.getString("response_tone", "natural") ?: "natural") }
    var input by remember { mutableStateOf("") }
    var currentThreadId by remember { mutableStateOf(ChatStorage.currentThreadId(context)) }
    var messages by remember { mutableStateOf(ChatStorage.findThread(context, currentThreadId)?.messages ?: emptyList()) }
    var threads by remember { mutableStateOf(ChatStorage.loadThreads(context)) }
    var waitingForResponse by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showTone by remember { mutableStateOf(false) }
    var showProfile by remember { mutableStateOf(false) }
    var profileName by remember { mutableStateOf(FirebaseAuth.getInstance().currentUser?.displayName.orEmpty()) }
    var profileStatus by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    val colors = if (darkMode) ChatColors(
        background = Color(0xFF080D14),
        surface = Color(0xFF101923),
        text = Color(0xFFF4F4F4),
        muted = Color(0xFF8293A5),
        userBubble = Color(0xFF172431),
        assistantBubble = Color(0xFF0D151E),
        border = Color(0xFF2A3A47),
        accent = Color(0xFF35D5E8)
    ) else ChatColors(
        background = Color(0xFFF7F7F7),
        surface = Color.White,
        text = Color(0xFF171717),
        muted = Color(0xFF666666),
        userBubble = Color(0xFFEAEAEA),
        assistantBubble = Color(0xFFF7F7F7),
        border = Color(0xFFE0E0E0),
        accent = Color(0xFF187F91)
    )

    fun refreshThreads() {
        threads = ChatStorage.loadThreads(context)
    }

    fun append(role: String, text: String) {
        val thread = ChatStorage.appendMessage(context, currentThreadId, role, text)
        currentThreadId = thread.id
        messages = thread.messages
        refreshThreads()
    }

    fun sendMessage() {
        val message = input.trim()
        if (message.isEmpty() || waitingForResponse) return
        input = ""
        append("user", message)
        waitingForResponse = true
        agent.executeTask(message)
    }

    LaunchedEffect(agentState) {
        if (waitingForResponse) {
            when (val state = agentState) {
                is AgentState.Done -> {
                    append("assistant", state.result)
                    waitingForResponse = false
                }
                is AgentState.Error -> {
                    append("assistant", state.message)
                    waitingForResponse = false
                }
                else -> Unit
            }
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(modifier = modifier.fillMaxSize().background(colors.background).statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showHistory = true }) {
                        Icon(Icons.Default.History, contentDescription = "تاریخچه", tint = colors.text)
                    }
                    IconButton(onClick = {
                        currentThreadId = null
                        ChatStorage.setCurrentThreadId(context, null)
                        messages = emptyList()
                        input = ""
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "چت جدید", tint = colors.text)
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("JARVIS", color = colors.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Text("دستیار هوشمند", color = colors.accent, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        darkMode = !darkMode
                        prefs.edit().putBoolean("dark_mode", darkMode).apply()
                    }) {
                        Icon(if (darkMode) Icons.Default.LightMode else Icons.Default.DarkMode, contentDescription = "تغییر پوسته", tint = colors.text)
                    }
                    IconButton(onClick = {
                        profileName = FirebaseAuth.getInstance().currentUser?.displayName.orEmpty()
                        profileStatus = null
                        showProfile = true
                    }) {
                        Icon(Icons.Default.AccountCircle, contentDescription = "پروفایل", tint = colors.text)
                    }
                    IconButton(onClick = onOpenPermissions) {
                        Icon(Icons.Default.Security, contentDescription = "مجوزها", tint = colors.text)
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "تنظیمات", tint = colors.text)
                    }
                }
            }
            Divider(color = colors.border)
            Row(
                modifier = Modifier.fillMaxWidth().clickable { showTone = true }.padding(horizontal = 18.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "لحن پاسخ: " + when (tone) {
                        "concise" -> "کوتاه"
                        "formal" -> "رسمی"
                        "friendly" -> "دوستانه"
                        else -> "طبیعی"
                    },
                    color = colors.muted,
                    fontSize = 11.sp
                )
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (messages.isEmpty()) {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(horizontal = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("سلام، من جارویسم.", color = colors.text, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "هر چیزی می‌خواهی بپرس یا بگو چه کاری برایت انجام بدهم.",
                            color = colors.muted,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 22.sp
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 16.dp)
                    ) {
                        items(messages, key = { it.id }) { message -> MessageBubble(message, colors) }
                        if (waitingForResponse) {
                            item { Text("در حال فکر کردن…", color = colors.muted, fontSize = 12.sp, modifier = Modifier.padding(start = 12.dp)) }
                        }
                    }
                }
            }

            Surface(
                color = colors.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.border),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(onClick = onStartOverlay) {
                        Icon(Icons.Default.Mic, contentDescription = "فرمان صوتی", tint = colors.text)
                    }
                    BasicTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                        textStyle = TextStyle(color = colors.text, fontSize = 15.sp, textAlign = TextAlign.Start),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { sendMessage() }),
                        maxLines = 5,
                        decorationBox = { inner ->
                            Box {
                                if (input.isEmpty()) Text("پیامت را بنویس…", color = colors.muted, fontSize = 14.sp)
                                inner()
                            }
                        }
                    )
                    IconButton(onClick = { sendMessage() }, enabled = input.isNotBlank() && !waitingForResponse) {
                        Icon(
                            Icons.Default.Send,
                            contentDescription = "ارسال",
                            tint = if (input.isNotBlank() && !waitingForResponse) colors.accent else colors.muted
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }

    if (showHistory) {
        AlertDialog(
            onDismissRequest = { showHistory = false },
            containerColor = colors.surface,
            title = { Text("گفت‌وگوهای قبلی", color = colors.text) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (threads.isEmpty()) Text("هنوز گفت‌وگویی ذخیره نشده.", color = colors.muted)
                    threads.forEach { thread ->
                        Column(
                            modifier = Modifier.fillMaxWidth().clickable {
                                currentThreadId = thread.id
                                ChatStorage.setCurrentThreadId(context, thread.id)
                                messages = thread.messages
                                showHistory = false
                            }.padding(vertical = 7.dp)
                        ) {
                            Text(thread.title, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(formatDate(thread.updatedAt), color = colors.muted, fontSize = 11.sp)
                        }
                        Divider(color = colors.border)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showHistory = false }) { Text("بستن") } }
        )
    }

    if (showProfile) {
        val user = FirebaseAuth.getInstance().currentUser
        AlertDialog(
            onDismissRequest = { showProfile = false },
            containerColor = colors.surface,
            title = { Text("پروفایل کاربری", color = colors.text) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(user?.email.orEmpty(), color = colors.muted, fontSize = 12.sp)
                    androidx.compose.material3.OutlinedTextField(
                        value = profileName,
                        onValueChange = { profileName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("نام نمایشی") },
                        singleLine = true
                    )
                    if (profileStatus != null) {
                        Text(profileStatus.orEmpty(), color = colors.muted, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val current = FirebaseAuth.getInstance().currentUser
                    if (current == null) {
                        profileStatus = "حساب واردشده پیدا نشد."
                    } else {
                        val request = UserProfileChangeRequest.Builder()
                            .setDisplayName(profileName.trim())
                            .build()
                        current.updateProfile(request).addOnCompleteListener { task ->
                            profileStatus = if (task.isSuccessful) "نام پروفایل ذخیره شد." else "ذخیره ناموفق بود."
                        }
                    }
                }) { Text("ذخیره") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { showProfile = false }) { Text("بستن") }
                    TextButton(onClick = {
                        FirebaseAuth.getInstance().signOut()
                        val intent = Intent(context, AuthActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        context.startActivity(intent)
                        (context as? Activity)?.finish()
                    }) { Text("خروج از حساب") }
                }
            }
        )
    }

    if (showTone) {
        AlertDialog(
            onDismissRequest = { showTone = false },
            containerColor = colors.surface,
            title = { Text("لحن پاسخ جارویس", color = colors.text) },
            text = {
                Column {
                    listOf(
                        "natural" to "طبیعی و محاوره‌ای",
                        "concise" to "کوتاه و مستقیم",
                        "friendly" to "دوستانه",
                        "formal" to "رسمی"
                    ).forEach { (key, label) ->
                        Text(
                            label,
                            modifier = Modifier.fillMaxWidth().clickable {
                                tone = key
                                prefs.edit().putString("response_tone", key).apply()
                                showTone = false
                            }.padding(vertical = 12.dp),
                            color = if (tone == key) colors.text else colors.muted
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showTone = false }) { Text("تمام") } }
        )
    }
}

@Composable
private fun MessageBubble(message: ChatMessage, colors: ChatColors) {
    val isUser = message.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.Start else Arrangement.End
    ) {
        Surface(
            color = if (isUser) colors.userBubble else colors.assistantBubble,
            shape = RoundedCornerShape(18.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.border),
            modifier = Modifier.fillMaxWidth(0.88f)
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
                Text(message.text, color = colors.text, fontSize = 14.sp, lineHeight = 22.sp)
                Text(
                    formatTime(message.timestamp),
                    color = colors.muted,
                    fontSize = 10.sp,
                    modifier = Modifier.align(Alignment.End).padding(top = 5.dp)
                )
            }
        }
    }
}

private fun formatTime(timestamp: Long): String =
    SimpleDateFormat("HH:mm", Locale("fa", "IR")).format(Date(timestamp))

private fun formatDate(timestamp: Long): String =
    SimpleDateFormat("yyyy/MM/dd • HH:mm", Locale("fa", "IR")).format(Date(timestamp))
