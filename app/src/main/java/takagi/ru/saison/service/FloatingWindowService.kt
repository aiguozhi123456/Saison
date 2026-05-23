package takagi.ru.saison.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import android.view.Gravity
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

class FloatingWindowService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    companion object {
        const val NOTIFICATION_ID = 2001
        const val CHANNEL_ID = "floating_window_channel"

        const val ACTION_SHOW = "takagi.ru.saison.FLOATING_SHOW"
        const val ACTION_HIDE = "takagi.ru.saison.FLOATING_HIDE"
        const val ACTION_TOGGLE = "takagi.ru.saison.FLOATING_TOGGLE"

        private const val COLLAPSED_WIDTH_DP = 24
        private const val EXPANDED_WIDTH_DP = 180
        private const val TAG = "FloatingWindowService"

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

    private var screenWidthPx = 0
    private var screenHeightPx = 0
    private var isOnRightEdge = true

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED

        createNotificationChannel()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        refreshScreenSize()
    }

    @Suppress("DEPRECATION")
    private fun refreshScreenSize() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager!!.currentWindowMetrics.bounds
            screenWidthPx = bounds.width()
            screenHeightPx = bounds.height()
        } else {
            val display = windowManager!!.defaultDisplay
            val size = Point()
            display.getSize(size)
            screenWidthPx = size.x
            screenHeightPx = size.y
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent.action) {
            ACTION_SHOW -> {
                if (floatingView == null) {
                    showFloatingWindow()
                    startForegroundCompat(NOTIFICATION_ID, buildNotification())
                    lifecycleRegistry.currentState = Lifecycle.State.RESUMED
                }
            }
            ACTION_HIDE -> {
                hideFloatingWindow()
                stopForegroundCompat()
                stopSelf()
                lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
            }
            ACTION_TOGGLE -> {
                if (floatingView == null) {
                    showFloatingWindow()
                    startForegroundCompat(NOTIFICATION_ID, buildNotification())
                    lifecycleRegistry.currentState = Lifecycle.State.RESUMED
                } else {
                    hideFloatingWindow()
                    stopForegroundCompat()
                    stopSelf()
                    lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundCompat(id: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(id, notification)
        }
    }

    @Suppress("DEPRECATION")
    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            stopForeground(true)
        }
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

        refreshScreenSize()
        val collapsedWidthPx = dpToPx(COLLAPSED_WIDTH_DP)

        val layoutParams = WindowManager.LayoutParams(
            collapsedWidthPx,
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
            x = screenWidthPx - collapsedWidthPx
            y = screenHeightPx / 3
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
                    onExpand = { resizeWindow(EXPANDED_WIDTH_DP) },
                    onCollapse = { resizeWindow(COLLAPSED_WIDTH_DP) },
                    onDrag = { dx, dy -> updateWindowPosition(dx, dy) },
                    onDragEnd = { snapToEdge() },
                    onClose = { stopSelf() },
                    onNavigate = { route -> navigateToRoute(route) }
                )
            }
        }

        container.addView(composeView)

        floatingView = container
        windowManager?.addView(container, layoutParams)
    }

    private fun resizeWindow(targetWidthDp: Int) {
        refreshScreenSize()
        val view = floatingView ?: return
        params?.let { p ->
            val targetWidthPx = dpToPx(targetWidthDp)
            val oldWidth = p.width
            p.width = targetWidthPx
            if (isOnRightEdge) {
                p.x = p.x + oldWidth - targetWidthPx
            }
            safeUpdateLayout(view, p)
        }
    }

    private fun updateWindowPosition(dx: Float, dy: Float) {
        val view = floatingView ?: return
        params?.let { p ->
            p.x += dx.toInt()
            p.y += dy.toInt()
            p.x = p.x.coerceIn(0, (screenWidthPx - p.width).coerceAtLeast(0))
            p.y = p.y.coerceIn(0, (screenHeightPx - dpToPx(56)).coerceAtLeast(0))
            safeUpdateLayout(view, p)
        }
    }

    private fun snapToEdge() {
        refreshScreenSize()
        val view = floatingView ?: return
        params?.let { p ->
            val centerX = p.x + p.width / 2
            isOnRightEdge = centerX > screenWidthPx / 2
            p.x = if (isOnRightEdge) {
                screenWidthPx - p.width
            } else {
                0
            }
            safeUpdateLayout(view, p)
        }
    }

    private fun navigateToRoute(route: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("floating_navigate_to", route)
        }
        startActivity(intent)
    }

    private fun safeUpdateLayout(view: View, p: WindowManager.LayoutParams) {
        try {
            windowManager?.updateViewLayout(view, p)
        } catch (e: Exception) {
            Log.w(TAG, "WindowManager updateViewLayout failed", e)
        }
    }

    private fun hideFloatingWindow() {
        floatingView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                Log.w(TAG, "WindowManager removeView failed", e)
            }
            floatingView = null
        }
    }
}
