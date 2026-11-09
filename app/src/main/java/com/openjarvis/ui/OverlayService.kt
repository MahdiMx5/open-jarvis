package com.openjarvis.ui

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.openjarvis.R
import com.openjarvis.agent.AgentCore
import com.openjarvis.bridge.SocketServer
import com.openjarvis.graphify.GraphifyRepository
import com.openjarvis.graphify.nodes.TaskNode
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

    private var isInitialized = false

    override fun onCreate() {
        super.onCreate()

        // Promote the service before doing database, crypto, or AI initialization.
        startForeground(NOTIFICATION_ID, createNotification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)

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
                        voiceManager.speak(state.result)
                        processingCommand = false
                        scheduleWakeListening(1600L)
                    }
                    is com.openjarvis.agent.AgentState.Error -> {
                        voiceManager.speak(state.message)
                        processingCommand = false
                        scheduleWakeListening(1800L)
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
                        if (!processingCommand) scheduleWakeListening(1800L)
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
        if (processingCommand) return
        val text = spokenText.trim()
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
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            val intent = Intent(context, OverlayService::class.java)
            context.startForegroundService(intent)
        }
    }
}
