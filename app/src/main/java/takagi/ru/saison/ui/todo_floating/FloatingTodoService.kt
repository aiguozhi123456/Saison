package takagi.ru.saison.ui.todo_floating

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
import androidx.compose.ui.platform.ComposeView
import androidx.hilt.work.HiltWorkerFactory
import dagger.hilt.android.AndroidEntryPoint
import takagi.ru.saison.ui.theme.SaisonTheme
import javax.inject.Inject

@AndroidEntryPoint
class FloatingTodoService : Service() {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory // 注入HiltWorkerFactory以确保ComposeView中的ViewModel可以正确注入

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        floatingView = ComposeView(this).apply {
            setContent {
                SaisonTheme {
                    FloatingTodoScreen(
                        onClose = { stopSelf() }
                    )
                }
            }
        }

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 100
        }

        // 添加拖动逻辑
        floatingView.setOnTouchListener(object : View.OnTouchListener {
            private var initialX: Int = 0
            private var initialY: Int = 0
            private var initialTouchX: Float = 0f
            private var initialTouchY: Float = 0f
            private val clickThreshold = 5.dp.toPx(resources.displayMetrics)

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(floatingView, params)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        val finalX = event.rawX
                        val finalY = event.rawY
                        val dx = finalX - initialTouchX
                        val dy = finalY - initialTouchY
                        
                        // 判断是否为点击事件（移动距离小于阈值）
                        if (kotlin.math.abs(dx) < clickThreshold && kotlin.math.abs(dy) < clickThreshold) {
                            // 模拟点击事件，让ComposeView处理内部点击
                            v.performClick()
                        }
                        return true
                    }
                }
                return false
            }
        })

        windowManager.addView(floatingView, params)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::floatingView.isInitialized) {
            windowManager.removeView(floatingView)
        }
    }
    
    // 扩展函数，用于将dp转换为px
    private fun Int.toPx(displayMetrics: android.util.DisplayMetrics): Float {
        return this * displayMetrics.density
    }
}
