package takagi.ru.saison.ui.components

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import takagi.ru.saison.util.FloatingWindowPermissionHelper

@Composable
fun FloatingWindowButton() {
    val context = LocalContext.current
    val activity = context as? Activity

    // 用于处理权限请求结果的Launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        // 权限请求返回后，再次检查权限并尝试启动Service
        if (FloatingWindowPermissionHelper.hasOverlayPermission(context)) {
            FloatingWindowPermissionHelper.startFloatingService(context)
        }
    }

    FloatingActionButton(
        onClick = {
            if (FloatingWindowPermissionHelper.hasOverlayPermission(context)) {
                // 已有权限，直接启动Service
                FloatingWindowPermissionHelper.startFloatingService(context)
            } else {
                // 无权限，请求权限
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val intent = FloatingWindowPermissionHelper.getPermissionIntent(context)
                    permissionLauncher.launch(intent)
                }
            }
        }
    ) {
        Icon(Icons.Default.List, contentDescription = "待办悬浮窗")
    }
}
