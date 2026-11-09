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
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.graphics.PixelFormat
import android.widget.EditText
import android.widget.TextView
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

class OverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

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
        windowManager = getSystemService(WindowManager::class.java)
        showFloatingWidget()

        stateCollectJob = serviceScope.launch {
            agentCore.state.collect { state ->
                val status = overlayView?.findViewById<TextView>(R.id.status_text)
                status?.text = when (state) {
                    is com.openjarvis.agent.AgentState.Idle -> "Ready"
                    is com.openjarvis.agent.AgentState.Running -> state.step
                    is com.openjarvis.agent.AgentState.Done -> state.result
                    is com.openjarvis.agent.AgentState.Error -> state.message
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
        serviceScope.cancel()
        removeOverlayView()
        if (::socketServer.isInitialized) socketServer.stop()
        if (::voiceManager.isInitialized) voiceManager.release()
        super.onDestroy()
    }

    private fun showFloatingWidget() {
        val manager = windowManager ?: return
        removeOverlayView()

        val view = LayoutInflater.from(this).inflate(R.layout.overlay_floating, null, false)
        val params = WindowManager.LayoutParams(
            dp(56),
            dp(56),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(16)
            y = dp(160)
        }

        view.setOnClickListener { showExpandedWidget() }
        try {
            manager.addView(view, params)
            overlayView = view
        } catch (error: Exception) {
            Log.e(TAG, "Unable to display floating widget", error)
        }
    }

    private fun showExpandedWidget() {
        val manager = windowManager ?: return
        removeOverlayView()

        val view = LayoutInflater.from(this).inflate(R.layout.overlay_expanded, null, false)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

        view.findViewById<View>(R.id.minimize_button).setOnClickListener {
            showFloatingWidget()
        }
        view.findViewById<View>(R.id.send_button).setOnClickListener {
            val input = view.findViewById<EditText>(R.id.command_input)
            val command = input.text?.toString()?.trim().orEmpty()
            if (command.isNotEmpty()) {
                executeCommand(command)
                input.text?.clear()
                view.findViewById<TextView>(R.id.status_text).text = "Working..."
            }
        }

        try {
            manager.addView(view, params)
            overlayView = view
            view.findViewById<TextView>(R.id.status_text).text = "Ready"
        } catch (error: Exception) {
            Log.e(TAG, "Unable to expand floating widget", error)
            showFloatingWidget()
        }
    }

    private fun removeOverlayView() {
        val view = overlayView ?: return
        try {
            windowManager?.removeViewImmediate(view)
        } catch (error: Exception) {
            Log.w(TAG, "Floating widget was already detached", error)
        } finally {
            overlayView = null
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

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
