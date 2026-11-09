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
import android.view.WindowManager
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OverlayService : Service() {

    private var windowManager: WindowManager? = null

    private lateinit var agentCore: AgentCore
    private lateinit var voiceManager: VoiceManager
    private lateinit var graphifyRepo: GraphifyRepository
    private lateinit var socketServer: SocketServer
    private var stateCollectJob: Job? = null
    private var recentTasks = emptyList<TaskNode>()

    private var isInitialized = false

    override fun onCreate() {
        super.onCreate()

        // Promote the service before doing database, crypto, or AI initialization.
        startForeground(NOTIFICATION_ID, createNotification())

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
        } catch (error: Exception) {
            Log.e(TAG, "Voice initialization failed", error)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stateCollectJob?.cancel()
        if (::socketServer.isInitialized) socketServer.stop()
        if (::voiceManager.isInitialized) voiceManager.release()
        super.onDestroy()
    }

    private fun createNotification(): Notification {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_description)
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
            .setContentText(getString(R.string.notification_text))
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
        private const val CHANNEL_ID = "jarvis_overlay_channel"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            val intent = Intent(context, OverlayService::class.java)
            context.startForegroundService(intent)
        }
    }
}
