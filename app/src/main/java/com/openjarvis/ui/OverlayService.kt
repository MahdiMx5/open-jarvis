package com.openjarvis.ui

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.consume
import android.graphics.PixelFormat
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.LifecycleService
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.openjarvis.R
import com.openjarvis.agent.AgentCore
import com.openjarvis.graphify.GraphifyRepository
import com.openjarvis.local.MusicDownloadManager
import com.openjarvis.graphify.nodes.TaskNode
import com.openjarvis.ui.chat.ChatStorage
import com.openjarvis.ui.overlay.FloatingOverlayWidget
import com.openjarvis.ui.theme.OpenJarvisTheme
import com.openjarvis.voice.VoiceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collect
import java.util.Locale

class OverlayService : LifecycleService(), SavedStateRegistryOwner {

    private val savedStateController: SavedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var agentCore: AgentCore
    private lateinit var voiceManager: VoiceManager
    private lateinit var graphifyRepo: GraphifyRepository
    private var stateCollectJob: Job? = null
    private var voiceCollectJob: Job? = null
    private var recentTaskItems by mutableStateOf(emptyList<TaskNode>())
    private var overlayView: ComposeView? = null
    private var windowManager: WindowManager? = null
    private var wakeRestartJob: Job? = null
    private var waitingForCommand = false
    private var processingCommand = false
    private var listeningForInterruption = false
    private var interruptionDetected = false
    private var interruptionStartedAt = 0L
    private var messageNotificationId = 20

    private var isInitialized = false

    override fun onCreate() {
        super.onCreate()
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        serviceRunning = true

        // Promote the service before doing database, crypto, or AI initialization.
        startForeground(NOTIFICATION_ID, createNotification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        createMessageNotificationChannel()

        try {
            graphifyRepo = GraphifyRepository(applicationContext)
            agentCore = AgentCore(applicationContext)
            voiceManager = VoiceManager(applicationContext)
        } catch (error: Exception) {
            Log.e(TAG, "Unable to initialize overlay service", error)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        attachFloatingOverlay()

        CoroutineScope(Dispatchers.Main).launch {
            initializeServices()
        }


        stateCollectJob = serviceScope.launch {
            agentCore.state.collect { state ->
                when (state) {
                    is com.openjarvis.agent.AgentState.Done -> {
                        processingCommand = false
                        recordChatMessage("assistant", state.result)
                        postReplyNotification(state.result)
                        beginInterruptibleSpeech(state.result)
                    }
                    is com.openjarvis.agent.AgentState.Error -> {
                        processingCommand = false
                        recordChatMessage("assistant", state.message)
                        postReplyNotification(state.message)
                        beginInterruptibleSpeech(state.message)
                    }
                    else -> Unit
                }
            }
        }

        voiceCollectJob = serviceScope.launch {
            voiceManager.state.collect { voiceState ->
                when (voiceState) {
                    is VoiceManager.VoiceState.Result -> handleRecognizedSpeech(voiceState.text)
                    is VoiceManager.VoiceState.Error -> {
                        Log.w(TAG, "Speech recognition failed: ${voiceState.message}")
                        if (listeningForInterruption && !interruptionDetected) scheduleWakeListening(500L)
                        else if (!processingCommand && !listeningForInterruption) scheduleWakeListening(1800L)
                    }
                    else -> Unit
                }
            }
        }

        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                recentTaskItems = graphifyRepo.getRecentTasks(10)
            }.onFailure {
                Log.e(TAG, "Unable to load recent tasks", it)
            }
        }
    }

    private fun attachFloatingOverlay() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M &&
            !android.provider.Settings.canDrawOverlays(this)
        ) {
            Log.w(TAG, "Overlay permission is not granted; floating window was not attached")
            return
        }

        try {
            val manager = getSystemService(WINDOW_SERVICE) as WindowManager
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                y = (72 * resources.displayMetrics.density).toInt()
            }
            val view = ComposeView(this).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setViewTreeLifecycleOwner(this@OverlayService)
                setViewTreeSavedStateRegistryOwner(this@OverlayService)
                setContent {
                    OpenJarvisTheme {
                        val state by agentCore.state.collectAsState()
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 12.dp)
                                .pointerInput(Unit) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        params.x = (params.x - dragAmount.x.toInt()).coerceAtLeast(0)
                                        params.y = (params.y + dragAmount.y.toInt()).coerceAtLeast(0)
                                        runCatching { manager.updateViewLayout(view, params) }
                                    }
                                },
                            contentAlignment = Alignment.TopEnd
                        ) {
                            FloatingOverlayWidget(
                                agentState = state,
                                voiceManager = voiceManager,
                                recentTasks = recentTaskItems,
                                onCommand = { command -> executeCommand(command) },
                                onCollapse = {}
                            )
                        }
                    }
                }
            }
            manager.addView(view, params)
            windowManager = manager
            overlayView = view
        } catch (error: Exception) {
            Log.e(TAG, "Unable to attach floating overlay window", error)
        }
    }

    private suspend fun initializeServices() {
        if (isInitialized) return
        isInitialized = true

        withContext(Dispatchers.IO) {
            try {
                graphifyRepo.getRecentTasks(10)
            } catch (error: Exception) {
                Log.e(TAG, "Graphify initialization failed", error)
            }
        }

        try {
            voiceManager.initialize()
            voiceManager.setSpeechDetectedListener {
                if (listeningForInterruption &&
                    !interruptionDetected &&
                    System.currentTimeMillis() - interruptionStartedAt > 900L
                ) {
                    interruptionDetected = true
                    voiceManager.stopSpeaking()
                }
            }
            scheduleWakeListening(900L)
        } catch (error: Exception) {
            Log.e(TAG, "Voice initialization failed", error)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> {
                if (::voiceManager.isInitialized) {
                    voiceManager.setVoiceEnabled(false)
                    voiceManager.stopListening()
                    voiceManager.stopSpeaking()
                }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_DISABLE_MIC -> {
                if (::voiceManager.isInitialized) {
                    voiceManager.setVoiceEnabled(false)
                    voiceManager.stopListening()
                    voiceManager.stopSpeaking()
                }
                wakeRestartJob?.cancel()
            }
            ACTION_ENABLE_MIC -> {
                if (::voiceManager.isInitialized) {
                    voiceManager.setVoiceEnabled(true)
                    scheduleWakeListening(300L)
                }
            }
            ACTION_REFRESH_SETTINGS -> {
                if (::voiceManager.isInitialized) {
                    voiceManager.stopListening()
                    voiceManager.stopSpeaking()
                    voiceManager.reloadSettings()
                    if (voiceManager.isVoiceEnabled() && !processingCommand) {
                        scheduleWakeListening(350L)
                    } else {
                        wakeRestartJob?.cancel()
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        serviceRunning = false
        stateCollectJob?.cancel()
        voiceCollectJob?.cancel()
        wakeRestartJob?.cancel()
        serviceScope.cancel()
        overlayView?.let { view ->
            runCatching { windowManager?.removeView(view) }
                .onFailure { Log.w(TAG, "Unable to remove floating overlay", it) }
            overlayView = null
        }
        if (::voiceManager.isInitialized) voiceManager.release()
        super.onDestroy()
    }

    private fun handleRecognizedSpeech(spokenText: String) {
        val text = spokenText.trim()
        if (listeningForInterruption && interruptionDetected) {
            listeningForInterruption = false
            interruptionDetected = false
            voiceManager.setSpeechDetectedListener(null)
            executeVoiceCommand(text)
            return
        }
        if (processingCommand) return
        if (text.isBlank()) {
            scheduleWakeListening(700L)
            return
        }
        if (waitingForCommand) {
            waitingForCommand = false
            executeVoiceCommand(text)
            return
        }

        val normalized = text.lowercase(Locale.ROOT).replace('‌', ' ')
        val wakePhrases = listOf("سلام جارویس", "سلام جاویس", "hey jarvis", "hi jarvis", "جارویس")
        val phrase = wakePhrases.firstOrNull { normalized.contains(it) }
        if (phrase == null) {
            scheduleWakeListening(500L)
            return
        }

        val commandStart = normalized.indexOf(phrase) + phrase.length
        val command = text.substring(commandStart).trim().trim(',', '،', '.', '!', '؟', '?')
        if (command.isNotBlank()) {
            executeVoiceCommand(command)
        } else {
            waitingForCommand = true
            voiceManager.speak("بله، بفرما.")
            scheduleWakeListening(2500L)
        }
    }

    private fun executeVoiceCommand(command: String) {
        if (command.isBlank()) {
            scheduleWakeListening(500L)
            return
        }
        recordChatMessage("user", command)
        if (openMediaSearch(command)) return
        processingCommand = true
        voiceManager.resetState()
        executeCommand(command)
    }

    private fun scheduleWakeListening(delayMs: Long) {
        wakeRestartJob?.cancel()
        wakeRestartJob = serviceScope.launch {
            delay(delayMs)
            if (!isInitialized || processingCommand || !::voiceManager.isInitialized) return@launch
            if (!voiceManager.isVoiceEnabled()) return@launch
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    this@OverlayService,
                    android.Manifest.permission.RECORD_AUDIO
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) return@launch
            voiceManager.resetState()
            voiceManager.startListeningWithVAD()
        }
    }

    private fun recordChatMessage(role: String, text: String) {
        runCatching {
            val activeThreadId = ChatStorage.currentThreadId(this)
            ChatStorage.appendMessage(this, activeThreadId, role, text)
        }.onFailure { Log.w(TAG, "Unable to store voice conversation", it) }
    }

    private fun beginInterruptibleSpeech(text: String) {
        interruptionDetected = false
        listeningForInterruption = true
        interruptionStartedAt = System.currentTimeMillis()
        voiceManager.speak(text) {
            if (!interruptionDetected) {
                listeningForInterruption = false
                voiceManager.setSpeechDetectedListener(null)
                voiceManager.stopListening()
                scheduleWakeListening(450L)
            }
        }
        scheduleWakeListening(1100L)
    }

    private fun openMediaSearch(command: String): Boolean {
        val normalized = command.lowercase(Locale.ROOT)
        val isDownload = listOf("دانلود", "download").any { normalized.contains(it) }
        val isPlay = listOf("پخش", "بذار", "بزار", "بگذار", "play").any { normalized.contains(it) }
        if (!isDownload && !isPlay) return false

        val query = command
            .replace(Regex("(?i)\\b(download|play)\\b"), " ")
            .replace(Regex("دانلود(ش)?( کن)?"), " ")
            .replace(Regex("(پخش کن|پخشش کن|بذار|بزار|بگذار|گوش بده|برام پخش کن)"), " ")
            .replace(Regex("^(لطفاً\\s*)?(این\\s+)?(آهنگ|موزیک)\\s+"), "")
            .replace(Regex("^(لطفاً\\s*|لطفا\\s*|برام\\s*|برای من\\s*)"), "")
            .replace(Regex("\\s+(رو|را|لطفاً|لطفا)$"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { command.trim() }

        if (isDownload) {
            processingCommand = true
            serviceScope.launch {
                val result = MusicDownloadManager.download(this@OverlayService, query)
                val response = result.fold(
                    onSuccess = { downloaded ->
                        if (downloaded.alreadyPresent) {
                            "«${downloaded.title}» از قبل در فهرست آهنگ‌های گوشی وجود دارد؛ دوباره دانلود نکردم."
                        } else {
                            "دانلود «${downloaded.title}» شروع شد. فایل در پوشهٔ Music/Jarvis ذخیره می‌شود."
                        }
                    },
                    onFailure = { error ->
                        error.message ?: "دانلود آهنگ انجام نشد."
                    }
                )
                processingCommand = false
                recordChatMessage("assistant", response)
                postReplyNotification(response)
                beginInterruptibleSpeech(response)
            }
            return true
        }

        val url = "https://www.youtube.com/results?search_query=${Uri.encode("$query official audio")}"
        return try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            val response = "نتایج پخش رو باز کردم. آهنگ موردنظرت رو از نتیجه‌های رسمی انتخاب کن."
            recordChatMessage("assistant", response)
            voiceManager.speak(response)
            listeningForInterruption = false
            interruptionDetected = false
            voiceManager.setSpeechDetectedListener(null)
            true
        } catch (error: Exception) {
            Log.e(TAG, "Unable to open media search", error)
            voiceManager.speak("نتونستم صفحهٔ جست‌وجو رو باز کنم.")
            false
        }
    }

    private fun createMessageNotificationChannel() {
        val channel = NotificationChannel(
            MESSAGE_CHANNEL_ID,
            "پیام‌های جارویس",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "اعلان پاسخ‌های گفت‌وگوی جارویس"
            enableVibration(true)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun postReplyNotification(message: String) {
        val pendingIntent = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, MESSAGE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("جارویس")
            .setContentText(message.take(180))
            .setStyle(
                NotificationCompat.MessagingStyle("جارویس")
                    .addMessage(message.take(3000), System.currentTimeMillis(), "جارویس")
                    .setConversationTitle("گفت‌وگو با جارویس")
            )
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        runCatching {
            getSystemService(NotificationManager::class.java).notify(messageNotificationId++, notification)
        }.onFailure { Log.w(TAG, "Unable to show reply notification", it) }
    }

    private fun createNotification(): Notification {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "وضعیت فعال بودن شنود صوتی جارویس"
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText("جارویس فعال است؛ برای خاموش‌کردن میکروفون توقف را بزن")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .addAction(
                R.drawable.ic_notification,
                "توقف و خاموش‌کردن میکروفون",
                stopIntent
            )
            .setOngoing(true)
            .build()
    }

    fun getAgentCore() = agentCore
    fun getVoiceManager() = voiceManager
    fun getRecentTasks() = recentTaskItems

    fun executeCommand(command: String) {
        if (!::agentCore.isInitialized || !::graphifyRepo.isInitialized) return

        agentCore.executeTask(command)

        CoroutineScope(Dispatchers.IO).launch {
            delay(1000)
            runCatching {
                recentTaskItems = graphifyRepo.getRecentTasks(10)
            }.onFailure {
                Log.e(TAG, "Unable to refresh recent tasks", it)
            }
        }
    }

    companion object {
        private const val TAG = "OpenJarvisOverlay"
        private const val CHANNEL_ID = "jarvis_voice_channel"
        private const val MESSAGE_CHANNEL_ID = "jarvis_messages"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "com.openjarvis.action.STOP_ASSISTANT"
        private const val ACTION_DISABLE_MIC = "com.openjarvis.action.DISABLE_MIC"
        private const val ACTION_ENABLE_MIC = "com.openjarvis.action.ENABLE_MIC"
        private const val ACTION_REFRESH_SETTINGS = "com.openjarvis.action.REFRESH_SETTINGS"
        @Volatile private var serviceRunning = false

        fun syncVoicePreference(context: Context, enabled: Boolean) {
            if (!serviceRunning) return
            val action = if (enabled) ACTION_ENABLE_MIC else ACTION_DISABLE_MIC
            runCatching {
                context.startService(Intent(context, OverlayService::class.java).setAction(action))
            }
        }

        /** Apply saved voice/language/speech settings to the already-running service. */
        fun refreshRuntimeSettings(context: Context) {
            if (!serviceRunning) return
            runCatching {
                context.startService(
                    Intent(context, OverlayService::class.java).setAction(ACTION_REFRESH_SETTINGS)
                )
            }
        }

        fun start(context: Context) {
            val intent = Intent(context, OverlayService::class.java)
            context.startForegroundService(intent)
        }
    }
}
