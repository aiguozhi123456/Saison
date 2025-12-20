package takagi.ru.saison.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import takagi.ru.saison.service.PomodoroEvent
import takagi.ru.saison.service.PomodoroEventBus
import takagi.ru.saison.service.PomodoroTimerService

/**
 * 番茄钟通知操作接收器
 * 处理来自通知栏的暂停/继续/停止操作
 */
class PomodoroActionReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context?, intent: Intent?) {
        context ?: return
        
        val event = when (intent?.action) {
            PomodoroTimerService.ACTION_PAUSE -> PomodoroEvent.Pause
            PomodoroTimerService.ACTION_RESUME -> PomodoroEvent.Resume
            PomodoroTimerService.ACTION_STOP -> PomodoroEvent.Stop
            else -> return
        }
        
        // 转发到服务
        val serviceIntent = Intent(context, PomodoroTimerService::class.java).apply {
            action = intent.action
        }
        context.startService(serviceIntent)
        
        // 通知 ViewModel
        PomodoroEventBus.emitEvent(event)
    }
}
