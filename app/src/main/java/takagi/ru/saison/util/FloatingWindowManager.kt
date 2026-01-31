package takagi.ru.saison.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import takagi.ru.saison.service.FloatingWindowService

/**
 * 悬浮窗管理器
 * 管理悬浮窗权限和服务的启动/停止
 */
object FloatingWindowManager {

    /**
     * 检查是否有悬浮窗权限
     */
    fun canDrawOverlays(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    /**
     * 请求悬浮窗权限
     */
    fun requestPermission(activity: Activity) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${activity.packageName}")
        )
        activity.startActivity(intent)
    }

    /**
     * 打开悬浮窗
     */
    fun showFloatingWindow(context: Context) {
        if (canDrawOverlays(context)) {
            FloatingWindowService.startService(context)
        }
    }

    /**
     * 关闭悬浮窗
     */
    fun hideFloatingWindow(context: Context) {
        FloatingWindowService.stopService(context)
    }

    /**
     * 切换悬浮窗状态
     */
    fun toggleFloatingWindow(context: Context) {
        if (canDrawOverlays(context)) {
            FloatingWindowService.toggleService(context)
        }
    }

    /**
     * 检查悬浮窗是否正在运行
     */
    fun isFloatingWindowRunning(): Boolean {
        return FloatingWindowService.isRunning()
    }
}
