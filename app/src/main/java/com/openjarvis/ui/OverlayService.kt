package com.openjarvis.ui

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.openjarvis.R
import com.openjarvis.agent.AgentCore
import com.openjarvis.bridge.SocketServer
import com.openjarvis.graphify.GraphifyRepository
import com.openjarvis.local.MusicDownloadManager
import com.openjarvis.graphify.nodes.TaskNode
import com.openjarvis.ui.chat.ChatStorage
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

class OverlayService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var agentCore: AgentCore
    private lateinit var voiceManager: VoiceManager
    private lateinit var graphifyRepo: GraphifyRepository
    private lateinit var socketServer: SocketServer
    private var stateCollectJob: Job? = null
    private var voiceCollectJob: Job? = null
    private var recentTasks = emptyList<TaskNode>()
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

        // Promote the service before doing database, crypto, or AI initialization.
        startForeground(NOTIFICATION_ID, createNotification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        createMessageNotificationChannel()

        try {
            graphifyRepo = GraphifyRepository(applicationContext)
            agentCore = AgentCore(applicationContext)
            voiceManager = VoiceManager(applicationContext)
            socketServer = SocketServer(applicationContext, agentCore, graphifyRepo)
        } catch (error: Exception) {
            Log.e(TAG, "Unable to initialize overlay service", error)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        CoroutineScope(Dispatchers.Main).launch {
            initializeServices()
        }

        socketServer.start()

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
                recentTasks = graphifyRepo.getRecentTasks(10)
            }.onFailure {
                Log.e(TAG, "Unable to load recent tasks", it)
            }
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
            voiceManager.setVoiceEnabled(true)
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
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stateCollectJob?.cancel()
        voiceCollectJob?.cancel()
        wakeRestartJob?.cancel()
        serviceScope.cancel()
        if (::socketServer.isInitialized) socketServer.stop()
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

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText("جارویس فعال است و منتظر فرمان صوتی است")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    fun getAgentCore() = agentCore
    fun getVoiceManager() = voiceManager
    fun getRecentTasks() = recentTasks

    fun executeCommand(command: String) {
        if (!::agentCore.isInitialized || !::graphifyRepo.isInitialized) return

        agentCore.executeTask(command)

        CoroutineScope(Dispatchers.IO).launch {
            delay(1000)
            runCatching {
                recentTasks = graphifyRepo.getRecentTasks(10)
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

        fun start(context: Context) {
            val intent = Intent(context, OverlayService::class.java)
            context.startForegroundService(intent)
        }
    }
}
