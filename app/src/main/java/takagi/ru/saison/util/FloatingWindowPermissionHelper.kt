package takagi.ru.saison.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import takagi.ru.saison.ui.todo_floating.FloatingTodoService

object FloatingWindowPermissionHelper {

    /**
     * 检查是否拥有悬浮窗权限
     */
    fun hasOverlayPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true // 低版本系统默认有权限
        }
    }

    /**
     * 获取跳转到悬浮窗权限设置页面的Intent
     */
    fun getPermissionIntent(context: Context): Intent {
        return Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
    }

    /**
     * 启动悬浮窗Service
     */
    fun startFloatingService(context: Context) {
        if (hasOverlayPermission(context)) {
            val intent = Intent(context, FloatingTodoService::class.java)
            context.startService(intent)
        }
    }
    
    /**
     * 停止悬浮窗Service
     */
    fun stopFloatingService(context: Context) {
        val intent = Intent(context, FloatingTodoService::class.java)
        context.stopService(intent)
    }
}
