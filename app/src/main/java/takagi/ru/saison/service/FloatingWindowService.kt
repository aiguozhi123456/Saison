package takagi.ru.saison.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import takagi.ru.saison.MainActivity
import takagi.ru.saison.R
import takagi.ru.saison.ui.components.floating.FloatingWindowContent
import kotlin.math.abs

/**
 * 悬浮窗服务
 * 在其他应用之上显示可拖动的悬浮窗
 */
class FloatingWindowService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    companion object {
        const val NOTIFICATION_ID = 2001
        const val CHANNEL_ID = "floating_window_channel"

        const val ACTION_SHOW = "takagi.ru.saison.FLOATING_SHOW"
        const val ACTION_HIDE = "takagi.ru.saison.FLOATING_HIDE"
        const val ACTION_TOGGLE = "takagi.ru.saison.FLOATING_TOGGLE"

        fun startService(context: Context) {
            val intent = Intent(context, FloatingWindowService::class.java).apply {
                action = ACTION_SHOW
            }
            context.startService(intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, FloatingWindowService::class.java).apply {
                action = ACTION_HIDE
            }
            context.startService(intent)
        }

        fun toggleService(context: Context) {
            val intent = Intent(context, FloatingWindowService::class.java).apply {
                action = ACTION_TOGGLE
            }
            context.startService(intent)
        }

        fun isRunning(): Boolean {
            return instance != null
        }

        private var instance: FloatingWindowService? = null
    }

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var params: WindowManager.LayoutParams? = null

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    override fun onCreate() {
        super.onCreate()
        instance = this
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED

        createNotificationChannel()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> {
                if (floatingView == null) {
                    showFloatingWindow()
                    startForeground(NOTIFICATION_ID, buildNotification())
                    lifecycleRegistry.currentState = Lifecycle.State.RESUMED
                }
            }
            ACTION_HIDE -> {
                hideFloatingWindow()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
            }
            ACTION_TOGGLE -> {
                if (floatingView == null) {
                    showFloatingWindow()
                    startForeground(NOTIFICATION_ID, buildNotification())
                    lifecycleRegistry.currentState = Lifecycle.State.RESUMED
                } else {
                    hideFloatingWindow()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        hideFloatingWindow()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        instance = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "悬浮窗服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保持悬浮窗服务运行"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val hideIntent = Intent(this, FloatingWindowService::class.java).apply {
            action = ACTION_HIDE
        }
        val hidePendingIntent = PendingIntent.getService(
            this,
            1,
            hideIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("悬浮窗已开启")
            .setContentText("点击关闭悬浮窗")
            .setContentIntent(contentPendingIntent)
            .addAction(0, "关闭", hidePendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun showFloatingWindow() {
        if (floatingView != null) return

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 200
        }
        params = layoutParams

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingWindowService)
            setViewTreeSavedStateRegistryOwner(this@FloatingWindowService)
            setContent {
                FloatingWindowContent(
                    onClose = {
                        stopSelf()
                    },
                    onOpenApp = {
                        val intent = Intent(this@FloatingWindowService, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        startActivity(intent)
                    }
                )
            }
        }

        container.addView(composeView)

        container.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    layoutParams.x = initialX + (event.rawX - initialTouchX).toInt()
                    layoutParams.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager?.updateViewLayout(container, layoutParams)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    abs(dx) < 10 && abs(dy) < 10
                }
                else -> false
            }
        }

        floatingView = container
        windowManager?.addView(container, layoutParams)
    }

    private fun hideFloatingWindow() {
        floatingView?.let {
            windowManager?.removeView(it)
            floatingView = null
        }
    }
}
