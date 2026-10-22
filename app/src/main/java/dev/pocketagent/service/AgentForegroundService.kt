package dev.pocketagent.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import dev.pocketagent.MainActivity
import dev.pocketagent.R

class AgentForegroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var currentSessionId: String = ""
    private var currentSessionTitle: String = "Agent Task"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY

        when (action) {
            ACTION_START -> {
                currentSessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: ""
                currentSessionTitle = intent.getStringExtra(EXTRA_SESSION_TITLE) ?: "Coding Agent"
                val notification = buildOngoingNotification(
                    step = 0,
                    maxSteps = 20,
                    statusText = "Agent initialized"
                )
                startForeground(NOTIFICATION_ID, notification)
            }

            ACTION_UPDATE_PROGRESS -> {
                val step = intent.getIntExtra(EXTRA_STEP, 0)
                val maxSteps = intent.getIntExtra(EXTRA_MAX_STEPS, 20)
                val statusText = intent.getStringExtra(EXTRA_STATUS_TEXT) ?: "Running..."
                val notification = buildOngoingNotification(step, maxSteps, statusText)
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(NOTIFICATION_ID, notification)
            }

            ACTION_SHOW_APPROVAL -> {
                val approvalId = intent.getStringExtra(EXTRA_APPROVAL_ID) ?: ""
                val toolName = intent.getStringExtra(EXTRA_TOOL_NAME) ?: "Tool"
                val reason = intent.getStringExtra(EXTRA_REASON) ?: "Requires approval"
                val notification = buildApprovalNotification(approvalId, toolName, reason)
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(NOTIFICATION_ID, notification)
            }

            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "MazaAgent:AgentExecutionWakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire(60 * 60 * 1000L) // 1 hour safety timeout
            }
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Maza Agent Background Execution",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps agent execution and terminal processes active in background"
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildOngoingNotification(step: Int, maxSteps: Int, statusText: String): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("sessionId", currentSessionId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(currentSessionTitle)
            .setContentText("Step $step/$maxSteps: $statusText")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (maxSteps > 0) {
            builder.setProgress(maxSteps, step, false)
        }

        return builder.build()
    }

    private fun buildApprovalNotification(approvalId: String, toolName: String, reason: String): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("sessionId", currentSessionId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Approve Action Intent
        val approveIntent = PendingIntent.getBroadcast(
            this,
            1,
            Intent(this, AgentApprovalReceiver::class.java).apply {
                action = AgentApprovalReceiver.ACTION_APPROVE
                putExtra(AgentApprovalReceiver.EXTRA_APPROVAL_ID, approvalId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Deny Action Intent
        val denyIntent = PendingIntent.getBroadcast(
            this,
            2,
            Intent(this, AgentApprovalReceiver::class.java).apply {
                action = AgentApprovalReceiver.ACTION_DENY
                putExtra(AgentApprovalReceiver.EXTRA_APPROVAL_ID, approvalId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Approval Needed: $toolName")
            .setContentText(reason)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(android.R.drawable.checkbox_on_background, "Approve", approveIntent)
            .addAction(android.R.drawable.ic_delete, "Deny", denyIntent)
            .build()
    }

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "pocketagent_execution_channel"
        const val NOTIFICATION_ID = 4242

        private const val ACTION_START = "dev.pocketagent.action.START"
        private const val ACTION_UPDATE_PROGRESS = "dev.pocketagent.action.UPDATE_PROGRESS"
        private const val ACTION_SHOW_APPROVAL = "dev.pocketagent.action.SHOW_APPROVAL"
        private const val ACTION_STOP = "dev.pocketagent.action.STOP"

        private const val EXTRA_SESSION_ID = "extra_session_id"
        private const val EXTRA_SESSION_TITLE = "extra_session_title"
        private const val EXTRA_STEP = "extra_step"
        private const val EXTRA_MAX_STEPS = "extra_max_steps"
        private const val EXTRA_STATUS_TEXT = "extra_status_text"
        private const val EXTRA_APPROVAL_ID = "extra_approval_id"
        private const val EXTRA_TOOL_NAME = "extra_tool_name"
        private const val EXTRA_REASON = "extra_reason"

        fun start(context: Context, sessionId: String, sessionTitle: String) {
            val intent = Intent(context, AgentForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_SESSION_ID, sessionId)
                putExtra(EXTRA_SESSION_TITLE, sessionTitle)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun updateProgress(context: Context, step: Int, maxSteps: Int, statusText: String) {
            val intent = Intent(context, AgentForegroundService::class.java).apply {
                action = ACTION_UPDATE_PROGRESS
                putExtra(EXTRA_STEP, step)
                putExtra(EXTRA_MAX_STEPS, maxSteps)
                putExtra(EXTRA_STATUS_TEXT, statusText)
            }
            context.startService(intent)
        }

        fun showApproval(context: Context, approvalId: String, toolName: String, reason: String) {
            val intent = Intent(context, AgentForegroundService::class.java).apply {
                action = ACTION_SHOW_APPROVAL
                putExtra(EXTRA_APPROVAL_ID, approvalId)
                putExtra(EXTRA_TOOL_NAME, toolName)
                putExtra(EXTRA_REASON, reason)
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, AgentForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
