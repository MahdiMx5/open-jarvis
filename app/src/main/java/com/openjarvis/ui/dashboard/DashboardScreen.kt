package com.openjarvis.ui.dashboard

import androidx.compose.foundation.Image
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
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
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    var responseMode by remember { mutableStateOf(prefs.getString("response_mode", "normal") ?: "normal") }
    var showMode by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    var attachedFileName by remember { mutableStateOf<String?>(null) }
    var attachedFileContent by remember { mutableStateOf<String?>(null) }
    var fileLoading by remember { mutableStateOf(false) }
    var fileError by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val textFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            fileLoading = true
            fileError = null
            coroutineScope.launch {
                val loaded = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
                            it.readText().take(30000)
                        } ?: throw IllegalStateException("خواندن فایل ممکن نشد")
                    }
                }
                fileLoading = false
                loaded.onSuccess { content ->
                    attachedFileName = uri.lastPathSegment?.substringAfterLast(':') ?: "فایل متنی"
                    attachedFileContent = content
                }.onFailure { error ->
                    fileError = error.localizedMessage ?: "خواندن فایل ناموفق بود"
                }
            }
        }
    }
    var currentThreadId by remember { mutableStateOf(ChatStorage.currentThreadId(context)) }
    var messages by remember { mutableStateOf(ChatStorage.findThread(context, currentThreadId)?.messages ?: emptyList()) }
    var threads by remember { mutableStateOf(ChatStorage.loadThreads(context)) }
    var waitingForResponse by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var historyQuery by remember { mutableStateOf("") }
    var favoritesOnly by remember { mutableStateOf(false) }
    var showTone by remember { mutableStateOf(false) }
    var showProfile by remember { mutableStateOf(false) }
    var profileName by remember { mutableStateOf(FirebaseAuth.getInstance().currentUser?.displayName.orEmpty()) }
    var profileStatus by remember { mutableStateOf<String?>(null) }
    var profilePhotoUri by remember { mutableStateOf(prefs.getString("profile_photo_uri", null)?.takeIf { it.isNotBlank() }?.let(Uri::parse)) }
    var profilePhotoBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val profilePhotoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            profilePhotoUri = uri
            prefs.edit().putString("profile_photo_uri", uri.toString()).apply()
        }
    }
    LaunchedEffect(profilePhotoUri) {
        val uri = profilePhotoUri
        profilePhotoBitmap = if (uri == null) null else withContext(Dispatchers.IO) {
            runCatching { context.contentResolver.openInputStream(uri)?.use { stream -> BitmapFactory.decodeStream(stream) } }.getOrNull()
        }
    }
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

    val visibleThreads = threads.filter { thread ->
        val matchesQuery = historyQuery.isBlank() || thread.title.contains(historyQuery, true) || thread.messages.any { it.text.contains(historyQuery, true) }
        val matchesFavorite = !favoritesOnly || thread.messages.any { it.isFavorite }
        matchesQuery && matchesFavorite
    }

    fun sendMessage() {
        val message = input.trim()
        if ((message.isEmpty() && attachedFileContent.isNullOrBlank()) || waitingForResponse || fileLoading) return
        val fileContent = attachedFileContent
        val fileName = attachedFileName
        val request = if (!fileContent.isNullOrBlank()) {
            "$message\n\nمحتوای فایل پیوست‌شده ($fileName):\n$fileContent"
        } else message
        val displayMessage = if (!fileName.isNullOrBlank()) {
            (message.ifBlank { "لطفاً این فایل را بررسی کن." }) + "\n\n📎 $fileName"
        } else message
        input = ""
        attachedFileName = null
        attachedFileContent = null
        append("user", displayMessage)
        waitingForResponse = true
        agent.executeTask(request, responseMode, tone)
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
                    Image(
                        painter = painterResource(id = com.openjarvis.R.drawable.mx5_mark),
                        contentDescription = "لوگوی MX5",
                        modifier = Modifier.size(28.dp),
                        contentScale = ContentScale.Fit
                    )
                    Text("MX5", color = colors.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
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
            Row(
                modifier = Modifier.fillMaxWidth().clickable { showMode = true }.padding(horizontal = 18.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "حالت هوش مصنوعی: " + when (responseMode) {
                        "deep" -> "تحلیل عمیق"
                        "code" -> "برنامه‌نویسی"
                        "creative" -> "خلاقانه"
                        "translate" -> "مترجم"
                        "brief" -> "پاسخ سریع"
                        else -> "استاندارد"
                    } + "  ▾",
                    color = colors.accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
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
                        items(messages, key = { it.id }) { message ->
                            MessageBubble(
                                message = message,
                                colors = colors,
                                onFavorite = {
                                    val threadId = currentThreadId
                                    if (threadId != null) {
                                        threads = ChatStorage.setMessageFavorite(context, threadId, message.id, !message.isFavorite)
                                        messages = ChatStorage.findThread(context, threadId)?.messages ?: messages
                                    }
                                },
                                onCopy = {
                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Open Mx5", message.text))
                                },
                                onShare = {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, message.text)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "اشتراک‌گذاری پاسخ"))
                                }
                            )
                        }
                        if (waitingForResponse) {
                            item { Text("در حال فکر کردن…", color = colors.muted, fontSize = 12.sp, modifier = Modifier.padding(start = 12.dp)) }
                        }
                    }
                }
            }

            if (fileLoading || attachedFileName != null || fileError != null) {
                Text(
                    when {
                        fileLoading -> "در حال خواندن فایل…"
                        fileError != null -> "خطای فایل: $fileError"
                        else -> "فایل پیوست: $attachedFileName  ×"
                    },
                    color = if (fileError != null) Color(0xFFFF7777) else colors.accent,
                    fontSize = 11.sp,
                    modifier = Modifier.fillMaxWidth().clickable {
                        if (!fileLoading) {
                            attachedFileName = null
                            attachedFileContent = null
                            fileError = null
                        }
                    }.padding(horizontal = 20.dp, vertical = 4.dp)
                )
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
                    IconButton(
                        onClick = { textFilePicker.launch(arrayOf("text/*", "application/json", "application/xml")) },
                        enabled = !fileLoading && !waitingForResponse
                    ) {
                        Icon(Icons.Default.AttachFile, contentDescription = "پیوست فایل متنی", tint = colors.text)
                    }
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
                    BasicTextField(
                        value = historyQuery,
                        onValueChange = { historyQuery = it },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        textStyle = TextStyle(color = colors.text, fontSize = 14.sp),
                        decorationBox = { inner ->
                            Box {
                                if (historyQuery.isEmpty()) Text("جست‌وجو در عنوان و متن گفت‌وگوها…", color = colors.muted, fontSize = 13.sp)
                                inner()
                            }
                        }
                    )
                    Text(
                        if (favoritesOnly) "★ نمایش گفت‌وگوهای نشان‌شده (برای لغو لمس کن)" else "☆ نمایش گفت‌وگوهای نشان‌شده",
                        color = colors.accent,
                        modifier = Modifier.fillMaxWidth().clickable { favoritesOnly = !favoritesOnly }.padding(vertical = 6.dp),
                        fontSize = 12.sp
                    )
                    if (visibleThreads.isEmpty()) Text("موردی پیدا نشد.", color = colors.muted)
                    visibleThreads.forEach { thread ->
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
        val providerNames = user?.providerData
            ?.mapNotNull { it.providerId.takeIf { id -> id != "firebase" } }
            ?.distinct()
            ?.joinToString("، ")
            .orEmpty()
        val createdAt = user?.metadata?.creationTimestamp?.let {
            SimpleDateFormat("yyyy/MM/dd", Locale("fa", "IR")).format(Date(it))
        } ?: "نامشخص"
        val lastSignIn = user?.metadata?.lastSignInTimestamp?.let {
            SimpleDateFormat("yyyy/MM/dd • HH:mm", Locale("fa", "IR")).format(Date(it))
        } ?: "نامشخص"

        AlertDialog(
            onDismissRequest = { showProfile = false },
            containerColor = colors.surface,
            title = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(68.dp).clickable { profilePhotoPicker.launch(arrayOf("image/*")) },
                        shape = RoundedCornerShape(34.dp),
                        color = colors.accent.copy(alpha = 0.16f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (profilePhotoBitmap != null) {
                                Image(
                                    bitmap = profilePhotoBitmap!!.asImageBitmap(),
                                    contentDescription = "عکس پروفایل",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Text(
                                    (user?.displayName?.firstOrNull() ?: user?.email?.firstOrNull() ?: 'M').uppercase(),
                                    color = colors.accent,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Text("برای تغییر عکس، روی تصویر بزن.", color = colors.muted, fontSize = 10.sp)
                    Text("پروفایل من", color = colors.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(user?.email ?: "حساب مهمان", color = colors.muted, fontSize = 12.sp)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("اطلاعات حساب", color = colors.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    androidx.compose.material3.OutlinedTextField(
                        value = profileName,
                        onValueChange = { profileName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("نام نمایشی") },
                        placeholder = { Text("نامی که در پروفایل دیده می‌شود") },
                        singleLine = true
                    )
                    ProfileInfoRow("وضعیت ایمیل", if (user?.isEmailVerified == true) "تأیید شده" else "تأیید نشده", colors)
                    ProfileInfoRow("روش ورود", providerNames.ifBlank { "ایمیل / رمز عبور" }, colors)
                    ProfileInfoRow("تاریخ ساخت حساب", createdAt, colors)
                    ProfileInfoRow("آخرین ورود", lastSignIn, colors)
                    ProfileInfoRow("شناسه حساب", user?.uid?.take(12)?.plus("…") ?: "—", colors)
                    if (profileStatus != null) {
                        Text(profileStatus.orEmpty(), color = colors.accent, fontSize = 12.sp)
                    }
                    Text(
                        "اطلاعات امنیتی",
                        color = colors.accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (user?.email?.isNotBlank() == true && user.isEmailVerified.not()) {
                        TextButton(
                            onClick = {
                                user.sendEmailVerification().addOnCompleteListener { task ->
                                    profileStatus = if (task.isSuccessful) {
                                        "لینک تأیید به ایمیلت ارسال شد."
                                    } else {
                                        "ارسال ایمیل تأیید ناموفق بود: ${task.exception?.localizedMessage ?: "خطای نامشخص"}"
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("ارسال دوباره ایمیل تأیید") }
                    }
                    if (user?.email?.isNotBlank() == true) {
                        TextButton(
                            onClick = {
                                FirebaseAuth.getInstance().sendPasswordResetEmail(user.email!!)
                                    .addOnCompleteListener { task ->
                                        profileStatus = if (task.isSuccessful) {
                                            "لینک بازیابی رمز به ایمیلت ارسال شد."
                                        } else {
                                            "ارسال لینک بازیابی ناموفق بود: ${task.exception?.localizedMessage ?: "خطای نامشخص"}"
                                        }
                                    }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("بازیابی یا تغییر رمز عبور") }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val current = FirebaseAuth.getInstance().currentUser
                    if (current == null) {
                        profileStatus = "حساب واردشده پیدا نشد."
                    } else if (profileName.trim().isBlank()) {
                        profileStatus = "نام نمایشی نمی‌تواند خالی باشد."
                    } else {
                        val request = UserProfileChangeRequest.Builder()
                            .setDisplayName(profileName.trim())
                            .build()
                        current.updateProfile(request).addOnCompleteListener { task ->
                            profileStatus = if (task.isSuccessful) {
                                "نام پروفایل با موفقیت ذخیره شد."
                            } else {
                                "ذخیره ناموفق بود: ${task.exception?.localizedMessage ?: "خطای نامشخص"}"
                            }
                        }
                    }
                }) { Text("ذخیره تغییرات") }
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

    if (showMode) {
        AlertDialog(
            onDismissRequest = { showMode = false },
            containerColor = colors.surface,
            title = { Text("انتخاب حالت هوش مصنوعی", color = colors.text) },
            text = {
                Column {
                    listOf(
                        "normal" to "استاندارد — گفت‌وگوی معمولی",
                        "deep" to "تحلیل عمیق — بررسی دقیق و مرحله‌ای",
                        "code" to "برنامه‌نویسی — کدنویسی و عیب‌یابی",
                        "creative" to "خلاقانه — ایده‌پردازی و تولید محتوا",
                        "translate" to "مترجم — ترجمه با حفظ لحن",
                        "brief" to "پاسخ سریع — کوتاه و مستقیم"
                    ).forEach { (key, label) ->
                        Text(
                            label,
                            modifier = Modifier.fillMaxWidth().clickable {
                                responseMode = key
                                prefs.edit().putString("response_mode", key).apply()
                                showMode = false
                            }.padding(vertical = 12.dp),
                            color = if (responseMode == key) colors.accent else colors.text
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showMode = false }) { Text("بستن") } }
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
private fun ProfileInfoRow(label: String, value: String, colors: ChatColors) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = colors.muted, fontSize = 11.sp)
        Text(
            value,
            color = colors.text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    colors: ChatColors,
    onFavorite: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit
) {
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
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onCopy, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "کپی متن", tint = colors.muted, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onShare, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.Share, contentDescription = "اشتراک‌گذاری متن", tint = colors.muted, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onFavorite, modifier = Modifier.size(30.dp)) {
                        Icon(
                            if (message.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = if (message.isFavorite) "حذف از نشان‌شده‌ها" else "افزودن به نشان‌شده‌ها",
                            tint = if (message.isFavorite) colors.accent else colors.muted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(formatTime(message.timestamp), color = colors.muted, fontSize = 10.sp)
                }
                /*
                Text(
                    formatTime(message.timestamp),
                    color = colors.muted,
                    fontSize = 10.sp,
                    modifier = Modifier.align(Alignment.End).padding(top = 5.dp)
                )
                */
            }
        }
    }
}

private fun formatTime(timestamp: Long): String =
    SimpleDateFormat("HH:mm", Locale("fa", "IR")).format(Date(timestamp))

private fun formatDate(timestamp: Long): String =
    SimpleDateFormat("yyyy/MM/dd • HH:mm", Locale("fa", "IR")).format(Date(timestamp))
