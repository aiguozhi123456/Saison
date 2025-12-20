package takagi.ru.saison.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import takagi.ru.saison.R
import takagi.ru.saison.domain.model.notification.NotificationChannels
import java.util.Timer
import java.util.TimerTask

/**
 * 番茄钟前台服务
 * 在通知栏显示计时状态并支持操作
 */
class PomodoroTimerService : Service() {
    
    companion object {
        const val NOTIFICATION_ID = 1001
        
        // Actions
        const val ACTION_START = "takagi.ru.saison.POMODORO_START"
        const val ACTION_PAUSE = "takagi.ru.saison.POMODORO_PAUSE"
        const val ACTION_RESUME = "takagi.ru.saison.POMODORO_RESUME"
        const val ACTION_STOP = "takagi.ru.saison.POMODORO_STOP"
        const val ACTION_UPDATE = "takagi.ru.saison.POMODORO_UPDATE"
        
        // Extras
        const val EXTRA_TOTAL_SECONDS = "total_seconds"
        const val EXTRA_START_TIME = "start_time"
        const val EXTRA_TASK_NAME = "task_name"
        const val EXTRA_REMAINING_SECONDS = "remaining_seconds"
        const val EXTRA_IS_PAUSED = "is_paused"
        
        // 广播给 ViewModel 的动作
        const val BROADCAST_ACTION_FROM_NOTIFICATION = "takagi.ru.saison.POMODORO_NOTIFICATION_ACTION"
        const val BROADCAST_EXTRA_ACTION = "action_type"
        
        fun startService(context: Context, totalSeconds: Int, startTime: Long, taskName: String?) {
            val intent = Intent(context, PomodoroTimerService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TOTAL_SECONDS, totalSeconds)
                putExtra(EXTRA_START_TIME, startTime)
                putExtra(EXTRA_TASK_NAME, taskName ?: "番茄钟")
            }
            ContextCompat.startForegroundService(context, intent)
        }
        
        fun updateService(context: Context, remainingSeconds: Int, isPaused: Boolean) {
            val intent = Intent(context, PomodoroTimerService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_REMAINING_SECONDS, remainingSeconds)
                putExtra(EXTRA_IS_PAUSED, isPaused)
            }
            context.startService(intent)
        }
        
        fun stopService(context: Context) {
            val intent = Intent(context, PomodoroTimerService::class.java)
            context.stopService(intent)
        }
    }
    
    private var totalSeconds = 0
    private var remainingSeconds = 0
    private var startTime = 0L
    private var taskName = "番茄钟"
    private var isPaused = false
    private var timer: Timer? = null
    
    private val notificationManager by lazy { NotificationManagerCompat.from(this) }
    
    private val actionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_PAUSE -> {
                    isPaused = true
                    updateNotification()
                    broadcastToViewModel("pause")
                }
                ACTION_RESUME -> {
                    isPaused = false
                    updateNotification()
                    broadcastToViewModel("resume")
                }
                ACTION_STOP -> {
                    broadcastToViewModel("stop")
                    stopSelf()
                }
            }
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter().apply {
            addAction(ACTION_PAUSE)
            addAction(ACTION_RESUME)
            addAction(ACTION_STOP)
        }
        ContextCompat.registerReceiver(
            this,
            actionReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                totalSeconds = intent.getIntExtra(EXTRA_TOTAL_SECONDS, 25 * 60)
                remainingSeconds = totalSeconds
                startTime = intent.getLongExtra(EXTRA_START_TIME, System.currentTimeMillis())
                taskName = intent.getStringExtra(EXTRA_TASK_NAME) ?: "番茄钟"
                isPaused = false
                
                startForeground(NOTIFICATION_ID, buildNotification())
                startTimer()
            }
            ACTION_UPDATE -> {
                remainingSeconds = intent.getIntExtra(EXTRA_REMAINING_SECONDS, remainingSeconds)
                isPaused = intent.getBooleanExtra(EXTRA_IS_PAUSED, isPaused)
                updateNotification()
            }
            ACTION_PAUSE -> {
                isPaused = true
                updateNotification()
            }
            ACTION_RESUME -> {
                isPaused = false
                updateNotification()
            }
        }
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
        timer = null
        try {
            unregisterReceiver(actionReceiver)
        } catch (e: Exception) {
            // Ignore
        }
    }
    
    private fun startTimer() {
        timer?.cancel()
        timer = Timer()
        timer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                if (!isPaused && remainingSeconds > 0) {
                    remainingSeconds--
                    updateNotification()
                }
            }
        }, 1000, 1000)
    }
    
    private fun updateNotification() {
        try {
            notificationManager.notify(NOTIFICATION_ID, buildNotification())
        } catch (e: SecurityException) {
            // 权限被拒绝
        }
    }
    
    private fun buildNotification(): Notification {
        val minutes = remainingSeconds / 60
        val seconds = remainingSeconds % 60
        val timeString = String.format("%02d:%02d", minutes, seconds)
        
        val totalMinutes = totalSeconds / 60
        val progressPercent = if (totalSeconds > 0) {
            ((totalSeconds - remainingSeconds) * 100 / totalSeconds)
        } else 0
        
        val contentIntent = Intent(this, Class.forName("takagi.ru.saison.MainActivity")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "pomodoro")
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val statusText = if (isPaused) "已暂停" else "专注中"
        val progressText = "$progressPercent% · $timeString / ${totalMinutes}分钟"
        
        val builder = NotificationCompat.Builder(this, NotificationChannels.POMODORO_TIMER)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("$taskName · $statusText")
            .setContentText(progressText)
            .setSubText(timeString)
            .setContentIntent(contentPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setProgress(totalSeconds, totalSeconds - remainingSeconds, false)
            .setShowWhen(false)
            .setUsesChronometer(false)  // 使用自定义时间显示
        
        // 添加操作按钮
        if (isPaused) {
            // 暂停状态：显示继续和停止按钮
            builder.addAction(buildResumeAction())
            builder.addAction(buildStopAction())
        } else {
            // 运行状态：显示暂停和停止按钮
            builder.addAction(buildPauseAction())
            builder.addAction(buildStopAction())
        }
        
        return builder.build()
    }
    
    private fun buildPauseAction(): NotificationCompat.Action {
        val intent = Intent(ACTION_PAUSE).apply {
            setPackage(packageName)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action.Builder(0, "暂停", pendingIntent).build()
    }
    
    private fun buildResumeAction(): NotificationCompat.Action {
        val intent = Intent(ACTION_RESUME).apply {
            setPackage(packageName)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            2,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action.Builder(0, "继续", pendingIntent).build()
    }
    
    private fun buildStopAction(): NotificationCompat.Action {
        val intent = Intent(ACTION_STOP).apply {
            setPackage(packageName)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            3,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action.Builder(0, "停止", pendingIntent).build()
    }
    
    private fun broadcastToViewModel(actionType: String) {
        when (actionType) {
            "pause" -> PomodoroEventBus.emitEvent(PomodoroEvent.Pause)
            "resume" -> PomodoroEventBus.emitEvent(PomodoroEvent.Resume)
            "stop" -> PomodoroEventBus.emitEvent(PomodoroEvent.Stop)
        }
    }
}
